"""
Copia de seguridad de la base de datos.

La base son ~4,5 GB que costaron cientos de miles de peticiones a API-Football: 1,4M de
partidos, 8,7M de eventos y 4,8M de estadísticas de jugador. Perder el volumen de Postgres
significa volver a gastar semanas de cuota, así que esto no es opcional.

Qué hace:
  1. `pg_dump -Fc` (formato custom, ya comprimido) dentro del contenedor, volcado a un
     fichero local con la fecha en el nombre.
  2. Lo VERIFICA con `pg_restore --list`: un fichero que existe pero está truncado no es
     una copia, y eso solo se descubre el día que hace falta restaurar.
  3. Rota: conserva las N últimas y borra las más viejas.

Uso:
    python scripts/backup_db.py                    # copia en ./backups
    python scripts/backup_db.py --destino D:/copias
    python scripts/backup_db.py --conservar 14

Para restaurar (CUIDADO: --clean borra los objetos existentes antes de recrearlos):
    docker cp copia.dump scorevia-backend-postgres-db-1:/tmp/copia.dump
    docker exec scorevia-backend-postgres-db-1 \\
        pg_restore -U postgres -d futbol_stats --clean --if-exists /tmp/copia.dump

Para dejarlo automático en Windows, Programador de tareas con:
    pythonw.exe C:\\Users\\danie\\Desktop\\FutbolProyect\\scripts\\backup_db.py
"""

from _consola import forzar_utf8
import argparse
import os
import subprocess
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

from _entorno import cargar_entorno

# La consola de Windows es cp1252 y revienta con el primer emoji que se imprima.
forzar_utf8()

cargar_entorno()

CONTENEDOR = os.environ.get("DB_CONTAINER", "scorevia-backend-postgres-db-1")
DB_NAME = os.environ.get("DB_NAME", "futbol_stats")
DB_USER = os.environ.get("DB_USER", "postgres")

# Un volcado de 4,5 GB comprimido tarda lo suyo; que no lo mate un timeout corto.
TIMEOUT_DUMP = 60 * 60


def humano(n_bytes):
    for unidad in ("B", "KB", "MB", "GB"):
        if n_bytes < 1024 or unidad == "GB":
            return f"{n_bytes:.1f} {unidad}"
        n_bytes /= 1024
    return f"{n_bytes:.1f} GB"


def contenedor_vivo():
    resultado = subprocess.run(
        ["docker", "ps", "--filter", f"name={CONTENEDOR}", "--format", "{{.Names}}"],
        capture_output=True, text=True,
    )
    return CONTENEDOR in resultado.stdout


def hacer_copia(destino: Path) -> Path:
    marca = datetime.now(timezone.utc).strftime("%Y%m%d_%H%M%S")
    fichero = destino / f"{DB_NAME}_{marca}.dump"

    print(f"📦 Volcando {DB_NAME} desde {CONTENEDOR}...")
    arranque = time.time()

    # -Fc: formato custom, comprimido y restaurable tabla a tabla con pg_restore.
    # La salida va por stdout y se escribe aquí, para no dejar el fichero dentro del
    # contenedor ocupando espacio en su capa de escritura.
    with fichero.open("wb") as salida:
        proceso = subprocess.run(
            ["docker", "exec", CONTENEDOR, "pg_dump", "-U", DB_USER, "-d", DB_NAME, "-Fc"],
            stdout=salida, stderr=subprocess.PIPE, timeout=TIMEOUT_DUMP,
        )

    if proceso.returncode != 0:
        fichero.unlink(missing_ok=True)
        print(f"❌ pg_dump falló: {proceso.stderr.decode(errors='replace')[:500]}")
        sys.exit(1)

    tam = fichero.stat().st_size
    print(f"   escrito en {time.time() - arranque:.0f}s -> {fichero.name} ({humano(tam)})")
    return fichero


def verificar(fichero: Path) -> None:
    """
    Un fichero que existe no es una copia: hay que poder leerlo.

    pg_restore --list recorre el índice del volcado, así que detecta un fichero truncado o
    corrupto sin necesidad de restaurar nada ni tocar la base de datos.
    """
    print("🔍 Verificando que la copia se puede leer...")
    with fichero.open("rb") as entrada:
        proceso = subprocess.run(
            ["docker", "exec", "-i", CONTENEDOR, "pg_restore", "--list"],
            stdin=entrada, capture_output=True, timeout=600,
        )

    if proceso.returncode != 0:
        print(f"❌ La copia NO es válida: {proceso.stderr.decode(errors='replace')[:400]}")
        print(f"   Se deja el fichero en {fichero} para que lo mires, pero no sirve.")
        sys.exit(1)

    objetos = len([l for l in proceso.stdout.decode(errors="replace").splitlines()
                   if l and not l.startswith(";")])
    print(f"   ✅ Copia válida, {objetos} objetos dentro.")


def rotar(destino: Path, conservar: int) -> None:
    copias = sorted(destino.glob(f"{DB_NAME}_*.dump"), key=lambda p: p.stat().st_mtime, reverse=True)
    sobrantes = copias[conservar:]
    if not sobrantes:
        print(f"🗂️  {len(copias)} copia(s) guardadas (se conservan {conservar}).")
        return
    for vieja in sobrantes:
        vieja.unlink()
        print(f"   borrada copia antigua: {vieja.name}")
    print(f"🗂️  {conservar} copias conservadas, {len(sobrantes)} borradas.")


def main():
    parser = argparse.ArgumentParser(description="Copia de seguridad de la base de datos de Scorevia.")
    parser.add_argument("--destino", default="backups", help="carpeta donde dejar las copias")
    parser.add_argument("--conservar", type=int, default=7, help="cuántas copias mantener")
    args = parser.parse_args()

    if not contenedor_vivo():
        print(f"❌ El contenedor '{CONTENEDOR}' no está levantado.")
        print("   Arráncalo con: docker compose -p scorevia-backend up -d postgres-db")
        sys.exit(1)

    destino = Path(args.destino).resolve()
    destino.mkdir(parents=True, exist_ok=True)

    fichero = hacer_copia(destino)
    verificar(fichero)
    rotar(destino, args.conservar)

    print(f"\n🎉 Copia terminada en {destino}")


if __name__ == "__main__":
    main()
