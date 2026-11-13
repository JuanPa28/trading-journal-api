# Estado estable del flujo (13 de noviembre de 2026)

Medido, no supuesto: tres corridas del perfil fijo [`caos/carga/perfil.js`](../carga/perfil.js) (5, 15 y 30 usuarios virtuales de 60 s cada uno) contra `GET /api/v2/trades/5540` del coordinador (Azure), con las tres nubes conectadas (Mari en GKE, Pipe en AWS) y **todos los interruptores de fallo y patrones de resiliencia apagados**. Versión del coordinador: `5.0.0-rc.1`.

| Corrida | Peticiones | Éxito | p50 | p95 | p99 | Máximo | Throughput |
|---|---|---|---|---|---|---|---|
| 1 | 3 738 | 100 % | 428 ms | 1 289 ms | 1 897 ms | 2,6 s | 20,6 req/s |
| 2 | 3 637 | 100 % | 417 ms | 1 392 ms | 1 856 ms | 2,8 s | 20,1 req/s |
| 3 | 3 090 | 99,90 % (3 fallos) | 453 ms | 1 218 ms | 1 401 ms | 60 s | 14,8 req/s |
| **Mediana** | | **100 %** | **428 ms** | **1 289 ms** | **1 856 ms** | | **20,1 req/s** |

## Estado estable declarado

> Con el perfil de carga fijo, el flujo mantiene una **tasa de éxito ≥ 99 %** y un **p95 extremo a extremo ≤ 1 600 ms** (mediana medida de 1 289 ms más un margen del 25 %).

Este es el umbral `P95_MS=1600` del perfil. La ronda 0 y la ronda 1 se compararán contra él, con el mismo perfil.

## Hallazgo: la API de Mari es el eslabón débil bajo carga

Todas las llamadas fallidas del coordinador a un compañero en esta medición fueron **hacia la API de Mari** (500 Internal Server Error y errores de entrada/salida hacia `34.68.14.217`), concentradas en los escalones de 15 y 30 usuarios; ninguna hacia Pipe.

| Corrida | Hora local (UTC−5) | Avisos hacia Mari |
|---|---|---|
| 1 | 16:48:02 | 2 (uno 500 y uno de conexión) |
| 3 | 16:56:47 a 16:57:49 | 12 (500 y errores de conexión) |

El coordinador degrada correctamente (esa entidad llega en `null` y la respuesta sigue siendo 200), por eso el éxito del flujo es de 99,9 % a 100 %. Pero esos fallos hacen que, en la corrida 3, una petición se quede esperando 60 s y que el rendimiento caiga de 20 a 14,8 req/s. Conviene revisar en Mari los reinicios de pods, el HPA y los logs de esos instantes antes de comparar rondas, para que la medición no se contamine con la inestabilidad de una dependencia.
