# Chaos gate en GitHub Actions (17 de noviembre de 2026)

El workflow [`chaos-gate.yml`](../../.github/workflows/chaos-gate.yml) ejecuta el experimento de latencia (`caos/experimentos/01-latencia.json`) con el perfil fijo de k6 desde un runner de GitHub y **falla si la métrica objetivo no cumple el umbral** (éxito ≥ 99 % y p95 ≤ 1 600 ms). Es la puerta de calidad: convierte la resiliencia en una condición del pipeline. Se ejecutó con la ventana avisada en el chat, con 3 000 ms de latencia inyectada hacia Mari.

| | Ronda 0 (sin protección) | Ronda 1 (timeout de 1 s + circuit breaker) |
|---|---|---|
| Ejecución | [44184758876](https://github.com/JuanPa28/trading-journal-api/actions/runs/44184758876) | [44185817955](https://github.com/JuanPa28/trading-journal-api/actions/runs/44185817955) |
| Veredicto del gate | **RECHAZADO** (workflow en rojo) | **APROBADO** (workflow en verde) |
| p95 extremo a extremo | **6 400 ms** (límite 1 600) | **759 ms** |
| Éxito | 100 % | 100 % |
| p50 · p99 · máximo | 6 303 · 6 433 · 6 545 ms | 403 · 1 043 · 3 140 ms |
| Peticiones | 334 | 4 357 |
| Motivo | «Latencia p95 extremo a extremo: 6 399,64 ms no cumple ≤ 1 600 ms» | cumple las dos comprobaciones |

El rechazo de la ronda 0 **es el resultado esperado**: un workflow en rojo en el historial de Actions demuestra que la puerta detiene un sistema frágil. La evidencia de cada ejecución (resumen de k6 y diario del experimento) está en [`gate-ronda-0/`](gate-ronda-0) y [`gate-ronda-1/`](gate-ronda-1).

**Comparación con la medición local:** las rondas medidas desde el computador de Juan dieron p95 de 6 523 y 474 ms; el runner de GitHub dio 6 400 y 759 ms. La diferencia de la ronda 1 se explica porque el runner está en otra red y región (más latencia hacia el coordinador y hacia las otras nubes) y porque el circuito necesita unos segundos de carga para abrirse; en las dos mediciones la conclusión es la misma.

## Un primer intento falló por un error mío y no cuenta

La primera ejecución ([44184224512](https://github.com/JuanPa28/trading-journal-api/actions/runs/44184224512)) terminó en 17 segundos con «rechazado», pero **k6 nunca llegó a correr**: ese mismo día se había agregado `--summary-trend-stats avg,min,med,max,p(90),p(95),p(99)` a los argumentos del experimento, y Chaos Toolkit ejecuta el comando con `/bin/sh` en Linux, donde los paréntesis dan `Syntax error: "(" unexpected`. En Windows funcionaba, y por eso no se vio antes. El gate rechazó porque no había resumen de k6, no porque el sistema fallara. Se corrigió moviendo las estadísticas a `summaryTrendStats` dentro de `perfil.js` (sin paréntesis en la línea de comandos).

**Lección:** una puerta que se pone en rojo no prueba nada si no se mira *por qué*. Aquí el rechazo real (p95 de 6 400 ms) y el rechazo falso (k6 que no corrió) se distinguieron leyendo el diario del experimento.

## Estado final

Tras las dos ejecuciones, la inyección, el timeout y el circuit breaker quedaron apagados y verificados (`FAULT_HEADER_ENABLED=false`, `COMPANIONS_CALL_TIMEOUT_MS=0`, `RESILIENCE_CB_ENABLED=false`).
