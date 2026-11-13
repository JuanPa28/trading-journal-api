#!/usr/bin/env python3
"""Puerta de calidad (chaos gate): evalúa el resumen de k6 contra el umbral declarado.

Lee el resumen que exporta k6 (--summary-export), compara la métrica objetivo con el
umbral y termina con código distinto de cero si no se cumple. Además escribe una tabla en
Markdown (en el resumen del paso de GitHub Actions, si existe $GITHUB_STEP_SUMMARY) que
explica POR QUÉ pasó o falló: ese es el rastro de evidencia que pide la rúbrica.

Uso:
  python caos/gate/evaluar.py --resumen caos/resultados/gate-resumen.json --p95-max 1600 --exito-min 99
"""
from __future__ import annotations

import argparse
import json
import os
import sys


def leer(resumen: dict) -> dict:
    """Extrae del resumen de k6 las señales que usa la puerta."""
    metricas = resumen["metrics"]
    duracion = metricas["http_req_duration"]
    fallos = metricas["http_req_failed"]["value"]
    return {
        "peticiones": int(metricas["http_reqs"]["count"]),
        "exito_pct": 100 * (1 - fallos),
        "p50_ms": duracion.get("med", duracion.get("avg", 0)),
        "p95_ms": duracion["p(95)"],
        "p99_ms": duracion.get("p(99)"),
        "maximo_ms": duracion.get("max"),
        "rps": metricas["http_reqs"].get("rate"),
    }


def evaluar(senales: dict, p95_max: float, exito_min: float) -> list[dict]:
    """Devuelve la lista de comprobaciones con su resultado."""
    return [
        {"nombre": "Tasa de éxito del flujo", "valor": senales["exito_pct"], "limite": exito_min,
         "unidad": "%", "ok": senales["exito_pct"] >= exito_min, "regla": "≥"},
        {"nombre": "Latencia p95 extremo a extremo", "valor": senales["p95_ms"], "limite": p95_max,
         "unidad": "ms", "ok": senales["p95_ms"] <= p95_max, "regla": "≤"},
    ]


def informe(senales: dict, comprobaciones: list[dict], titulo: str) -> str:
    aprobado = all(c["ok"] for c in comprobaciones)
    lineas = [f"## {titulo}", "", f"**Resultado: {'APROBADO' if aprobado else 'RECHAZADO'}**", "",
              "| Comprobación | Medido | Regla | Resultado |", "|---|---|---|---|"]
    for c in comprobaciones:
        lineas.append(f"| {c['nombre']} | {c['valor']:.2f} {c['unidad']} | {c['regla']} {c['limite']:g} {c['unidad']} | "
                      f"{'cumple' if c['ok'] else 'NO cumple'} |")
    lineas += ["", f"Peticiones: {senales['peticiones']} · p50: {senales['p50_ms']:.0f} ms · "
               f"p99: {senales['p99_ms'] or 0:.0f} ms · máximo: {senales['maximo_ms'] or 0:.0f} ms"]
    if not aprobado:
        lineas += ["", "**Por qué falló:**"]
        for c in comprobaciones:
            if not c["ok"]:
                lineas.append(f"- {c['nombre']}: {c['valor']:.2f} {c['unidad']} no cumple {c['regla']} {c['limite']:g} {c['unidad']}.")
    return "\n".join(lineas) + "\n"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--resumen", required=True, help="resumen JSON exportado por k6")
    parser.add_argument("--p95-max", type=float, required=True, help="umbral de p95 en milisegundos")
    parser.add_argument("--exito-min", type=float, default=99.0, help="éxito mínimo en porcentaje")
    parser.add_argument("--titulo", default="Chaos gate")
    args = parser.parse_args(argv)
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")  # la consola de Windows no imprime ≥ ni ≤ por defecto

    with open(args.resumen, encoding="utf-8") as archivo:
        senales = leer(json.load(archivo))
    comprobaciones = evaluar(senales, args.p95_max, args.exito_min)
    texto = informe(senales, comprobaciones, args.titulo)

    print(texto)
    destino = os.environ.get("GITHUB_STEP_SUMMARY")
    if destino:
        with open(destino, "a", encoding="utf-8") as archivo:
            archivo.write(texto)
    return 0 if all(c["ok"] for c in comprobaciones) else 1


if __name__ == "__main__":
    sys.exit(main())
