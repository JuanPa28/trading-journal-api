# Experimentos de caos como código

Cada experimento es un archivo JSON de [Chaos Toolkit](https://chaostoolkit.org/) con **estado estable, hipótesis, método, reversión** y, en `extensions`, el **radio de impacto, la duración, las condiciones de aborto y el interruptor de parada**.

| # | Archivo | Fallo | Responsable | Estado |
|---|---|---|---|---|
| 1 | [`01-latencia.json`](01-latencia.json) | Latencia saliente del coordinador hacia Mari | Juan | listo; falta ejecutar la ronda 0 |
| 2 | [`02-indisponibilidad.json`](02-indisponibilidad.json) | Una instancia de la API de Pipe deja de estar disponible | Pipe | estructura lista; falta `acciones/pipe-inyectar.sh` y `pipe-revertir.sh` |
| 3 | [`03-agotamiento.json`](03-agotamiento.json) | CPU de un pod de la API de Mari al 100 % | Mari | estructura lista; falta ajustar `NAMESPACE` y `APP_LABEL` |

## Validar y ensayar (sin ejecutar nada)

```bash
pip install chaostoolkit
chaos validate caos/experimentos/01-latencia.json
chaos run --dry=actions caos/experimentos/01-latencia.json     # verifica la hipótesis de estado estable y recorre el experimento sin inyectar
```

## Ejecutar de verdad

Se avisa antes en el chat la **hora de inicio y de fin**: el perfil de carga llega a las APIs de los tres.

```bash
chaos run --journal-path caos/resultados/01-latencia-diario.json caos/experimentos/01-latencia.json
python caos/gate/evaluar.py --resumen caos/resultados/gate-resumen.json --p95-max 1600 --exito-min 99
```

El experimento 1 lee su configuración de variables de entorno (`BASE_URL`, `TRADE_ID`, `FAULT_LATENCY_MS`, `FAULT_TARGET`, `P95_MS`), con valores por defecto. Para que la falla por cabecera surta efecto, el coordinador debe tener `FAULT_HEADER_ENABLED=true`.

## Puerta de calidad en el pipeline

[`.github/workflows/chaos-gate.yml`](../../.github/workflows/chaos-gate.yml) corre el experimento 1 y **falla** si el p95 o el éxito no cumplen el umbral, dejando el resumen de k6, el diario del experimento y una tabla que explica el porqué.

## Cómo completar los experimentos 2 y 3

Los scripts de [`acciones/`](acciones) son los que inyectan y revierten la falla; el resto del archivo ya está listo.

- **Pipe:** reemplaza `pipe-inyectar.sh` y `pipe-revertir.sh` por el comando real (detener y volver a levantar una instancia, o activar y quitar `FAULT_ERROR_RATE=1`). Mientras digan «PENDIENTE», el experimento 2 falla a propósito.
- **Mari:** define `APP_LABEL` (la etiqueta `app` de los pods de su API) y `NAMESPACE`; `mari-inyectar.sh` aplica un `StressChaos` de Chaos Mesh y `mari-revertir.sh` lo elimina.
