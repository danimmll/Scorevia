"""
Refresca quien entrena a cada equipo de las competiciones destacadas.

POR QUE UN SCRIPT
El endpoint existe (`POST /api/v1/partidos/entrenadores`), pero pide un token de admin, asi
que lanzarlo a mano significaba copiar un `curl` con el token pegado dentro. Este script hace
el login igual que sync_plantillas.py y ademas vigila el avance contra la base de datos, que
es lo unico que dice de verdad si ha servido de algo: el endpoint responde al instante y sigue
trabajando en un hilo aparte.

QUE ARREGLA
`entrenadores.id_equipo` solo se actualiza cuando se vuelve a preguntar por el equipo. Como la
pasada completa recorre los 28.025 equipos de la base (28.000 peticiones), en la practica no
se lanzaba nunca y el dato se pudria: Jose Mourinho seguia figurando en el Benfica cuando la
API lo da en el Real Madrid desde julio. Acotado a las 22 destacadas son 260 peticiones.

Uso:
    python scripts/sync_entrenadores.py          # las 22 destacadas (260 peticiones)
    python scripts/sync_entrenadores.py --todas  # los 28.025 equipos de la base
"""

from _consola import forzar_utf8
import os
import sys
import time

import psycopg2
import requests

from _entorno import cargar_entorno, config_bd

forzar_utf8()

cargar_entorno()

BASE_URL = os.environ.get("SCOREVIA_BASE_URL", "http://localhost:8085")
ADMIN_EMAIL = os.environ.get("ADMIN_EMAIL", "")
ADMIN_PASSWORD = os.environ.get("ADMIN_PASSWORD", "")

DB_CONFIG = config_bd()

INTERVALO_SONDEO = 20
SONDEOS_SIN_CAMBIOS = 9  # tres minutos parados: o ha terminado, o no hay cuota

LIGAS_DESTACADAS = (2, 3, 11, 13, 39, 40, 61, 71, 78, 79, 88, 94, 128, 135,
                    136, 140, 141, 144, 203, 253, 262, 848)


def iniciar_sesion():
    if not ADMIN_EMAIL or not ADMIN_PASSWORD:
        print("❌ Faltan ADMIN_EMAIL / ADMIN_PASSWORD en el .env.")
        sys.exit(1)
    resp = requests.post(
        f"{BASE_URL}/api/v1/auth/login",
        json={"correoUsuario": ADMIN_EMAIL, "contrasenhaUsuario": ADMIN_PASSWORD},
        timeout=30,
    )
    if resp.status_code != 200:
        print(f"❌ Login rechazado (HTTP {resp.status_code}) para {ADMIN_EMAIL}.")
        sys.exit(1)
    print(f"🔑 Sesión iniciada como {ADMIN_EMAIL}.")
    return resp.json()["accessToken"]


def foto_de_progreso():
    """Cuántos equipos destacados tienen entrenador y cuántos se han tocado hoy."""
    with psycopg2.connect(**DB_CONFIG) as conn, conn.cursor() as cur:
        cur.execute(
            """
            SELECT count(*) FILTER (WHERE en.id_entrenador IS NOT NULL),
                   count(DISTINCT e.id_equipo)
            FROM sch_estadisticas.equipos e
            LEFT JOIN sch_estadisticas.entrenadores en ON en.id_equipo = e.id_equipo
            WHERE e.id_liga IN %s
            """,
            (LIGAS_DESTACADAS,),
        )
        return cur.fetchone()


def main():
    solo_destacadas = "--todas" not in sys.argv[1:]

    con_entrenador, equipos = foto_de_progreso()
    print(f"\nEstado inicial: {con_entrenador:,} entrenadores asignados en "
          f"{equipos:,} equipos de las competiciones destacadas")
    print(f"Alcance: {'solo destacadas (~260 peticiones)' if solo_destacadas else 'los 28.025 equipos'}")

    token = iniciar_sesion()
    resp = requests.post(
        f"{BASE_URL}/api/v1/partidos/entrenadores",
        headers={"Authorization": f"Bearer {token}"},
        params={"soloDestacadas": str(solo_destacadas).lower()},
        timeout=60,
    )
    if resp.status_code not in (200, 202):
        print(f"❌ El backend rechazó la petición (HTTP {resp.status_code}): {resp.text[:200]}")
        sys.exit(1)

    print(f"🚀 {resp.text.strip()[:160]}")
    print("   Corre en un hilo del backend; este script solo vigila el avance.")
    print("   Puedes cortar con Ctrl+C: el backend sigue por su cuenta.\n")

    sin_cambios = 0
    try:
        while True:
            time.sleep(INTERVALO_SONDEO)
            nuevos, _ = foto_de_progreso()
            delta = nuevos - con_entrenador
            if delta == 0:
                sin_cambios += 1
                print(f"   sin cambios ({sin_cambios})... {nuevos:,} con entrenador")
                if sin_cambios >= SONDEOS_SIN_CAMBIOS:
                    print("\n✅ Tres minutos sin cambios: se da por terminado.")
                    break
            else:
                sin_cambios = 0
                print(f"   {delta:+,} equipos con entrenador | {nuevos:,} en total")
            con_entrenador = nuevos
    except KeyboardInterrupt:
        print("\n⏹️  Dejas de mirar, pero el backend sigue.")

    final, _ = foto_de_progreso()
    print(f"\nEstado final: {final:,} entrenadores asignados en competiciones destacadas")
    print("   Ojo: que la cifra no suba no significa que no haya servido. Este refresco sobre "
          "todo CORRIGE\n   a quién entrena cada uno, y eso no cambia el total.")


if __name__ == "__main__":
    main()
