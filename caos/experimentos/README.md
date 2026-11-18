# Experimentos de caos como código

Cada experimento es un archivo JSON de [Chaos Toolkit](https://chaostoolkit.org/) con **estado estable, hipótesis, método, reversión** y, en `extensions`, el **radio de impacto, la duración, las condiciones de aborto y el interruptor de parada**.

Cada API versiona **su propio experimento en su repositorio**; aquí está el de trading-journal.

| API (nube) | Falla | Experimento | Estado |
|---|---|---|---|
| **trading-journal** (Azure) | Latencia saliente del coordinador hacia StatsNostra, por cabecera | [`01-latencia.json`](01-latencia.json) (este directorio) | ejecutado: ronda 0 (p95 de 6 523 ms) y ronda 1 (474 ms); además corrió como puerta de calidad en GitHub Actions (ver [`../resultados/04-chaos-gate.md`](../resultados/04-chaos-gate.md)) |
| **StatsNostra** (GCP) | CPU de un pod al 100 % (Chaos Toolkit y un endpoint propio, `POST /fault/cpu`) | `caos/experimentos/03-agotamiento.json` en [`MarianaPineda0/StatsNostra`](https://github.com/MarianaPineda0/StatsNostra) | ejecutado en su repositorio: ronda 0 de 3 425 ms, ronda 1 de 774 ms |
| **ecommerce** (AWS) | Se detiene el 50 % de las tareas ECS | `chaos/experiments/stop-tasks.json` en [`jfpa11100/ecommerce-api`](https://github.com/jfpa11100/ecommerce-api) | ejecutado en su repositorio: ronda 0 de 298 ms, ronda 1 de 173 ms |

Los archivos de ejemplo que había aquí para los experimentos de StatsNostra y ecommerce (`02-indisponibilidad.json`, `03-agotamiento.json` y sus scripts) eran marcadores de posición escritos antes de que existieran los reales; se eliminaron para que no queden dos versiones.

## Validar y ensayar (sin ejecutar nada)

```bash
pip install chaostoolkit
chaos validate caos/experimentos/01-latencia.json
chaos run --dry=actions caos/experimentos/01-latencia.json     # verifica la hipótesis de estado estable y recorre el experimento sin inyectar
```

## Ejecutar de verdad

Se avisa antes en el chat la **hora de inicio y de fin**: el perfil de carga llega a las APIs de las tres.

```bash
chaos run --journal-path caos/resultados/01-latencia-diario.json caos/experimentos/01-latencia.json
python caos/gate/evaluar.py --resumen caos/resultados/gate-resumen.json --p95-max 1600 --exito-min 99
```

El experimento lee su configuración de variables de entorno (`BASE_URL`, `TRADE_ID`, `FAULT_LATENCY_MS`, `FAULT_TARGET`, `P95_MS`), con valores por defecto. Para que la falla por cabecera surta efecto, el coordinador debe tener `FAULT_HEADER_ENABLED=true`. Las estadísticas del resumen de k6 (incluido `p(99)`) van en `caos/carga/perfil.js` y no en la línea de comandos: los paréntesis rompen el comando cuando Chaos Toolkit lo ejecuta con `/bin/sh` en Linux.

## Puerta de calidad en el pipeline

[`.github/workflows/chaos-gate.yml`](../../.github/workflows/chaos-gate.yml) corre el experimento y **falla** si el p95 o el éxito no cumplen el umbral, dejando el resumen de k6, el diario del experimento y una tabla que explica el porqué. Resultados en [`../resultados/04-chaos-gate.md`](../resultados/04-chaos-gate.md).
