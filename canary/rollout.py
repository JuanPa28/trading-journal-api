#!/usr/bin/env python3
"""Orquestador del Canary Deployment sobre Google Cloud Run.

Despliega la versión estable y la canary como dos revisiones del mismo servicio,
reparte el tráfico por etapas (0 % -> 10 % -> 50 % -> 100 %), mide cada etapa con
carga concurrente, decide con la puerta de calidad (gate.py + slo.json) y, si se
viola un umbral, revierte de inmediato a 100 % stable.

Subcomandos:
  deploy    despliega una revisión (stable o canary) con su imagen inmutable
  traffic   fija el reparto de tráfico entre stable y canary
  rollback  devuelve el 100 % del tráfico a stable
  run       el flujo completo y automático (el que usa el pipeline)

Usa el CLI de gcloud ya autenticado. Con --dry-run solo imprime los comandos.
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
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

import gate
import load

HERE = Path(__file__).resolve().parent
PROJECT = os.environ.get("GCP_PROJECT_ID", "trading-journal-canary")
REGION = os.environ.get("GCP_REGION", "us-central1")
SERVICE = os.environ.get("CANARY_SERVICE", "api")
RUNTIME_SA = f"api-runtime@{PROJECT}.iam.gserviceaccount.com"

DRY_RUN = False


class RolloutError(RuntimeError):
    pass


# --------------------------------------------------------------------------- gcloud

def gcloud(*args: str, capture: bool = True) -> str:
    """Ejecuta gcloud; con DRY_RUN solo muestra el comando."""
    command = [shutil.which("gcloud") or "gcloud", *args, "--project", PROJECT]
    if DRY_RUN:
        print("  [dry-run] gcloud " + " ".join(args))
        return ""
    result = subprocess.run(command, capture_output=capture, text=True)
    if result.returncode != 0:
        raise RolloutError(f"gcloud {' '.join(args[:3])} falló: {(result.stderr or '').strip()[-400:]}")
    return (result.stdout or "").strip()


def service_json() -> dict | None:
    if DRY_RUN:
        return None
    result = subprocess.run([shutil.which("gcloud") or "gcloud", "run", "services", "describe", SERVICE,
                             "--region", REGION, "--project", PROJECT, "--format", "json"],
                            capture_output=True, text=True)
    return json.loads(result.stdout) if result.returncode == 0 else None


def tag_url(tag: str) -> str:
    """URL propia de la revisión con esa etiqueta: sirve para probar sin tráfico público."""
    service = service_json() or {}
    for entry in service.get("status", {}).get("traffic", []):
        if entry.get("tag") == tag and entry.get("url"):
            return entry["url"].rstrip("/")
    raise RolloutError(f"No existe una revisión con la etiqueta '{tag}'")


def service_url() -> str:
    service = service_json() or {}
    url = service.get("status", {}).get("url")
    if not url:
        raise RolloutError("El servicio todavía no existe")
    return url.rstrip("/")


# ------------------------------------------------------------------------ despliegue

def revision_suffix(track: str, version: str, extra: str = "") -> str:
    suffix = f"{track}-{version.replace('.', '')}"
    return f"{suffix}-{extra}" if extra else suffix


def runtime_env(track: str, version: str, fault_error_rate: float = 0.0, fault_latency_ms: int = 0) -> dict:
    """Variables de entorno de una revisión. Los secretos van aparte (Secret Manager)."""
    required = {k: os.environ.get(k) for k in ("OCI_TENANCY_ID", "OCI_USER_ID", "OCI_FINGERPRINT")}
    missing = [k for k, v in required.items() if not v]
    if missing and not DRY_RUN:
        raise RolloutError(f"Faltan variables de entorno: {', '.join(missing)}")
    env = {
        "SPRING_PROFILES_ACTIVE": "prod",
        "SPRING_JPA_HIBERNATE_DDL_AUTO": "update",
        # La base Always Free admite pocas sesiones: pool chico por instancia y pocas instancias.
        "SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE": "4",
        "DB_USERNAME": "ADMIN",
        "APP_VERSION": version,
        "APP_TRACK": track,
        "JAVA_TOOL_OPTIONS": "-XX:MaxRAMPercentage=70 -XX:+UseSerialGC",
        "OCI_REGION": os.environ.get("OCI_REGION", "sa-bogota-1"),
        "OCI_TENANCY_ID": required["OCI_TENANCY_ID"] or "dry-run",
        "OCI_USER_ID": required["OCI_USER_ID"] or "dry-run",
        "OCI_FINGERPRINT": required["OCI_FINGERPRINT"] or "dry-run",
    }
    if fault_error_rate > 0:
        env["FAULT_ERROR_RATE"] = str(fault_error_rate)
    if fault_latency_ms > 0:
        env["FAULT_LATENCY_MS"] = str(fault_latency_ms)
    return env


def deploy(track: str, image: str, version: str, extra: str = "", fault_error_rate: float = 0.0,
           fault_latency_ms: int = 0) -> str:
    """Despliega una revisión. El stable inicial recibe el 100 %; el canary nace en 0 %."""
    suffix = revision_suffix(track, version, extra)
    exists = service_json() is not None
    env = runtime_env(track, version, fault_error_rate, fault_latency_ms)

    with tempfile.NamedTemporaryFile("w", suffix=".yaml", delete=False, encoding="utf-8") as env_file:
        env_file.write("\n".join(f"{k}: {json.dumps(v)}" for k, v in env.items()) + "\n")
    try:
        args = ["run", "deploy", SERVICE, "--region", REGION, "--image", image,
                "--revision-suffix", suffix, "--tag", track,
                "--service-account", RUNTIME_SA, "--allow-unauthenticated",
                "--port", "8080", "--memory", "1Gi", "--cpu", "1", "--cpu-boost",
                "--min-instances", "0", "--max-instances", "2", "--concurrency", "40", "--timeout", "60",
                "--env-vars-file", env_file.name,
                "--set-secrets", "DB_PASSWORD=db-password:latest,DB_URL=db-url:latest,OCI_PRIVATE_KEY=oci-private-key:latest",
                "--quiet"]
        if exists:
            args.append("--no-traffic")
        print(f"Desplegando {SERVICE}-{suffix} ({track} {version}) con {image}")
        gcloud(*args)
    finally:
        os.unlink(env_file.name)
    return f"{SERVICE}-{suffix}"


def set_traffic(canary_percent: int) -> None:
    """Reparte el tráfico entre las etiquetas stable y canary."""
    if canary_percent <= 0:
        allocation = "stable=100"
    elif canary_percent >= 100:
        allocation = "canary=100"
    else:
        allocation = f"stable={100 - canary_percent},canary={canary_percent}"
    print(f"Tráfico -> {allocation}")
    gcloud("run", "services", "update-traffic", SERVICE, "--region", REGION, "--to-tags", allocation, "--quiet")


def rollback() -> None:
    print("ROLLBACK: 100 % del tráfico a la versión estable, 0 % al canary")
    set_traffic(0)


# --------------------------------------------------------------------------- medición

def saturation(revision: str) -> dict | None:
    """CPU y memoria (percentil 99) de una revisión según Cloud Monitoring; None si no hay datos."""
    if DRY_RUN:
        return None
    try:
        token = subprocess.run([shutil.which("gcloud") or "gcloud", "auth", "print-access-token"],
                               capture_output=True, text=True, check=True).stdout.strip()
        now = time.time()
        result = {}
        for key, metric in (("cpu", "container/cpu/utilizations"), ("memory", "container/memory/utilizations")):
            query = urllib.parse.urlencode({
                "filter": f'metric.type="run.googleapis.com/{metric}" AND resource.labels.revision_name="{revision}"',
                "interval.startTime": datetime.fromtimestamp(now - 300, timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
                "interval.endTime": datetime.fromtimestamp(now, timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
                "aggregation.alignmentPeriod": "300s",
                "aggregation.perSeriesAligner": "ALIGN_PERCENTILE_99",
            })
            request = urllib.request.Request(
                f"https://monitoring.googleapis.com/v3/projects/{PROJECT}/timeSeries?{query}",
                headers={"Authorization": f"Bearer {token}"})
            with urllib.request.urlopen(request, timeout=20) as response:
                series = json.load(response).get("timeSeries", [])
            if series and series[0].get("points"):
                result[key] = series[0]["points"][0]["value"].get("doubleValue")
        return result or None
    except Exception:
        return None


def print_round(stage_name: str, stats: dict, verdict: dict, warning: str | None) -> None:
    print(f"\n=== {stage_name} ===")
    print(f"{'track':<8} {'peticiones':>10} {'errores':>8} {'p50 ms':>8} {'p95 ms':>8} {'p99 ms':>8} {'reparto':>8}")
    for track in ("stable", "canary"):
        s = stats.get(track)
        if s:
            print(f"{track:<8} {s['requests']:>10} {s['error_rate']:>7.1%} {s['p50']:>8.0f} {s['p95']:>8.0f} "
                  f"{s['p99']:>8.0f} {s['share']:>7.0%}")
    for check in verdict["checks"]:
        print(f"  {'OK ' if check['ok'] else 'MAL'} {check['name']}: {check['value']} (límite {check['limit']})")
    for reason in verdict["reasons"]:
        print(f"  -> {reason}")
    if warning:
        print(f"  aviso: {warning}")
    print(f"  DECISIÓN: {verdict['decision']}")


def run_flow(args) -> int:
    slo = json.loads((HERE / "slo.json").read_text(encoding="utf-8"))
    report = {"started": datetime.now(timezone.utc).isoformat(), "stable": args.stable_version,
              "canary": args.canary_version, "fault_error_rate": args.fault_error_rate, "stages": []}

    extra = "faulty" if args.fault_error_rate or args.fault_latency_ms else ""
    if args.reuse_revisions:
        print("Se reutilizan las revisiones ya desplegadas (stable y canary).")
        canary_rev = f"{SERVICE}-{revision_suffix('canary', args.canary_version, extra)}"
    else:
        deploy("stable", args.stable_image, args.stable_version)
        canary_rev = deploy("canary", args.canary_image, args.canary_version, extra,
                            args.fault_error_rate, args.fault_latency_ms)
    set_traffic(0)

    if DRY_RUN:
        print("[dry-run] el resto del flujo (smoke, etapas y decisiones) no se simula")
        return 0

    # Etapa 0: 0 % de tráfico público, prueba de humo por la URL de la revisión.
    print("\n=== etapa-0 (0 % público, prueba de humo por la URL de la revisión) ===")
    smoke = slo["smoke"]
    samples = load.run_load(tag_url("canary"), duration=120, concurrency=8,
                            until=lambda s: sum(1 for x in s if x["track"] == "canary") >= smoke["requests"])
    stats = gate.summarize(samples)
    canary_stats = stats.get("canary")
    if not canary_stats or canary_stats["error_rate"] > smoke["max_error_rate"]:
        print("Prueba de humo FALLIDA: el canary no se expone al público.")
        report["result"] = "ABORT en etapa-0"
        save_report(args, report)
        return 2
    print(f"Prueba de humo correcta: {canary_stats['requests']} peticiones, 0 errores, versión canary.")
    report["stages"].append({"name": "etapa-0", "decision": gate.PROMOTE, "stats": stats})

    baseline = None
    for stage in slo["stages"]:
        set_traffic(stage["canary_percent"])
        bake = args.bake_seconds if args.bake_seconds else stage["bake_seconds_real"]
        rounds = 0
        while True:
            rounds += 1
            print(f"\n{stage['name']}: {stage['canary_percent']} % al canary, medición de {bake}s (ronda {rounds})")
            samples = load.run_load(service_url(), duration=bake, concurrency=args.concurrency,
                                    until=None)
            stats = gate.summarize(samples)
            if stats.get("stable"):
                baseline = stats["stable"]
            verdict = gate.evaluate(stage, stats, slo, baseline=baseline, saturation=saturation(canary_rev))
            warning = gate.share_warning(stage, stats, slo["expected_share_tolerance"])
            print_round(stage["name"], stats, verdict, warning)
            report["stages"].append({"name": stage["name"], "round": rounds, "decision": verdict["decision"],
                                     "reasons": verdict["reasons"], "stats": stats})
            if verdict["decision"] == gate.HOLD and rounds <= slo["sample_size"]["max_extra_bake_rounds"]:
                print("  Se extiende la medición: todavía no hay evidencia suficiente.")
                continue
            break
        if verdict["decision"] != gate.PROMOTE:
            reason = "ABORT" if verdict["decision"] == gate.ABORT else "sin evidencia suficiente"
            print(f"\nNo se promueve ({reason}). Se revierte.")
            rollback()
            report["result"] = f"ROLLBACK en {stage['name']}: {'; '.join(verdict['reasons'])}"
            save_report(args, report)
            return 2

    print("\nCanary promovido al 100 % del tráfico.")
    report["result"] = "PROMOVIDO al 100 %"
    save_report(args, report)
    return 0


def save_report(args, report: dict) -> None:
    report["finished"] = datetime.now(timezone.utc).isoformat()
    if args.report:
        Path(args.report).parent.mkdir(parents=True, exist_ok=True)
        Path(args.report).write_text(json.dumps(report, indent=2), encoding="utf-8")
        print(f"Informe guardado en {args.report}")


# ------------------------------------------------------------------------------ CLI

def main(argv: list[str] | None = None) -> int:
    global DRY_RUN
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--dry-run", action="store_true", help="solo imprime los comandos de gcloud")
    sub = parser.add_subparsers(dest="command", required=True)

    p = sub.add_parser("deploy")
    p.add_argument("--track", choices=["stable", "canary"], required=True)
    p.add_argument("--image", required=True, help="imagen inmutable, preferible por digest @sha256")
    p.add_argument("--version", required=True)
    p.add_argument("--fault-error-rate", type=float, default=0.0)
    p.add_argument("--fault-latency-ms", type=int, default=0)

    p = sub.add_parser("traffic")
    p.add_argument("--canary-percent", type=int, required=True)

    sub.add_parser("rollback")

    p = sub.add_parser("run")
    p.add_argument("--stable-image", required=True)
    p.add_argument("--stable-version", required=True)
    p.add_argument("--canary-image", required=True)
    p.add_argument("--canary-version", required=True)
    p.add_argument("--bake-seconds", type=int, default=0, help="medición por etapa (0 usa los tiempos reales de slo.json)")
    p.add_argument("--concurrency", type=int, default=12)
    p.add_argument("--fault-error-rate", type=float, default=0.0, help="inyecta errores 500 en el canary")
    p.add_argument("--fault-latency-ms", type=int, default=0, help="inyecta latencia en el canary")
    p.add_argument("--reuse-revisions", action="store_true", help="no despliega: usa las revisiones que ya existen")
    p.add_argument("--report", help="archivo JSON donde guardar el informe")

    args = parser.parse_args(argv)
    DRY_RUN = args.dry_run
    try:
        if args.command == "deploy":
            deploy(args.track, args.image, args.version, "faulty" if args.fault_error_rate else "",
                   args.fault_error_rate, args.fault_latency_ms)
        elif args.command == "traffic":
            set_traffic(args.canary_percent)
        elif args.command == "rollback":
            rollback()
        else:
            return run_flow(args)
    except RolloutError as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
