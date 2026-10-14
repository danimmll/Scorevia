"""
Arregla los textos que llegaron con la codificación mal, tipo "SÃ£o Paulo" por "São Paulo".

QUÉ ES ESTO
-----------
Mojibake: un texto UTF-8 que en algún punto se leyó como Latin-1. Cada tilde se convierte en
dos caracteres raros, siempre los mismos, y por eso se puede deshacer: se vuelven a coger los
bytes tal y como se guardaron y se leen otra vez como UTF-8.

    "SÃ£o Paulo".encode("latin-1").decode("utf-8")  ->  "São Paulo"

NO ES CULPA DE LA INGESTA. Si lo fuera estarían mal los 524.121 jugadores, no 487: los equipos
y los entrenadores no tienen ni un caso. Son fichas que API-Football sirve ya rotas, así que no
hay nada que arreglar en el código y sí un repaso que conviene poder repetir cuando entren más.

POR QUÉ NO ES UN UPDATE Y YA
----------------------------
Porque casi ninguna ficha está rota del todo, y deshacer la cadena entera falla por culpa de
la parte que estaba bien: "Iván RodrÃ­guez" tiene la "á" correcta y solo la "í" rota, así que
el viaje de vuelta completo revienta ("invalid byte sequence for encoding UTF8: 0xc1 0x6e") y
en Postgres una sola de esas tumba el UPDATE entero. Aquí se arregla tramo a tramo y lo que no
salga limpio se deja como está: mejor un nombre feo que un nombre inventado.

Uso:
    python scripts/reparar_mojibake.py            # solo enseña lo que haría
    python scripts/reparar_mojibake.py --aplicar  # lo escribe
    python scripts/reparar_mojibake.py --comprobar # prueba la logica, sin tocar la BD
"""

import argparse
import sys
import re

import psycopg2

from _consola import forzar_utf8
from _entorno import cargar_entorno, config_bd

forzar_utf8()
cargar_entorno()

# Los caracteres que puede dejar un byte de continuación (0x80-0xBF) al releerse mal. Si la
# lectura fue Latin-1 se queda como carácter de control; si fue Windows-1252 —lo normal en un
# Windows— los 32 primeros salen como símbolos visibles: €, ‚, ƒ, †, ‡, ‰, Š, Œ, ', ", •, —, ™...
CONTINUACION = "\x80-\xbf\u20ac\u201a\u0192\u201e\u2026\u2020\u2021\u02c6\u2030\u0160" \
               "\u2039\u0152\u017d\u2018\u2019\u201c\u201d\u2022\u2013\u2014\u02dc" \
               "\u2122\u0161\u203a\u0153\u017e\u0178"

# Lo que delata el mojibake: una letra de arranque de UTF-8 (Ã, Â, Ä, Å, È, Ð, Ñ...) seguida de
# uno de esos caracteres. Buscar solo la letra daría falsos positivos: "Santo Ângelo" está bien
# escrito, y "Åge" es un nombre noruego de verdad.
#
# ANTES SOLO MIRABA 'Ã', 'Â' Y 'â€', o sea las tildes del oeste de Europa. Eso dejaba fuera
# familias enteras: la 'č' de Vuković, la 'š' de Škoda, la 'ą' de Piątek, la 'ş' de Taşkın y la
# 'ț' de Luchiță empiezan por 'Ä', 'Å' o 'È', y ninguna entraba en la consulta. Medido el
# 2026-09-10, después de dar por reparada la base: 171 nombres seguían rotos.
# La 'â' entra aunque sea minúscula: es el arranque del bloque de puntuación, o sea las
# comillas y los guiones tipográficos rotos ('Dâ€™Urso' por 'D’Urso').
ARRANQUE = "À-ÖØ-Þâ"

PATRON = r'[' + ARRANQUE + r'][' + CONTINUACION + r']'

# (tabla, clave primaria, columnas de texto a repasar)
OBJETIVOS = [
    ("sch_estadisticas.jugadores", "id_jugador", ["lugar_nacimiento", "nombre", "nombre_completo"]),
    ("sch_estadisticas.equipos", "id_equipo", ["nombre", "ciudad"]),
    ("sch_estadisticas.entrenadores", "id_entrenador", ["nombre"]),
    ("sch_estadisticas.estadios", "id_estadio", ["nombre", "ciudad"]),
]


# Un tramo roto: una letra de arranque de UTF-8 leída como Latin-1 (Ã, Â, Ä, Å, Ð, Ñ...)
# seguida de uno o más caracteres de continuación. Se buscan TRAMOS, no la cadena entera, y
# aquí está la gracia: casi ninguna ficha está rota del todo. "Iván RodrÃ­guez" tiene la "á" de
# Iván perfecta y solo la "í" rota, así que deshacer la cadena completa falla ("invalid byte
# sequence") y se quedaba sin arreglar. Eran 183 de los 545 casos.
TRAMO_ROTO = re.compile(r"(?:[" + ARRANQUE + r"][" + CONTINUACION + r"]+)+")


def _probar(trozo):
    """El trozo bien escrito, o None si no vuelve limpio por ninguna de las dos lecturas.

    Latin-1 y Windows-1252 solo se diferencian en 32 posiciones, pero son justo las que usan
    la 'ć' de Cvetković o la comilla de D'Urso: con una sola de las dos, esos nombres se
    quedaban sin arreglar.
    """
    for codificacion in ("latin-1", "cp1252"):
        try:
            return trozo.encode(codificacion).decode("utf-8")
        except (UnicodeEncodeError, UnicodeDecodeError):
            continue
    return None


def _deshacer(tramo):
    """El tramo bien escrito, o el mismo tramo si no hay vuelta atrás limpia."""
    entero = _probar(tramo)
    if entero is not None:
        return entero

    # El tramo entero no vuelve: se va tomando el trozo más largo que sí lo haga. Una letra
    # UTF-8 ocupa 2, 3 o 4 bytes, o sea el arranque más una, dos o tres continuaciones.
    #
    # Hace falta porque un tramo puede mezclar una letra rota con otra que está bien: en
    # "RÅ¯žičková" la 'ž' de después es correcta, pero también es uno de los símbolos de
    # Windows-1252, así que entra en el tramo y lo hacía irrecuperable entero.
    salida = []
    i = 0
    while i < len(tramo):
        arreglado = None
        for largo in (4, 3, 2):
            if i + largo > len(tramo):
                continue
            arreglado = _probar(tramo[i:i + largo])
            if arreglado is not None:
                salida.append(arreglado)
                i += largo
                break
        if arreglado is None:
            salida.append(tramo[i])
            i += 1
    return "".join(salida)


def reparar(texto):
    """El texto bien escrito, o None si no había nada que arreglar."""
    if texto is None:
        return None

    arreglado = TRAMO_ROTO.sub(lambda m: _deshacer(m.group()), texto)
    # El espacio duro (U+00A0) sale de un "Â " mal leído y en un nombre no pinta nada: parece
    # un espacio normal pero rompe las búsquedas y los cortes de línea.
    arreglado = arreglado.replace(" ", " ").strip()
    return arreglado if arreglado != texto else None


# Casos de control de `--comprobar`. Los de arriba tienen que arreglarse tal cual; los de
# abajo son nombres CORRECTOS que llevan las mismas letras y no se pueden tocar -- son la
# mitad que importa, porque el error caro aqui no es dejar un nombre feo, es estropear uno
# que estaba bien.
CASOS_ROTOS = {
    "Kristýna R\u00c5\u00af\u017eičková": "Kristýna Růžičková",
    "Artūrs Ozoli\u00c5\u0086\u0161": "Artūrs Ozoliņš",
    "D. Vukovi\u00c4\u0087": "D. Vuković",
    "N. Vukovi\u00c4\u2021": "N. Vuković",
    "Giuliano D\u00e2\u20ac\u2122Urso": "Giuliano D\u2019Urso",
    "P. R\u00c5\u00af\u00c5\u00beek": "P. Růžek",
    "Iv\u00c3\u00a1n Rodr\u00c3\u00adguez": "Iván Rodríguez",
}
CASOS_INTACTOS = ["Santo Ângelo", "Åge Hareide", "Jäger", "Škoda", "Vuković", "München"]


def comprobar():
    """Devuelve el numero de casos que fallan (0 = todo bien)."""
    fallos = 0
    for roto, esperado in CASOS_ROTOS.items():
        obtenido = reparar(roto)
        if obtenido != esperado:
            fallos += 1
            print(f"  MAL {roto!r} -> {obtenido!r}, se esperaba {esperado!r}")
    for bueno in CASOS_INTACTOS:
        obtenido = reparar(bueno)
        if obtenido is not None:
            fallos += 1
            print(f"  MAL se ha tocado un nombre correcto: {bueno!r} -> {obtenido!r}")
    print(f"Comprobacion: {len(CASOS_ROTOS)} rotos y {len(CASOS_INTACTOS)} correctos, "
          f"{fallos} fallos.")
    return fallos


def columnas_existentes(cursor, tabla, columnas):
    esquema, nombre = tabla.split(".")
    cursor.execute(
        "SELECT column_name FROM information_schema.columns "
        "WHERE table_schema = %s AND table_name = %s", (esquema, nombre))
    hay = {fila[0] for fila in cursor.fetchall()}
    return [c for c in columnas if c in hay]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--aplicar", action="store_true",
                        help="escribe los cambios; sin esto solo los enseña")
    parser.add_argument("--comprobar", action="store_true",
                        help="prueba la logica con casos conocidos y sale; no toca la BD")
    args = parser.parse_args()

    if args.comprobar:
        sys.exit(1 if comprobar() else 0)

    total_arreglados = 0
    total_intocables = 0

    with psycopg2.connect(**config_bd()) as conexion:
        for tabla, clave, columnas in OBJETIVOS:
            with conexion.cursor() as cursor:
                presentes = columnas_existentes(cursor, tabla, columnas)
                if not presentes:
                    continue

                for columna in presentes:
                    cursor.execute(
                        f"SELECT {clave}, {columna} FROM {tabla} WHERE {columna} ~ %s", (PATRON,))
                    filas = cursor.fetchall()
                    if not filas:
                        continue

                    cambios = []
                    intocables = 0
                    for identificador, valor in filas:
                        arreglado = reparar(valor)
                        if arreglado is None:
                            intocables += 1
                        else:
                            cambios.append((arreglado, identificador))

                    print(f"\n{tabla}.{columna}: {len(filas)} con la codificación rota "
                          f"-> {len(cambios)} arreglables, {intocables} sin arreglo posible")
                    for arreglado, identificador in cambios[:5]:
                        original = next(v for i, v in filas if i == identificador)
                        print(f"    {identificador}: {original!r} -> {arreglado!r}")
                    if len(cambios) > 5:
                        print(f"    ... y {len(cambios) - 5} más")

                    total_arreglados += len(cambios)
                    total_intocables += intocables

                    if args.aplicar and cambios:
                        with conexion.cursor() as escritor:
                            escritor.executemany(
                                f"UPDATE {tabla} SET {columna} = %s WHERE {clave} = %s", cambios)

        if args.aplicar:
            conexion.commit()

    print(f"\n{'APLICADO' if args.aplicar else 'ENSAYO EN SECO'}: "
          f"{total_arreglados} textos arreglados, {total_intocables} sin arreglo posible.")
    if not args.aplicar and total_arreglados:
        print("Vuelve a lanzarlo con --aplicar para escribirlo.")


if __name__ == "__main__":
    main()
