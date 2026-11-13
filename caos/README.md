# Ingeniería del Caos — flujo multinube de las tres APIs

Seguimiento #4 (DevOps). Un flujo síncrono de punta a punta, con un generador de carga fijo y experimentos de caos declarados como código, para refutar una hipótesis de resiliencia, mejorar el sistema y demostrar la mejora con datos comparables.

> Estado: **en construcción** (entrega el 18 de noviembre de 2026). Las secciones marcadas como *pendiente* se completan al ejecutar las rondas.

## El flujo

```text
Cliente (k6, perfil fijo)
   │  X-Trace-Id
   ▼
Coordinador (Juan) ── Azure Container Apps ──► base de datos Oracle
   GET /api/v2/trades/{id}
   │  X-Trace-Id               │  X-Trace-Id
   ▼                           ▼
Mari · Google Cloud (GKE)      Pipe · AWS
/api/v1/partidos               /api/v1/commerce
/api/v1/predicciones           /api/v1/products
```

El coordinador llama en cadena a una entidad de cada compañero y reenvía el identificador de correlación. Cada API está en una nube distinta. *Diagrama con los puntos de inyección, la frontera del radio de impacto y los puntos de medición: pendiente.*

### Qué se conserva y qué se retira del flujo anterior

| Se conserva | Se retira |
|---|---|
| Las tres APIs completas, cada una en su nube; el flujo síncrono con el trace id de extremo a extremo; la observabilidad | La cola de mensajería, la caché distribuida y el almacenamiento de objetos como piezas obligatorias del flujo |

## Matriz integrante – fallo – métrica – patrón

| Integrante | Fallo | Métrica objetivo | Patrones de resiliencia |
|---|---|---|---|
| Juan (Azure) | (a) latencia inyectada en la llamada saliente hacia Mari | latencia p95 extremo a extremo | pendiente: timeout, paralelismo, circuit breaker, degradación |
| Pipe (AWS) | (b) indisponibilidad de su API | tasa de error del flujo y MTTR | pendiente |
| Mari (GKE) | (c) agotamiento de CPU de un pod | latencia p95 o throughput sostenido | pendiente |

## Piezas

| Pieza | Dónde |
|---|---|
| Perfil de carga fijo (k6) | [`carga/`](carga) |
| Experimentos como código (Chaos Toolkit) | [`experimentos/`](experimentos) |
| Despliegue del coordinador en Azure (un solo grupo de recursos) | [`azure/deploy.py`](azure/deploy.py) |
| Resultados de cada corrida | [`resultados/`](resultados) |
| Interruptor de fallo propio | `FaultInjectionFilter` (cabecera `X-Inject-Fault`) y `CompanionClient` (latencia saliente, `OUTBOUND_FAULT_LATENCY_MS`) |

## Cómo operar el coordinador

```bash
python caos/azure/deploy.py deploy --pipe-url <URL de Pipe> --mari-url <URL de Mari>   # crea o actualiza todo
python caos/azure/deploy.py fault --latency-ms 3000 --target mari                       # activa la latencia saliente
python caos/azure/deploy.py fault --latency-ms 0                                        # la apaga (reversión)
python caos/azure/deploy.py env COMPANIONS_READ_TIMEOUT_MS=1000                         # cambia un tiempo de espera
python caos/azure/deploy.py teardown                                                    # elimina todo el grupo de recursos
```

## Resultados

*Pendiente:* estado estable medido, ronda 0, diagnóstico, mejoras, ronda 1 y tabla comparativa.
