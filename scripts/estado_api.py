"""
Estado de la cuota de API-Football y qué falta por ingerir.

Lánzalo SIEMPRE antes de un backfill largo: los scripts de ingesta no distinguen entre "la
API no tiene ese dato" y "te has quedado sin cuota", así que sin esta comprobación se puede
tirar media hora marcando miles de filas como procesadas cuando en realidad no llegó nada.

API-Football devuelve dos límites distintos en las cabeceras de cualquier respuesta:
  - x-ratelimit-limit / x-ratelimit-remaining            -> por MINUTO
  - x-ratelimit-requests-limit / -requests-remaining     -> por DÍA (se reinicia a las 00:00 UTC)
"""

from _consola import forzar_utf8
import os
from datetime import datetime, timezone

import requests
from _entorno import cargar_entorno, config_bd

# La consola de Windows es cp1252 y revienta con el primer emoji que se imprima.
forzar_utf8()

cargar_entorno()

API_KEY = os.environ.get("API_FOOTBALL_KEY", "")
URL_STATUS = "https://v3.football.api-sports.io/status"

DB_CONFIG = config_bd()

LIGAS_PRINCIPALES = (140, 39, 78, 135, 61, 2)


def estado_cuota():
    if not API_KEY:
        print("❌ Falta API_FOOTBALL_KEY en el .env")
        return

    respuesta = requests.get(URL_STATUS, headers={"x-apisports-key": API_KEY}, timeout=20)
    cabeceras = respuesta.headers
    cuerpo = respuesta.json()

    print("== CUOTA DE API-FOOTBALL ==")
    por_minuto = cabeceras.get("x-ratelimit-remaining", "?")
    limite_min = cabeceras.get("x-ratelimit-limit", "?")
    por_dia = cabeceras.get("x-ratelimit-requests-remaining", "?")
    limite_dia = cabeceras.get("x-ratelimit-requests-limit", "?")
    print(f"  Por minuto : {por_minuto} / {limite_min}")
    print(f"  Por día    : {por_dia} / {limite_dia}")

    errores = cuerpo.get("errors")
    # La API contesta 200 con un objeto "errors" cuando rechaza por cuota; un
    # response.raise_for_status() no lo detectaría.
    if errores:
        print(f"  ⚠️  LA API ESTÁ RECHAZANDO PETICIONES: {errores}")
        ahora = datetime.now(timezone.utc)
        faltan = (24 - ahora.hour - 1) * 60 + (60 - ahora.minute)
        print(f"  El contador diario se reinicia a las 00:00 UTC (faltan ~{faltan // 60}h {faltan % 60}m).")
        return False

    datos = cuerpo.get("response") or {}
    if isinstance(datos, dict):
        plan = datos.get("subscription", {})
        print(f"  Plan       : {plan.get('plan')} (activo: {plan.get('active')}, hasta {plan.get('end')})")
    print("  ✅ La API responde con normalidad.")
    return True


def estado_datos():
    try:
        import psycopg2
    except ImportError:
        print("\n(psycopg2 no instalado: me salto el resumen de la base de datos)")
        return

    consultas = {
        "Último partido almacenado":
            "SELECT MAX(fecha)::date FROM sch_estadisticas.partidos",
        # OJO: la cifra global (1,1M) engana y por eso ya no se imprime a secas. Solo 116 de
        # las 1.244 competiciones publican estadisticas de jugador, asi que la inmensa mayoria
        # de esos partidos NO las va a tener nunca por mucho que se pidan: son amistosos de
        # club, filiales, juveniles y ligas regionales. Lo unico que es trabajo pendiente de
        # verdad son los partidos de competiciones que SI las publican.
        "Partidos sin stats de jugador EN COMPETICIONES QUE LAS PUBLICAN (esto sí es trabajo)":
            "SELECT COUNT(*) FROM sch_estadisticas.partidos p "
            "JOIN sch_estadisticas.ligas l ON l.id_liga = p.id_liga "
            "WHERE p.estado = 'FINALIZADO' AND l.cobertura_stats_jugadores IS TRUE "
            "AND NOT EXISTS (SELECT 1 FROM sch_estadisticas.estadisticas_jugador_partido e "
            "WHERE e.id_partido = p.id_partido)",
        "Partidos sin stats de jugador porque su competición no las publica (no es trabajo)":
            "SELECT COUNT(*) FROM sch_estadisticas.partidos p "
            "JOIN sch_estadisticas.ligas l ON l.id_liga = p.id_liga "
            "WHERE p.estado = 'FINALIZADO' AND l.cobertura_stats_jugadores IS FALSE "
            "AND NOT EXISTS (SELECT 1 FROM sch_estadisticas.estadisticas_jugador_partido e "
            "WHERE e.id_partido = p.id_partido)",
        # OJO: este numero NO es trabajo pendiente. El catalogo de /players/profiles se
        # recorrio entero el 08/09/2026 (2.721 paginas) y dejo 416.303 perfiles verificados.
        # Los que quedan no estan en el catalogo de la API: pedirlos de uno en uno cuesta
        # una peticion cada uno y la respuesta viene vacia. Ver sync_nombres_completos.py.
        "Jugadores sin perfil (no estan en el catalogo de la API, no es trabajo pendiente)":
            "SELECT COUNT(*) FROM sch_estadisticas.jugadores WHERE perfil_verificado IS NOT TRUE",
        # Lo que queda son lugares que API-Football escribe mal ("Plymuth", "Chrstchurch",
        # "Krfeld") y que ademas no aparecen en GeoNames ni buscando por parecido dentro de su
        # pais: aldeas pequenas, sitios con el nombre demasiado destrozado y curiosidades tipo
        # "*Born at sea".
        #
        # Con Nominatim (backfill_coordenadas.py) se resolvieron 0 de 672 el 10/09/2026, porque
        # busca por coincidencia exacta. Con la tabla ciudades_mundo que ya estaba en la base y
        # busqueda por trigramas acotada al pais se recuperaron 240 jugadores mas ese mismo dia,
        # sin gastar cuota: ver scripts/coordenadas_desde_geonames.py. Merece la pena volver a
        # lanzarlo despues de cada ingesta grande de jugadores.
        "Jugadores sin coordenadas (nombres mal escritos en la API, no es trabajo pendiente)":
            "SELECT COUNT(*) FROM sch_estadisticas.jugadores "
            "WHERE lugar_nacimiento IS NOT NULL AND lugar_nacimiento <> '' AND latitud_nacimiento IS NULL",
        # Tampoco es trabajo pendiente. Comprobado sobre dos muestras de equipos sin estadio
        # (21 en total, incluidas Juventus W, Valencia W y Anderlecht W, que son de los que
        # mas partidos tienen): /teams?id=X devuelve el equipo pero con venue vacio en el
        # 100% de los casos. Son filiales, juveniles, femeninos y clubes regionales, y
        # API-Football sencillamente no les guarda estadio.
        "Equipos sin estadio (la API no lo tiene, no es trabajo pendiente)":
            "SELECT COUNT(*) FROM sch_estadisticas.equipos WHERE id_estadio IS NULL",
        # Se relleno entero el 08/09/2026 desde datos_api; si esto deja de ser 0, es que una
        # via de ingesta esta escribiendo partidos sin pasar por aplicarEstadoYMinuto().
        "Partidos sin estado_corto (deberia ser 0)":
            "SELECT COUNT(*) FROM sch_estadisticas.partidos WHERE estado_corto IS NULL",
    }

    print("\n== QUÉ FALTA EN LA BASE DE DATOS ==")
    with psycopg2.connect(**DB_CONFIG) as conn, conn.cursor() as cur:
        for etiqueta, sql in consultas.items():
            cur.execute(sql)
            print(f"  {etiqueta}: {cur.fetchone()[0]}")

        print("\n== TEMPORADA MÁS RECIENTE POR LIGA PRINCIPAL ==")
        cur.execute(
            "SELECT l.nombre, MAX(p.temporada), MAX(p.fecha)::date "
            "FROM sch_estadisticas.partidos p JOIN sch_estadisticas.ligas l ON l.id_liga = p.id_liga "
            "WHERE p.id_liga = ANY(%s) GROUP BY l.nombre ORDER BY l.nombre",
            (list(LIGAS_PRINCIPALES),),
        )
        for nombre, temporada, ultima in cur.fetchall():
            print(f"  {nombre:<24} temporada {temporada} | último partido {ultima}")


if __name__ == "__main__":
    estado_cuota()
    estado_datos()
