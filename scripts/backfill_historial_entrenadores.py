from _consola import forzar_utf8
import os
import time
import requests
import psycopg2
from psycopg2.extras import execute_values
from _entorno import cargar_entorno, config_bd
from _cuota import guardia_cuota, CuotaDiariaAgotada
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry

# La consola de Windows es cp1252 y revienta con el primer emoji que se imprima.
forzar_utf8()

cargar_entorno()

DB_CONFIG = config_bd()

API_KEY = os.environ.get("API_FOOTBALL_KEY", "TU_API_KEY_AQUI")
URL_COACHS = "https://v3.football.api-sports.io/coachs"

ESPERA_ENTRE_PETICIONES = 0.1

session = requests.Session()
retries = Retry(
    total=3,
    backoff_factor=1,
    status_forcelist=[429, 500, 502, 503, 504],
    allowed_methods=["GET"]
)
adapter = HTTPAdapter(max_retries=retries)
session.mount("http://", adapter)
session.mount("https://", adapter)


def extraer_temporada(fecha_inicio):
    if not fecha_inicio:
        return None
    try:
        return int(fecha_inicio.split("-")[0])
    except (ValueError, IndexError):
        return None


def backfill_historial_entrenadores():
    if not API_KEY or not DB_CONFIG["password"]:
        print("⚠️ ALERTA: Faltan credenciales en tu .env")
        return

    print("🚀 BACKFILL DE HISTORIAL DE ENTRENADORES (vía /coachs)")

    conn = psycopg2.connect(**DB_CONFIG)
    cursor = conn.cursor()

    cursor.execute("""
        CREATE TABLE IF NOT EXISTS sch_estadisticas.historial_entrenadores_procesados (
            id_entrenador INT PRIMARY KEY
        )
    """)
    conn.commit()

    cursor.execute("""
        SELECT e.id_entrenador FROM sch_estadisticas.entrenadores e
        WHERE NOT EXISTS (
            SELECT 1 FROM sch_estadisticas.historial_entrenadores_procesados h
            WHERE h.id_entrenador = e.id_entrenador
        )
        ORDER BY e.id_entrenador
    """)
    pendientes = [row[0] for row in cursor.fetchall()]
    total = len(pendientes)
    print(f"📊 {total} entrenadores pendientes de historial.")

    if total == 0:
        print("✅ Nada que hacer, todos los entrenadores ya han sido procesados.")
        cursor.close()
        conn.close()
        return

    entrenadores_con_historial = 0

    for i, id_entrenador in enumerate(pendientes, start=1):
        try:
            resp = session.get(
                URL_COACHS,
                headers={"x-apisports-key": API_KEY},
                params={"id": id_entrenador},
                timeout=15,
            )

            # Suelo de cuota diaria: este backfill son ~27.000 peticiones (una por
            # entrenador), y desde que la ingesta corre sola no puede dejar el día a cero.
            # Parar aquí no cuesta nada: la tabla de procesados hace que retome donde iba.
            try:
                guardia_cuota(resp)
            except CuotaDiariaAgotada as e:
                print(f"🛑 {e}")
                break

            if resp.status_code != 200:
                print(f"  [{i}/{total}] ❌ HTTP {resp.status_code} entrenador {id_entrenador}")
                time.sleep(ESPERA_ENTRE_PETICIONES)
                continue

            data = resp.json()
            response = data.get("response", [])

            if not response:
                cursor.execute("""
                    INSERT INTO sch_estadisticas.historial_entrenadores_procesados (id_entrenador)
                    VALUES (%s) ON CONFLICT DO NOTHING
                """, (id_entrenador,))
                conn.commit()
                print(f"  [{i}/{total}] ⚠️  Entrenador {id_entrenador} -> Sin datos en la API.")
                time.sleep(ESPERA_ENTRE_PETICIONES)
                continue

            carrera = response[0].get("career", []) or []

            equipos_faltantes = {}
            historial_filas = []

            for etapa in carrera:
                equipo = etapa.get("team") or {}
                id_equipo = equipo.get("id")
                nombre_equipo = equipo.get("name")
                temporada = extraer_temporada(etapa.get("start"))

                if id_equipo is None or temporada is None:
                    continue

                equipos_faltantes[id_equipo] = (id_equipo, nombre_equipo if nombre_equipo else "Equipo Desconocido")
                historial_filas.append((id_entrenador, id_equipo, temporada))

            if equipos_faltantes:
                execute_values(cursor, """
                    INSERT INTO sch_estadisticas.equipos (id_equipo, nombre)
                    VALUES %s
                    ON CONFLICT (id_equipo) DO NOTHING;
                """, list(equipos_faltantes.values()))

            if historial_filas:
                execute_values(cursor, """
                    INSERT INTO sch_estadisticas.historial_entrenadores
                        (id_entrenador, id_equipo, temporada)
                    VALUES %s
                    ON CONFLICT (id_entrenador, id_equipo, temporada) DO NOTHING;
                """, historial_filas)
                entrenadores_con_historial += 1

            cursor.execute("""
                INSERT INTO sch_estadisticas.historial_entrenadores_procesados (id_entrenador)
                VALUES (%s) ON CONFLICT DO NOTHING
            """, (id_entrenador,))

            conn.commit()
            print(f"  [{i}/{total}] ✅ Entrenador {id_entrenador} -> {len(historial_filas)} etapas guardadas.")

        except Exception as e:
            conn.rollback()
            print(f"  [{i}/{total}] ❌ Error crítico con entrenador {id_entrenador}: {e}")

        time.sleep(ESPERA_ENTRE_PETICIONES)

    cursor.close()
    conn.close()

    print("\n🎉 BACKFILL COMPLETADO")
    print(f"  Entrenadores con historial guardado: {entrenadores_con_historial}")
    print(f"  Total procesados: {total}")

if __name__ == "__main__":
    backfill_historial_entrenadores()
