"""
Pasa el análisis de SonarQube igual que el Jenkinsfile, pero sin Jenkins.

El pipeline hace dos cosas distintas según el proyecto:
  - Los 7 módulos del backend son proyectos Maven y usan `sonar-maven-plugin`.
  - El frontend no es Maven: necesita el `sonar-scanner` standalone, que en CI viene
    instalado en la imagen del agente. Aquí se usa la imagen oficial del scanner por Docker
    para no tener que instalarlo en la máquina.

Jenkins inyecta el host y el token con `withSonarQubeEnv('SonarQube')`. Fuera de Jenkins hay
que dárselos a mano, así que el script los lee del .env.

Uso:
    python scripts/sonar.py                 # analiza todo y resume los hallazgos
    python scripts/sonar.py --solo-back
    python scripts/sonar.py --solo-front
    python scripts/sonar.py --resumen       # NO analiza: solo lee lo del último análisis

Antes de la primera vez hay que crear un token:
    http://localhost:9000  ->  tu usuario  ->  My Account  ->  Security  ->  Generate Token
y guardarlo en el .env como SONAR_TOKEN=...
"""

from _consola import forzar_utf8
import argparse
import os
import subprocess
import sys
from collections import Counter
from pathlib import Path

import requests

from _entorno import cargar_entorno

# La consola de Windows es cp1252 y revienta con el primer emoji que se imprima.
forzar_utf8()

cargar_entorno()

RAIZ = Path(__file__).resolve().parent.parent
BACKEND = RAIZ / "scorevia-backend"
FRONTEND = RAIZ / "scorevia-frontend"

SONAR_HOST = os.environ.get("SONAR_HOST_URL", "http://localhost:9000")
SONAR_TOKEN = os.environ.get("SONAR_TOKEN", "")

# Los mismos siete del Jenkinsfile, en el mismo orden.
MODULOS_BACKEND = [
    "EurekaServer",
    "Gateway",
    "Microservicio-usuarios-roles",
    "Microservicio-incidencias",
    "Microservicio-estadisticas",
    "Microservicio-predicciones",
]

SEVERIDAD_ORDEN = ["BLOCKER", "CRITICAL", "MAJOR", "MINOR", "INFO"]


def comprobar_entorno():
    try:
        estado = requests.get(f"{SONAR_HOST}/api/system/status", timeout=15).json()
    except requests.exceptions.RequestException:
        print(f"❌ SonarQube no responde en {SONAR_HOST}.")
        print("   Levántalo con: docker start scorevia-sonarqube")
        sys.exit(1)

    if estado.get("status") != "UP":
        print(f"❌ SonarQube está en estado {estado.get('status')}, no UP. Espera a que arranque.")
        sys.exit(1)

    if not SONAR_TOKEN:
        print("❌ Falta SONAR_TOKEN en el .env.")
        print(f"   Créalo en {SONAR_HOST} -> tu usuario -> My Account -> Security -> Generate Token")
        print("   y añade al .env:  SONAR_TOKEN=el_token_que_te_dé")
        sys.exit(1)

    print(f"✅ SonarQube {estado.get('version')} en {SONAR_HOST}\n")


def analizar_backend():
    """Un `mvn sonar:sonar` por módulo, igual que el pipeline."""
    mvnw = "mvnw.cmd" if os.name == "nt" else "./mvnw"
    for i, modulo in enumerate(MODULOS_BACKEND, 1):
        carpeta = BACKEND / modulo
        if not carpeta.is_dir():
            print(f"  [{i}/{len(MODULOS_BACKEND)}] ⏭️  {modulo}: no existe, se salta.")
            continue

        print(f"  [{i}/{len(MODULOS_BACKEND)}] {modulo}...", flush=True)
        proceso = subprocess.run(
            [mvnw, "-q", "org.sonarsource.scanner.maven:sonar-maven-plugin:sonar",
             f"-Dsonar.host.url={SONAR_HOST}", f"-Dsonar.token={SONAR_TOKEN}"],
            cwd=carpeta, capture_output=True, text=True, errors="replace",
        )
        if proceso.returncode != 0:
            cola = (proceso.stdout or proceso.stderr or "").strip().splitlines()[-6:]
            print(f"      ❌ falló:\n         " + "\n         ".join(cola))
        else:
            print("      ✅")


def analizar_frontend():
    """
    El frontend usa el scanner standalone; aquí va por Docker para no instalar nada.

    `host.docker.internal` es como el contenedor alcanza el SonarQube que corre en la
    máquina: dentro del contenedor, localhost sería el propio contenedor.
    """
    host_desde_docker = SONAR_HOST.replace("localhost", "host.docker.internal").replace("127.0.0.1", "host.docker.internal")
    print("  frontend (sonar-scanner por Docker)...", flush=True)
    proceso = subprocess.run(
        ["docker", "run", "--rm",
         "-v", f"{FRONTEND}:/usr/src",
         "-e", f"SONAR_HOST_URL={host_desde_docker}",
         "-e", f"SONAR_TOKEN={SONAR_TOKEN}",
         "--add-host", "host.docker.internal:host-gateway",
         "sonarsource/sonar-scanner-cli"],
        capture_output=True, text=True, errors="replace",
    )
    if proceso.returncode != 0:
        cola = (proceso.stdout or proceso.stderr or "").strip().splitlines()[-8:]
        print("      ❌ falló:\n         " + "\n         ".join(cola))
    else:
        print("      ✅")


def descubrir_proyectos():
    """
    Pregunta a SonarQube qué proyectos tiene, en vez de adivinar las claves.

    El plugin de Maven las compone como groupId:artifactId (com.scorevia.estadisticas:
    Microservicio-estadisticas) y el frontend usa la suya de sonar-project.properties, así
    que hardcodearlas se rompería en cuanto alguien tocara un pom.
    """
    try:
        resp = requests.get(f"{SONAR_HOST}/api/projects/search",
                            params={"ps": 100}, auth=(SONAR_TOKEN, ""), timeout=30)
        if resp.status_code != 200:
            print(f"   (no se pudo listar proyectos: HTTP {resp.status_code})")
            return []
        return [c["key"] for c in resp.json().get("components", [])]
    except requests.exceptions.RequestException as e:
        print(f"   (no se pudo listar proyectos: {e})")
        return []


def resumir(clave_proyecto):
    """Lee los hallazgos abiertos de un proyecto y los agrupa por regla."""
    try:
        resp = requests.get(
            f"{SONAR_HOST}/api/issues/search",
            params={"componentKeys": clave_proyecto, "resolved": "false", "ps": 500},
            auth=(SONAR_TOKEN, ""), timeout=60,
        )
    except requests.exceptions.RequestException as e:
        print(f"   (no se pudo consultar {clave_proyecto}: {e})")
        return 0

    if resp.status_code != 200:
        return 0

    datos = resp.json()
    total = datos.get("total", 0)
    if total == 0:
        return 0

    issues = datos.get("issues", [])
    por_severidad = Counter(i.get("severity", "?") for i in issues)
    por_regla = Counter((i.get("rule", "?"), i.get("message", "")[:70]) for i in issues)

    resumen_sev = "  ".join(f"{s}:{por_severidad[s]}" for s in SEVERIDAD_ORDEN if por_severidad[s])
    print(f"\n  {clave_proyecto}: {total} hallazgos abiertos   [{resumen_sev}]")
    for (regla, mensaje), veces in por_regla.most_common(8):
        print(f"     {veces:>4}x  {regla:<28} {mensaje}")
    return total


def main():
    parser = argparse.ArgumentParser(description="Análisis de SonarQube sin pasar por Jenkins.")
    parser.add_argument("--solo-back", action="store_true")
    parser.add_argument("--solo-front", action="store_true")
    parser.add_argument("--resumen", action="store_true", help="no analiza, solo lee el último análisis")
    args = parser.parse_args()

    comprobar_entorno()

    if not args.resumen:
        if not args.solo_front:
            print("== BACKEND ==")
            analizar_backend()
        if not args.solo_back:
            print("\n== FRONTEND ==")
            analizar_frontend()

    print("\n" + "=" * 72)
    print("RESUMEN DE HALLAZGOS ABIERTOS")
    print("=" * 72)

    claves = [m.lower() for m in MODULOS_BACKEND] + ["scorevia-frontend"]
    total = sum(resumir(c) for c in claves)
    print(f"\nTotal: {total} hallazgos.")
    print(f"Detalle completo en {SONAR_HOST}")


if __name__ == "__main__":
    main()
