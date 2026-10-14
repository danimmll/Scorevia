"""
Rellena las coordenadas de nacimiento que faltan usando la tabla de GeoNames que ya está en
la base, sin gastar ni una petición.

QUÉ PASABA ANTES
----------------
746 jugadores no tienen coordenadas de nacimiento. El
2026-09-10 se pasó `backfill_coordenadas.py` (Nominatim / OpenStreetMap) por los 672 lugares
distintos que faltaban y resolvió **cero**:

    [1/672] ❌ No encontrado: Itsmina (Colombia)
    [9/672] ❌ No encontrado: Birkinhead (England)
    ...

No es que esos sitios no existan: es que API-Football los escribe mal. "Birkinhead" es
Birkenhead, "Krfeld" es Krefeld, "Chrstchurch" es Christchurch, "Viano do Castelo" es Viana
do Castelo, "Tuxla Gutiérrez" es Tuxtla Gutiérrez. Nominatim busca por coincidencia exacta,
así que con una letra de menos no encuentra nada.

Y las ciudades ya estaban en casa: `sch_estadisticas.ciudades_mundo` es un volcado de
GeoNames con 235.206 sitios que llevaba tiempo en la base **sin que ningún código la
mirara**. Krefeld, Port Harcourt, Christchurch y Viana do Castelo están dentro.

EL PROBLEMA DE BUSCAR POR PARECIDO A SECAS
------------------------------------------
Buscar el nombre más parecido en las 235.206 ciudades sin más da resultados peores que no
tener nada:

    Addis Adeba (Ethiopia)  ->  Addis (Estados Unidos)     parecido 0,60
    Acopora (Brazil)        ->  Acora  (Perú)              parecido 0,56

En un juego que consiste en señalar dónde nació alguien, poner la respuesta en el continente
equivocado es peor que no hacer la pregunta. Hay que acotar por país.

CÓMO SE DEDUCE EL PAÍS, SIN UNA LISTA A MANO
--------------------------------------------
La nacionalidad viene como nombre en inglés ("Brazil", "Côte d'Ivoire", "New Zealand") y
GeoNames usa códigos ISO de dos letras. En vez de escribir a mano las 119 equivalencias, se
deducen **votando con los datos que ya hay**: se miran los jugadores cuyo lugar de nacimiento
coincide EXACTO con una ciudad, y se cuenta en qué país caen.

Con una pega que casi arruina la idea: contando todas las coincidencias, "New Zealand" salía
como **US** (45%), porque Hamilton, Wellington y Christchurch también existen en Estados
Unidos y allí GeoNames tiene muchísimas más entradas. La solución es votar solo con nombres
que existan en **un único país** (Whangarei vota, Hamilton no):

    Sin filtrar          Filtrando por nombres únicos
    New Zealand -> US     New Zealand -> NZ  (78,5%)
    Germany -> DE 39%     Germany     -> DE  (93,8%)
    Brazil  -> BR 63%     Brazil      -> BR  (99,6%)

Uso:
    python scripts/coordenadas_desde_geonames.py            # solo enseña lo que haría
    python scripts/coordenadas_desde_geonames.py --aplicar  # lo escribe
    python scripts/coordenadas_desde_geonames.py --umbral 0.6
"""

from _consola import forzar_utf8
import argparse

import psycopg2

from _entorno import cargar_entorno, config_bd

forzar_utf8()
cargar_entorno()

# Cuánto parecido exige para dar por bueno un emparejamiento. 0,55 deja pasar "Krfeld" ->
# "Krefeld" (0,50 con similarity a secas, más alto tras quitar tildes y normalizar) y corta
# antes de los parecidos de casualidad. Se puede mover con --umbral.
UMBRAL_PARECIDO = 0.55

# Un país solo se da por deducido si su candidato gana con holgura y con suficientes votos:
# con dos coincidencias sueltas cualquier cosa "gana".
MINIMO_VOTOS = 5
MINIMO_PORCENTAJE = 60.0

SQL_MAPA_PAISES = """
WITH nombres_unicos AS (
    -- Solo nombres que existan en UN pais: los repetidos (Hamilton, Santiago, Victoria)
    -- votarian por el pais con mas entradas en GeoNames, no por el correcto.
    SELECT lower(unaccent(asciiname)) AS nombre, MIN(country_code) AS country_code
    FROM sch_estadisticas.ciudades_mundo
    WHERE asciiname IS NOT NULL AND country_code IS NOT NULL
    GROUP BY 1
    HAVING COUNT(DISTINCT country_code) = 1
),
votos AS (
    SELECT trim(j.nacionalidad) AS pais, n.country_code, COUNT(*) AS votos
    FROM sch_estadisticas.jugadores j
    JOIN nombres_unicos n ON n.nombre = lower(unaccent(trim(j.lugar_nacimiento)))
    WHERE j.nacionalidad IS NOT NULL AND trim(j.nacionalidad) <> ''
      AND j.lugar_nacimiento IS NOT NULL AND trim(j.lugar_nacimiento) <> ''
    GROUP BY 1, 2
),
ganador AS (
    SELECT DISTINCT ON (pais)
           pais, country_code, votos,
           100.0 * votos / SUM(votos) OVER (PARTITION BY pais) AS porcentaje
    FROM votos
    ORDER BY pais, votos DESC
)
SELECT pais, country_code, votos, round(porcentaje, 1)
FROM ganador
WHERE votos >= %(minimo_votos)s AND porcentaje >= %(minimo_porcentaje)s
ORDER BY pais
"""

SQL_PENDIENTES = """
SELECT trim(lugar_nacimiento) AS lugar,
       trim(coalesce(nacionalidad, '')) AS pais,
       COUNT(*) AS jugadores
FROM sch_estadisticas.jugadores
WHERE lugar_nacimiento IS NOT NULL
  AND trim(lugar_nacimiento) <> ''
  AND latitud_nacimiento IS NULL
GROUP BY 1, 2
ORDER BY jugadores DESC, lugar
"""

# El emparejamiento, acotado al pais deducido. Primero se intenta exacto (mas barato y sin
# riesgo) y si no, por parecido de trigramas quedandose con el mas poblado en caso de empate:
# entre dos sitios que se llaman parecido, la ciudad grande es muchisimo mas probable que la
# aldea.
SQL_EMPAREJAR = """
SELECT name, latitude, longitude, population,
       similarity(unaccent(%(lugar)s), unaccent(asciiname)) AS parecido
FROM sch_estadisticas.ciudades_mundo
WHERE country_code = %(pais)s
  AND latitude IS NOT NULL AND longitude IS NOT NULL
  AND similarity(unaccent(%(lugar)s), unaccent(asciiname)) >= %(umbral)s
ORDER BY (lower(unaccent(asciiname)) = lower(unaccent(%(lugar)s))) DESC,
         similarity(unaccent(%(lugar)s), unaccent(asciiname)) DESC,
         population DESC NULLS LAST
LIMIT 1
"""

SQL_ESCRIBIR = """
UPDATE sch_estadisticas.jugadores
SET latitud_nacimiento = %(lat)s, longitud_nacimiento = %(lon)s
WHERE trim(lugar_nacimiento) = %(lugar)s
  AND trim(coalesce(nacionalidad, '')) = %(pais)s
  AND latitud_nacimiento IS NULL
"""


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--aplicar", action="store_true",
                        help="escribe los cambios; sin esto solo los enseña")
    parser.add_argument("--umbral", type=float, default=UMBRAL_PARECIDO,
                        help=f"parecido mínimo para aceptar una ciudad (por defecto {UMBRAL_PARECIDO})")
    args = parser.parse_args()

    with psycopg2.connect(**config_bd()) as conexion, conexion.cursor() as cursor:
        cursor.execute("SET search_path TO sch_estadisticas, pg_catalog")

        cursor.execute(SQL_MAPA_PAISES, {"minimo_votos": MINIMO_VOTOS,
                                         "minimo_porcentaje": MINIMO_PORCENTAJE})
        mapa = {fila[0]: fila[1] for fila in cursor.fetchall()}
        print(f"🗺️  {len(mapa)} nacionalidades con país deducido (votando con nombres únicos).")

        cursor.execute(SQL_PENDIENTES)
        pendientes = cursor.fetchall()
        print(f"📍 {len(pendientes)} lugares distintos por resolver.\n")

        resueltos = jugadores_resueltos = 0
        sin_pais = sin_parecido = 0
        muestras = []

        for lugar, pais, cuantos in pendientes:
            codigo = mapa.get(pais)
            if not codigo:
                sin_pais += 1
                continue

            cursor.execute(SQL_EMPAREJAR, {"lugar": lugar, "pais": codigo, "umbral": args.umbral})
            fila = cursor.fetchone()
            if not fila:
                sin_parecido += 1
                continue

            nombre, lat, lon, _poblacion, parecido = fila
            resueltos += 1
            jugadores_resueltos += cuantos
            if len(muestras) < 15:
                muestras.append(f"    {lugar!r} ({pais}) -> {nombre} [{codigo}] "
                                f"{lat:.3f},{lon:.3f} parecido {parecido:.2f} · {cuantos} jugador(es)")

            if args.aplicar:
                cursor.execute(SQL_ESCRIBIR, {"lat": lat, "lon": lon, "lugar": lugar, "pais": pais})

        for linea in muestras:
            print(linea)
        if resueltos > len(muestras):
            print(f"    ... y {resueltos - len(muestras)} más")

        print(f"\n{'APLICADO' if args.aplicar else 'ENSAYO EN SECO'}: "
              f"{resueltos} lugares emparejados ({jugadores_resueltos} jugadores).")
        print(f"   sin país deducible: {sin_pais} | sin ninguna ciudad parecida: {sin_parecido}")

        if args.aplicar:
            conexion.commit()
        elif resueltos:
            print("Vuelve a lanzarlo con --aplicar para escribirlo.")


if __name__ == "__main__":
    main()
