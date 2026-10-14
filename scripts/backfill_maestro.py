"""
Backfill de entidades secundarias de los jugadores: trofeos, bajas y traspasos.

QUÉ PASABA ANTES (05/09/2026)
-----------------------------
El script no avanzaba ni un jugador y se comía la cuota a cambio de nada:

    [1/347795] ❌ duplicate key value violates unique constraint "uq_trofeo_natural"
    [2/347795] ❌ duplicate key value violates unique constraint "uq_trofeo_natural"
    ...

La causa era una comprobación de existencia que no coincidía con el índice único:

    SELECT 1 FROM trofeos WHERE id_jugador = %s AND competicion = %s AND temporada = %s

...mientras que el índice va sobre `COALESCE(temporada, '')`. Cuando la API manda
`"season": null` —que lo hace, y mucho—, la comparación `temporada = NULL` es NULL en SQL,
o sea que NUNCA encuentra la fila que sí está. Resultado: se intentaba insertar un trofeo
que ya existía y saltaba el índice. Además la comprobación ignoraba `pais` y `resultado`,
que también forman parte de la clave natural.

Y lo peor no era el error, sino lo que venía después: el `except` hacía `conn.rollback()`
del jugador entero, así que se perdía también el `UPDATE ultima_sync_bajas`. El jugador
seguía "pendiente" para siempre, las 3 peticiones gastadas en él no servían de nada, y la
siguiente ejecución volvía a empezar por el mismo sitio.

QUÉ HACE AHORA
--------------
1. **ON CONFLICT DO NOTHING** en vez de SELECT-y-luego-INSERT. Deja el problema de los NULL
   sin sitio donde esconderse (es Postgres quien decide qué es duplicado, con su propio
   índice) y quita una consulta por cada fila de cada jugador.

2. **De 20 en 20**. `/trophies` y `/sidelined` aceptan `players=id-id-id` con un máximo de
   20 ids, y la respuesta viene agrupada por jugador (`{"id": 93371, "trophies": [...]}`).
   Eso convierte 2 peticiones por jugador en 2 por cada veinte: para los 347.795 jugadores
   pendientes, de ~695.000 peticiones a ~34.800.

3. **Un jugador raro ya no tira el lote**: cada uno va dentro de su SAVEPOINT, así que si
   uno falla se descarta solo él y los otros 19 se guardan igual (con sus peticiones ya
   pagadas).

LOS TRASPASOS VAN APARTE
------------------------
`/transfers` NO admite varios jugadores por petición (solo `player` o `team`), así que por
jugador cuesta 1 petición: 347.795 más. Por eso están detrás de `--con-traspasos` y
apagados por defecto. La vía barata para eso es por equipo, que ya existe en el backend:
`POST /api/v1/partidos/sync-traspasos` recorre los equipos, no los jugadores.
"""

from _consola import forzar_utf8
import os
import sys
import time
import requests
import psycopg2
from psycopg2.extras import execute_values
from _entorno import cargar_entorno, config_bd
from _cuota import guardia_cuota, CuotaDiariaAgotada

# La consola de Windows es cp1252 y revienta con el primer emoji que se imprima.
forzar_utf8()

cargar_entorno()

DB_CONFIG = config_bd()

API_KEY = os.environ.get("API_FOOTBALL_KEY", "TU_API_KEY_AQUI")
URL_TROFEOS = "https://v3.football.api-sports.io/trophies"
URL_LESIONES = "https://v3.football.api-sports.io/sidelined"
URL_TRASPASOS = "https://v3.football.api-sports.io/transfers"

# Tope que impone la API al parámetro `players` de /trophies y /sidelined.
TAMANO_LOTE = 20

ESPERA_ENTRE_PETICIONES = 0.01
MAX_REINTENTOS = 4

# Los traspasos por jugador cuestan una petición cada uno (la API no los agrupa), así que
# no entran salvo que se pidan a propósito.
CON_TRASPASOS = "--con-traspasos" in sys.argv

session = requests.Session()
session.headers.update({"x-apisports-key": API_KEY})


class LimiteApiAlcanzado(Exception):
    """Se lanza cuando la API confirma que se ha agotado la cuota de peticiones."""


def hacer_peticion(url, params):
    for intento in range(MAX_REINTENTOS):
        try:
            resp = session.get(url, params=params, timeout=20)

            # Suelo de cuota DIARIA. Lo de abajo mira el límite por minuto, que es otra cosa:
            # sin esto, un backfill largo se lleva por delante la cuota del día entero y deja
            # sin nada al directo y a la ingesta post-partido, que ahora corren solos.
            try:
                guardia_cuota(resp)
            except CuotaDiariaAgotada as e:
                print(f"🛑 {e}")
                raise LimiteApiAlcanzado() from e

            remaining = int(resp.headers.get('x-ratelimit-remaining', 900))

            if remaining < 10:
                print("⏳ Quedan menos de 10 peticiones. Esperando 60s por seguridad...")
                time.sleep(60)

            if resp.status_code == 200:
                data = resp.json()
                if "errors" in data and data["errors"]:
                    if isinstance(data["errors"], dict) and "requests" in data["errors"]:
                        print("⚠️ LÍMITE DE API ALCANZADO.")
                        raise LimiteApiAlcanzado()
                return data
            elif resp.status_code == 429:
                print(f"⏳ Rate limit (429). Esperando 5s... (Intento {intento+1})")
                time.sleep(5)
            else:
                espera = 2 ** intento
                print(f"❌ HTTP {resp.status_code}. Esperando {espera}s... (Intento {intento+1})")
                time.sleep(espera)
        except LimiteApiAlcanzado:
            raise
        except Exception as e:
            espera = 2 ** intento
            print(f"❌ Error de red: {e}. Esperando {espera}s... (Intento {intento+1})")
            time.sleep(espera)
    return None


def trocear(lista, tamano):
    """Parte una lista en trozos del tamaño que admite la API."""
    for i in range(0, len(lista), tamano):
        yield lista[i:i + tamano]


def pedir_por_lote(url, ids_jugadores, clave_lista):
    """
    Pide un endpoint que admite varios jugadores y devuelve {id_jugador: [items]}.

    La respuesta agrupada trae `{"id": 93371, "<clave_lista>": [...]}` por jugador, y
    **omite** a los jugadores de los que no tiene nada. Por eso quien llama tiene que seguir
    marcando como sincronizados a TODOS los del lote, no solo a los que vuelven: si no, los
    que no tienen trofeos se quedarían pendientes para siempre.
    """
    parametro = "-".join(str(i) for i in ids_jugadores)
    data = hacer_peticion(url, {"players": parametro})
    if not data:
        return {}

    por_jugador = {}
    for entrada in data.get("response", []) or []:
        id_jugador = entrada.get("id")
        if id_jugador is not None:
            por_jugador[id_jugador] = entrada.get(clave_lista, []) or []
    return por_jugador


def procesar_trofeos(cursor, id_jugador, trofeos):
    filas = []
    vistos = set()

    for item in trofeos:
        competicion = item.get("league")
        resultado = item.get("place")
        # Las dos son NOT NULL en la tabla: una fila sin ellas reventaría el INSERT entero
        # del lote, y un trofeo sin competición ni resultado no dice nada de todas formas.
        if not competicion or not resultado:
            continue

        pais = item.get("country")
        temporada = item.get("season")

        # La clave del dedupe interno es la MISMA que la del índice único (competición, país,
        # temporada y resultado). Antes era solo (competición, temporada), así que un "Winner"
        # y un "2nd Place" de la misma competición se pisaban entre ellos.
        clave = (competicion, pais, temporada, resultado)
        if clave in vistos:
            continue
        vistos.add(clave)

        filas.append((id_jugador, competicion, pais, temporada, resultado))

    if not filas:
        return 0

    # ON CONFLICT DO NOTHING sin nombrar columnas: uq_trofeo_natural es un índice sobre
    # EXPRESIONES (COALESCE de media clave), y esa forma es la única que lo cubre sin tener
    # que repetir aquí los COALESCE uno a uno.
    execute_values(cursor, """
        INSERT INTO sch_estadisticas.trofeos
        (id_jugador, competicion, pais, temporada, resultado)
        VALUES %s
        ON CONFLICT DO NOTHING
    """, filas)
    return cursor.rowcount


def procesar_lesiones(cursor, id_jugador, lesiones):
    filas = []
    vistos = set()

    for item in lesiones:
        tipo = item.get("type")
        fecha_inicio = item.get("start")
        # tipo y fecha_inicio son NOT NULL y forman el único junto con id_jugador.
        if not tipo or not fecha_inicio:
            continue

        clave = (tipo, fecha_inicio)
        if clave in vistos:
            continue
        vistos.add(clave)

        filas.append((id_jugador, tipo, fecha_inicio, item.get("end")))

    if not filas:
        return 0

    execute_values(cursor, """
        INSERT INTO sch_estadisticas.historial_bajas
        (id_jugador, tipo, fecha_inicio, fecha_fin)
        VALUES %s
        ON CONFLICT DO NOTHING
    """, filas)
    return cursor.rowcount


def procesar_traspasos(cursor, id_jugador, data_traspasos):
    filas = []
    equipos_faltantes = {}
    vistos = set()

    for traspaso_grupo in data_traspasos:
        for transaccion in traspaso_grupo.get("transfers", []) or []:
            equipos = transaccion.get("teams", {}) or {}
            equipo_origen = equipos.get("out") or {}
            equipo_destino = equipos.get("in") or {}

            id_eq_origen = equipo_origen.get("id")
            id_eq_destino = equipo_destino.get("id")
            fecha = transaccion.get("date")
            tipo = transaccion.get("type")

            # `fecha` forma parte del índice único y no lleva COALESCE: sin ella la fila no
            # se puede deduplicar de ninguna manera.
            if not fecha:
                continue

            if id_eq_origen:
                equipos_faltantes[id_eq_origen] = equipo_origen.get("name")
            if id_eq_destino:
                equipos_faltantes[id_eq_destino] = equipo_destino.get("name")

            # Otra vez la clave natural completa, la misma que uq_traspaso_natural.
            clave = (fecha, id_eq_origen, id_eq_destino, tipo)
            if clave in vistos:
                continue
            vistos.add(clave)

            filas.append((id_jugador, fecha, tipo, id_eq_origen, id_eq_destino))

    if equipos_faltantes:
        # Los equipos van primero por la FK: un traspaso hacia un club que no está en la base
        # fallaría entero.
        execute_values(cursor, """
            INSERT INTO sch_estadisticas.equipos (id_equipo, nombre)
            VALUES %s
            ON CONFLICT (id_equipo) DO NOTHING
        """, [(k, v) for k, v in equipos_faltantes.items()])

    if not filas:
        return 0

    execute_values(cursor, """
        INSERT INTO sch_estadisticas.traspasos
        (id_jugador, fecha, tipo, id_equipo_origen, id_equipo_destino)
        VALUES %s
        ON CONFLICT DO NOTHING
    """, filas)
    return cursor.rowcount


def procesar_jugador(cursor, id_jugador, trofeos, lesiones):
    """
    Guarda lo de un jugador dentro de su propio SAVEPOINT.

    Sin esto, un jugador con un dato raro abortaba la transacción del lote entero y se
    perdían los otros 19 — con sus peticiones ya gastadas.
    """
    cursor.execute("SAVEPOINT jugador")
    try:
        n_trofeos = procesar_trofeos(cursor, id_jugador, trofeos)
        n_lesiones = procesar_lesiones(cursor, id_jugador, lesiones)

        n_traspasos = 0
        if CON_TRASPASOS:
            data = hacer_peticion(URL_TRASPASOS, {"player": id_jugador})
            n_traspasos = procesar_traspasos(cursor, id_jugador, data.get("response", []) if data else [])
            time.sleep(ESPERA_ENTRE_PETICIONES)

        cursor.execute("RELEASE SAVEPOINT jugador")
        return n_trofeos, n_lesiones, n_traspasos
    except LimiteApiAlcanzado:
        cursor.execute("ROLLBACK TO SAVEPOINT jugador")
        raise
    except Exception as e:
        cursor.execute("ROLLBACK TO SAVEPOINT jugador")
        print(f"    ⚠️ Jugador {id_jugador} descartado: {e}")
        return 0, 0, 0


def main():
    fases = "TROFEOS + BAJAS" + (" + TRASPASOS" if CON_TRASPASOS else "")
    print(f"🚀 BACKFILL DE ENTIDADES SECUNDARIAS ({fases})")
    if not CON_TRASPASOS:
        print("   ℹ️  Traspasos desactivados: /transfers no admite varios jugadores por petición")
        print("      (sería 1 por jugador). Para el histórico usa POST /api/v1/partidos/sync-traspasos,")
        print("      que va por equipo. Si aun así los quieres aquí: --con-traspasos")

    conn = psycopg2.connect(**DB_CONFIG)
    cursor = conn.cursor()

    # Jugadores que NUNCA se han sincronizado O hace más de 7 días
    cursor.execute("""
        SELECT id_jugador
        FROM sch_estadisticas.jugadores
        WHERE ultima_sync_bajas IS NULL
           OR ultima_sync_bajas < CURRENT_TIMESTAMP - INTERVAL '7 days'
        ORDER BY id_jugador
    """)
    jugadores = [row[0] for row in cursor.fetchall()]

    if not jugadores:
        print("✅ No hay jugadores pendientes de actualizar en la base de datos.")
        cursor.close()
        conn.close()
        return

    lotes = (len(jugadores) + TAMANO_LOTE - 1) // TAMANO_LOTE
    peticiones = lotes * 2 + (len(jugadores) if CON_TRASPASOS else 0)
    print(f"🌍 {len(jugadores)} jugadores pendientes -> {lotes} lotes de {TAMANO_LOTE}, "
          f"~{peticiones} peticiones.")

    totales = {"trofeos": 0, "bajas": 0, "traspasos": 0}
    procesados = 0

    for n_lote, lote in enumerate(trocear(jugadores, TAMANO_LOTE), 1):
        try:
            trofeos_por_jugador = pedir_por_lote(URL_TROFEOS, lote, "trophies")
            time.sleep(ESPERA_ENTRE_PETICIONES)
            lesiones_por_jugador = pedir_por_lote(URL_LESIONES, lote, "sidelined")
            time.sleep(ESPERA_ENTRE_PETICIONES)

            for id_jugador in lote:
                n_t, n_b, n_tr = procesar_jugador(
                    cursor, id_jugador,
                    trofeos_por_jugador.get(id_jugador, []),
                    lesiones_por_jugador.get(id_jugador, []))
                totales["trofeos"] += n_t
                totales["bajas"] += n_b
                totales["traspasos"] += n_tr

            # TODOS los del lote, no solo los que la API devolvió: un jugador sin trofeos ni
            # lesiones no aparece en la respuesta, y si no se marcase seguiría saliendo como
            # pendiente en cada ejecución para siempre.
            cursor.execute("""
                UPDATE sch_estadisticas.jugadores
                SET ultima_sync_bajas = CURRENT_TIMESTAMP
                WHERE id_jugador = ANY(%s)
            """, (lote,))

            conn.commit()
            procesados += len(lote)
            print(f"  [{n_lote}/{lotes}] ✅ {procesados}/{len(jugadores)} jugadores | "
                  f"+{totales['trofeos']} trofeos, +{totales['bajas']} bajas"
                  + (f", +{totales['traspasos']} traspasos" if CON_TRASPASOS else ""))

        except LimiteApiAlcanzado:
            conn.rollback()
            print(f"🛑 Cuota agotada en el lote {n_lote}/{lotes}. Lo guardado hasta el lote "
                  f"anterior se queda; relánzalo mañana y sigue por aquí.")
            break
        except Exception as e:
            conn.rollback()
            print(f"  [{n_lote}/{lotes}] ❌ Lote descartado: {e}")

        time.sleep(ESPERA_ENTRE_PETICIONES)

    cursor.close()
    conn.close()
    print(f"\n🎉 PROCESO COMPLETADO: {procesados} jugadores | "
          f"{totales['trofeos']} trofeos, {totales['bajas']} bajas, "
          f"{totales['traspasos']} traspasos nuevos.")


if __name__ == "__main__":
    main()
