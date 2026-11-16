#!/usr/bin/env python3
"""Experimento 1 de seguridad: identidad de máquina del coordinador (Azure).

Condición adversa: alguien con acceso de administración a la suscripción lista los secretos de la
Container App (contraseña de la base de datos, URL de conexión y llave de Oracle). Se mide si la
acción se detecta y en cuánto tiempo. Técnica de ATT&CK: T1552.007 (Unsecured Credentials:
Container API). Controles de D3FEND relacionados: D3-CRO (Credential Rotation) y D3-UAP (User
Account Permissions).

Subcomandos:
  atacar --ronda N    Una llamada de administración listSecrets. Descarta los valores: solo cuenta.
  medir --ronda N     Calcula cuándo quedó visible la acción y cuándo sonó la alerta (MTTD).
  mejorar             Ronda 1: configuración de diagnóstico del registro de actividad, grupo de
                      acciones con correo y regla de alerta (deteccion.kql).
  revertir            Elimina lo que creó `mejorar` y deja el estado de la ronda 0.

Todo queda en seguridad/bitacora.md (hora de Colombia, responsable, acción y resultado) y en
seguridad/resultados/01-ronda-N.json. El correo de la alerta sale de la variable ALERT_EMAIL o del
archivo ~/.caos-secretos/alerta.env, nunca del repositorio.
"""
from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
import sys
import tempfile
import time
from datetime import datetime, timedelta, timezone
from pathlib import Path

AQUI = Path(__file__).resolve().parent
RESULTADOS = AQUI.parent / "resultados"
RG = os.environ.get("CAOS_RG", "rg-caos-trading-journal")
APP = "api-coordinador"
WORKSPACE = "log-caos"
AJUSTE = "caos-actividad"
GRUPO = "ag-caos"
REGLA = "alerta-listar-secretos"
RESPONSABLE = "Juan Pablo"
UMBRAL_DETECCION_S = 900   # T1 de la hipótesis: alerta en 15 minutos o menos


def ahora() -> datetime:
    return datetime.now(timezone.utc)


def hora_colombia() -> str:
    return (ahora() - timedelta(hours=5)).strftime("%H:%M:%S")


def iso(momento: datetime) -> str:
    return momento.strftime("%Y-%m-%dT%H:%M:%SZ")


def az(*args: str, check: bool = True) -> str:
    r = subprocess.run([shutil.which("az") or "az", *args], capture_output=True, text=True,
                       env={**os.environ, "MSYS_NO_PATHCONV": "1"})
    if check and r.returncode != 0:
        raise SystemExit(f"az {' '.join(args[:3])} falló:\n{r.stderr.strip()[-500:]}")
    return r.stdout.strip()


def arm(metodo: str, ruta: str, version: str, cuerpo: dict | None = None, parametros: list[str] | None = None) -> dict:
    """Llamada a Azure Resource Manager; el cuerpo viaja por archivo para no mezclar comillas."""
    # Los parámetros extra van por --url-parameters: az.cmd rompe un "&" dentro de la URL en Windows.
    argumentos = ["rest", "--method", metodo, "--url", f"https://management.azure.com{ruta}", "--url-parameters", f"api-version={version}", *(parametros or [])]
    ruta_cuerpo = None
    if cuerpo is not None:
        with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False, encoding="utf-8") as f:
            json.dump(cuerpo, f)
            ruta_cuerpo = f.name
        argumentos += ["--body", f"@{ruta_cuerpo}"]
    try:
        salida = az(*argumentos)
    finally:
        if ruta_cuerpo:
            os.unlink(ruta_cuerpo)
    return json.loads(salida) if salida else {}


def suscripcion() -> str:
    return az("account", "show", "--query", "id", "-o", "tsv")


def bitacora(accion: str, resultado: str) -> None:
    RESULTADOS.mkdir(parents=True, exist_ok=True)
    archivo = RESULTADOS.parent / "bitacora.md"
    if not archivo.exists():
        archivo.write_text("# Bitácora de los experimentos de seguridad (trading-journal)\n\n"
                           "| Hora (Colombia) | Quién | Acción | Resultado |\n|---|---|---|---|\n", encoding="utf-8")
    with archivo.open("a", encoding="utf-8") as f:
        f.write(f"| {hora_colombia()} | {RESPONSABLE} | {accion} | {resultado} |\n")


def correo() -> str:
    valor = os.environ.get("ALERT_EMAIL")
    if not valor:
        archivo = Path.home() / ".caos-secretos" / "alerta.env"
        if archivo.exists():
            datos = dict(l.split("=", 1) for l in archivo.read_text(encoding="utf-8").splitlines() if "=" in l)
            valor = datos.get("ALERT_EMAIL")
    if not valor:
        raise SystemExit("Falta ALERT_EMAIL (variable de entorno o ~/.caos-secretos/alerta.env)")
    return valor


def workspace_id() -> str:
    return az("monitor", "log-analytics", "workspace", "show", "-g", RG, "-n", WORKSPACE, "--query", "id", "-o", "tsv")


def consultar_logs(consulta: str) -> list[list]:
    cliente = az("monitor", "log-analytics", "workspace", "show", "-g", RG, "-n", WORKSPACE, "--query", "customerId", "-o", "tsv")
    with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False, encoding="utf-8") as f:
        json.dump({"query": consulta}, f)
        ruta = f.name
    try:
        r = subprocess.run([shutil.which("az") or "az", "rest", "--method", "post",
                            "--url", f"https://api.loganalytics.io/v1/workspaces/{cliente}/query",
                            "--resource", "https://api.loganalytics.io", "--headers", "Content-Type=application/json",
                            "--body", f"@{ruta}"], capture_output=True, text=True)
    finally:
        os.unlink(ruta)
    if r.returncode != 0:
        return []  # la tabla AzureActivity no existe todavía: no hay telemetría
    return json.loads(r.stdout)["tables"][0]["rows"]


# --------------------------------------------------------------------------- atacar
def atacar(ronda: int) -> None:
    ruta = f"/subscriptions/{suscripcion()}/resourceGroups/{RG}/providers/Microsoft.App/containerApps/{APP}/listSecrets"
    t0 = ahora()
    respuesta = arm("POST", ruta, "2024-03-01")
    cantidad = len(respuesta.get("value", []))  # los valores se descartan: solo se cuenta
    del respuesta
    RESULTADOS.mkdir(parents=True, exist_ok=True)
    (RESULTADOS / f"01-ronda-{ronda}.json").write_text(
        json.dumps({"ronda": ronda, "t0": iso(t0), "secretos_listados": cantidad}, indent=2), encoding="utf-8")
    bitacora(f"Ronda {ronda}: listSecrets sobre {APP}", f"{cantidad} secretos listados (valores descartados) a las {iso(t0)}")
    print(f"t0 = {iso(t0)}  secretos listados: {cantidad} (valores descartados)")


# --------------------------------------------------------------------------- medir
def medir(ronda: int, espera: int) -> None:
    archivo = RESULTADOS / f"01-ronda-{ronda}.json"
    datos = json.loads(archivo.read_text(encoding="utf-8"))
    t0 = datetime.strptime(datos["t0"], "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=timezone.utc)
    limite = time.time() + espera
    visible = alerta = None
    alertas_regla = 0
    while time.time() < limite:
        filas = consultar_logs(
            f'AzureActivity | where TimeGenerated > datetime({datos["t0"]}) - 1m '
            '| where OperationNameValue =~ "MICROSOFT.APP/CONTAINERAPPS/LISTSECRETS/ACTION" '
            '| summarize min(TimeGenerated), min(ingestion_time())')
        if filas and filas[0][0] and visible is None:
            visible = datetime.fromisoformat(filas[0][1].replace("Z", "+00:00"))
        alertas = arm("GET", f"/subscriptions/{suscripcion()}/providers/Microsoft.AlertsManagement/alerts",
                      "2019-03-01", parametros=["timeRange=1d"]).get("value", [])
        for a in alertas:
            esenciales = a["properties"]["essentials"]
            inicio = datetime.fromisoformat(esenciales["startDateTime"].replace("Z", "+00:00"))
            if REGLA in esenciales.get("alertRule", "") and inicio >= t0:
                alerta = inicio
                alertas_regla += 1
        if alerta:
            break
        time.sleep(30)
    datos["evento_visible_en_logs_s"] = round((visible - t0).total_seconds()) if visible else None
    datos["mttd_s"] = round((alerta - t0).total_seconds()) if alerta else None
    datos["detectado"] = alerta is not None
    datos["alertas_de_la_regla"] = alertas_regla
    datos["umbral_hipotesis_s"] = UMBRAL_DETECCION_S
    datos["cumple_t1"] = bool(alerta) and datos["mttd_s"] <= UMBRAL_DETECCION_S
    archivo.write_text(json.dumps(datos, indent=2), encoding="utf-8")
    bitacora(f"Ronda {ronda}: medición", "alerta a los %s s" % datos["mttd_s"] if alerta else "no hubo alerta en la ventana de espera")
    print(json.dumps(datos, indent=2))


# --------------------------------------------------------------------------- mejorar / revertir
def mejorar() -> None:
    sus = suscripcion()
    espacio = workspace_id()
    arm("PUT", f"/subscriptions/{sus}/providers/microsoft.insights/diagnosticSettings/{AJUSTE}", "2021-05-01-preview",
        {"properties": {"workspaceId": espacio, "logs": [{"category": "Administrative", "enabled": True}]}})
    bitacora("Mejora 1a: configuración de diagnóstico del registro de actividad hacia log-caos", "creada")
    grupo = f"/subscriptions/{sus}/resourceGroups/{RG}/providers/microsoft.insights/actionGroups/{GRUPO}"
    arm("PUT", grupo, "2023-01-01", {"location": "Global", "properties": {
        "groupShortName": "caos", "enabled": True,
        "emailReceivers": [{"name": "responsable", "emailAddress": correo(), "useCommonAlertSchema": True}]}})
    bitacora("Mejora 1b: grupo de acciones con correo", "creado")
    consulta = "\n".join(l for l in (AQUI / "deteccion.kql").read_text(encoding="utf-8").splitlines() if not l.startswith("//"))
    ubicacion = az("group", "show", "-n", RG, "--query", "location", "-o", "tsv")
    arm("PUT", f"/subscriptions/{sus}/resourceGroups/{RG}/providers/microsoft.insights/scheduledQueryRules/{REGLA}", "2022-06-15", {
        "location": ubicacion, "properties": {
            "displayName": "Listado de secretos de la Container App", "severity": 2, "enabled": True,
            "evaluationFrequency": "PT5M", "windowSize": "PT10M", "scopes": [espacio],
            "criteria": {"allOf": [{"query": consulta, "timeAggregation": "Count", "operator": "GreaterThan", "threshold": 0,
                                    "failingPeriods": {"numberOfEvaluationPeriods": 1, "minFailingPeriodsToAlert": 1}}]},
            "actions": {"actionGroups": [grupo]}}})
    bitacora("Mejora 1c: regla de alerta sobre deteccion.kql (cada 5 min, ventana de 10 min)", "creada")
    print("Mejora 1 aplicada: diagnóstico + grupo de acciones + regla de alerta")


def revertir() -> None:
    sus = suscripcion()
    for ruta, version in ((f"/subscriptions/{sus}/resourceGroups/{RG}/providers/microsoft.insights/scheduledQueryRules/{REGLA}", "2022-06-15"),
                          (f"/subscriptions/{sus}/resourceGroups/{RG}/providers/microsoft.insights/actionGroups/{GRUPO}", "2023-01-01"),
                          (f"/subscriptions/{sus}/providers/microsoft.insights/diagnosticSettings/{AJUSTE}", "2021-05-01-preview")):
        az("rest", "--method", "delete", "--url", f"https://management.azure.com{ruta}?api-version={version}", check=False)
    bitacora("Reversión: elimino regla, grupo de acciones y diagnóstico", "estado de la ronda 0")
    print("Revertido: sin regla, sin grupo de acciones, sin diagnóstico")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="cmd", required=True)
    for nombre in ("atacar", "medir"):
        p = sub.add_parser(nombre)
        p.add_argument("--ronda", type=int, choices=[0, 1], required=True)
        if nombre == "medir":
            p.add_argument("--espera", type=int, default=900, help="segundos máximos de espera de la alerta")
    sub.add_parser("mejorar")
    sub.add_parser("revertir")
    a = parser.parse_args()
    {"atacar": lambda: atacar(a.ronda), "medir": lambda: medir(a.ronda, a.espera),
     "mejorar": mejorar, "revertir": revertir}[a.cmd]()


if __name__ == "__main__":
    sys.exit(main())
