# Estado estable del flujo (13 de noviembre de 2026)

> **Estado: PROVISIONAL.** La corrida 3 queda **descartada** porque coincidió con el reinicio de los pods de la API de Mari (ver más abajo). Hay que repetirla en una ventana avisada en el chat antes de dar este estado estable por definitivo.

Medido, no supuesto: tres corridas del perfil fijo [`caos/carga/perfil.js`](../carga/perfil.js) (5, 15 y 30 usuarios virtuales de 60 s cada uno) contra `GET /api/v2/trades/5540` del coordinador (Azure), con las tres nubes conectadas (Mari en GKE, Pipe en AWS) y **todos los interruptores de fallo y patrones de resiliencia apagados**. Versión del coordinador: `5.0.0-rc.1`.

| Corrida | Hora local | Validez | Peticiones | Éxito | p50 | p95 | p99 | Throughput |
|---|---|---|---|---|---|---|---|---|
| 1 | 16:46:42 a 16:49:44 | válida (2 avisos hacia Mari a las 16:48:02, sin explicar) | 3 738 | 100 % | 428 ms | 1 289 ms | 1 897 ms | 20,6 req/s |
| 2 | 16:51:20 a 16:54:22 | válida (termina justo cuando Mari empezó a reiniciar) | 3 637 | 100 % | 417 ms | 1 392 ms | 1 856 ms | 20,1 req/s |
| 3 | 16:55:22 a 16:58:51 | **descartada**: Mari reinició sus pods entre las 16:54 y las 16:57 | 3 090 | 99,90 % | 453 ms | 1 218 ms | 1 401 ms | 14,8 req/s |

Con las corridas 1 y 2 el resultado preliminar es: éxito 100 % y p95 de 1 289 a 1 392 ms.

## Estado estable declarado (provisional)

> Con el perfil de carga fijo, el flujo mantiene una **tasa de éxito ≥ 99 %** y un **p95 extremo a extremo ≤ 1 600 ms** (mediana medida de 1 289 ms más un margen del 25 %).

Este es el umbral `P95_MS=1600` del perfil. Se confirma o se ajusta con la corrida 3 repetida.

## Qué pasó con las llamadas fallidas hacia Mari

El coordinador registró llamadas fallidas **hacia la API de Mari** (500 y errores de conexión); ninguna hacia Pipe.

| Hora local | Avisos | Explicación |
|---|---|---|
| 16:48:02 (corrida 1) | 2 | **Sin explicar.** Es anterior al reinicio que Mari reportó (16:54 a 16:57). Pendiente de que Mari revise sus logs. |
| 16:56:47 a 16:57:49 (corrida 3) | 12 | Coinciden con el reinicio de sus pods mientras k6 estaba corriendo: **causa conocida, no es una debilidad de la API bajo carga**. |

Una conclusión que se había anotado antes («la API de Mari es el eslabón débil bajo carga») era prematura y queda retirada: los fallos de la corrida 3 se explican por el reinicio. Sigue pendiente entender los 2 avisos de las 16:48:02.

El coordinador absorbió todos esos fallos como debe (esa entidad llega en `null` y la respuesta sigue siendo 200).

## Regla acordada para no pisarnos

Antes de correr k6 o tocar una API, se avisa en el chat la **hora de inicio y de fin**. Mari, además, dejó su script de reinicio de modo que se niega a actuar si detecta tráfico.
