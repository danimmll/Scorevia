from _consola import forzar_utf8
import os
import sys
import time
import requests
import psycopg2
from psycopg2.extras import execute_values, Json
from _entorno import cargar_entorno, config_bd
from _cuota import guardia_cuota, CuotaDiariaAgotada

# La consola de Windows es cp1252 y revienta con el primer emoji que se imprima.
forzar_utf8()

cargar_entorno()

DB_CONFIG = config_bd()

API_KEY = os.environ.get("API_FOOTBALL_KEY", "TU_API_KEY_AQUI")
URL_STATS_EQUIPO = "https://v3.football.api-sports.io/fixtures/statistics"
URL_STATS_JUGADOR = "https://v3.football.api-sports.io/fixtures/players"

ESPERA_ENTRE_PETICIONES = 0.01
MAX_REINTENTOS = 4

# --reintentar: igual que en el de eventos, vuelve a pasar los partidos
# marcados como "sin stats" por si fue un fallo transitorio de la API.
MODO_REINTENTO = "--reintentar" in sys.argv

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


def parsear_numero(valor):
    """La API devuelve porcentajes como '54%' (string) y números normales como int/float/None.
    Úsalo para columnas NUMERIC/DECIMAL (posesion, goles_esperados, valoracion)."""
    if valor is None:
        return None
    if isinstance(valor, str):
        valor = valor.replace('%', '').strip()
        if valor == '':
            return None
        try:
            return float(valor)
        except ValueError:
            return None
    if isinstance(valor, bool):
        return None
    return valor


def parsear_entero(valor):
    """Para columnas INTEGER: castea de forma segura, soportando '54%', strings vacíos,
    decimales tipo '3.0' o cualquier basura que la API devuelva donde no toca.
    Este era el bug real: campos como tarjetas/tiros/corners se insertaban con
    stats.get(...) a pelo, sin pasar por aquí, y cualquier valor no-numérico
    (p.ej. un porcentaje colado en el campo equivocado) reventaba el INSERT."""
    numero = parsear_numero(valor)
    if numero is None:
        return None
    try:
        return int(round(numero))
    except (ValueError, TypeError):
        return None


def mapear_stats_equipo(lista_stats):
    """Convierte [{'type':..., 'value':...}, ...] en un dict {type: value}."""
    return {item.get("type"): item.get("value") for item in lista_stats if item.get("type")}


def upsert_equipos(cursor, equipos_dict):
    """equipos_dict: {id_equipo: nombre}. Inserta los que falten, sin tocar los que ya existen."""
    if not equipos_dict:
        return
    query = """
        INSERT INTO sch_estadisticas.equipos (id_equipo, nombre)
        VALUES %s
        ON CONFLICT (id_equipo) DO NOTHING;
    """
    execute_values(cursor, query, [(k, v) for k, v in equipos_dict.items()])


def upsert_jugadores(cursor, jugadores_dict):
    """jugadores_dict: {id_jugador: nombre}. Inserta los que falten, sin tocar los que ya existen."""
    if not jugadores_dict:
        return
    query = """
        INSERT INTO sch_estadisticas.jugadores (id_jugador, nombre, perfil_verificado)
        VALUES %s
        ON CONFLICT (id_jugador) DO NOTHING;
    """
    execute_values(cursor, query, [(k, v, False) for k, v in jugadores_dict.items()])


def procesar_stats_equipo(cursor, id_partido, respuesta_stats):
    """
    respuesta_stats = data['response'] de /fixtures/statistics:
    lista con un objeto por equipo -> {'team': {...}, 'statistics': [{type, value}, ...]}
    """
    equipos_faltantes = {}
    filas = []

    for equipo_data in respuesta_stats:
        team = equipo_data.get("team", {})
        id_equipo = team.get("id")
        if id_equipo is None:
            continue
        equipos_faltantes[id_equipo] = team.get("name") or "Equipo Desconocido"

        stats = mapear_stats_equipo(equipo_data.get("statistics", []))

        filas.append((
            id_partido,
            id_equipo,
            parsear_numero(stats.get("Ball Possession")),   # posesion NUMERIC(5,2)
            parsear_entero(stats.get("Total Shots")),        # tiros
            parsear_entero(stats.get("Shots on Goal")),      # tiros_puerta
            parsear_entero(stats.get("Corner Kicks")),       # corners
            parsear_entero(stats.get("Fouls")),              # faltas
            parsear_entero(stats.get("Yellow Cards")),       # tarjetas_amarillas
            parsear_entero(stats.get("Red Cards")),          # tarjetas_rojas
            parsear_numero(stats.get("expected_goals")),     # goles_esperados
            None,                                              # sistema_tactico (no viene de este endpoint)
            Json(stats),                                       # estadisticas_extra: guardamos todo el crudo
        ))

    upsert_equipos(cursor, equipos_faltantes)

    if filas:
        query = """
            INSERT INTO sch_estadisticas.estadisticas_equipo_partido
            (id_partido, id_equipo, posesion, tiros, tiros_puerta, corners, faltas,
             tarjetas_amarillas, tarjetas_rojas, goles_esperados, sistema_tactico, estadisticas_extra)
            VALUES %s
            ON CONFLICT (id_partido, id_equipo) DO NOTHING;
        """
        execute_values(cursor, query, filas)
    return len(filas)


def procesar_stats_jugador(cursor, id_partido, respuesta_jugadores):
    """
    respuesta_jugadores = data['response'] de /fixtures/players:
    lista por equipo -> {'team': {...}, 'players': [{'player':{...}, 'statistics':[{...}]}, ...]}
    """
    equipos_faltantes = {}
    jugadores_faltantes = {}
    filas = []

    for equipo_data in respuesta_jugadores:
        team = equipo_data.get("team", {})
        id_equipo = team.get("id")
        if id_equipo is not None:
            equipos_faltantes[id_equipo] = team.get("name") or "Equipo Desconocido"

        for jugador_entry in equipo_data.get("players", []):
            jugador = jugador_entry.get("player", {})
            id_jugador = jugador.get("id")
            if id_jugador is None or id_equipo is None:
                continue
            jugadores_faltantes[id_jugador] = jugador.get("name") or "Jugador Desconocido"

            stats_lista = jugador_entry.get("statistics", [])
            stats = stats_lista[0] if stats_lista else {}

            games = stats.get("games", {}) or {}
            goals = stats.get("goals", {}) or {}
            passes = stats.get("passes", {}) or {}
            cards = stats.get("cards", {}) or {}

            substitute = games.get("substitute")
            titular = None if substitute is None else (not substitute)

            filas.append((
                id_partido,
                id_jugador,
                id_equipo,
                parsear_entero(games.get("minutes")),        # minutos_jugados
                parsear_entero(goals.get("total")),           # goles
                parsear_entero(goals.get("assists")),          # asistencias
                parsear_entero(cards.get("yellow")),            # tarjetas_amarillas
                parsear_entero(cards.get("red")),                # tarjetas_rojas
                parsear_entero(passes.get("accuracy")),           # pases_completados
                parsear_entero(passes.get("total")),               # pases_totales
                parsear_numero(games.get("rating")),                 # valoracion NUMERIC(3,1)
                titular,                                                # titular
                games.get("position"),                                  # posicion_campo
            ))

    upsert_equipos(cursor, equipos_faltantes)
    upsert_jugadores(cursor, jugadores_faltantes)

    if filas:
        query = """
            INSERT INTO sch_estadisticas.estadisticas_jugador_partido
            (id_partido, id_jugador, id_equipo, minutos_jugados, goles, asistencias,
             tarjetas_amarillas, tarjetas_rojas, pases_completados, pases_totales,
             valoracion, titular, posicion_campo)
            VALUES %s
            ON CONFLICT (id_partido, id_jugador) DO NOTHING;
        """
        execute_values(cursor, query, filas)
    return len(filas)


def main():
    if MODO_REINTENTO:
        print("🚀 REINTENTANDO PARTIDOS MARCADOS COMO 'SIN STATS' (por si fue un fallo transitorio)")
    else:
        print("🚀 INICIANDO BACKFILL MASIVO DE ESTADÍSTICAS (EQUIPO + JUGADOR)")

    conn = psycopg2.connect(**DB_CONFIG)
    cursor = conn.cursor()

    if MODO_REINTENTO:
        cursor.execute("""
            SELECT p.id_partido
            FROM sch_estadisticas.partidos p
            WHERE p.estado IN ('FINALIZADO', 'FT', 'AET', 'PEN')
              AND p.stats_not_found = true
              AND NOT EXISTS (
                  SELECT 1 FROM sch_estadisticas.estadisticas_equipo_partido ep
                  WHERE ep.id_partido = p.id_partido
              )
            ORDER BY p.fecha DESC
        """)
    else:
        cursor.execute("""
            SELECT p.id_partido
            FROM sch_estadisticas.partidos p
            WHERE p.estado IN ('FINALIZADO', 'FT', 'AET', 'PEN')
              AND (p.stats_not_found = false OR p.stats_not_found IS NULL)
              AND NOT EXISTS (
                  SELECT 1 FROM sch_estadisticas.estadisticas_equipo_partido ep
                  WHERE ep.id_partido = p.id_partido
              )
            ORDER BY p.fecha DESC
        """)
    partidos_pendientes = [row[0] for row in cursor.fetchall()]

    if not partidos_pendientes:
        print("✅ No hay partidos pendientes de estadísticas en la base de datos.")
        cursor.close()
        conn.close()
        return

    print(f"🌍 Se han encontrado {len(partidos_pendientes)} partidos pendientes.")

    for idx, id_partido in enumerate(partidos_pendientes, 1):
        try:
            data_equipo = hacer_peticion(URL_STATS_EQUIPO, {"fixture": id_partido})
        except LimiteApiAlcanzado:
            print(f"🛑 Cuota de API agotada en el partido {idx}/{len(partidos_pendientes)}. "
                  f"Deteniendo el script (podrás reanudarlo más tarde, retoma justo donde lo dejó).")
            break

        equipos_stats = data_equipo.get("response", []) if data_equipo else []

        if not equipos_stats:
            cursor.execute("""
                UPDATE sch_estadisticas.partidos
                SET stats_not_found = true
                WHERE id_partido = %s
            """, (id_partido,))
            conn.commit()
            print(f"  [{idx}/{len(partidos_pendientes)}] ⚠️ Partido {id_partido} sin stats -> Marcado con flag.")
            time.sleep(ESPERA_ENTRE_PETICIONES)
            continue

        try:
            n_equipo = procesar_stats_equipo(cursor, id_partido, equipos_stats)

            time.sleep(ESPERA_ENTRE_PETICIONES)
            try:
                data_jugador = hacer_peticion(URL_STATS_JUGADOR, {"fixture": id_partido})
            except LimiteApiAlcanzado:
                conn.commit()  # guardamos las stats de equipo que sí conseguimos antes de parar
                print(f"🛑 Cuota de API agotada a mitad del partido {idx}/{len(partidos_pendientes)} "
                      f"(stats de equipo ya guardadas). Deteniendo el script.")
                break

            equipos_jugadores = data_jugador.get("response", []) if data_jugador else []
            n_jugador = procesar_stats_jugador(cursor, id_partido, equipos_jugadores)

            # Si esta pasada consiguió stats para un partido antes marcado "sin stats", limpiamos el flag.
            cursor.execute("""
                UPDATE sch_estadisticas.partidos
                SET stats_not_found = false
                WHERE id_partido = %s AND stats_not_found = true
            """, (id_partido,))

            conn.commit()
            print(f"  [{idx}/{len(partidos_pendientes)}] ✅ Partido {id_partido} -> "
                  f"{n_equipo} filas de equipo, {n_jugador} filas de jugador.")

        except Exception as e:
            conn.rollback()
            print(f"  [{idx}/{len(partidos_pendientes)}] ❌ Error guardando stats en BD para {id_partido}: {e}")

        time.sleep(ESPERA_ENTRE_PETICIONES)

    print("\n🎉 PROCESO COMPLETADO")
    cursor.close()
    conn.close()


if __name__ == "__main__":
    main()