#!/usr/bin/env python3
"""Exporta de Log Analytics la serie por minuto de las rondas (latencia, peticiones, errores y ventana de inyección).

Es la misma información del Workbook, guardada como JSON para que el informe la dibuje sin depender de la nube.
Uso: python caos/tablero/exportar_serie.py 2026-11-16T17:20:00Z 2026-11-16T17:46:00Z caos/resultados/serie-rondas.json
"""
import json
import os
import shutil
import subprocess
import sys
import tempfile

RG, WORKSPACE = "rg-caos-trading-journal", "log-caos"
desde, hasta, salida = sys.argv[1:4]


def az(*args):
    r = subprocess.run([shutil.which("az") or "az", *args], capture_output=True, text=True)
    if r.returncode:
        raise SystemExit(r.stderr[-400:])
    return r.stdout.strip()


def consulta(workspace, texto):
    with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False, encoding="utf-8") as f:
        json.dump({"query": texto}, f)
        ruta = f.name
    try:
        salida_az = az("rest", "--method", "post", "--url", f"https://api.loganalytics.io/v1/workspaces/{workspace}/query",
                       "--resource", "https://api.loganalytics.io", "--headers", "Content-Type=application/json", "--body", "@" + ruta)
    finally:
        os.unlink(ruta)
    tabla = json.loads(salida_az)["tables"][0]
    return [dict(zip([c["name"] for c in tabla["columns"]], fila)) for fila in tabla["rows"]]


ws = az("monitor", "log-analytics", "workspace", "show", "-g", RG, "-n", WORKSPACE, "--query", "customerId", "-o", "tsv")
rango = f'TimeGenerated between (datetime({desde}) .. datetime({hasta}))'
flujo = consulta(ws, f'ContainerAppConsoleLogs_CL | where {rango} | where Log_s has "event=request" and Log_s has "route=/api/v2/trades" '
                     '| extend ms=toint(extract("ms=([0-9]+)",1,Log_s)), st=extract("status=([0-9]+)",1,Log_s) '
                     '| summarize peticiones=count(), p95_ms=percentile(ms,95), errores=countif(st != "200") by minuto=bin(TimeGenerated,1m) | order by minuto asc')
inyeccion = consulta(ws, f'ContainerAppConsoleLogs_CL | where {rango} | where Log_s has "event=outbound_fault_header" '
                         '| summarize con_cabecera=count() by minuto=bin(TimeGenerated,1m) | order by minuto asc')
json.dump({"desde": desde, "hasta": hasta, "flujo": flujo, "inyeccion": inyeccion}, open(salida, "w", encoding="utf-8"), indent=1, ensure_ascii=False)
print(f"{len(flujo)} minutos de flujo, {len(inyeccion)} con inyección -> {salida}")
