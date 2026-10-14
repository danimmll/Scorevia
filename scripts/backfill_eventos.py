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
URL_EVENTS = "https://v3.football.api-sports.io/fixtures/events"

ESPERA_ENTRE_PETICIONES = 0.01  # ~8 peticiones por segundo
MAX_REINTENTOS = 4

# --reintentar: en vez de saltarse los partidos ya marcados como
# "sin eventos", vuelve a pasarlos por si el flag se puso por un fallo
# transitorio de la API en su momento, no porque el partido no tuviera eventos.
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


def main():
    if MODO_REINTENTO:
        print("🚀 REINTENTANDO PARTIDOS MARCADOS COMO 'SIN EVENTOS' (por si fue un fallo transitorio)")
    else:
        print("🚀 INICIANDO BACKFILL MASIVO DE EVENTOS (AUTO-INSERCIÓN EQUIPOS Y JUGADORES)")

    conn = psycopg2.connect(**DB_CONFIG)
    cursor = conn.cursor()

    if MODO_REINTENTO:
        cursor.execute("""
            SELECT p.id_partido
            FROM sch_estadisticas.partidos p
            WHERE p.estado IN ('FINALIZADO', 'FT', 'AET', 'PEN')
              AND p.eventos_not_found = true
              AND NOT EXISTS (
                  SELECT 1 FROM sch_estadisticas.eventos_partido ep
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
              AND (p.eventos_not_found = false OR p.eventos_not_found IS NULL)
              AND NOT EXISTS (
                  SELECT 1 FROM sch_estadisticas.eventos_partido ep
                  WHERE ep.id_partido = p.id_partido
              )
            ORDER BY p.fecha DESC
        """)
    partidos_pendientes = [row[0] for row in cursor.fetchall()]

    if not partidos_pendientes:
        print("✅ No hay partidos pendientes de eventos en la base de datos.")
        cursor.close()
        conn.close()
        return

    print(f"🌍 Se han encontrado {len(partidos_pendientes)} partidos pendientes.")

    for idx, id_partido in enumerate(partidos_pendientes, 1):
        try:
            data = hacer_peticion(URL_EVENTS, {"fixture": id_partido})
        except LimiteApiAlcanzado:
            print(f"🛑 Cuota de API agotada en el partido {idx}/{len(partidos_pendientes)}. Deteniendo el script (podrás reanudarlo más tarde, retoma justo donde lo dejó).")
            break

        if not data:
            print(f"  [{idx}/{len(partidos_pendientes)}] ⏭️ Partido {id_partido} sin respuesta tras los reintentos, se dejará para la próxima pasada.")
            time.sleep(ESPERA_ENTRE_PETICIONES)
            continue

        eventos_api = data.get("response", [])

        if len(eventos_api) == 0:
            cursor.execute("""
                UPDATE sch_estadisticas.partidos
                SET eventos_not_found = true
                WHERE id_partido = %s
            """, (id_partido,))
            conn.commit()
            print(f"  [{idx}/{len(partidos_pendientes)}] ⚠️ Partido {id_partido} sin eventos -> Marcado con flag.")
            time.sleep(ESPERA_ENTRE_PETICIONES)
            continue

        equipos_faltantes = {}
        jugadores_faltantes = {}
        lista_eventos_db = []

        for evento in eventos_api:
            # 1. Extraemos EQUIPOS de forma segura
            t_id = evento.get('team', {}).get('id')
            t_name = evento.get('team', {}).get('name')
            if t_id is not None:
                equipos_faltantes[t_id] = (t_id, t_name if t_name else 'Equipo Desconocido')

            # 2. Extraemos JUGADORES de forma segura
            p_id = evento.get('player', {}).get('id')
            p_name = evento.get('player', {}).get('name')

            a_id = evento.get('assist', {}).get('id')
            a_name = evento.get('assist', {}).get('name')

            # Metemos jugadores asegurando que no haya Nombres nulos
            if p_id is not None:
                jugadores_faltantes[p_id] = (p_id, p_name if p_name else 'Jugador Desconocido', False)
            if a_id is not None:
                jugadores_faltantes[a_id] = (a_id, a_name if a_name else 'Jugador Desconocido', False)

            # 3. Aseguramos el TIPO DE EVENTO
            tipo_ev = evento.get('type')
            tipo_seguro = tipo_ev if tipo_ev else 'Unknown'

            # 4. Tiempo y detalle de forma segura (algunos eventos no traen 'extra' o 'detail')
            tiempo = evento.get('time') or {}
            minuto = tiempo.get('elapsed')
            minuto_extra = tiempo.get('extra')
            detalle = evento.get('detail') or 'Sin detalle'

            if minuto is None:
                # Sin minuto no hay evento válido que insertar (columna NOT NULL en BD)
                continue

            lista_eventos_db.append((
                id_partido,
                minuto,
                minuto_extra,
                t_id,
                p_id,
                a_id,
                tipo_seguro,
                detalle
            ))

        try:
            # 1. INSERTAMOS EQUIPOS (ON CONFLICT DO NOTHING)
            if equipos_faltantes:
                query_equipos = """
                    INSERT INTO sch_estadisticas.equipos (id_equipo, nombre)
                    VALUES %s
                    ON CONFLICT (id_equipo) DO NOTHING;
                """
                execute_values(cursor, query_equipos, list(equipos_faltantes.values()))

            # 2. INSERTAMOS JUGADORES (ON CONFLICT DO NOTHING)
            if jugadores_faltantes:
                query_jugadores = """
                    INSERT INTO sch_estadisticas.jugadores (id_jugador, nombre, perfil_verificado)
                    VALUES %s
                    ON CONFLICT (id_jugador) DO NOTHING;
                """
                execute_values(cursor, query_jugadores, list(jugadores_faltantes.values()))

            # 3. INSERTAMOS LOS EVENTOS
            #
            # Se borran antes los del partido y se vuelven a insertar enteros, que es lo
            # mismo que hace el ingest de Java (EventoPartidoIngestServiceImpl).
            #
            # Antes esto era un INSERT con "ON CONFLICT DO NOTHING", pero eventos_partido
            # no tiene ninguna restricción única sobre la clave natural -- solo la PK de
            # secuencia id_evento, que nunca choca -- así que el ON CONFLICT no hacía nada
            # y cada reejecución del script duplicaba todos los eventos del partido. Así se
            # colaron 4.557 filas repetidas en 3.704 partidos (tarjetas por duplicado y
            # tandas de penaltis con el mismo lanzamiento hasta 13 veces).
            #
            # No se pone un índice único como red de seguridad a propósito: un jugador SÍ
            # puede marcar dos veces en el mismo minuto, y esas filas son legítimas.
            if lista_eventos_db:
                cursor.execute(
                    "DELETE FROM sch_estadisticas.eventos_partido WHERE id_partido = %s",
                    (id_partido,),
                )
                query_insert = """
                    INSERT INTO sch_estadisticas.eventos_partido
                    (id_partido, tiempo_minuto, tiempo_extra, id_equipo, id_jugador_principal, id_jugador_secundario, tipo_evento, detalle)
                    VALUES %s;
                """
                execute_values(cursor, query_insert, lista_eventos_db)

            # Si esta pasada ha conseguido eventos para un partido que antes estaba
            # marcado como "sin eventos", limpiamos el flag para que no quede inconsistente.
            if lista_eventos_db:
                cursor.execute("""
                    UPDATE sch_estadisticas.partidos
                    SET eventos_not_found = false
                    WHERE id_partido = %s AND eventos_not_found = true
                """, (id_partido,))

            conn.commit()
            print(f"  [{idx}/{len(partidos_pendientes)}] ✅ Partido {id_partido} -> Insertados {len(lista_eventos_db)} eventos.")

        except Exception as e:
            conn.rollback()
            print(f"  [{idx}/{len(partidos_pendientes)}] ❌ Error guardando eventos en BD para {id_partido}: {e}")

        time.sleep(ESPERA_ENTRE_PETICIONES)

    print("\n🎉 PROCESO COMPLETADO")
    cursor.close()
    conn.close()

if __name__ == "__main__":
    main()
