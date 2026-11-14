#!/usr/bin/env python3
"""Genera y publica el tablero del coordinador como un libro de trabajo (Workbook) de Azure.

Toma las consultas de consultas.kql (una sola fuente de verdad), arma el libro con una gráfica
por consulta, en el orden en que se leen (latencia, errores y ventana de inyección una debajo
de la otra, con el mismo eje de tiempo) y lo publica en el grupo de recursos del coordinador,
de modo que `deploy.py teardown` lo elimina junto con todo lo demás.

Uso:
  python caos/tablero/generar_tablero.py                # solo escribe tablero.workbook.json
  python caos/tablero/generar_tablero.py --publicar     # además lo crea o actualiza en Azure
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
import uuid
from pathlib import Path

AQUI = Path(__file__).resolve().parent
RESOURCE_GROUP = os.environ.get("CAOS_RG", "rg-caos-trading-journal")
WORKSPACE = "log-caos"
TITULO = "Caos: flujo de las tres nubes"
# Nombre fijo (un GUID derivado del título) para que publicar de nuevo actualice, no duplique.
NOMBRE = str(uuid.uuid5(uuid.NAMESPACE_URL, "caos-flujo-tres-nubes"))

# (índice de la consulta en consultas.kql, título de la gráfica, visualización)
GRAFICAS = [
    (0, "Latencia extremo a extremo por minuto: p50, p95 y p99 (ms)", "timechart"),
    (1, "Tasa de error del flujo por minuto (%)", "timechart"),
    (2, "Ventana de inyección de la falla: peticiones con la cabecera de latencia por minuto", "barchart"),
    (3, "Llamadas fallidas o cortadas hacia los compañeros y circuito abierto", "barchart"),
]


def leer_consultas() -> list[str]:
    texto = (AQUI / "consultas.kql").read_text(encoding="utf-8")
    bloques = re.split(r"^// === .* ===$", texto, flags=re.M)[1:]
    return ["\n".join(l for l in b.splitlines() if not l.strip().startswith("//")).strip() for b in bloques]


def construir() -> dict:
    consultas = leer_consultas()
    items = [
        {"type": 1, "name": "introduccion", "content": {"json": (
            f"## {TITULO}\n\n"
            "Cada gráfica usa el **mismo eje de tiempo**: la ventana de inyección de la falla aparece justo debajo de la latencia y de los errores. "
            "Así se ve el instante exacto en que se inyecta la falla y cómo responde la métrica objetivo en la ronda 0 y en la ronda 1.\n\n"
            "Fuente: la línea de acceso (`event=request`) que escribe el coordinador por cada petición.")}},
        {"type": 9, "name": "parametros", "content": {
            "version": "KqlParameterItem/1.0",
            "parameters": [{
                "id": str(uuid.uuid5(uuid.NAMESPACE_URL, "caos-rango")), "version": "KqlParameterItem/1.0",
                "name": "Rango", "label": "Rango de tiempo", "type": 4, "isRequired": True,
                "value": {"durationMs": 3600000},
                "typeSettings": {"selectableValues": [{"durationMs": 900000}, {"durationMs": 3600000},
                                                      {"durationMs": 14400000}, {"durationMs": 86400000}],
                                 "allowCustom": True}}],
            "style": "pills", "queryType": 0, "resourceType": "microsoft.operationalinsights/workspaces"}},
    ]
    for indice, titulo, visualizacion in GRAFICAS:
        items.append({"type": 3, "name": f"consulta-{indice + 1}", "content": {
            "version": "KqlItem/1.0", "query": consultas[indice], "size": 0, "title": titulo,
            "timeContext": {"durationMs": 0}, "timeContextFromParameter": "Rango",
            "queryType": 0, "resourceType": "microsoft.operationalinsights/workspaces",
            "visualization": visualizacion}})
    return {"version": "Notebook/1.0", "items": items, "$schema": "https://github.com/Microsoft/Application-Insights-Workbooks/blob/master/schema/workbook.json"}


def az(*args: str) -> str:
    resultado = subprocess.run([shutil.which("az") or "az", *args], capture_output=True, text=True)
    if resultado.returncode != 0:
        raise SystemExit(f"az {' '.join(args[:3])} falló:\n{resultado.stderr.strip()[-500:]}")
    return resultado.stdout.strip()


def publicar(libro: dict) -> str:
    suscripcion = az("account", "show", "--query", "id", "-o", "tsv")
    espacio = az("monitor", "log-analytics", "workspace", "show", "-g", RESOURCE_GROUP, "-n", WORKSPACE,
                 "--query", "id", "-o", "tsv")
    ubicacion = az("group", "show", "-n", RESOURCE_GROUP, "--query", "location", "-o", "tsv")
    recurso = (f"/subscriptions/{suscripcion}/resourceGroups/{RESOURCE_GROUP}"
               f"/providers/microsoft.insights/workbooks/{NOMBRE}")
    cuerpo = {"location": ubicacion, "kind": "shared", "properties": {
        "displayName": TITULO, "category": "workbook", "version": "Notebook/1.0",
        "sourceId": espacio, "serializedData": json.dumps(libro)}}
    with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False, encoding="utf-8") as archivo:
        json.dump(cuerpo, archivo)
        ruta = archivo.name
    try:
        az("rest", "--method", "put", "--url", f"https://management.azure.com{recurso}?api-version=2022-04-01",
           "--body", f"@{ruta}", "-o", "none")
    finally:
        os.unlink(ruta)
    return recurso


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--publicar", action="store_true", help="crea o actualiza el libro en Azure")
    argumentos = parser.parse_args()
    libro = construir()
    (AQUI / "tablero.workbook.json").write_text(json.dumps(libro, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"Escrito tablero.workbook.json ({len(libro['items'])} elementos)")
    if argumentos.publicar:
        recurso = publicar(libro)
        print("Publicado:", recurso)
        print(f"Abrir: https://portal.azure.com/#@/resource{recurso}/workbook")


if __name__ == "__main__":
    sys.exit(main())
