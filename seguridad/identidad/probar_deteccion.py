#!/usr/bin/env python3
"""Prueba la regla deteccion.kql con casos positivos y negativos, sin tocar recursos reales.

Sustituye la tabla AzureActivity por una tabla de datos de prueba (datatable) y ejecuta la
regla en el espacio de trabajo log-caos. Un caso positivo debe devolver su fila; un negativo, ninguna.
Uso: python seguridad/identidad/probar_deteccion.py <workspace-id>
"""
import json, os, shutil, subprocess, sys, tempfile
from pathlib import Path

REGLA = (Path(__file__).parent / "deteccion.kql").read_text(encoding="utf-8")
OP = "MICROSOFT.APP/CONTAINERAPPS/LISTSECRETS/ACTION"
# (nombre, categoría, operación, estado, ¿debe alertar?)
CASOS = [
    ("listar secretos con éxito", "Administrative", OP, "Succeeded", True),
    ("listar secretos fallido", "Administrative", OP, "Failed", True),
    ("operación en minúsculas", "Administrative", OP.lower(), "Success", True),
    ("lectura de la app, no de secretos", "Administrative", "MICROSOFT.APP/CONTAINERAPPS/READ", "Succeeded", False),
    ("escritura de la app", "Administrative", "MICROSOFT.APP/CONTAINERAPPS/WRITE", "Succeeded", False),
    ("evento de inicio sin resultado", "Administrative", OP, "Started", False),
    ("categoría que no es administrativa", "Policy", OP, "Succeeded", False),
]


def consulta() -> str:
    filas = ",\n".join(
        f'    datetime(2026-11-18T15:00:00Z), "{n}", "{c}", "{o}", "{e}", "usuario-prueba", "203.0.113.7", "/subscriptions/x/resourceGroups/rg/providers/Microsoft.App/containerApps/api"'
        for n, c, o, e, _ in CASOS)
    tabla = ("let AzureActivity = datatable(TimeGenerated:datetime, Caso:string, CategoryValue:string, "
             "OperationNameValue:string, ActivityStatusValue:string, Caller:string, CallerIpAddress:string, ResourceId:string)[\n" + filas + "\n];\n")
    cuerpo = REGLA.replace("| project TimeGenerated, Caller", "| project Caso, TimeGenerated, Caller")
    return tabla + cuerpo


def ejecutar(workspace: str) -> list[list]:
    h = tempfile.NamedTemporaryFile("w", suffix=".json", delete=False, encoding="utf-8")
    json.dump({"query": consulta()}, h); h.close()
    r = subprocess.run([shutil.which("az"), "rest", "--method", "post",
                        "--url", f"https://api.loganalytics.io/v1/workspaces/{workspace}/query",
                        "--resource", "https://api.loganalytics.io", "--headers", "Content-Type=application/json",
                        "--body", "@" + h.name], capture_output=True, text=True)
    os.unlink(h.name)
    if r.returncode:
        sys.exit("ERROR: " + r.stderr[-400:])
    return json.loads(r.stdout)["tables"][0]["rows"]


def main() -> int:
    alertaron = {fila[0] for fila in ejecutar(sys.argv[1])}
    fallos = 0
    for nombre, *_, esperado in CASOS:
        obtenido = nombre in alertaron
        ok = obtenido == esperado
        fallos += not ok
        print(("OK   " if ok else "FALLA"), f"{nombre}: esperado={'alerta' if esperado else 'silencio'} obtenido={'alerta' if obtenido else 'silencio'}")
    print(f"{len(CASOS) - fallos}/{len(CASOS)} casos correctos")
    return 1 if fallos else 0


if __name__ == "__main__":
    sys.exit(main())
