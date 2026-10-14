from _consola import forzar_utf8
import os
import time
import requests
import psycopg2
import re
from _entorno import cargar_entorno, config_bd
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry

# La consola de Windows es cp1252 y revienta con el primer emoji que se imprima.
forzar_utf8()

cargar_entorno()

DB_CONFIG = config_bd()

API_KEY = os.environ.get("API_FOOTBALL_KEY", "")
HEADERS = {"x-apisports-key": API_KEY}
URL_PROFILES = "https://v3.football.api-sports.io/players/profiles"

# MEGA PLAN: 900 requests por minuto = 15 por segundo. 
# 1 segundo / 15 = 0.066. Le ponemos 0.075 por seguridad para no asfixiar la conexión.
ESPERA_ENTRE_PETICIONES = 0.075

session = requests.Session()
retries = Retry(
    total=3,                
    backoff_factor=1,       
    status_forcelist=[ 429, 500, 502, 503, 504 ], 
    allowed_methods=["GET"]
)
adapter = HTTPAdapter(max_retries=retries)
session.mount("http://", adapter)
session.mount("https://", adapter)

def limpiar(valor):
    if valor is None:
        return None
    valor_str = str(valor)
    valor_str = re.sub(r'[\r\n\t¶]+', ' ', valor_str)
    valor_str = re.sub(r'\s+', ' ', valor_str).strip()
    return valor_str if valor_str and valor_str.lower() != 'null' else None

def con_unidad(valor, unidad):
    """
    "187" -> "187 cm". Si ya trae unidad, se deja.

    /players/profiles devuelve la altura y el peso como numero pelado, mientras que
    /players?team=&season= —la ingesta normal de plantillas— los manda con unidad. Este
    script guardaba el crudo, asi que la misma columna acabo con las dos formas: 37.954
    alturas sin unidad frente a 116.630 con ella, y la ficha de un jugador poniendo "187"
    y la del de al lado "180 cm". Se arreglo en la migracion
    2026-09-08-altura-peso-con-unidad.sql; esto es para no volver a meterlas.
    """
    if valor is None:
        return None
    return f"{valor} {unidad}" if re.fullmatch(r"\d+", valor) else valor


def construir_nombre_completo(player):
    firstname = limpiar(player.get("firstname"))
    lastname = limpiar(player.get("lastname"))
    if firstname and lastname:
        return f"{firstname} {lastname}"
    return None

def backfill_por_perfil():
    if not API_KEY or not DB_CONFIG["password"]:
        print("⚠️ ALERTA: Faltan credenciales en tu .env")
        return

    print("🚀 BACKFILL vía /players/profiles (MEGA PLAN - Extracción Total)")

    conn = psycopg2.connect(**DB_CONFIG)
    cursor = conn.cursor()

    cursor.execute("""
        SELECT id_jugador FROM sch_estadisticas.jugadores
        WHERE perfil_verificado IS NOT TRUE
        ORDER BY id_jugador
    """)
    
    pendientes = [row[0] for row in cursor.fetchall()]
    total = len(pendientes)
    print(f"📊 {total} jugadores pendientes de verificar.")

    if total == 0:
        print("✅ Nada que hacer, todos los jugadores han sido verificados.")
        cursor.close()
        conn.close()
        return

    actualizados = 0
    sin_datos = 0

    for i, id_jugador in enumerate(pendientes, start=1):
        try:
            resp = session.get(
                URL_PROFILES,
                headers=HEADERS,
                params={"player": id_jugador},
                timeout=15, 
            )

            if resp.status_code != 200:
                print(f"  [{i}/{total}] ❌ HTTP {resp.status_code} jugador {id_jugador}")
                time.sleep(ESPERA_ENTRE_PETICIONES)
                continue

            data = resp.json()
            response = data.get("response", [])

            if not response:
                sin_datos += 1
                cursor.execute("""
                    UPDATE sch_estadisticas.jugadores 
                    SET perfil_verificado = TRUE 
                    WHERE id_jugador = %s
                """, (id_jugador,))
                conn.commit()
                print(f"  [{i}/{total}] ⚠️  Jugador {id_jugador} -> Sin datos. Marcado como verificado.")
                time.sleep(ESPERA_ENTRE_PETICIONES)
                continue

            # Extracción de todos los campos disponibles
            player = response[0].get("player", {})
            altura = con_unidad(limpiar(player.get("height")), "cm")
            peso = con_unidad(limpiar(player.get("weight")), "kg")
            nombre = construir_nombre_completo(player)
            nacionalidad = limpiar(player.get("nationality"))
            dorsal = player.get("number") # int o None
            posicion = limpiar(player.get("position"))
            
            # Navegamos dentro del objeto "birth" para sacar el lugar de nacimiento
            birth = player.get("birth", {})
            lugar_nacimiento = limpiar(birth.get("place"))

            cursor.execute("""
                UPDATE sch_estadisticas.jugadores
                SET altura = COALESCE(NULLIF(altura, ''), %s),
                    peso = COALESCE(NULLIF(peso, ''), %s),
                    nombre = COALESCE(NULLIF(nombre, ''), %s),
                    nacionalidad = COALESCE(NULLIF(nacionalidad, ''), %s),
                    dorsal = COALESCE(dorsal, %s),
                    posicion = COALESCE(NULLIF(posicion, ''), %s),
                    lugar_nacimiento = COALESCE(NULLIF(lugar_nacimiento, ''), %s),
                    perfil_verificado = TRUE
                WHERE id_jugador = %s
            """, (altura, peso, nombre, nacionalidad, dorsal, posicion, lugar_nacimiento, id_jugador))

            conn.commit()
            
            # Print simplificado para que no te inunde la consola con tanto campo
            print(f"  [{i}/{total}] ✅ Jugador {id_jugador} -> Dor:{dorsal}|Pos:{posicion}|Nac:{lugar_nacimiento}")
            actualizados += 1

        except Exception as e:
            conn.rollback() 
            print(f"  [{i}/{total}] ❌ Error crítico con jugador {id_jugador}: {e}")

        time.sleep(ESPERA_ENTRE_PETICIONES)

    cursor.close()
    conn.close()

    print("\n🎉 BACKFILL COMPLETADO")
    print(f"  Actualizados con datos: {actualizados}")
    print(f"  Verificados vacíos en la API: {sin_datos}")
    print(f"  Restantes por errores: {total - actualizados - sin_datos}")

if __name__ == "__main__":
    backfill_por_perfil()