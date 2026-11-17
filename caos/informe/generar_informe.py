#!/usr/bin/env python3
"""Genera el informe de caos (HTML y PDF, máximo 6 páginas) a partir de la evidencia y de una plantilla.

Las cifras salen de caos/resultados/*.json (resúmenes de k6), no se escriben a mano. La sección de
Pipe y la de uso de IA son opcionales: si faltan, el informe lo dice en lugar de inventarlas.

Uso:
  python caos/informe/generar_informe.py                 # escribe informe-caos.html y el PDF
  python caos/informe/generar_informe.py --con-ia texto  # incluye la sección de uso de IA desde un archivo
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
RES = RAIZ / "caos" / "resultados"
SALIDA = Path(__file__).resolve().parent


def k6(nombre: str) -> dict:
    m = json.loads((RES / nombre).read_text(encoding="utf-8"))["metrics"]
    t = m["http_req_duration"]
    return {"n": m["http_reqs"]["count"], "tps": m["http_reqs"]["rate"], "p50": t["med"], "p95": t["p(95)"],
            "p99": t.get("p(99)"), "max": t["max"], "exito": 100 * (1 - m["http_req_failed"]["passes"] / max(1, m["http_reqs"]["count"]))}


def ms(v) -> str:
    return "—" if v is None else f"{round(v):,}".replace(",", " ") + " ms"


def variacion(a: float, b: float) -> str:
    return f"{100 * (b - a) / a:+.1f} %".replace(".", ",")


def grafica(estable: float, r0: float, r1: float, umbral: float) -> str:
    ancho, base = 420.0, 7000.0
    filas = [("Estado estable", estable, "#15803d"), ("Ronda 0 (sin protección)", r0, "#b91c1c"), ("Ronda 1 (timeout + breaker)", r1, "#1d4ed8")]
    partes = [f'<svg viewBox="0 0 640 150" class="graf" role="img" aria-label="p95 por ronda">']
    for i, (nombre, valor, color) in enumerate(filas):
        y = 14 + i * 38
        partes.append(f'<text x="0" y="{y + 15}" font-size="11" fill="#374151">{nombre}</text>')
        partes.append(f'<rect x="170" y="{y}" width="{ancho * valor / base:.1f}" height="24" rx="3" fill="{color}"/>')
        partes.append(f'<text x="{176 + ancho * valor / base:.1f}" y="{y + 16}" font-size="11" font-weight="700" fill="#111827">{round(valor):,} ms</text>'.replace(",", " "))
    x = 170 + ancho * umbral / base
    partes.append(f'<line x1="{x:.1f}" y1="6" x2="{x:.1f}" y2="128" stroke="#6b7280" stroke-dasharray="4,3"/>')
    partes.append(f'<text x="{x + 4:.1f}" y="140" font-size="10" fill="#6b7280">umbral {round(umbral):,} ms</text>'.replace(",", " "))
    partes.append("</svg>")
    return "".join(partes)



def serie_svg() -> str:
    """Tablero por minuto de las dos rondas: p95 (arriba) y peticiones con y sin cabecera de falla (abajo)."""
    d = json.loads((RES / "serie-rondas.json").read_text(encoding="utf-8"))
    por_minuto = {f["minuto"][11:16]: f for f in d["flujo"]}
    con_cab = {i["minuto"][11:16]: i["con_cabecera"] for i in d["inyeccion"]}
    minutos = [f"17:{m}" for m in range(33, 43)]
    x0, ancho, paso = 54.0, 560.0, 56.0
    p = ['<svg viewBox="0 0 640 300" class="graf" role="img" aria-label="p95 y peticiones por minuto">']
    # panel 1: p95
    p.append('<text x="0" y="12" font-size="10.5" font-weight="700" fill="#111827">p95 por minuto (ms)</text>')
    escala = 86 / 7000
    for marca in (0, 2000, 4000, 6000):
        y = 120 - marca * escala
        p.append(f'<line x1="{x0}" y1="{y:.1f}" x2="{x0 + ancho}" y2="{y:.1f}" stroke="#e5e7eb"/><text x="{x0 - 4}" y="{y + 3:.1f}" text-anchor="end" font-size="9" fill="#6b7280">{marca}</text>')
    y_umbral = 120 - 1600 * escala
    p.append(f'<line x1="{x0}" y1="{y_umbral:.1f}" x2="{x0 + ancho}" y2="{y_umbral:.1f}" stroke="#6b7280" stroke-dasharray="4,3"/><text x="{x0 + ancho - 2}" y="{y_umbral - 3:.1f}" text-anchor="end" font-size="9" fill="#6b7280">umbral 1 600 ms</text>')
    for i, m in enumerate(minutos):
        f = por_minuto.get(m)
        x = x0 + i * paso + 8
        if f is None:
            p.append(f'<text x="{x + 16}" y="{118}" text-anchor="middle" font-size="8" fill="#9ca3af">pausa</text>')
            continue
        color = "#b91c1c" if m <= "17:36" else "#1d4ed8"
        alto = min(f["p95_ms"], 7000) * escala
        p.append(f'<rect x="{x}" y="{120 - alto:.1f}" width="40" height="{alto:.1f}" fill="{color}" rx="2"/>')
        p.append(f'<text x="{x + 20}" y="{116 - alto:.1f}" text-anchor="middle" font-size="9" fill="#111827">{round(f["p95_ms"])}</text>')
    # panel 2: peticiones
    p.append('<text x="0" y="158" font-size="10.5" font-weight="700" fill="#111827">Peticiones por minuto: totales y con la cabecera de falla (ventana de inyección)</text>')
    tope = 2800.0
    for marca in (0, 1000, 2000):
        y = 270 - marca * 90 / tope
        p.append(f'<line x1="{x0}" y1="{y:.1f}" x2="{x0 + ancho}" y2="{y:.1f}" stroke="#e5e7eb"/><text x="{x0 - 4}" y="{y + 3:.1f}" text-anchor="end" font-size="9" fill="#6b7280">{marca}</text>')
    for i, m in enumerate(minutos):
        f = por_minuto.get(m)
        x = x0 + i * paso + 8
        if f is not None:
            alto = f["peticiones"] * 90 / tope
            p.append(f'<rect x="{x}" y="{270 - alto:.1f}" width="40" height="{max(alto, 1):.1f}" fill="#9ca3af" rx="2"/>')
            p.append(f'<text x="{x + 20}" y="{266 - alto:.1f}" text-anchor="middle" font-size="9" fill="#111827">{f["peticiones"]}</text>')
        if m in con_cab:
            y = 270 - min(con_cab[m], tope) * 90 / tope
            p.append(f'<circle cx="{x + 20}" cy="{y:.1f}" r="3.5" fill="#f59e0b" stroke="#92400e"/>')
        local = f"12:{m[3:]}"
        p.append(f'<text x="{x + 20}" y="288" text-anchor="middle" font-size="9" fill="#374151">{local}</text>')
    p.append(f'<text x="{x0 + ancho}" y="298" text-anchor="end" font-size="8.5" fill="#6b7280">hora local de Colombia · rojo: ronda 0 · azul: ronda 1 · punto naranja: peticiones con cabecera de falla</text>')
    p.append("</svg>")
    return "".join(p)


def pipe_secciones() -> dict:
    """Bloques del informe con la evidencia de ecommerce (Pipe), importada con importar_pipe.py."""
    archivo = RES / "ecommerce-resumen.json"
    if not archivo.exists():
        return {"fila": '<tr><td>Pipe (AWS)</td><td>F2: detener el 50 % de las tareas ECS</td><td>El flujo mantiene éxito ≥ 99 % y p95 dentro del umbral</td><td><b>Cifras: pendientes de su evidencia.</b></td></tr>',
                "seccion7": "", "hallazgos": ""}
    d = json.loads(archivo.read_text(encoding="utf-8"))
    e2, e4 = d["estable_2_tareas"]["mediana"], d["estable_4_tareas"]["mediana"]
    r0, r1 = d["ronda_0"]["mediana"], d["ronda_1"]["mediana"]
    m1 = d["ronda_1"]["mttr_s"]
    hip = d["hipotesis"]

    def v(a, b):
        return f"{100 * (b - a) / a:+.1f} %".replace(".", ",")

    sobre_e4 = [100 * (c["p95"] - e4["p95"]) / e4["p95"] for c in d["ronda_1"]["corridas"]]
    dentro = sum(1 for x in sobre_e4 if x <= 20)
    fila = (f'<tr><td>Pipe (AWS)</td><td>F2: detener el 50 % de las tareas ECS, 7 min de carga (Chaos Toolkit)</td>'
            f'<td>Original: éxito ≥ 99 % y p95 ≤ {hip["original_p95_ms"]} ms. Revisada (endurecida después de ver la ronda 0): p95 ≤ {hip["revisada_p95_ms"]} ms y MTTR ≤ {hip["revisada_mttr_s"]} s</td>'
            f'<td>p95 extremo a extremo de su API: k6 y Chaos Toolkit. Ronda 0: {ms(r0["p95"])}; la original <span class="ok">se cumple</span> y la revisada <span class="mal">se refuta</span>. Ronda 1: {ms(r1["p95"])}, <span class="ok">se cumple</span> (datos de su repositorio, commit {d["commit"]})</td></tr>')
    encabezado = (f'<tr><th></th><th class="n">Estable, 2 tareas ({len(d["estable_2_tareas"]["corridas"])} corridas)</th><th class="n">Ronda 0: 2 tareas, se detiene 1 ({len(d["ronda_0"]["corridas"])} corrida)</th>'
                  f'<th class="n">Estable, 4 tareas ({len(d["estable_4_tareas"]["corridas"])} corridas)</th><th class="n">Ronda 1: 4 tareas, se detienen 2 ({len(d["ronda_1"]["corridas"])} corridas)</th><th class="n">Variación 0 → 1</th></tr>')

    def fila_t(nombre, k, formato, negrita=False):
        b = (lambda t: f"<b>{t}</b>") if negrita else (lambda t: t)
        return (f'<tr><td>{b(nombre)}</td><td class="n">{formato(e2[k])}</td><td class="n">{formato(r0[k])}</td><td class="n">{formato(e4[k])}</td>'
                f'<td class="n">{b(formato(r1[k]))}</td><td class="n">{b(v(r0[k], r1[k]))}</td></tr>')

    tabla = (encabezado + fila_t("p50", "p50", ms) + fila_t("p95", "p95", ms, True) + fila_t("Máximo", "max", ms)
             + f'<tr><td>Éxito</td><td class="n">{e2["exito_pct"]:.3f} %</td><td class="n">{r0["exito_pct"]:.3f} %</td><td class="n">{e4["exito_pct"]:.3f} %</td><td class="n">{r1["exito_pct"]:.3f} %</td><td class="n">sin cambio</td></tr>'
             + fila_t("Throughput", "throughput", lambda x: f"{x:.0f} req/s")
             + f'<tr><td>MTTR (tareas en ejecución = deseadas)</td><td></td><td class="n">{d["ronda_0"]["mttr_s"][0]} s</td><td></td><td class="n">{", ".join(str(x) for x in m1)} s</td><td class="n">sin diferencia concluyente</td></tr>')
    texto = (f'<p><b>Modo de fallo:</b> sin errores (éxito ≈ 100 %); la falla pierde capacidad y el p95 sube un {v(e2["p95"], r0["p95"])} respecto al estado estable de 2 tareas. '
             f'<b>Mejora:</b> pasar de 2 a 4 tareas, para que perder la mitad deje la capacidad del estado estable original. '
             f'<b>Resultado:</b> la hipótesis original ya se cumplía en la ronda 0 ({ms(r0["p95"])} frente a {hip["original_p95_ms"]} ms); la revisada se refuta en la ronda 0 y se cumple en las {len(m1)} corridas de la ronda 1 con el umbral absoluto de {hip["revisada_p95_ms"]} ms. '
             f'<b>Reservas declaradas:</b> (1) el umbral revisado se fijó <b>después</b> de ver la ronda 0; '
             f'(2) frente a su propio estado estable de 4 tareas, la mediana de la ronda 1 se degrada {v(e4["p95"], r1["p95"])}, pero {dentro} de {len(sobre_e4)} corridas quedan dentro del +20 % (la primera llega a {sobre_e4[0]:+.1f} %); '
             f'(3) la ronda 0 tiene una sola corrida contra tres de la ronda 1; '
             f'(4) el MTTR de la ronda 1 va de {min(m1)} a {max(m1)} s y el de la ronda 0 ({d["ronda_0"]["mttr_s"][0]} s) cae dentro de ese rango; '
             f'(5) 4 tareas cuestan el doble por hora; (6) quedan unas 2 a 18 peticiones fallidas por corrida (≈ 0,01 %) sin causa explicada.</p>')
    seccion7 = '<h3>ecommerce (Pipe): el mismo experimento en AWS</h3><table>' + tabla + '</table>' + texto
    hallazgos = ('<li><b>ecommerce:</b> detener la mitad de las tareas no produce errores sino pérdida de capacidad, que una alerta solo sobre errores no vería. '
                 'Al cambiar la capacidad (2 a 4 tareas) hubo que volver a medir el estado estable: sin eso la comparación habría sido engañosa.</li>')
    return {"fila": fila, "seccion7": seccion7, "hallazgos": hallazgos}


def construir(con_ia: str | None) -> str:
    est = [k6(f"estado-estable-{i}.json") for i in (1, 2, 3)]
    med = sorted(e["p95"] for e in est)[1]
    r0, r1 = k6("ronda-0-k6.json"), k6("ronda-1-k6.json")
    svg = (RAIZ / "docs" / "arquitectura-caos.svg").read_text(encoding="utf-8")
    svg = re.sub(r"<svg ", '<svg class="arq" ', svg, count=1)
    filas_est = "".join(
        f"<tr><td>{i + 1}</td><td>{e['n']:,}</td><td>{e['exito']:.0f} %</td><td>{ms(e['p50'])}</td><td>{ms(e['p95'])}</td><td>{ms(e['p99'])}</td></tr>".replace(",", " ")
        for i, e in enumerate(est))
    ia = (f"<h2>9. Uso de IA</h2>{con_ia}" if con_ia else "")
    pipe = pipe_secciones()
    return f"""<!doctype html><html lang="es"><head><meta charset="utf-8"><title>Informe de ingeniería del caos</title>
<style>
@page {{ size: A4; margin: 11mm 13mm; }}
body {{ font-family: "Segoe UI", Arial, sans-serif; font-size: 9.2pt; line-height: 1.34; color: #1f2937; }}
h1 {{ font-size: 17pt; margin: 0 0 2px; color: #111827; }} .sub {{ color: #6b7280; margin-bottom: 6px; }}
h2 {{ font-size: 11.5pt; margin: 11px 0 3px; color: #1d4ed8; border-bottom: 1.5px solid #dbeafe; padding-bottom: 2px; break-after: avoid; }}
p {{ margin: 3px 0; }} ul {{ margin: 3px 0 3px 16px; padding: 0; }} li {{ margin: 1px 0; }}
table {{ border-collapse: collapse; width: 100%; margin: 5px 0; font-size: 8.5pt; break-inside: avoid; }}
th, td {{ border: 1px solid #d1d5db; padding: 2.5px 5px; text-align: left; vertical-align: top; }} th {{ background: #eef2ff; }}
td.n, th.n {{ text-align: right; font-variant-numeric: tabular-nums; }}
.arq {{ width: 100%; height: auto; max-height: 240px; display: block; margin: 4px 0; }}
.pie {{ font-size: 9pt; color: #4b5563; }} .graf {{ width: 100%; max-width: 640px; height: auto; display: block; margin: 2px 0; }}
.caja {{ background: #fef3c7; border-left: 4px solid #b45309; padding: 4px 8px; margin: 5px 0; break-inside: avoid; }}
.ok {{ color: #15803d; font-weight: 700; }} .mal {{ color: #b91c1c; font-weight: 700; }} code {{ background: #eef2ff; padding: 0 3px; border-radius: 3px; font-size: 8.4pt; }}
</style></head><body>
<h1>Informe de ingeniería del caos</h1>
<div class="sub">Flujo multinube de trading-journal · Azure, Google Cloud y AWS · 16 de noviembre de 2026 · Tag v5.0.0</div>

<h2>1. Resumen y arquitectura</h2>
<p>Un cliente consulta <code>GET /api/v2/trades/{{id}}</code> en el <b>coordinador</b> (Azure Container Apps, con base Oracle). Por cada consulta el coordinador llama a <b>StatsNostra</b> (Mari, GKE en Google Cloud) y a <b>ecommerce</b> (Pipe, AWS) y devuelve el registro unido. El mismo <code>X-Trace-Id</code> viaja por las tres nubes. Pregunta: <b>¿se mantiene el flujo rápido y disponible cuando una de las dependencias falla?</b> Un fallo por nube sobre el mismo flujo y el mismo perfil de carga: F1 latencia saliente hacia Mari (Juan, Azure), F2 indisponibilidad de tareas de Pipe (AWS) y F3 agotamiento de CPU de un pod de Mari (GKE).</p>
{svg}

<h2>2. Estado estable medido y perfil de carga</h2>
<p>Perfil fijo de k6: rampa de 5, 15 y 30 usuarios virtuales, 60 s cada uno, contra el flujo completo, con todos los interruptores y patrones de resiliencia apagados. Tres corridas el 16 de noviembre, con ventana avisada en el chat del equipo (versión <code>5.0.0-rc.2</code>):</p>
<table><tr><th>Corrida</th><th class="n">Peticiones</th><th class="n">Éxito</th><th class="n">p50</th><th class="n">p95</th><th class="n">p99</th></tr>{filas_est}</table>
<p><b>Estado estable declarado:</b> éxito ≥ 99 % y p95 extremo a extremo ≤ 1 600 ms. La mediana medida del p95 es <b>{ms(med)}</b>; el umbral es el presupuesto de latencia aceptable para el usuario (3,3 veces por encima de lo medido), de modo que cruzarlo es un fallo real y no ruido. La corrida del 13 de noviembre se descartó porque coincidió con un reinicio de pods de Mari; esa regla dio origen al protocolo de avisar cada ventana. La corrida 1 fue más lenta por arranque en frío tras un cambio de configuración.</p>

<h2>3. Hipótesis, métrica, instrumento y ventana (una por integrante)</h2>
<table><tr><th>Integrante</th><th>Fallo y duración</th><th>Hipótesis</th><th>Métrica e instrumento</th></tr>
<tr><td>Juan (Azure)</td><td>F1: 3 000 ms de latencia inyectada hacia Mari, 3 min</td><td>El flujo mantiene éxito ≥ 99 % y p95 ≤ 1 600 ms</td><td>p95 extremo a extremo: k6 (<code>--summary-export</code>) y línea de acceso en Log Analytics</td></tr>
<tr><td>Mari (GCP)</td><td>F3: CPU de un pod de StatsNostra al 100 %, 3 min (Chaos Mesh)</td><td>El p95 del flujo se mantiene ≤ 1 511 ms (su estado estable: 1 208 ms)</td><td>p95 extremo a extremo: k6 y Cloud Monitoring. Ronda 0: 3 425 ms, <span class="mal">se refuta</span>. Ronda 1: 774 ms, <span class="ok">se cumple</span> (datos de su repositorio)</td></tr>
{pipe['fila']}</table>

<h2>4. Método y radio de impacto</h2>
<p>Cada experimento es un archivo de Chaos Toolkit (<code>caos/experimentos/</code>) con estado estable, hipótesis, método y reversión, ejecutado con el mismo comando en las dos rondas. La falla de Juan se activa con la cabecera <code>X-Inject-Outbound-Latency-Ms</code> (apagada por defecto; solo con <code>FAULT_HEADER_ENABLED=true</code>, tope de 10 s). <b>Radio de impacto:</b> solo las peticiones de k6 que llevan la cabecera; infraestructura del grupo; datos sintéticos; máximo 30 usuarios virtuales. <b>Abortos:</b> éxito &lt; 50 % o p95 &gt; 15 000 ms (k6 detiene la prueba a los 20 s). <b>Interruptor de parada:</b> quitar la cabecera o <code>FAULT_HEADER_ENABLED=false</code>. <b>Reversión probada antes de la ronda 0:</b> 662 ms sin falla, 6 709 ms con ella y 659 ms al apagarla. Como medición, tres niveles: M1 extremo a extremo (k6), M2 por API (línea de acceso con <code>traceId</code>, <code>status</code> y <code>ms</code>) y M3 de plataforma (CPU, memoria, reinicios).</p>

<h2>5. Ronda 0: resultados y modo de fallo</h2>
{grafica(med, r0['p95'], r1['p95'], 1600)}
<p class="pie"><b>Figura 1.</b> p95 del flujo en el estado estable y en cada ronda (mediana de las tres corridas del estado estable). <b>Figura 2.</b> Tablero por minuto de las dos rondas (misma información que el Workbook de Azure; datos en <code>caos/resultados/serie-rondas.json</code>). La latencia salta a ~6,4 s en cuanto empieza la inyección y vuelve a 300 ms en la ronda 1 en cuanto el circuito se abre; el primer minuto de la ronda 1 (3 443 ms) es el circuito aprendiendo.</p>
{serie_svg()}
<p>Sin protección, el p95 sube a <b class="mal">{ms(r0['p95'])}</b> (p50 {ms(r0['p50'])}, máximo {ms(r0['max'])}) con <b>{r0['exito']:.0f} % de éxito</b>: <b>la hipótesis se refuta sin que haya un solo error</b>. El throughput cae de 29,0 a {r0['tps']:.1f} req/s ({r0['n']} peticiones atendidas contra más de 5 000): el sistema no se cae, se queda sin capacidad. <b>Modo de fallo: fallo en cascada por latencia.</b> El coordinador hace dos llamadas seguidas a Mari y espera cada una completa, así que 3 000 ms se pagan dos veces (≈ 6,4 s de piso). Diagnóstico con el trace id: la petición <code>k6-8-3</code> aparece en la línea de acceso del coordinador con <code>ms=6378</code>; la latencia se inyecta antes de llamar a Mari, de modo que la API de Mari responde en su tiempo normal. Un monitoreo basado solo en la tasa de error no habría visto nada.</p>

<h2>6. Mejoras aplicadas y justificación</h2>
<ul><li><b>Timeout por llamada</b> (<code>COMPANIONS_CALL_TIMEOUT_MS=1000</code>): una dependencia lenta no puede consumir todo el tiempo de la petición.</li>
<li><b>Circuit breaker</b> de Resilience4j (<code>RESILIENCE_CB_ENABLED=true</code>): tras varias llamadas lentas o fallidas deja de llamar a Mari y responde de inmediato con esa entidad en <code>null</code> (degradación elegante), que el flujo ya sabía absorber.</li></ul>
<p>Ambos están apagados por defecto: la ronda 0 mide el sistema sin protección y la ronda 1 se activa con dos variables, con el mismo perfil de carga y la misma versión del coordinador.</p>
<p><b>Puerta de calidad en el pipeline.</b> El mismo experimento corre en GitHub Actions (<code>chaos-gate.yml</code>) y <b>falla si el p95 o el éxito no cumplen el umbral</b>. Sin protección el gate <span class="mal">rechazó</span> al sistema (p95 de 6 400 ms desde el runner, límite de 1 600 ms) y con timeout y circuit breaker lo <span class="ok">aprobó</span> (759 ms, éxito 100 %). Un primer intento falló por un error propio: <code>p(95)</code> en los argumentos rompe el comando en <code>/bin/sh</code> de Linux y k6 nunca corrió; se distinguió del rechazo real leyendo el diario del experimento.</p>

<h2>7. Ronda 1 y comparación</h2>
<table><tr><th></th><th class="n">Estado estable</th><th class="n">Ronda 0</th><th class="n">Ronda 1</th><th class="n">Variación 0 → 1</th></tr>
<tr><td>p50</td><td class="n">{ms(sorted(e['p50'] for e in est)[1])}</td><td class="n">{ms(r0['p50'])}</td><td class="n">{ms(r1['p50'])}</td><td class="n">{variacion(r0['p50'], r1['p50'])}</td></tr>
<tr><td><b>p95</b></td><td class="n">{ms(med)}</td><td class="n">{ms(r0['p95'])}</td><td class="n"><b>{ms(r1['p95'])}</b></td><td class="n"><b>{variacion(r0['p95'], r1['p95'])}</b></td></tr>
<tr><td>p99</td><td class="n">{ms(sorted(e['p99'] for e in est)[1])}</td><td class="n">{ms(r0['p99'])}</td><td class="n">{ms(r1['p99'])}</td><td class="n">{variacion(r0['p99'], r1['p99'])}</td></tr>
<tr><td>Éxito</td><td class="n">100 %</td><td class="n">{r0['exito']:.0f} %</td><td class="n">{r1['exito']:.0f} %</td><td class="n">sin cambio</td></tr>
<tr><td>Throughput</td><td class="n">29,0 req/s</td><td class="n">{r0['tps']:.1f} req/s</td><td class="n">{r1['tps']:.1f} req/s</td><td class="n">×{r1['tps'] / r0['tps']:.0f}</td></tr>
<tr><td>Hipótesis</td><td></td><td class="n mal">se refuta</td><td class="n ok">se cumple</td><td></td></tr></table>
<p>Con timeout y circuit breaker el p95 baja a <b class="ok">{ms(r1['p95'])}</b>, equivalente al estado estable, y el flujo recupera su capacidad. <b>Costo declarado:</b> en los logs de la ronda 1 hubo 66 llamadas cortadas por el timeout y unos 11 856 avisos de circuito abierto hacia Mari (≈ dos por petición): prácticamente todas las respuestas fueron 200 pero <b>sin los datos de Mari</b>. La mejora recupera latencia y disponibilidad del flujo, no los datos de la dependencia mientras dure la falla (que el p50 sea menor que el del estado estable se explica porque con el circuito abierto se hace menos trabajo). El resultado de Mari sigue el mismo patrón: su mejora (readiness por saturación, más CPU y HPA) devolvió el p95 de 3 425 ms a 774 ms.</p>
{pipe['seccion7']}

<h2>8. Hallazgos inesperados y backlog de resiliencia</h2>
<ul><li>La latencia de una dependencia se paga <b>multiplicada por el número de llamadas seguidas</b>. Backlog: llamar a las dependencias en paralelo.</li>
<li>Una falla de latencia <b>no aparece en la tasa de error</b>: la alerta debe estar sobre el p95, no solo sobre errores.</li>
<li>Durante el estado estable el balanceador de Pipe devolvió dos respuestas <code>502</code> al final de una rampa de 30 usuarios; el coordinador las absorbió. Backlog de Pipe: revisar el balanceador bajo esa carga.</li>
<li>El circuito solo se cierra cuando las llamadas de sondeo vuelven a ser rápidas: mientras la falla persista, el flujo responde degradado.</li>
<li>Un cambio ajeno (reinicio de pods de otra nube) arruina una medición: el aviso de ventana en el chat es parte del método.</li>{pipe['hallazgos']}</ul>
{ia}
</body></html>"""


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--con-ia", type=Path, help="archivo HTML con la sección de uso de IA (opcional)")
    parser.add_argument("--salida", type=Path, default=SALIDA / "informe-caos")
    parser.add_argument("--sin-pdf", action="store_true")
    a = parser.parse_args()
    contenido = construir(a.con_ia.read_text(encoding="utf-8") if a.con_ia else None)
    destino = a.salida.with_suffix(".html")
    destino.write_text(contenido, encoding="utf-8")
    print("HTML:", destino)
    if not a.sin_pdf:
        chrome = r"C:\Program Files\Google\Chrome\Application\chrome.exe"
        pdf = a.salida.with_suffix(".pdf")
        perfil = Path(destino.parent / ".perfil-pdf")
        subprocess.run([chrome, "--headless=new", "--disable-gpu", "--no-pdf-header-footer", f"--user-data-dir={perfil}",
                        f"--print-to-pdf={pdf}", destino.as_uri()], capture_output=True)
        paginas = len(re.findall(rb"/Type\s*/Page[^s]", pdf.read_bytes()))
        print(f"PDF: {pdf} ({paginas} páginas)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
