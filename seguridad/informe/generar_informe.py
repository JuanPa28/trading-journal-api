#!/usr/bin/env python3
"""Genera el informe de seguridad (HTML y PDF, máximo 10 páginas) a partir de la evidencia y la bitácora.

Las cifras de los experimentos 1 y 4 salen de seguridad/resultados/*.json y la cronología sale de
seguridad/bitacora.md; no se escriben a mano. Lo que falta (experimento 3 de Pipe) se dice como pendiente.

Uso:
  python seguridad/informe/generar_informe.py                       # informe del repositorio
  python seguridad/informe/generar_informe.py --herramienta "Claude (Anthropic)" --salida RUTA   # versión nombrando la herramienta
"""
from __future__ import annotations

import argparse
import html
import json
import re
import subprocess
import sys
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[2]
SEG = RAIZ / "seguridad"
RES = SEG / "resultados"
SALIDA = Path(__file__).resolve().parent


def cargar(nombre: str) -> dict:
    return json.loads((RES / nombre).read_text(encoding="utf-8"))


def cronologia() -> str:
    """Tabla de la bitácora sin las filas de medición repetidas, con la hora local y la hora UTC."""
    filas = []
    for linea in (SEG / "bitacora.md").read_text(encoding="utf-8").splitlines():
        if not linea.startswith("| ") or linea.startswith("| Hora") or linea.startswith("|---"):
            continue
        celdas = [c.strip() for c in linea.strip().strip("|").split("|")]
        if len(celdas) < 4 or ": medición" in celdas[2] and celdas[3].startswith("{"):
            continue  # las mediciones en JSON ya están en las tablas de resultados
        hora, _, accion, resultado = celdas[:4]
        solo = re.match(r"(\d{2}):(\d{2})(?::(\d{2}))?", hora)
        utc = f"{int(solo.group(1)) + 5:02d}:{solo.group(2)}" + (f":{solo.group(3)}" if solo.group(3) else "") if solo else ""
        filas.append(f"<tr><td class=\"n\">{html.escape(hora)}</td><td class=\"n\">{utc}</td><td>{html.escape(accion)}</td><td>{html.escape(resultado)}</td></tr>")
    return "".join(filas)


def construir(herramienta: str) -> str:
    r0, r1 = cargar("01-ronda-0.json"), cargar("01-ronda-1.json")
    d0, d1, d2 = cargar("04-ronda-0.json"), cargar("04-ronda-1.json"), cargar("04-ronda-2.json")
    svg = (RAIZ / "docs" / "seguridad-capas.svg").read_text(encoding="utf-8")
    svg = svg.replace("<svg ", '<svg class="capas" ', 1)
    kql = (SEG / "identidad" / "deteccion.kql").read_text(encoding="utf-8")
    kql = "\n".join(l for l in kql.splitlines() if not l.startswith("//"))
    plantilla = (SALIDA / "plantilla.html").read_text(encoding="utf-8")
    valores = {
        "SVG_CAPAS": svg, "CRONOLOGIA": cronologia(), "KQL": html.escape(kql),
        "MTTD1": str(r1["mttd_s"]), "VISIBLE1": str(r1["evento_visible_en_logs_s"]), "SECRETOS": str(r0["secretos_listados"]),
        "T0_R0": r0["t0"][11:19], "T0_R1": r1["t0"][11:19],
        "D0_REC": f"{d0['objetos_recuperados']} de {d0['objetos_total']}", "D1_REC": f"{d1['objetos_recuperados']} de {d1['objetos_total']}",
        "D2_REC": f"{d2['objetos_recuperados']} de {d2['objetos_total']}", "D1_RTO": f"{d1['rto_s']} s", "D2_RTO": f"{d2['rto_s']} s",
        "HERRAMIENTA": herramienta,
    }
    for clave, valor in valores.items():
        plantilla = plantilla.replace(f"__{clave}__", valor)
    return plantilla


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--herramienta", default="un asistente de IA", help="cómo se nombra la herramienta de IA en la sección de IA")
    parser.add_argument("--salida", type=Path, default=SALIDA / "informe-seguridad")
    parser.add_argument("--sin-pdf", action="store_true")
    a = parser.parse_args()
    destino = a.salida.with_suffix(".html")
    destino.write_text(construir(a.herramienta), encoding="utf-8")
    print("HTML:", destino)
    if not a.sin_pdf:
        pdf = a.salida.with_suffix(".pdf")
        perfil = destino.parent / ".perfil-pdf"
        subprocess.run([r"C:\Program Files\Google\Chrome\Application\chrome.exe", "--headless=new", "--disable-gpu", "--no-pdf-header-footer",
                        f"--user-data-dir={perfil}", f"--print-to-pdf={pdf}", destino.as_uri()], capture_output=True)
        paginas = len(re.findall(rb"/Type\s*/Page[^s]", pdf.read_bytes()))
        print(f"PDF: {pdf} ({paginas} páginas)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
