"""
Sincroniza las clasificaciones de una temporada, liga por liga.

El endpoint del backend (`POST /api/v1/partidos/clasificaciones`) va de una en una y exige
`idLiga`, `temporada` y un token de ADMIN, así que a mano en Postman son cientos de
peticiones con un token que caduca a los 15 minutos. Este script hace el login, recorre las
ligas y renueva la sesión cuando hace falta.

Sirve para dos cosas:

  - **Poner al día la tabla.** Se ingirió el calendario hasta 2027 pero las clasificaciones
    no se han vuelto a sincronizar, así que marcan cero partidos jugados en la temporada en
    curso (por eso el selector decía "2026/27 (sin jugar)" en pleno septiembre).

  - **Rellenar `clasificaciones.grupo`.** La columna es nueva y llega vacía hasta que se
    resincroniza. Sin ella, una competición con fase de grupos —el Mundial, la Europa
    League— se ve como una única tabla con las posiciones repetidas una vez por grupo.

Cuesta 1 petición por liga.

Uso:
    python scripts/sync_clasificaciones.py --todas         # TODO lo que falta, sin vigilar
    python scripts/sync_clasificaciones.py                 # ligas con partidos en 2026
    python scripts/sync_clasificaciones.py --temporada 2025
    python scripts/sync_clasificaciones.py --solo-grupos   # solo las que tienen grupos
    python scripts/sync_clasificaciones.py --limite 50     # para probar

`--todas` es el modo "déjalo corriendo": busca en la base TODAS las parejas liga+temporada
que tienen partidos acabados pero ninguna clasificación, y las recorre de la temporada más
reciente a la más antigua. Solo pide lo que falta, así que se puede cortar y relanzar sin
repetir trabajo, y con `--desde` se decide hasta dónde atrás merece la pena bajar.
"""

from _consola import forzar_utf8
import argparse
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

# El token dura 15 minutos; se renueva antes por si acaso.
SEGUNDOS_ENTRE_LOGIN = 10 * 60
ESPERA_ENTRE_LIGAS = 0.2


def iniciar_sesion(session):
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
    session.headers.update({"Authorization": f"Bearer {resp.json()['accessToken']}"})
    return time.time()


def ligas_objetivo(temporada, solo_grupos, limite):
    """
    Qué ligas sincronizar.

    Por defecto, las que tienen partidos en la temporada: son las que pueden tener una
    clasificación con sentido. Con --solo-grupos, solo aquellas cuya tabla actual tiene
    posiciones repetidas, que es la señal de que hay varios grupos mezclados.
    """
    if solo_grupos:
        sql = """
            SELECT DISTINCT c.id_liga, l.nombre
            FROM sch_estadisticas.clasificaciones c
            JOIN sch_estadisticas.ligas l ON l.id_liga = c.id_liga
            WHERE c.temporada = %(temporada)s
            GROUP BY c.id_liga, l.nombre, c.temporada
            HAVING count(*) > count(DISTINCT c.posicion)
            ORDER BY 1
        """
    else:
        sql = """
            SELECT DISTINCT p.id_liga, l.nombre
            FROM sch_estadisticas.partidos p
            JOIN sch_estadisticas.ligas l ON l.id_liga = p.id_liga
            WHERE p.temporada = %(temporada)s
            ORDER BY 1
        """
    with psycopg2.connect(**DB_CONFIG) as conn, conn.cursor() as cur:
        cur.execute(sql, {"temporada": temporada})
        filas = cur.fetchall()
    return filas[:limite] if limite else filas


def pendientes_todas_las_temporadas(desde, limite):
    """
    Todas las parejas liga+temporada que tienen partidos pero ninguna clasificación.

    Es lo que hace que el modo `--todas` no repita trabajo: en vez de recorrer todas las
    competiciones de todos los años (miles de peticiones para reescribir lo que ya está),
    pregunta a la base qué falta de verdad. De la temporada más reciente hacia atrás, porque
    el campeón del año pasado interesa más que el de 2011.

    Se salta las competiciones que la API no cubre (`cobertura_stats_partido = false`): ahí
    no hay clasificación que traer y sería tirar una petición por cada una.
    """
    sql = """
        SELECT p.temporada, p.id_liga, l.nombre
        FROM sch_estadisticas.partidos p
        JOIN sch_estadisticas.ligas l ON l.id_liga = p.id_liga
        WHERE p.estado = 'FINALIZADO'
          AND p.temporada >= %(desde)s
          AND l.cobertura_stats_partido IS NOT FALSE
          AND NOT EXISTS (
              SELECT 1 FROM sch_estadisticas.clasificaciones c
              WHERE c.id_liga = p.id_liga AND c.temporada = p.temporada
          )
        GROUP BY p.temporada, p.id_liga, l.nombre
        ORDER BY p.temporada DESC, p.id_liga
    """
    with psycopg2.connect(**DB_CONFIG) as conn, conn.cursor() as cur:
        cur.execute(sql, {"desde": desde})
        filas = cur.fetchall()
    return filas[:limite] if limite else filas


def sincronizar(session, tareas, ultimo_login):
    """Recorre una lista de (temporada, id_liga, nombre) pidiendo su clasificación."""
    ok = 0
    fallidas = []
    total = len(tareas)

    for i, (temporada, id_liga, nombre) in enumerate(tareas, 1):
        if time.time() - ultimo_login > SEGUNDOS_ENTRE_LOGIN:
            ultimo_login = iniciar_sesion(session)

        try:
            resp = session.post(
                f"{BASE_URL}/api/v1/partidos/clasificaciones",
                params={"idLiga": id_liga, "temporada": temporada},
                timeout=120,
            )
        except requests.exceptions.RequestException as e:
            print(f"  [{i}/{total}] ❌ {nombre} {temporada}: {type(e).__name__}")
            fallidas.append((id_liga, f"{nombre} {temporada}"))
            continue

        if resp.status_code == 200:
            ok += 1
            if i % 25 == 0 or i == total:
                print(f"  [{i}/{total}] {ok} ok, {len(fallidas)} con error")
        else:
            print(f"  [{i}/{total}] ❌ {nombre} {temporada} (liga {id_liga}): HTTP {resp.status_code}")
            fallidas.append((id_liga, f"{nombre} {temporada}"))

        time.sleep(ESPERA_ENTRE_LIGAS)

    return ok, fallidas, ultimo_login


def main():
    parser = argparse.ArgumentParser(description="Sincroniza clasificaciones liga por liga.")
    parser.add_argument("--todas", action="store_true",
                        help="todas las temporadas con partidos pero sin clasificación")
    parser.add_argument("--desde", type=int, default=2010,
                        help="temporada más antigua que se toca en modo --todas")
    parser.add_argument("--temporada", type=int, default=2026)
    parser.add_argument("--solo-grupos", action="store_true",
                        help="solo las competiciones cuya tabla tiene varios grupos mezclados")
    parser.add_argument("--limite", type=int, default=0, help="0 = sin límite")
    args = parser.parse_args()

    if args.todas:
        tareas = pendientes_todas_las_temporadas(args.desde, args.limite)
        descripcion = f"{len(tareas)} clasificaciones que faltan, desde {args.desde}"
    else:
        tareas = [(args.temporada, id_liga, nombre)
                  for id_liga, nombre in ligas_objetivo(args.temporada, args.solo_grupos, args.limite)]
        descripcion = f"{len(tareas)} ligas, temporada {args.temporada}"

    if not tareas:
        print("✅ No hay clasificaciones que sincronizar con esos criterios.")
        return

    session = requests.Session()
    ultimo_login = iniciar_sesion(session)
    print(f"🔑 Sesión iniciada como {ADMIN_EMAIL}.")
    print(f"🚀 {descripcion} (1 petición por clasificación).\n")

    if args.todas:
        temporadas = sorted({t for t, _, _ in tareas}, reverse=True)
        print(f"   Temporadas a tocar: {', '.join(str(t) for t in temporadas[:12])}"
              f"{' ...' if len(temporadas) > 12 else ''}\n")

    ok, fallidas, _ = sincronizar(session, tareas, ultimo_login)

    print(f"\nTerminado. Clasificaciones OK: {ok} | con error: {len(fallidas)}")
    if fallidas:
        muestra = ", ".join(n for _, n in fallidas[:10])
        print(f"   Fallaron: {muestra}{' ...' if len(fallidas) > 10 else ''}")
        print("   Se puede relanzar: la ingesta reescribe la clasificación, no duplica.")
    return


if __name__ == "__main__":
    main()
