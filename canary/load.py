"""Generador de carga concurrente para comparar canary y stable.

Manda una mezcla realista de peticiones al API (lecturas y creaciones de
trades) desde varios hilos y registra, por cada respuesta, qué track la atendió
(cabecera X-App-Track), el código HTTP y la latencia. Con eso se mide el reparto
real del tráfico y las señales doradas de cada versión bajo la misma carga, que
es la comparación justa que pide una evaluación canary.

Solo usa la biblioteca estándar de Python.
"""
from __future__ import annotations

import argparse
import json
import random
import threading
import time
import urllib.error
import urllib.request
import uuid
from concurrent.futures import ThreadPoolExecutor

import gate

TRADE_BODY = {
    "strategy": {"htfPdArray": True, "ifvg": True, "cisd": False, "followedRules": True,
                 "continuation": False, "reversal": False, "correctRisk": True},
    "externalId": "load-test",
    "contract": "MNQU26",
    "size": 2,
    "direction": "SHORT",
    "entryTime": "2026-08-10T08:40:01",
    "exitTime": "2026-08-10T08:41:32",
    "entryPrice": 29770.25,
    "exitPrice": 29726.75,
    "pnl": 174.00,
    "commissions": 1.00,
    "fees": 1.44,
    "notes": "carga canary",
}


def call(method: str, url: str, body: dict | None = None, headers: dict | None = None, timeout: int = 20):
    """Hace una petición y devuelve (status, track, version, ms, cuerpo)."""
    data = json.dumps(body).encode() if body is not None else None
    request = urllib.request.Request(url, data=data, method=method)
    request.add_header("Content-Type", "application/json")
    for key, value in (headers or {}).items():
        request.add_header(key, value)
    start = time.perf_counter()
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            payload = response.read()
            status, meta = response.status, response.headers
    except urllib.error.HTTPError as error:
        payload, status, meta = error.read(), error.code, error.headers
    except Exception:  # sin respuesta: tiempo agotado, conexión rechazada...
        return 0, "unknown", "unknown", (time.perf_counter() - start) * 1000, b""
    ms = (time.perf_counter() - start) * 1000
    return status, meta.get("X-App-Track", "unknown"), meta.get("X-App-Version", "unknown"), ms, payload


def seed_trader(base_url: str, headers: dict | None = None) -> int:
    """Crea un trader dedicado a la carga y devuelve su id."""
    unique = uuid.uuid4().hex[:8]
    body = {"fullName": f"Carga {unique}", "username": f"carga_{unique}",
            "email": f"carga_{unique}@canary.test", "availableFunds": 10000}
    status, _, _, _, payload = call("POST", f"{base_url}/api/traders", body, headers)
    if status != 201:
        raise RuntimeError(f"No se pudo crear el trader de carga: HTTP {status}")
    return json.loads(payload)["id"]


def run_load(base_url: str, duration: float, concurrency: int = 16, headers: dict | None = None,
             trader_id: int | None = None, until=None) -> list[dict]:
    """Genera carga hasta que pase `duration` segundos o `until(muestras)` sea verdadero."""
    trader_id = trader_id or seed_trader(base_url, headers)
    trade_ids: list[int] = []
    samples: list[dict] = []
    lock = threading.Lock()
    stop = threading.Event()
    deadline = time.monotonic() + duration

    def one_request():
        roll = random.random()
        if roll < 0.15 or not trade_ids:
            body = dict(TRADE_BODY, traderId=trader_id)
            result = call("POST", f"{base_url}/api/trades", body, headers)
            if result[0] == 201:
                with lock:
                    trade_ids.append(json.loads(result[4])["id"])
        elif roll < 0.45:
            result = call("GET", f"{base_url}/api/trades/{random.choice(trade_ids)}", None, headers)
        elif roll < 0.75:
            result = call("GET", f"{base_url}/api/trades", None, headers)
        else:
            result = call("GET", f"{base_url}/api/traders/{trader_id}", None, headers)
        status, track, version, ms, _ = result
        with lock:
            samples.append({"track": track, "version": version, "status": status, "ms": ms})

    def worker():
        while not stop.is_set() and time.monotonic() < deadline:
            one_request()
            if until is not None:
                with lock:
                    if until(samples):
                        stop.set()

    with ThreadPoolExecutor(max_workers=concurrency) as pool:
        for _ in range(concurrency):
            pool.submit(worker)
    return samples


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--url", required=True, help="URL base del servicio, sin barra final")
    parser.add_argument("--duration", type=float, default=30, help="segundos de carga")
    parser.add_argument("--concurrency", type=int, default=16)
    parser.add_argument("--header", action="append", default=[], help="Cabecera extra 'Nombre: valor'")
    parser.add_argument("--json", help="archivo donde guardar el resumen")
    args = parser.parse_args()

    headers = dict(h.split(": ", 1) for h in args.header)
    samples = run_load(args.url.rstrip("/"), args.duration, args.concurrency, headers)
    stats = gate.summarize(samples)
    print(json.dumps(stats, indent=2))
    if args.json:
        with open(args.json, "w", encoding="utf-8") as out:
            json.dump(stats, out, indent=2)


if __name__ == "__main__":
    main()
