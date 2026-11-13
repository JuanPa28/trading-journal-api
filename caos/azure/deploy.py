#!/usr/bin/env python3
"""Despliega la API del coordinador en Azure Container Apps, todo en UN grupo de recursos.

Crea (es idempotente): el grupo de recursos, un espacio de trabajo de Log Analytics, el
entorno de Container Apps y la aplicación. Nada más: no crea identidades ni toca la
organización. Para eliminar todo basta `python caos/azure/deploy.py teardown`
(o `az group delete -n <grupo>`).

Los secretos (contraseña de la base, URL de conexión, llave de Oracle) se leen de los
archivos locales de canary/db y nunca se imprimen. Se pasan a Azure en un archivo temporal
que se borra al terminar.
"""
from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DB_DIR = ROOT / "canary" / "db"

RESOURCE_GROUP = os.environ.get("CAOS_RG", "rg-caos-trading-journal")
LOCATION = os.environ.get("CAOS_LOCATION", "eastus")
WORKSPACE = "log-caos"
ENVIRONMENT = "cae-caos"
APP = "api-coordinador"
TAGS = ["proyecto=trading-journal", "proposito=proyecto-universitario", "borrar-despues=si"]


def az(*args: str, check: bool = True) -> str:
    result = subprocess.run([shutil.which("az") or "az", *args], capture_output=True, text=True)
    if check and result.returncode != 0:
        raise SystemExit(f"az {' '.join(args[:3])} falló:\n{result.stderr.strip()[-600:]}")
    return result.stdout.strip()


def tfvar(name: str) -> str:
    text = (DB_DIR / "db.auto.tfvars").read_text(encoding="utf-8")
    match = re.search(rf'^{name}\s*=\s*"?([^"\r\n]*)"?\s*$', text, flags=re.M)
    if not match:
        raise SystemExit(f"Falta {name} en canary/db/db.auto.tfvars")
    return match.group(1)


def jdbc_url() -> str:
    result = subprocess.run([shutil.which("terraform") or "terraform", "output", "-raw", "database_jdbc_url"],
                            cwd=DB_DIR, capture_output=True, text=True)
    if result.returncode != 0:
        raise SystemExit("No pude leer la URL de la base con terraform output (¿está aplicado canary/db?)")
    return result.stdout.strip().splitlines()[-1]


def deploy(image: str, version: str, track: str, mari_url: str, pipe_url: str) -> None:
    print("Suscripción:", az("account", "show", "--query", "name", "-o", "tsv"))
    az("group", "create", "-n", RESOURCE_GROUP, "-l", LOCATION, "--tags", *TAGS, "-o", "none")
    print(f"Grupo de recursos {RESOURCE_GROUP}: listo")

    az("monitor", "log-analytics", "workspace", "create", "-g", RESOURCE_GROUP, "-n", WORKSPACE, "-l", LOCATION,
       "--retention-time", "30", "--tags", *TAGS, "-o", "none")
    workspace_id = az("monitor", "log-analytics", "workspace", "show", "-g", RESOURCE_GROUP, "-n", WORKSPACE,
                      "--query", "customerId", "-o", "tsv")
    workspace_key = az("monitor", "log-analytics", "workspace", "get-shared-keys", "-g", RESOURCE_GROUP, "-n", WORKSPACE,
                       "--query", "primarySharedKey", "-o", "tsv")
    print("Log Analytics: listo")

    az("containerapp", "env", "create", "-g", RESOURCE_GROUP, "-n", ENVIRONMENT, "-l", LOCATION,
       "--logs-workspace-id", workspace_id, "--logs-workspace-key", workspace_key, "--tags", *TAGS, "-o", "none")
    env_id = az("containerapp", "env", "show", "-g", RESOURCE_GROUP, "-n", ENVIRONMENT, "--query", "id", "-o", "tsv")
    print("Entorno de Container Apps: listo")

    key_path = Path(os.path.expanduser(tfvar("oci_private_key_path")))
    secrets = {
        "db-password": tfvar("db_admin_password"),
        "db-url": jdbc_url(),
        "oci-private-key": key_path.read_text(encoding="utf-8"),
    }
    env = {
        "SPRING_PROFILES_ACTIVE": "prod",
        "SPRING_JPA_HIBERNATE_DDL_AUTO": "update",
        "SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE": "6",
        "DB_USERNAME": "ADMIN",
        "APP_VERSION": version,
        "APP_TRACK": track,
        "JAVA_TOOL_OPTIONS": "-XX:MaxRAMPercentage=70 -XX:+UseSerialGC",
        "OCI_REGION": "sa-bogota-1",
        "OCI_TENANCY_ID": tfvar("oci_tenancy_ocid"),
        "OCI_USER_ID": tfvar("oci_user_ocid"),
        "OCI_FINGERPRINT": tfvar("oci_fingerprint"),
        "MARI_BASE_URL": mari_url,
        "PIPE_BASE_URL": pipe_url,
    }
    plain = [{"name": k, "value": v} for k, v in env.items()]
    refs = [{"name": "DB_PASSWORD", "secretRef": "db-password"}, {"name": "DB_URL", "secretRef": "db-url"},
            {"name": "OCI_PRIVATE_KEY", "secretRef": "oci-private-key"}]

    def probe(kind: str, path: str, **extra):
        return {"type": kind, "httpGet": {"path": path, "port": 8080}, **extra}

    definition = {
        "location": LOCATION,
        "tags": dict(t.split("=", 1) for t in TAGS),
        "properties": {
            "environmentId": env_id,
            "configuration": {
                "ingress": {"external": True, "targetPort": 8080, "transport": "auto"},
                "secrets": [{"name": k, "value": v} for k, v in secrets.items()],
            },
            "template": {
                "containers": [{
                    "name": APP, "image": image,
                    "resources": {"cpu": 0.5, "memory": "1Gi"},
                    "env": plain + refs,
                    "probes": [
                        probe("Startup", "/actuator/health/readiness", initialDelaySeconds=10, periodSeconds=5,
                              failureThreshold=36, timeoutSeconds=5),
                        probe("Liveness", "/actuator/health/liveness", periodSeconds=30, failureThreshold=3,
                              timeoutSeconds=5),
                        probe("Readiness", "/actuator/health/readiness", periodSeconds=10, failureThreshold=3,
                              timeoutSeconds=5),
                    ],
                }],
                "scale": {"minReplicas": 1, "maxReplicas": 3,
                          "rules": [{"name": "http", "http": {"metadata": {"concurrentRequests": "40"}}}]},
            },
        },
    }
    subscription = az("account", "show", "--query", "id", "-o", "tsv")
    url = (f"https://management.azure.com/subscriptions/{subscription}/resourceGroups/{RESOURCE_GROUP}"
           f"/providers/Microsoft.App/containerApps/{APP}?api-version=2024-03-01")
    with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False, encoding="utf-8") as handle:
        json.dump(definition, handle)
        path = handle.name
    try:
        print(f"Aplicación {APP}: aplicando con {image}")
        az("rest", "--method", "put", "--url", url, "--body", f"@{path}", "-o", "none")
    finally:
        os.unlink(path)

    for _ in range(40):  # espera a que Azure termine de aprovisionar (hasta ~5 minutos)
        state = az("containerapp", "show", "-g", RESOURCE_GROUP, "-n", APP, "--query",
                   "properties.provisioningState", "-o", "tsv", check=False)
        if state in ("Succeeded", "Failed", "Canceled"):
            print("Estado de aprovisionamiento:", state)
            break
        time.sleep(8)

    fqdn = az("containerapp", "show", "-g", RESOURCE_GROUP, "-n", APP, "--query",
              "properties.configuration.ingress.fqdn", "-o", "tsv")
    print(f"\nListo. URL: https://{fqdn}")
    print(f"Salud:    https://{fqdn}/actuator/health")


def teardown() -> None:
    if not az("group", "exists", "-n", RESOURCE_GROUP) == "true":
        print(f"El grupo {RESOURCE_GROUP} no existe: nada que eliminar.")
        return
    az("group", "delete", "-n", RESOURCE_GROUP, "--yes", "--no-wait")
    print(f"Eliminando {RESOURCE_GROUP} y todo lo que contiene (tarda unos minutos).")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)
    p = sub.add_parser("deploy")
    p.add_argument("--image", default="ghcr.io/juanpa28/trading-journal-api:v4.0.0")
    p.add_argument("--version", default="4.0.0")
    p.add_argument("--track", default="stable", choices=["stable", "canary"])
    p.add_argument("--mari-url", default="http://127.0.0.1:9", help="URL de la API de Mari (por defecto, falla rápido)")
    p.add_argument("--pipe-url", default="http://127.0.0.1:9", help="URL de la API de Pipe (por defecto, falla rápido)")
    sub.add_parser("teardown")
    args = parser.parse_args()
    if args.command == "deploy":
        deploy(args.image, args.version, args.track, args.mari_url, args.pipe_url)
    else:
        teardown()


if __name__ == "__main__":
    sys.exit(main())
