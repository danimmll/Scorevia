"""
Backfill de partidos por rango de fechas.

Una llamada por DÍA, y cada una trae todos los partidos del mundo de esa fecha
(`fixtures?date=`), así que un rango de tres meses cuesta ~90 peticiones de API-Football:
es, con diferencia, la ingesta más barata que hay y la primera que conviene lanzar cuando
se recupera cuota.

Por defecto arranca en el día siguiente al último partido que ya hay en la base de datos y
llega hasta hoy, que es lo que hace falta para ponerse al día con la temporada en curso. Se
puede fijar el rango a mano con las variables de entorno FECHA_INICIO / FECHA_FIN
(formato YYYY-MM-DD).

Sirve igual para el futuro: `fixtures?date=` devuelve el calendario ya publicado, no solo
partidos jugados. Sondeando la API el 2026-09-04, el calendario llega hasta **mediados de
mayo de 2027** (el 15/05/2027 tiene 514 partidos) y a partir del 31/05/2027 devuelve 0,
porque la temporada 27/28 todavía no está publicada. Pedir más allá de ahí no rompe nada,
simplemente gasta una petición por día para no traer nada.
"""

from _consola import forzar_utf8
import os
import sys
import time
from datetime import date, timedelta

import requests
from _entorno import cargar_entorno, config_bd

# La consola de Windows es cp1252 y revienta con el primer emoji que se imprima.
forzar_utf8()

cargar_entorno()

TOKEN_JWT = os.environ.get("ADMIN_JWT", "")
ADMIN_EMAIL = os.environ.get("ADMIN_EMAIL", "")
ADMIN_PASSWORD = os.environ.get("ADMIN_PASSWORD", "")

BASE_URL = os.environ.get("SCOREVIA_BASE_URL", "http://localhost:8085")
# La API se unificó bajo /api/v1/** el 2026-09-01: la ruta suelta /partidos/sync que usaba
# este script devuelve 404 desde entonces.
URL_SYNC = f"{BASE_URL}/api/v1/partidos/sync"
URL_LOGIN = f"{BASE_URL}/api/v1/auth/login"

DB_CONFIG = config_bd()

MAX_INTENTOS = 5
CODIGOS_TRANSITORIOS = {429, 502, 503, 504}
ESPERA_ENTRE_FECHAS = 0.3

# Un 503 con "instance":"/fallback" no viene de API-Football: es el circuit breaker del
# Gateway, que al abrirse rechaza cualquier petición al instante durante
# `wait-duration-in-open-state` (10s). Reintentar a los 2s solo gasta intentos dentro de esa
# ventana, así que aquí se espera bastante más que ante un error de red normal.
ESPERA_CIRCUITO_ABIERTO = 15

# Cuánto se espera a que el backend vuelva antes de rendirse. Un microservicio recién
# desplegado tarda entre 220s y 355s en arrancar (compiten seis JVM por CPU), así que
# cinco minutos se quedan cortos si el reinicio pilla la máquina cargada.
ESPERA_BACKEND_CAIDO = 600

# El backend ingiere el día entero de forma síncrona antes de contestar, y hay días con
# más de 700 partidos en todo el mundo (el 2026-10-04 tiene 705). Con 60s se cortaba la
# conexión a mitad de ingesta: la petición a API-Football ya estaba gastada, pero el
# script la contaba como fallo y la repetía.
TIMEOUT_SYNC = 180


def ultima_fecha_en_bd():
    """Último partido almacenado, para no repetir lo que ya está ingerido."""
    try:
        import psycopg2
    except ImportError:
        return None
    try:
        with psycopg2.connect(**DB_CONFIG) as conn, conn.cursor() as cur:
            cur.execute("SELECT MAX(fecha)::date FROM sch_estadisticas.partidos")
            fila = cur.fetchone()
            return fila[0] if fila else None
    # Cualquier fallo aquí (BD caída, credenciales, red) solo impide calcular el rango
    # por defecto; no es motivo para abortar, se avisa y se pide FECHA_INICIO.
    except Exception as e:  # noqa: BLE001
        print(f"⚠️  No se pudo leer la última fecha de la BD ({e}); usa FECHA_INICIO a mano.")
        return None


def resolver_rango():
    inicio_env = os.environ.get("FECHA_INICIO")
    fin_env = os.environ.get("FECHA_FIN")

    fin = date.fromisoformat(fin_env) if fin_env else date.today()
    if inicio_env:
        return date.fromisoformat(inicio_env), fin

    ultima = ultima_fecha_en_bd()
    if ultima is None:
        print("❌ Sin FECHA_INICIO y sin poder consultar la BD. Define FECHA_INICIO=YYYY-MM-DD.")
        sys.exit(1)

    print(f"📅 Último partido en la base de datos: {ultima}")
    return ultima + timedelta(days=1), fin


def iniciar_sesion():
    """
    Pide un accessToken recién hecho con ADMIN_EMAIL / ADMIN_PASSWORD.

    Antes había que copiar a mano un token en ADMIN_JWT, y como los access token caducan
    en minutos, cada backfill empezaba por un login manual y a media ingesta empezaban a
    caer 401. Con las credenciales en el .env el script se autentica solo.
    """
    if not ADMIN_EMAIL or not ADMIN_PASSWORD:
        return None
    try:
        resp = requests.post(
            URL_LOGIN,
            json={"correoUsuario": ADMIN_EMAIL, "contrasenhaUsuario": ADMIN_PASSWORD},
            timeout=30,
        )
    except requests.exceptions.RequestException as e:
        print(f"⚠️  No se pudo contactar con {URL_LOGIN}: {e}")
        return None

    if resp.status_code != 200:
        print(f"⚠️  Login rechazado (HTTP {resp.status_code}) para {ADMIN_EMAIL}.")
        return None

    token = resp.json().get("accessToken")
    if token:
        print(f"🔑 Sesión iniciada como {ADMIN_EMAIL}.")
    return token


def resolver_token(session):
    """
    Deja la sesión autenticada, o corta con un mensaje que diga qué falta.

    Se prefiere el login automático (token fresco) y se cae a ADMIN_JWT solo si no hay
    credenciales, para no romper a quien ya tuviera el .env montado a la antigua.
    """
    token = iniciar_sesion() or TOKEN_JWT
    if not token or token.startswith("<"):
        print("❌ No hay forma de autenticarse.")
        print("   Añade al .env (lo más cómodo, se renueva solo):")
        print("     ADMIN_EMAIL=admin@gmail.com")
        print("     ADMIN_PASSWORD=tu_password")
        print("   o, si lo prefieres a mano, un ADMIN_JWT recién sacado del login.")
        sys.exit(1)

    session.headers.update({"Authorization": f"Bearer {token}"})

    sonda = session.post(f"{URL_SYNC}?fecha={date.today().isoformat()}", timeout=TIMEOUT_SYNC)
    if sonda.status_code in (401, 403):
        print(f"❌ El token no vale (HTTP {sonda.status_code}). ¿Caducado, o de un usuario sin ROLE_ADMIN?")
        sys.exit(1)
    if sonda.status_code == 404:
        print(f"❌ 404 en {URL_SYNC}. ¿Está el Gateway levantado y con las rutas /api/v1/**?")
        sys.exit(1)
    print(f"🔑 Token válido (sonda de hoy: HTTP {sonda.status_code}).")


def renovar_sesion(session):
    """
    Vuelve a autenticarse a mitad de ejecución.

    El access token dura 15 minutos (JwtTokenServiceImpl) y un backfill de una temporada
    entera son varias horas. Sin esto, a los 15 minutos empezaban a caer 401: como 401 no
    es un código transitorio, cada fecha se daba por fallida al instante y el script
    recorría los cientos de días restantes en segundos sin ingerir nada, terminando con un
    "hecho" engañoso. Pasó de verdad el 2026-09-04: 37 días ingeridos de 304.
    """
    token = iniciar_sesion()
    if not token:
        return False
    session.headers.update({"Authorization": f"Bearer {token}"})
    return True


# Resultado de un intento: qué hacer a continuación, y cuántos segundos esperar antes.
_HECHO, _REINTENTAR, _RENDIRSE = "hecho", "reintentar", "rendirse"


class _BackendCaido(Exception):
    """El backend no responde; no tiene sentido seguir probando fechas."""


def _evaluar(session, respuesta, fecha_str, intento, quedan_intentos):
    """Traduce la respuesta del servidor en (qué hacer, cuánto esperar antes de reintentar)."""
    if respuesta.status_code == 200:
        print(f"  ✅ {fecha_str}: {respuesta.text.strip()[:120]}")
        return _HECHO, 0

    # Token caducado a mitad de ejecución: se renueva y se reintenta esta misma fecha ya.
    if respuesta.status_code in (401, 403) and quedan_intentos:
        print(f"  🔄 {fecha_str}: sesión caducada (HTTP {respuesta.status_code}), renovando...")
        if renovar_sesion(session):
            return _REINTENTAR, 0
        print("  ❌ No se pudo renovar la sesión. Se detiene el backfill.")
        raise SystemExit(1)

    print(f"  ❌ {fecha_str}: HTTP {respuesta.status_code} {respuesta.text.strip()[:160]}")
    if respuesta.status_code not in CODIGOS_TRANSITORIOS or not quedan_intentos:
        return _RENDIRSE, 0

    # Un 503 es el circuit breaker del Gateway: hay que esperar más que en un error normal.
    base = ESPERA_CIRCUITO_ABIERTO if respuesta.status_code == 503 else 2
    return _REINTENTAR, base * intento


def esperar_a_que_vuelva(session):
    """
    Espera a que el backend responda otra vez, en vez de seguir quemando fechas.

    Si el Gateway se cae o se reinicia (un despliegue a media ejecución, por ejemplo), sin
    esto el script se comía el calendario entero a golpe de ConnectionError: cada fecha
    agotaba sus reintentos en segundos y se daba por fallida. Pasó el 2026-09-04 y se
    perdieron 13 días seguidos así.
    """
    print(f"\n⏸️  El backend no responde. Esperando a que vuelva (hasta {ESPERA_BACKEND_CAIDO // 60} min)...")
    limite = time.time() + ESPERA_BACKEND_CAIDO
    while time.time() < limite:
        time.sleep(15)
        try:
            if session.get(f"{BASE_URL}/actuator/health", timeout=10).status_code < 500:
                print("▶️  El backend ha vuelto, se retoma.\n")
                # Tras un reinicio el token viejo puede seguir valiendo, pero renovarlo
                # es una petición y ahorra una ronda entera de 401.
                renovar_sesion(session)
                return True
        except requests.exceptions.RequestException:
            pass
        print("   sigue sin responder...")

    print("\n❌ El backend no ha vuelto. Se detiene el backfill para no quemar el calendario.")
    return False


def _pedir(session, fecha_str, intento, quedan_intentos):
    """Hace la petición. Devuelve la respuesta, o None si hubo error de red y toca reintentar."""
    try:
        return session.post(f"{URL_SYNC}?fecha={fecha_str}", timeout=TIMEOUT_SYNC)
    except requests.exceptions.RequestException as e:
        print(f"  ❌ {fecha_str}: {type(e).__name__} {str(e)[:120]}")
        if quedan_intentos:
            time.sleep(2 * intento)
            return None
        # Agotados los reintentos por problemas de conexión: no es cosa de esta fecha, es
        # que el backend está caído. Se señaliza para que el llamante espere a que vuelva.
        raise _BackendCaido from e


def _intentar_fecha(session, fecha_str):
    """
    Agota los reintentos de una fecha.

    Devuelve True si se ingirió, False si se da por perdida, y None si lo que pasa es que
    el backend no responde (que no es un problema de esta fecha en concreto).
    """
    for intento in range(1, MAX_INTENTOS + 1):
        quedan_intentos = intento < MAX_INTENTOS
        try:
            respuesta = _pedir(session, fecha_str, intento, quedan_intentos)
        except _BackendCaido:
            return None
        if respuesta is None:
            continue

        decision, espera = _evaluar(session, respuesta, fecha_str, intento, quedan_intentos)
        if decision == _HECHO:
            return True
        if decision == _RENDIRSE:
            return False
        if espera:
            print(f"     reintento en {espera}s...")
            time.sleep(espera)
    return False


def sincronizar_fecha(session, fecha_str):
    """Sincroniza una fecha, esperando una vez a que el backend vuelva si se ha caído."""
    for _ in range(2):
        resultado = _intentar_fecha(session, fecha_str)
        if resultado is not None:
            return resultado
        # El backend no responde: se espera a que vuelva y se reintenta la fecha entera.
        # Si no vuelve, se corta -- seguir con el calendario solo produce cientos de
        # fechas "fallidas" en segundos, que es justo lo que pasó el 2026-09-04.
        if not esperar_a_que_vuelva(session):
            raise SystemExit(1)
    return False


def main():
    session = requests.Session()
    resolver_token(session)
    inicio, fin = resolver_rango()

    if inicio > fin:
        print(f"✅ Nada que hacer: la base de datos ya llega a {fin}.")
        return

    dias = (fin - inicio).days + 1
    print(f"🚀 Backfill {inicio} → {fin} ({dias} días ≈ {dias} peticiones a API-Football)")

    ok = 0
    fallidas = []
    fecha_actual = inicio
    interrumpido = False
    try:
        while fecha_actual <= fin:
            if sincronizar_fecha(session, fecha_actual.isoformat()):
                ok += 1
            else:
                fallidas.append(fecha_actual.isoformat())
            fecha_actual += timedelta(days=1)
            time.sleep(ESPERA_ENTRE_FECHAS)
    except KeyboardInterrupt:
        # Ctrl+C es una forma legítima de parar esto, no un error: en vez de escupir un
        # traceback se dice por dónde iba, que es lo único que hace falta para retomarlo.
        interrumpido = True
        print(f"\n\n⏹️  Interrumpido por ti en {fecha_actual}.")
        print("   Lo ya ingerido está guardado. Para retomar desde aquí:")
        print(f"     $env:FECHA_INICIO=\"{fecha_actual}\"; $env:FECHA_FIN=\"{fin}\"; python backfill.py")
        print("   (o sin FECHA_INICIO: lo calcula solo desde el último partido de la BD)")

    print(f"\nTerminado. Días OK: {ok} | con error: {len(fallidas)}")
    if interrumpido and not fallidas:
        return

    if not fallidas:
        print("🎉 Todo el rango ingerido.")
        return

    # Un resumen que solo diga "hecho" esconde una ejecución que falló entera. Se listan las
    # fechas para poder relanzar justo esas, y se avisa fuerte si se cayó más de la mitad.
    print(f"   Fechas con error: {', '.join(fallidas[:15])}{' ...' if len(fallidas) > 15 else ''}")
    print("   Vuelve a lanzarlo: la ingesta es idempotente (upsert por id de partido).")
    if len(fallidas) > ok:
        print("\n⚠️  Han fallado MÁS días de los que han ido bien: esto no es un backfill")
        print("   completado. Mira los errores de arriba antes de darlo por bueno.")


if __name__ == "__main__":
    main()
