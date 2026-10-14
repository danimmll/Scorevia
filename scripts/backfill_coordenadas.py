from _consola import forzar_utf8
import os
import time
import psycopg2
from geopy.geocoders import Nominatim
from _entorno import cargar_entorno, config_bd

# La consola de Windows es cp1252 y revienta con el primer emoji que se imprima.
forzar_utf8()

cargar_entorno()

DB_CONFIG = config_bd()

def geocode_with_retry(geolocator, query, max_retries=3):
    for attempt in range(1, max_retries + 1):
        try:
            return geolocator.geocode(query, timeout=15)
        except Exception as e:
            error_str = str(e)
            if "429" in error_str:
                print(f"      🛑 [BLOQUEO 429] OpenStreetMap nos pide calma. Durmiendo 60 segundos... (Intento {attempt}/{max_retries})")
                time.sleep(60)
            else:
                print(f"      ⚠️ Error con '{query}': {e}. Reintentando...")
                time.sleep(3)
    return None

def backfill_coordenadas():
    """
    Geocodifica los lugares de nacimiento que faltan, UNA CONSULTA POR CIUDAD.

    Antes iba jugador a jugador: 11.104 pendientes eran 11.104 consultas a Nominatim a 1,5
    segundos cada una, mas de cuatro horas. Pero los jugadores se repiten muchisimo de
    ciudad, asi que la mayoria de esas consultas preguntaban por un sitio cuya respuesta ya
    se habia recibido hacia rato. Repetir la misma consulta es ademas justo lo que la
    politica de uso de Nominatim pide evitar.

    Ahora se agrupa por (ciudad, pais) y cada respuesta se aplica de golpe a todos los
    jugadores nacidos ahi. Lo que ya estaba resuelto en otra fila lo copia la migracion
    2026-09-08-coordenadas-por-ciudad-repetida.sql sin tocar la red (9.231 jugadores).
    """
    print("🚀 GEOCODIFICACIÓN POR CIUDAD (una consulta por sitio, no por jugador)")

    conn = psycopg2.connect(**DB_CONFIG)
    cursor = conn.cursor()

    cursor.execute("""
        SELECT trim(lugar_nacimiento), trim(coalesce(nacionalidad, '')), count(*)
        FROM sch_estadisticas.jugadores
        WHERE lugar_nacimiento IS NOT NULL
          AND trim(lugar_nacimiento) <> ''
          AND latitud_nacimiento IS NULL
        GROUP BY 1, 2
        ORDER BY count(*) DESC
    """)
    sitios = cursor.fetchall()

    if not sitios:
        print("✅ No hay lugares pendientes.")
        cursor.close()
        conn.close()
        return

    jugadores_afectados = sum(n for _, _, n in sitios)
    print(f"🌍 {len(sitios)} lugares distintos, {jugadores_afectados} jugadores.")
    print(f"   A 1,5s por consulta son unos {len(sitios) * 1.5 / 60:.0f} minutos.\n")

    user_agent = "scorevia-data-pipeline"
    geolocator = Nominatim(user_agent=user_agent)

    resueltos = fallidos = 0

    for idx, (ciudad, pais, cuantos) in enumerate(sitios, 1):
        # Ciudad + país primero: hay un Santiago en Chile, otro en Cuba y otro en España, y
        # sin el país los tres caerían en el mismo punto del mapa.
        queries = [f"{ciudad}, {pais}"] if pais else []
        queries.append(ciudad)

        location = None
        query_ok = None
        for query in queries:
            location = geocode_with_retry(geolocator, query)
            if location:
                query_ok = query
                break
            # Solo se espera cuando de verdad se ha ido a la red.
            time.sleep(1.5)

        if location:
            # La respuesta se aplica a TODOS los que nacieron ahí, no solo a uno.
            cursor.execute("""
                UPDATE sch_estadisticas.jugadores
                SET latitud_nacimiento = %s, longitud_nacimiento = %s
                WHERE latitud_nacimiento IS NULL
                  AND trim(lugar_nacimiento) = %s
                  AND trim(coalesce(nacionalidad, '')) = %s
            """, (location.latitude, location.longitude, ciudad, pais))
            conn.commit()
            resueltos += cursor.rowcount
            print(f"  [{idx}/{len(sitios)}] ✅ {query_ok} -> "
                  f"{location.latitude:.4f}, {location.longitude:.4f} ({cursor.rowcount} jugadores)")
            time.sleep(1.5)
        else:
            fallidos += cuantos
            print(f"  [{idx}/{len(sitios)}] ❌ No encontrado: {ciudad} ({pais or 'sin país'})")

    cursor.close()
    conn.close()
    print(f"\n🎉 PROCESO COMPLETADO: {resueltos} jugadores situados, "
          f"{fallidos} sin sitio reconocible.")


if __name__ == "__main__":
    backfill_coordenadas()