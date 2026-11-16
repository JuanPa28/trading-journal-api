# Estado estable del flujo (16 de noviembre de 2026)

Medido, no supuesto: tres corridas del perfil fijo [`caos/carga/perfil.js`](../carga/perfil.js) (5, 15 y 30 usuarios virtuales de 60 s cada uno) contra `GET /api/v2/trades/5540` del coordinador (Azure), con las tres nubes conectadas (Mari en GKE, Pipe en AWS) y **todos los interruptores de fallo y patrones de resiliencia apagados**. Versión del coordinador: `5.0.0-rc.2`. Ventana anunciada en el chat del equipo; durante las corridas nadie envió carga ni reinició nada.

| Corrida | Hora local | Peticiones | Éxito | p50 | p95 | p99 | Throughput |
|---|---|---|---|---|---|---|---|
| 1 | 12:08:41 a 12:11:42 | 4 198 | 100 % | 429 ms | 689 ms | 972 ms | 23,2 req/s |
| 2 | 12:12:43 a 12:15:44 | 5 236 | 100 % | 367 ms | 477 ms | 610 ms | 29,0 req/s |
| 3 | 12:16:44 a 12:19:45 | 5 232 | 100 % | 364 ms | 480 ms | 603 ms | 29,0 req/s |

Mediana de las tres corridas: **éxito 100 %, p95 de 480 ms** (p50 de 367 ms). La corrida 1 fue más lenta (689 ms) que las otras dos: se interpreta como arranque en frío tras el último cambio de configuración (la revisión nueva del coordinador había arrancado minutos antes).

Contra las medidas del 13 de noviembre (versión `5.0.0-rc.1`, p95 de 1 289 a 1 392 ms) la latencia bajó a menos de la mitad. Lo que cambió entre ambas mediciones: la versión del coordinador y la API de Mari, que aplicó mejoras en su ronda 1 (readiness por saturación, más CPU y HPA). Esas medidas anteriores quedan como referencia histórica en el historial de git (`estado-estable-*.json` del 13 de nov.).

## Estado estable declarado

> Con el perfil de carga fijo, el flujo mantiene una **tasa de éxito ≥ 99 %** y un **p95 extremo a extremo ≤ 1 600 ms**.

El umbral de 1 600 ms (`P95_MS=1600` en el perfil) es el **presupuesto de latencia aceptable para el usuario**, no una mediana más un margen: el estado estable medido (480 ms) está 3,3 veces por debajo, de modo que cualquier degradación que lo cruce es un fallo real y no ruido de medición. Coincide en orden de magnitud con el límite de 1 511 ms que el grupo acordó midiendo junto con Mari.

## Llamadas fallidas durante la ventana

Entre las 12:08 y las 12:20 el coordinador registró **2 avisos**, ambos de la misma causa: dos respuestas `502 Bad Gateway` del balanceador de Pipe a `GET /api/v1/commerce` (12:19:29 y 12:19:34, durante la rampa de 30 usuarios de la corrida 3). Ninguna hacia Mari. El coordinador las absorbió como debe (esa entidad llega en `null` y la respuesta sigue siendo 200): k6 no registró ninguna petición fallida. Pendiente de que Pipe revise su balanceador para esos instantes.

## Regla acordada para no pisarnos

Antes de correr k6 o tocar una API, se avisa en el chat la **hora de inicio y de fin**.
