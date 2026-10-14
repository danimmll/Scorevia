"""
Salida de consola utilizable en Windows.

La consola de Windows usa cp1252 por defecto, que no sabe representar ni un emoji ni
una flecha. Como todos los scripts de ingesta imprimen ✅ / ❌ / 🚀 para marcar el
progreso, el primer print con emoji reventaba el proceso entero:

    UnicodeEncodeError: 'charmap' codec can't encode character '\\u2705'

Y lo hacía a traición: el script moría a mitad de un backfill largo, después de haber
gastado cuota de API, por un carácter decorativo.

Se usa llamando a `forzar_utf8()` al principio de cada script. Podría hacerse solo con
importar el módulo, pero entonces el import queda como "no usado" y acaba borrándolo
cualquier limpieza automática o un linter, sin que nadie entienda qué se ha roto.
"""

import sys


def forzar_utf8():
    for flujo in (sys.stdout, sys.stderr):
        # Bajo pytest o con la salida redirigida el flujo puede no ser reconfigurable.
        reconfigurar = getattr(flujo, "reconfigure", None)
        if reconfigurar is None:
            continue
        try:
            reconfigurar(encoding="utf-8", errors="replace")
        except (ValueError, OSError):
            # Si ni siquiera se puede pasar a UTF-8, al menos que no lance: con
            # errors="replace" los emoji salen como "?" pero el backfill sigue vivo.
            try:
                reconfigurar(errors="replace")
            except (ValueError, OSError):
                pass
