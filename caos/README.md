# Ingeniería del Caos — flujo multinube de las tres APIs

Seguimiento #4 (DevOps). Un flujo síncrono de punta a punta, con un generador de carga fijo y experimentos de caos declarados como código, para refutar una hipótesis de resiliencia, mejorar el sistema y demostrar la mejora con datos comparables.

> Estado del flujo (13 de noviembre): las tres APIs responden y el coordinador recorre las tres nubes; ver [`resultados/01-flujo-por-las-tres-nubes.md`](resultados/01-flujo-por-las-tres-nubes.md).

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

## Patrones de resiliencia del coordinador (apagados por defecto)

El comportamiento sin proteger es el de la **ronda 0**. Cada patrón se activa con una variable, sin tocar el código, y se revierte quitándola.

| Patrón | Variable | Efecto |
|---|---|---|
| Timeout por dependencia | `COMPANIONS_CALL_TIMEOUT_MS=1000` | Tope de tiempo para toda la llamada a un compañero; si se pasa, esa entidad queda en `null` y la petición sigue |
| Circuit breaker por compañero | `RESILIENCE_CB_ENABLED=true` | Si la mitad de las últimas 10 llamadas fallan o tardan más de 1 s, el circuito se abre y se responde `null` al instante; tras 10 s se prueba de nuevo |
| Ajustes del circuito | `RESILIENCE_CB_WINDOW_SIZE`, `RESILIENCE_CB_MINIMUM_CALLS`, `RESILIENCE_CB_FAILURE_RATE_PERCENT`, `RESILIENCE_CB_SLOW_CALL_MS`, `RESILIENCE_CB_OPEN_SECONDS` | Valores por defecto: 10, 5, 50, 1000 y 10 |
| Tiempos del socket | `COMPANIONS_CONNECT_TIMEOUT_MS`, `COMPANIONS_READ_TIMEOUT_MS` | Por defecto 5000 y 10000 |

```bash
python caos/azure/deploy.py env COMPANIONS_CALL_TIMEOUT_MS=1000 RESILIENCE_CB_ENABLED=true   # ronda 1
python caos/azure/deploy.py env COMPANIONS_CALL_TIMEOUT_MS=0 RESILIENCE_CB_ENABLED=false      # volver a la ronda 0
```

Cuando el circuito se abre o se cierra queda una línea de log (`Circuit breaker companion-mari: CLOSED_TO_OPEN`), que sirve para mostrarlo en la demostración.

## Resultados

*Pendiente:* estado estable medido, ronda 0, diagnóstico, mejoras, ronda 1 y tabla comparativa.
