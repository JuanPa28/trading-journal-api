# Analista de IA del game day

La IA lee la telemetría de las rondas y propone un análisis; no ejecuta nada ni decide por el equipo. Dos controles la mantienen honesta, y ambos se prueban con casos negativos y positivos.

| Control | Qué hace | Mapeo |
|---|---|---|
| `preparar --sanitizar` | La telemetría (logs, bitácora) es texto no confiable. Antes de dársela al asistente se neutralizan las frases de mando (inyección de instrucciones), se quitan caracteres de control, se acota cada línea y se encierra todo entre `<datos>` y `</datos>`. | MITRE ATLAS AML.T0051 (LLM Prompt Injection), en su forma indirecta |
| `verificar` | Toda cifra (`45 s`, `12 ms`, `80 %`) y todo identificador (trace id) que cite la respuesta debe aparecer en la evidencia. Lo que no aparezca se marca como posible invención. | Contraste con la telemetría exigido por la rúbrica |

## Cómo se usa

```bash
python seguridad/ia/analista.py preparar --evidencia seguridad/resultados --sanitizar --salida entrada.md
# se pega entrada.md en el asistente y se guarda su respuesta en respuesta.md
python seguridad/ia/analista.py verificar --evidencia seguridad/resultados --respuesta respuesta.md
```

Para el contraste se usan **dos asistentes distintos** con la misma `entrada.md`, y se compara en el informe qué acertó cada uno, qué inventó y qué se descartó.

## Pruebas

```bash
python -m pytest seguridad/ia/test_analista.py
```

Cubren las inyecciones plantadas en una **URL** (con `%20`, `+` o guiones en lugar de espacios, que un atacante puede dejar en el campo `route` de un log), cinco líneas de inyección (español e inglés, orden de revelar secretos, falso mensaje de sistema, cierre del bloque de datos), una línea normal que no se debe tocar, caracteres de control y la verificación de cifras e identificadores inventados.

## Límites

La sanitización por patrones no detiene toda inyección posible: reduce la superficie y, sobre todo, la IA recibe instrucciones de tratar la telemetría como datos y de **reportar** cualquier orden que encuentre. Por eso la última tarea del prompt es listar los textos que intentan dar órdenes, y por eso toda cifra se verifica antes de entrar al informe.
