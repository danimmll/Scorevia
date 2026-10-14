"""
Carga de variables de entorno para los scripts de ingesta.

El proyecto tiene DOS ficheros .env y cada uno lleva cosas distintas:

  - `.env` (raíz)              -> API_FOOTBALL_KEY, ADMIN_EMAIL/ADMIN_PASSWORD...
  - `scorevia-backend/.env`    -> DB_NAME, DB_USER, DB_PASS... (el que lee docker compose)

Un `load_dotenv()` a secas busca hacia arriba desde el fichero que lo llama, encuentra el de
la raíz y para ahí, así que los scripts se quedaban sin credenciales de base de datos y
fallaban con:

    psycopg2.OperationalError: FATAL: password authentication failed for user "postgres"

...que hace pensar en una contraseña equivocada cuando en realidad no había ninguna: el
`os.environ.get("DB_PASS", ...)` caía a su valor por defecto.

`cargar_entorno()` carga los dos, en orden: primero la raíz y luego el del backend. Como
`load_dotenv` no pisa lo ya definido, la raíz manda si una variable estuviera en ambos.
"""

import os
from pathlib import Path

from dotenv import load_dotenv

# scripts/ cuelga de la raíz del proyecto.
RAIZ = Path(__file__).resolve().parent.parent

FICHEROS = (
    RAIZ / ".env",
    RAIZ / "scorevia-backend" / ".env",
)


def cargar_entorno():
    """Carga los .env del proyecto. Devuelve los que ha encontrado, para poder avisar."""
    encontrados = []
    for fichero in FICHEROS:
        if fichero.is_file():
            load_dotenv(fichero)
            encontrados.append(fichero)
    return encontrados


def config_bd():
    """
    Configuración de conexión para psycopg2, ya con el entorno cargado.

    Falla pronto y diciendo qué falta: sin esto, una variable ausente se convertía en una
    contraseña vacía y el error de Postgres apuntaba al sitio equivocado.
    """
    cargar_entorno()
    faltan = [k for k in ("DB_NAME", "DB_USER", "DB_PASS") if not os.environ.get(k)]
    if faltan:
        raise SystemExit(
            f"❌ Faltan variables de entorno: {', '.join(faltan)}.\n"
            f"   Se han buscado en:\n"
            + "".join(f"     - {f} {'(existe)' if f.is_file() else '(NO existe)'}\n" for f in FICHEROS)
            + "   Añádelas al .env que corresponda."
        )
    return {
        "dbname": os.environ["DB_NAME"],
        "user": os.environ["DB_USER"],
        "password": os.environ["DB_PASS"],
        "host": os.environ.get("DB_HOST", "127.0.0.1"),
        "port": os.environ.get("DB_PORT", "5433"),
    }
