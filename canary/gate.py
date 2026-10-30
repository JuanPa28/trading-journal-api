"""Puerta de calidad (quality gate) del Canary Deployment.

Compara el canary contra la línea base estable con los umbrales de slo.json y
devuelve una decisión: PROMOTE, HOLD o ABORT. Son funciones puras (sin red ni
gcloud) para poder probarlas con pruebas unitarias.
"""
from __future__ import annotations

import math
from collections import defaultdict

PROMOTE = "PROMOTE"
HOLD = "HOLD"
ABORT = "ABORT"


def percentile(values: list[float], p: float) -> float:
    """Percentil p (0-100) por el método del rango más cercano."""
    if not values:
        return 0.0
    ordered = sorted(values)
    rank = max(1, math.ceil(p / 100 * len(ordered)))
    return ordered[rank - 1]


def summarize(samples: list[dict]) -> dict[str, dict]:
    """Agrupa las muestras (track, status, ms) y calcula las señales doradas por track.

    Un error es una respuesta 5xx o una petición que ni siquiera obtuvo respuesta
    (status 0). Los 4xx no cuentan: son errores del cliente, no de la versión.
    """
    by_track: dict[str, list[dict]] = defaultdict(list)
    for sample in samples:
        by_track[sample["track"]].append(sample)

    total = len(samples) or 1
    stats = {}
    for track, items in by_track.items():
        latencies = [s["ms"] for s in items]
        errors = sum(1 for s in items if s["status"] == 0 or s["status"] >= 500)
        stats[track] = {
            "requests": len(items),
            "errors": errors,
            "error_rate": errors / len(items),
            "share": len(items) / total,
            "p50": percentile(latencies, 50),
            "p95": percentile(latencies, 95),
            "p99": percentile(latencies, 99),
        }
    return stats


def _check(name: str, value: float, limit: float, ok: bool) -> dict:
    return {"name": name, "value": round(value, 4), "limit": round(limit, 4), "ok": ok}


def evaluate(stage: dict, stats: dict, slo: dict, baseline: dict | None = None,
             saturation: dict | None = None) -> dict:
    """Decide qué hacer con el canary en la etapa dada.

    stage: una entrada de slo["stages"].
    stats: salida de summarize() de esta ronda.
    baseline: estadísticas estables de una ronda previa (cuando ya no hay tráfico
        estable, por ejemplo al 100 %, se compara contra lo medido antes).
    saturation: {"cpu": 0-1, "memory": 0-1} del canary, si hay datos.
    """
    canary = stats.get("canary")
    stable = stats.get("stable") or baseline
    sample = slo["sample_size"]
    abort_cfg = slo["abort"]
    hold_cfg = slo["hold"]
    checks: list[dict] = []
    reasons: list[str] = []

    if canary is None or canary["requests"] < stage["min_canary_requests"]:
        seen = canary["requests"] if canary else 0
        return {"decision": HOLD, "checks": checks,
                "reasons": [f"Muestra insuficiente del canary: {seen} de {stage['min_canary_requests']} peticiones"]}
    if stage["canary_percent"] < 100 and (stats.get("stable") is None or
                                          stats["stable"]["requests"] < sample["min_stable_requests"]):
        seen = stats["stable"]["requests"] if stats.get("stable") else 0
        return {"decision": HOLD, "checks": checks,
                "reasons": [f"Muestra insuficiente de la línea base: {seen} de {sample['min_stable_requests']} peticiones"]}

    # --- Umbrales duros: provocan el rollback ---
    ok = canary["error_rate"] <= abort_cfg["max_canary_error_rate"]
    checks.append(_check("error_rate_canary", canary["error_rate"], abort_cfg["max_canary_error_rate"], ok))
    if not ok:
        reasons.append(f"Tasa de errores del canary {canary['error_rate']:.1%} supera {abort_cfg['max_canary_error_rate']:.1%}")

    if stable:
        delta = canary["error_rate"] - stable["error_rate"]
        ok = delta <= abort_cfg["max_error_rate_over_stable"]
        checks.append(_check("error_rate_vs_stable", delta, abort_cfg["max_error_rate_over_stable"], ok))
        if not ok:
            reasons.append(f"El canary tiene {delta:.1%} más errores que la versión estable")

        limit = max(stable["p95"] * abort_cfg["max_p95_ratio_over_stable"],
                    stable["p95"] + abort_cfg["min_p95_margin_ms"])
        ok = canary["p95"] <= limit
        checks.append(_check("latency_p95_ms", canary["p95"], limit, ok))
        if not ok:
            reasons.append(f"Latencia p95 del canary {canary['p95']:.0f} ms supera el límite de {limit:.0f} ms")

    if reasons:
        return {"decision": ABORT, "checks": checks, "reasons": reasons}

    # --- Señales blandas: pausan sin revertir ---
    if stable:
        limit = max(stable["p99"] * hold_cfg["max_p99_ratio_over_stable"],
                    stable["p99"] + hold_cfg["min_p99_margin_ms"])
        ok = canary["p99"] <= limit
        checks.append(_check("latency_p99_ms", canary["p99"], limit, ok))
        if not ok:
            reasons.append(f"Latencia p99 del canary {canary['p99']:.0f} ms supera {limit:.0f} ms")

    if saturation:
        for key, cfg_key in (("cpu", "max_cpu_utilization"), ("memory", "max_memory_utilization")):
            if saturation.get(key) is not None:
                ok = saturation[key] <= hold_cfg[cfg_key]
                checks.append(_check(f"{key}_utilization", saturation[key], hold_cfg[cfg_key], ok))
                if not ok:
                    reasons.append(f"Saturación de {key} del canary {saturation[key]:.0%} supera {hold_cfg[cfg_key]:.0%}")

    if reasons:
        return {"decision": HOLD, "checks": checks, "reasons": reasons}
    return {"decision": PROMOTE, "checks": checks, "reasons": ["Todos los umbrales se cumplen"]}


def pick_baseline(current: dict | None, stable_stats: dict | None) -> dict | None:
    """Elige la línea base de la versión estable para comparar cuando ya no hay tráfico estable.

    Se conserva la medición con MÁS peticiones: es la de mayor carga y muestra más
    grande. Así, al 100 % el canary no se compara contra una etapa en la que la
    estable atendía menos tráfico (y por eso respondía más rápido), lo que daría un
    falso aborto: la latencia sube con la carga aunque no haya ninguna regresión.
    """
    if stable_stats is None:
        return current
    if current is None or stable_stats["requests"] > current["requests"]:
        return stable_stats
    return current


def share_warning(stage: dict, stats: dict, tolerance: float) -> str | None:
    """Avisa si el reparto observado se aleja del porcentaje configurado."""
    canary = stats.get("canary")
    observed = canary["share"] if canary else 0.0
    expected = stage["canary_percent"] / 100
    if abs(observed - expected) > tolerance * max(expected, 0.1):
        return f"Reparto observado {observed:.0%} distinto del esperado {expected:.0%}"
    return None
