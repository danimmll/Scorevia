from _consola import forzar_utf8
import os
import time
import requests
import psycopg2
from psycopg2.extras import execute_values
from _entorno import cargar_entorno, config_bd
from datetime import datetime

# La consola de Windows es cp1252 y revienta con el primer emoji que se imprima.
forzar_utf8()

cargar_entorno()

DB_CONFIG = config_bd()

API_KEY = os.environ.get("API_FOOTBALL_KEY", "TU_API_KEY_AQUI")
URL_FIXTURES = "https://v3.football.api-sports.io/fixtures"
URL_PLAYERS_STATS = "https://v3.football.api-sports.io/fixtures/players"

ESPERA_ENTRE_PETICIONES = 0.12
MAX_REINTENTOS = 4

# Usamos Session para que vaya más rápido
session = requests.Session()
session.headers.update({"x-apisports-key": API_KEY})

TEMPORADAS_A_PROCESAR = list(range(2026, 2027))

def hacer_peticion(url, params):
    for intento in range(MAX_REINTENTOS):
        try:
            resp = session.get(url, params=params, timeout=20)
            if resp.status_code == 200:
                data = resp.json()
                if "errors" in data and data["errors"]:
                    if isinstance(data["errors"], dict) and "requests" in data["errors"]:
                        print("⚠️ LÍMITE DE API ALCANZADO. Deteniendo script.")
                        return None
                return data
            elif resp.status_code == 429:
                print(f"⏳ Rate limit (429). Esperando 5s... (Intento {intento+1})")
                time.sleep(5)
            else:
                espera = 2 ** intento
                print(f"❌ HTTP {resp.status_code}. Esperando {espera}s... (Intento {intento+1})")
                time.sleep(espera)
        except Exception as e:
            espera = 2 ** intento
            print(f"❌ Error de red: {e}. Esperando {espera}s... (Intento {intento+1})")
            time.sleep(espera)
    return None

def procesar_partido(cursor, id_partido, temporada):
    data = hacer_peticion(URL_PLAYERS_STATS, {"fixture": id_partido})

    if not data or not data.get("response"):
        return 0

    jugadores = {}
    dorsales = {}

    ahora = datetime.now()

    for team_data in data["response"]:

        id_equipo = team_data["team"]["id"]

        for player_data in team_data.get("players", []):

            jugador = player_data.get("player", {})

            id_jugador = jugador.get("id")
            nombre = jugador.get("name")

            if id_jugador is None:
                continue

            # Guardamos el jugador una sola vez
            jugadores[id_jugador] = (id_jugador, nombre)

            estadisticas = player_data.get("statistics", [])

            if not estadisticas:
                continue

            stats = estadisticas[0]

            games = stats.get("games", {})

            dorsal = games.get("number")
            posicion = games.get("position")

            # Limpiamos el dorsal
            if isinstance(dorsal, str):

                dorsal = dorsal.strip()

                if dorsal.isdigit():
                    dorsal = int(dorsal)
                else:
                    dorsal = None

            # Si no hay ni dorsal ni posición no guardamos nada
            if dorsal is None and posicion is None:
                continue

            clave = (id_jugador, id_equipo, temporada)

            # Si aparece repetido el mismo jugador nos quedamos
            # con el registro que tenga más información.
            if clave in dorsales:

                anterior = dorsales[clave]

                dorsal_anterior = anterior[3]
                posicion_anterior = anterior[4]

                if dorsal_anterior is None and dorsal is not None:
                    dorsal_anterior = dorsal

                if posicion_anterior is None and posicion is not None:
                    posicion_anterior = posicion

                dorsales[clave] = (
                    id_jugador,
                    id_equipo,
                    temporada,
                    dorsal_anterior,
                    posicion_anterior,
                    id_partido,
                    ahora,
                )

            else:

                dorsales[clave] = (
                    id_jugador,
                    id_equipo,
                    temporada,
                    dorsal,
                    posicion,
                    id_partido,
                    ahora,
                )

    if not dorsales:
        time.sleep(ESPERA_ENTRE_PETICIONES)
        return 0

    query_jugadores = """
        INSERT INTO sch_estadisticas.jugadores
            (id_jugador, nombre)
        VALUES %s
        ON CONFLICT (id_jugador)
        DO NOTHING;
    """

    execute_values(
        cursor,
        query_jugadores,
        list(jugadores.values())
    )

    query_dorsales = """
        INSERT INTO sch_estadisticas.historial_dorsales
        (
            id_jugador,
            id_equipo,
            temporada,
            dorsal,
            posicion,
            id_partido,
            fecha_actualizacion
        )
        VALUES %s

        ON CONFLICT (id_jugador,id_equipo,temporada)

        DO UPDATE SET

            dorsal = COALESCE(
                EXCLUDED.dorsal,
                sch_estadisticas.historial_dorsales.dorsal
            ),

            posicion = COALESCE(
                EXCLUDED.posicion,
                sch_estadisticas.historial_dorsales.posicion
            ),

            id_partido = EXCLUDED.id_partido,

            fecha_actualizacion = EXCLUDED.fecha_actualizacion;
    """

    execute_values(
        cursor,
        query_dorsales,
        list(dorsales.values())
    )

    time.sleep(ESPERA_ENTRE_PETICIONES)

    return len(dorsales)

def main():
    print("🚀 INICIANDO BACKFILL HISTÓRICO MASIVO (TODAS LAS LIGAS)")
    conn = psycopg2.connect(**DB_CONFIG)
    cursor = conn.cursor()

    cursor.execute("CREATE TABLE IF NOT EXISTS sch_estadisticas.backfill_partidos_hechos (id_partido INT PRIMARY KEY)")
    conn.commit()

    cursor.execute("SELECT id_liga FROM sch_estadisticas.ligas ORDER BY id_liga")
    ligas_a_procesar = [row[0] for row in cursor.fetchall()]
    
    print(f"🌍 Se han encontrado {len(ligas_a_procesar)} ligas en la base de datos.")

    cursor.execute("SELECT id_partido FROM sch_estadisticas.backfill_partidos_hechos")
    partidos_procesados = {row[0] for row in cursor.fetchall()}

    for temporada in TEMPORADAS_A_PROCESAR:
        for id_liga in ligas_a_procesar:
            print(f"\n======================================")
            print(f"📅 Procesando Liga {id_liga} - Temporada {temporada}")
            
            data = hacer_peticion(URL_FIXTURES, {"league": id_liga, "season": temporada})
            if not data:
                continue

            fixtures = data.get("response", [])
            partidos_a_procesar = [f["fixture"]["id"] for f in fixtures if f["fixture"]["status"]["short"] in ["FT", "AET", "PEN"]]
            
            partidos_pendientes = [p for p in partidos_a_procesar if p not in partidos_procesados]
            
            if not partidos_pendientes:
                print(f"✅ Liga {id_liga} temporada {temporada} ya está completa. Saltando.")
                continue
                
            print(f"📊 Se encontraron {len(partidos_a_procesar)} partidos. Faltan {len(partidos_pendientes)} por descargar.")
            
            contador_commits = 0
            
            for idx, id_partido in enumerate(partidos_pendientes, 1):
                registros = procesar_partido(cursor, id_partido, temporada)
                
                cursor.execute("INSERT INTO sch_estadisticas.backfill_partidos_hechos (id_partido) VALUES (%s) ON CONFLICT DO NOTHING", (id_partido,))
                partidos_procesados.add(id_partido)
                
                contador_commits += 1
                if contador_commits >= 100:
                    conn.commit()
                    contador_commits = 0

                print(f"  [{idx}/{len(partidos_pendientes)}] Partido ID {id_partido} -> {registros} jugadores guardados.")
            
            conn.commit()

    print("\n🎉 PROCESO COMPLETADO AL 100%")
    cursor.close()
    conn.close()

if __name__ == "__main__":
    main()