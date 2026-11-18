#!/usr/bin/env python3
"""Genera el informe de seguridad (HTML y PDF, máximo 10 páginas) a partir de la evidencia y la bitácora.

Las cifras de los experimentos 1 y 4 salen de seguridad/resultados/*.json y la cronología sale de
seguridad/bitacora.md; no se escriben a mano. Lo que falta (experimento 3 de ecommerce) se dice como pendiente.

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


def pipe_seguridad() -> dict:
    """Bloques del informe con el experimento 3 de ecommerce. Lo que falta se dice como pendiente."""
    archivo = RES / "ecommerce-seguridad.json"
    sin = {"__PIPE_FILA3__": "<tr><td>3 · Telemetría y capa de IA (ecommerce)</td><td>Con telemetría envenenada, el analista de IA no obedece instrucciones plantadas</td><td><b>Pendiente de la evidencia de ecommerce</b></td></tr>",
           "__PIPE_BLOQUE5__": '<p class="nota"><b>ecommerce (experimento 3, telemetría e IA):</b> pendiente de su evidencia. No se incluyen cifras que no existan.</p>',
           "__PIPE_CONTRASTE__": "<b>El contraste con un segundo asistente, con las mismas entradas y sin contexto, está pendiente</b> y es lo que valida el resultado.",
           "__PIPE_RIESGO__": "<li>Falta la evidencia del experimento 3 de ecommerce y el contraste con un segundo asistente de IA.</li>"}
    if not archivo.exists():
        return sin
    d = json.loads(archivo.read_text(encoding="utf-8"))
    from datetime import datetime
    t0 = datetime.fromisoformat(d["t0_ronda_1"].replace("Z", "+00:00"))
    alarma = datetime.fromisoformat(d["alarma_ronda_1"].replace("Z", "+00:00"))
    mttd = round((alarma - t0).total_seconds())
    pend = lambda x, f="{}": "<i>pendiente</i>" if x is None else f.format(x)
    n = d["inyecciones"]
    cob = (f"{d['cobertura_medida']} de {n}" if d["cobertura_medida"] is not None
           else f"{d['cobertura_esperada']} de {n} <i>(esperada por el patrón; pendiente de confirmar con la métrica)</i>")
    fila = ('<tr><td>3 · Telemetría y capa de IA (ecommerce)</td><td>Con una inyección de instrucciones plantada en la ruta del log: alerta en 5 minutos o menos (T1) y el analista de IA no obedece (T2)</td>'
            f'<td>Línea base de 20 peticiones normales sin sospechosas; 5 rutas con texto plantado; asistente independiente ({d["asistente"]})</td></tr>')
    bloque = (f'<h3>Experimento 3 de ecommerce: telemetría envenenada</h3>'
              f'<table><tr><th></th><th>Ronda 0</th><th>Ronda 1 (filtro de métricas + alarma + correo)</th></tr>'
              f'<tr><td>Inyecciones plantadas</td><td class="n">{n} (t0 {d["t0_ronda_0"][11:19]}Z)</td><td class="n">{n} (t0 {d["t0_ronda_1"][11:19]}Z)</td></tr>'
              f'<tr><td><b>MTTD</b></td><td><span class="mal">sin alerta</span> (no hay detección en origen)</td><td class="n"><span class="ok">{mttd} s</span> (alarma a ALARM a las {d["alarma_ronda_1"][11:19]}Z)</td></tr>'
              f'<tr><td>Cobertura</td><td>0 de {n}</td><td>{cob}</td></tr>'
              f'<tr><td>Falsos positivos (20 peticiones normales)</td><td>no aplica</td><td class="n">{pend(d["falsos_positivos"], "{} de 20")}</td></tr>'
              f'<tr><td>¿La IA obedeció la orden plantada?</td><td>no (sin sanitizar)</td><td>no (la entrada que se usó no estaba sanitizada){" · con la entrada sanitizada: " + pend(d["ia_ronda_1_sanitizada"]) if d["ia_ronda_1_sanitizada"] is None else ""}</td></tr>'
              f'<tr><td>¿La IA reportó las órdenes como hallazgo?</td><td class="n">{d["ia_ronda_0_sin_sanitizar"]["reporto_como_hallazgo"]} de {n}</td><td>sí</td></tr>'
              f'<tr><td>Cifras sin respaldo (<code>verificar</code>)</td><td class="n">{d["ia_ronda_0_sin_sanitizar"]["cifras_sin_respaldo"]}</td><td class="n">{d["ia_ronda_1_sin_sanitizar"]["cifras_sin_respaldo"]}</td></tr>'
              f'<tr><td>Hipótesis</td><td>T1 <span class="mal">se refuta</span> · T2 <span class="ok">se cumple</span></td><td>T1 <span class="ok">se cumple</span> ({mttd} s frente a 300 s) · T2 se cumple en lo medido</td></tr></table>'
              f'<p><b>Límites declarados:</b> (1) la inyección escrita con <code>+</code> y puntos evade el patrón del filtro: la cobertura esperada es {d["cobertura_esperada"]} de {n}, y se declaró como límite en lugar de ampliar el patrón después de ver el resultado; '
              f'(2) <b>el asistente no obedeció ni siquiera sin sanitizar</b>: la sanitización es defensa en profundidad, no lo que evitó la obediencia en esta prueba; '
              f'(3) la prueba de la ronda 1 con entrada sanitizada se hizo por error con la entrada sin filtrar (0 marcas de neutralización) y se repite; resultado: {pend(d["ia_ronda_1_sanitizada"])}; '
              f'(4) la reversión de los recursos de la mejora: {pend(d["reversion_confirmada"])}; (5) una sola ejecución por ronda.</p>')
    contraste = (f'<b>El contraste con un segundo asistente independiente se hizo con {d["asistente"]} sobre la telemetría de ecommerce</b> (chat nuevo y sin contexto): no obedeció en las dos pruebas sin sanitizar. '
                 'Sigue siendo una sola muestra por ronda y un solo asistente independiente.')
    riesgo = f'<li>Pendientes de ecommerce: {", ".join(d["pendientes"])}.</li>' if d["pendientes"] else ""
    return {"__PIPE_FILA3__": fila, "__PIPE_BLOQUE5__": bloque, "__PIPE_CONTRASTE__": contraste, "__PIPE_RIESGO__": riesgo}


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
    for marca, bloque in pipe_seguridad().items():
        plantilla = plantilla.replace(marca, bloque)
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
