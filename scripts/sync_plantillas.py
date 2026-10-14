"""
Sincroniza las plantillas (qué jugador está en qué equipo) de una temporada.

Por qué hace falta un script para esto: la ingesta de plantillas NO es un backfill de
Python, es un endpoint del backend (`POST /api/v1/partidos/jugadores/completos/{temporada}`,
que tira de `/players?team=X&season=Y`). Y es la ÚNICA pieza de todo el sistema que:

  - asigna `jugadores.id_equipo` -- ni el backfill de stats ni el de eventos lo tocan, solo
    dan de alta al jugador con su nombre;
  - rellena `jugadores.foto_url`, que se compone a partir del id del jugador.

O sea que las "plantillas desactualizadas" y los cientos de miles de fichas sin foto son el
mismo problema, y este es el que lo arregla.

El endpoint responde 202 al instante y sigue trabajando en un hilo aparte, así que este
script se queda vigilando el avance contra la base de datos en vez de dejarte a ciegas.

Por defecto refresca solo las 22 competiciones destacadas (218 equipos activos) y vuelve a
pedir la plantilla de todo equipo mirado hace mas de 12 horas. Esto ultimo importa: la tabla
de control `plantillas_sincronizadas` nacio para poder retomar un backfill largo, pero como la
marca no caducaba, un equipo ya sincronizado no se volvia a mirar NUNCA -- el 2026-09-09 la
sincronizacion dijo "0 pendientes" y no pidio nada, con Kroos todavia en el Madrid.

La ventana va en HORAS y no en dias por ese mismo dia: las marcas tenian catorce horas, "mas
de un dia" no las cogia, y justo despues de arreglar el codigo es cuando hace falta poder
decir "vuelve a mirarlos ya".

Uso:
    python scripts/sync_plantillas.py                  # 2026, destacadas, refresco > 12 h
    python scripts/sync_plantillas.py 2025             # otra temporada
    python scripts/sync_plantillas.py 2026 --todas     # las 11.508 activas, no solo las grandes
    python scripts/sync_plantillas.py 2026 --horas 1   # solo lo de hace mas de una hora
    python scripts/sync_plantillas.py 2026 --todo      # repasar todos, tengan la marca que tengan
"""

from _consola import forzar_utf8
import os
import sys
import time

import psycopg2
import requests

from _entorno import cargar_entorno, config_bd

# La consola de Windows es cp1252 y revienta con el primer emoji que se imprima.
forzar_utf8()

cargar_entorno()

BASE_URL = os.environ.get("SCOREVIA_BASE_URL", "http://localhost:8085")
ADMIN_EMAIL = os.environ.get("ADMIN_EMAIL", "")
ADMIN_PASSWORD = os.environ.get("ADMIN_PASSWORD", "")

DB_CONFIG = config_bd()

INTERVALO_SONDEO = 30


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
    """Cuántos jugadores tienen equipo y foto ahora mismo."""
    with psycopg2.connect(**DB_CONFIG) as conn, conn.cursor() as cur:
        cur.execute("""
            SELECT count(*) FILTER (WHERE id_equipo IS NOT NULL),
                   count(*) FILTER (WHERE foto_url IS NOT NULL),
                   count(DISTINCT id_equipo)
            FROM sch_estadisticas.jugadores
        """)
        return cur.fetchone()


def leer_argumentos():
    """Temporada posicional, y dos banderas opcionales."""
    temporada, solo_destacadas, horas = 2026, True, 12
    argumentos = sys.argv[1:]
    i = 0
    while i < len(argumentos):
        arg = argumentos[i]
        if arg == "--todas":
            solo_destacadas = False
        elif arg == "--todo":
            horas = -1
        elif arg == "--horas":
            i += 1
            horas = int(argumentos[i])
        else:
            temporada = int(arg)
        i += 1
    return temporada, solo_destacadas, horas


def main():
    temporada, solo_destacadas, horas = leer_argumentos()

    con_equipo, con_foto, equipos = foto_de_progreso()
    inicial_equipo, inicial_foto = con_equipo, con_foto
    print(f"\nEstado inicial: {con_equipo:,} jugadores con equipo | {con_foto:,} con foto "
          f"| {equipos:,} plantillas")
    print(f"Alcance: {'solo competiciones destacadas' if solo_destacadas else 'todas las competiciones'}"
          f" | se refresca: {'todos' if horas < 0 else f'lo de hace mas de {horas} h'}")

    token = iniciar_sesion()
    url = f"{BASE_URL}/api/v1/partidos/jugadores/completos/{temporada}"
    resp = requests.post(url, headers={"Authorization": f"Bearer {token}"},
                         params={"soloDestacadas": str(solo_destacadas).lower(), "maxHoras": horas},
                         timeout=60)

    if resp.status_code not in (200, 202):
        print(f"❌ El backend rechazó la petición (HTTP {resp.status_code}): {resp.text[:200]}")
        sys.exit(1)

    print(f"🚀 Sincronización de plantillas {temporada} lanzada: {resp.text.strip()[:120]}")
    print("   Corre en un hilo del backend; este script solo vigila el avance.")
    print("   Puedes cortar con Ctrl+C: el backend sigue por su cuenta.\n")

    sin_cambios = 0
    try:
        while True:
            time.sleep(INTERVALO_SONDEO)
            nuevos_equipo, nuevos_foto, nuevas_plantillas = foto_de_progreso()
            delta_eq = nuevos_equipo - con_equipo
            delta_foto = nuevos_foto - con_foto

            if delta_eq == 0 and delta_foto == 0:
                sin_cambios += 1
                print(f"   sin cambios ({sin_cambios})... "
                      f"{nuevos_equipo:,} con equipo, {nuevas_plantillas:,} plantillas")
                # Diez sondeos seguidos sin moverse: o ha terminado, o se ha quedado
                # sin cuota. En cualquier caso no tiene sentido seguir mirando.
                if sin_cambios >= 10:
                    print("\n✅ Cinco minutos sin cambios: se da por terminado.")
                    break
            else:
                sin_cambios = 0
                print(f"   +{delta_eq:,} con equipo | +{delta_foto:,} con foto "
                      f"| {nuevas_plantillas:,} plantillas")

            con_equipo, con_foto = nuevos_equipo, nuevos_foto
    except KeyboardInterrupt:
        print("\n⏹️  Dejas de mirar, pero el backend sigue sincronizando.")

    final_equipo, final_foto, final_plantillas = foto_de_progreso()
    print(f"\nEstado final: {final_equipo:,} jugadores con equipo | {final_foto:,} con foto "
          f"| {final_plantillas:,} plantillas")

    if (final_equipo, final_foto) == (inicial_equipo, inicial_foto):
        print("\n⚠️  No ha cambiado NADA. Ojo: 'sin cambios' no es lo mismo que 'ya estaba al día'.\n"
              "   Mira el log del backend: si dice '0 pendientes en este lote', es que todos los\n"
              "   equipos figuran como ya sincronizados y no se ha pedido nada. Usa --todo o\n"
              "   revisa que la temporada sea la correcta.")


if __name__ == "__main__":
    main()
