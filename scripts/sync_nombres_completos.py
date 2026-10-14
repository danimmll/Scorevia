"""
Rellena la ficha de los jugadores recorriendo el catalogo de API-Football.

**Que rellena.** El nombre sin abreviar y, desde el 08/09/2026, el perfil entero: altura,
peso, nacionalidad, posicion, dorsal, foto, y fecha y lugar de nacimiento. Todo eso viene en
la misma respuesta; antes se leian solo `firstname` y `lastname` y los otros once campos se
tiraban a la basura pagina tras pagina.

**El problema del nombre.** El campo `name` de API-Football llega abreviado ("D. Barrow",
"L. Messi"). El nombre entero esta en `firstname` y `lastname`, que vienen por separado.

**Por que un script y no un `curl`.** El endpoint es de ADMIN y el token caduca a los 15
minutos, asi que una tanda larga necesita renovar la sesion por el camino. Ademas conviene ir
en tandas y poder retomar por donde se quedo.

**El ahorro de cuota, que es lo importante.** `/players/profiles?player=X` cuesta UNA peticion
por jugador. Con 200.509 jugadores sin perfil verificado eso es dia y medio de cuota entera.
La misma ruta sin el parametro `player` devuelve el catalogo paginado a **250 fichas por
peticion**: 2.721 paginas, o sea un 1,8% de la cuota de un dia. **Setenta y cuatro veces
menos peticiones para exactamente los mismos campos.**

**Ojo con el orden.** El catalogo va por id, y los jugadores sin verificar estan casi todos
en la mitad alta (ids de 250.000 en adelante). Las primeras paginas son los veteranos, que ya
estaban completos: no te asustes si las primeras tandas apenas mueven el marcador.

Uso:
    python scripts/sync_nombres_completos.py                  # desde la pagina 1, 200 paginas
    python scripts/sync_nombres_completos.py --desde 201      # retomar donde se quedo
    python scripts/sync_nombres_completos.py --paginas 20     # una tanda corta, para probar
    python scripts/sync_nombres_completos.py --todo           # sin tope de paginas

Las credenciales salen del `.env` del proyecto (ADMIN_EMAIL / ADMIN_PASSWORD), igual que en
los demas scripts: no hay que exportar nada a mano.
"""

import argparse
import os
import sys
import time

import psycopg2
import requests

from _consola import forzar_utf8
from _entorno import cargar_entorno, config_bd

# La consola de Windows es cp1252 y revienta con el primer emoji que se imprima.
forzar_utf8()

cargar_entorno()

ADMIN_EMAIL = os.environ.get("ADMIN_EMAIL", "")
ADMIN_PASSWORD = os.environ.get("ADMIN_PASSWORD", "")
BASE_URL = os.environ.get("SCOREVIA_BASE_URL", "http://localhost:8085")

# El backend procesa la tanda en un hilo aparte y responde 202 al momento, así que la espera
# aquí es corta: solo cubre el arranque de la petición.
TIMEOUT = 60

# El token de admin caduca a los 15 minutos; se renueva antes por si una tanda se alarga.
SEGUNDOS_ENTRE_LOGIN = 10 * 60

# Cada página son 250 fichas. Tandas de 200 páginas = 200 peticiones a la API por tanda.
PAGINAS_POR_TANDA = 200

# El catálogo se acaba mucho antes de la página 100.000, pero el backend contesta 202 sin
# decir si quedaba algo, así que el final se detecta por el lado de la base: dos tandas
# seguidas sin rellenar un solo nombre es que ya no hay catálogo que recorrer.
TANDAS_VACIAS_PARA_PARAR = 2


def marcador_en_bd():
    """
    Lo que lleva rellenado el catalogo. Es el marcador que decide si una tanda ha servido.

    Cuenta DOS cosas, no una. Antes solo miraba los nombres completos, y eso se quedo corto
    en cuanto el recorrido paso a volcar la ficha entera: hay tramos del catalogo donde
    todos los jugadores ya tienen nombre pero ninguno tiene el perfil marcado, asi que la
    tanda rellenaba cientos de perfiles, el contador de nombres no se movia, y a las dos
    tandas asi el script daba el catalogo por terminado y paraba a media faena.
    """
    with psycopg2.connect(**config_bd()) as conexion, conexion.cursor() as cursor:
        cursor.execute(
            "SELECT count(*) FILTER (WHERE nombre_completo IS NOT NULL AND nombre_completo <> ''),"
            "       count(*) FILTER (WHERE perfil_verificado) "
            "FROM sch_estadisticas.jugadores")
        return cursor.fetchone()


def iniciar_sesion(session):
    if not ADMIN_EMAIL or not ADMIN_PASSWORD:
        print("❌ Faltan ADMIN_EMAIL / ADMIN_PASSWORD en el .env del proyecto.")
        sys.exit(1)

    resp = requests.post(
        f"{BASE_URL}/api/v1/auth/login",
        json={"correoUsuario": ADMIN_EMAIL, "contrasenhaUsuario": ADMIN_PASSWORD},
        timeout=30,
    )
    if resp.status_code != 200:
        print(f"❌ Login rechazado (HTTP {resp.status_code}) para {ADMIN_EMAIL}.")
        sys.exit(1)

    session.headers.update({"Authorization": f"Bearer {resp.json()['accessToken']}"})
    return time.time()


def lanzar_tanda(session, desde, paginas):
    """Dispara una tanda. El backend la corre en segundo plano y responde 202."""
    resp = session.post(
        f"{BASE_URL}/api/v1/partidos/nombres-completos",
        params={"desdePagina": desde, "maxPaginas": paginas},
        timeout=TIMEOUT,
    )
    if resp.status_code not in (200, 202):
        print(f"❌ El backend respondió HTTP {resp.status_code}: {resp.text[:200]}")
        return False

    print(f"   {resp.text.strip()}")
    return True


def main():
    parser = argparse.ArgumentParser(
        description="Rellena los nombres completos de los jugadores desde el catálogo de API-Football.")
    parser.add_argument("--desde", type=int, default=1,
                        help="primera página del catálogo (la API empieza en 1)")
    parser.add_argument("--paginas", type=int, default=PAGINAS_POR_TANDA,
                        help="páginas por tanda; cada una son 250 fichas y 1 petición")
    parser.add_argument("--todo", action="store_true",
                        help="encadena tandas hasta terminar el catálogo (unas 2.000 páginas)")
    args = parser.parse_args()

    session = requests.Session()
    ultimo_login = iniciar_sesion(session)
    print(f"🔑 Sesión iniciada como {ADMIN_EMAIL}.")

    # Sin --todo se lanza una sola tanda y se sale: el backend deja en su log por qué página
    # se quedó, para poder retomar con --desde.
    tandas = 0
    pagina = args.desde
    ultimo_marcador = marcador_en_bd() if args.todo else (0, 0)
    vacias = 0
    if args.todo:
        print(f"Punto de partida: {ultimo_marcador[0]} con nombre completo, "
              f"{ultimo_marcador[1]} con el perfil verificado.")
    while True:
        if time.time() - ultimo_login > SEGUNDOS_ENTRE_LOGIN:
            ultimo_login = iniciar_sesion(session)

        print(f"🚀 Tanda {tandas + 1}: páginas {pagina}-{pagina + args.paginas - 1} "
              f"({args.paginas * 250} fichas, {args.paginas} peticiones).")
        if not lanzar_tanda(session, pagina, args.paginas):
            return

        tandas += 1
        pagina += args.paginas

        if not args.todo:
            break

        # El backend responde 202 al instante pero la tanda sigue corriendo por dentro. Sin
        # esta espera, encadenar tandas las solaparía todas a la vez y se saltaría el límite
        # de peticiones por minuto. A ~10 req/s, 200 páginas tardan unos 20 segundos.
        espera = args.paginas / 8
        print(f"   Esperando {espera:.0f}s a que termine antes de la siguiente tanda.\n")
        time.sleep(espera)

        # `--todo` era un `while True` sin salida: el backend contesta 202 tanto si la
        # página traía 250 fichas como si el catálogo se había acabado hacía rato, así que
        # el script seguía pidiendo tandas para siempre. El marcador de verdad, el único
        # que sabe si una tanda ha servido de algo, está en la base.
        marcador = marcador_en_bd()
        nombres = marcador[0] - ultimo_marcador[0]
        perfiles = marcador[1] - ultimo_marcador[1]
        print(f"   Nombres completos: {marcador[0]} (+{nombres}) | "
              f"perfiles verificados: {marcador[1]} (+{perfiles}).\n")
        # Una tanda cuenta como vacia solo si no ha movido NINGUNO de los dos marcadores.
        vacias = vacias + 1 if (nombres == 0 and perfiles == 0) else 0
        ultimo_marcador = marcador
        if vacias >= TANDAS_VACIAS_PARA_PARAR:
            print("Dos tandas seguidas sin rellenar nada: el catálogo está recorrido.")
            break

    print(f"\n✅ Lanzadas {tandas} tanda(s). Mira el log de estadísticas para ver el avance:")
    print("   docker logs -f scorevia-backend-estadisticas-1 | grep 'Nombres completos'")


if __name__ == "__main__":
    main()
