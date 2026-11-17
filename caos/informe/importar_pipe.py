#!/usr/bin/env python3
"""Resume la evidencia del experimento de ecommerce (Pipe, AWS) para el informe de caos.

Lee los k6-summary.json y los diarios de Chaos Toolkit del repositorio de Pipe (jfpa11100/ecommerce-api,
carpeta chaos/results/) y escribe caos/resultados/ecommerce-resumen.json con las cifras y el commit de
origen. No copia los archivos: el informe cita su repositorio como fuente.

Uso: python caos/informe/importar_pipe.py <ruta al clon del repositorio de Pipe>
"""
from __future__ import annotations

import glob
import json
import re
import statistics as st
import subprocess
import sys
from pathlib import Path

DESTINO = Path(__file__).resolve().parents[1] / "resultados" / "ecommerce-resumen.json"


def corrida(archivo: str) -> dict:
    m = json.loads(Path(archivo).read_text(encoding="utf-8"))["metrics"]
    t = m["http_req_duration"]
    return {"peticiones": m["http_reqs"]["count"], "throughput": m["http_reqs"]["rate"], "p50": t["med"], "p90": t["p(90)"],
            "p95": t["p(95)"], "max": t["max"], "fallidas": m["http_req_failed"]["passes"],
            "exito_pct": 100 * (1 - m["http_req_failed"]["value"])}


def mediana(corridas: list[dict]) -> dict:
    return {k: st.median(c[k] for c in corridas) for k in corridas[0]}


def mttr(diario: str) -> int | None:
    j = json.loads(Path(diario).read_text(encoding="utf-8"))
    for accion in j["run"]:
        salida = accion.get("output")
        texto = (salida.get("stdout") if isinstance(salida, dict) else "") or ""
        hallado = re.search(r"Recuperado en (\d+)s", texto)
        if hallado:
            return int(hallado.group(1))
    return None


def main(repo: str) -> None:
    r = Path(repo) / "chaos" / "results"
    estable2 = [corrida(f) for f in sorted(glob.glob(str(r / "baseline" / "round-0" / "k6-summary-*.json")))]
    estable4 = [corrida(f) for f in sorted(glob.glob(str(r / "baseline" / "round-1" / "k6-summary-*.json")))]
    ronda0 = [corrida(str(r / "stop-tasks" / "round-0" / "k6-summary.json"))]
    carpetas1 = sorted(glob.glob(str(r / "stop-tasks" / "round-1" / "run-*")))
    ronda1 = [corrida(f"{c}/k6-summary.json") for c in carpetas1]
    commit = subprocess.run(["git", "-C", repo, "rev-parse", "--short", "HEAD"], capture_output=True, text=True).stdout.strip()
    resumen = {
        "fuente": "jfpa11100/ecommerce-api, chaos/results/", "commit": commit,
        "estable_2_tareas": {"corridas": estable2, "mediana": mediana(estable2)},
        "estable_4_tareas": {"corridas": estable4, "mediana": mediana(estable4)},
        "ronda_0": {"corridas": ronda0, "mediana": mediana(ronda0), "mttr_s": [mttr(str(r / "stop-tasks" / "round-0" / "journal-stop-tasks.json"))]},
        "ronda_1": {"corridas": ronda1, "mediana": mediana(ronda1), "mttr_s": [mttr(f"{c}/journal-stop-tasks.json") for c in carpetas1]},
        "hipotesis": {"original_p95_ms": 350, "revisada_p95_ms": 254, "revisada_mttr_s": 90, "degradacion_max_pct": 20},
    }
    DESTINO.write_text(json.dumps(resumen, indent=1, ensure_ascii=False), encoding="utf-8")
    print(f"Resumen escrito en {DESTINO} (commit {commit}): {len(estable2)} + {len(estable4)} corridas de estado estable, "
          f"{len(ronda0)} de ronda 0 y {len(ronda1)} de ronda 1")


if __name__ == "__main__":
    main(sys.argv[1])
