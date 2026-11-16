# Rondas 0 y 1 del experimento 1: latencia saliente hacia Mari (16 de noviembre de 2026)

Mismo perfil de k6 (5, 15 y 30 usuarios virtuales de 60 s), mismo trade 5540, misma falla (3 000 ms de latencia hacia Mari en cada petición, por cabecera) y misma versión del coordinador (`5.0.0-rc.2`). Solo cambia la configuración: la ronda 0 corre sin protección; la ronda 1 activa `COMPANIONS_CALL_TIMEOUT_MS=1000` y `RESILIENCE_CB_ENABLED=true`. Ejecutadas con Chaos Toolkit (`caos/experimentos/01-latencia.json`); los diarios están en `ronda-0-diario.json` y `ronda-1-diario.json` y los resúmenes de k6 en `ronda-N-k6.json`.

**Hipótesis:** con 3 000 ms de latencia hacia Mari durante 3 minutos, el flujo mantiene éxito ≥ 99 % y p95 ≤ 1 600 ms.

| | Estado estable | Ronda 0 (sin protección) | Ronda 1 (timeout 1 s + circuit breaker) | Variación 0 → 1 |
|---|---|---|---|---|
| Inicio y fin (hora local) | 12:08 a 12:20 | 12:33:10 a 12:36:22 | 12:39:34 a 12:42:38 | |
| Peticiones atendidas | 4 198 a 5 236 por corrida | 325 | 5 961 | +1 734 % |
| Throughput | 29,0 req/s | 1,7 req/s | 32,9 req/s | ×19 |
| Éxito (peticiones que no fallan) | 100 % | 100 % | 100 % | sin cambio |
| p50 | 367 ms | 6 458 ms | 300 ms | −95,4 % |
| p95 | 480 ms | **6 523 ms** | **474 ms** | **−92,7 %** |
| p99 | 603 ms | 6 895 ms | 1 277 ms | −81,5 % |
| Máximo | | 16 433 ms | 3 548 ms | −78,4 % |
| Peticiones por encima de 1 600 ms (línea de acceso) | | 325 de 388 | 4 de 6 084 | |
| **Hipótesis** | | **se refuta** (p95 de 6 523 ms) | **se cumple** (p95 de 474 ms) | |

Una primera ejecución de la ronda 0 (`ronda-0-k6-intento-1.json`) dio el mismo resultado: 326 peticiones, p95 de 6 493 ms. Se repitió solo porque el diario del experimento no se guardó por un error de codificación en Windows (ya corregido).

## Modo de fallo de la ronda 0

**Fallo en cascada por latencia, sin errores.** La falla no produce ningún error: el éxito se mantiene en 100 % y por eso un monitoreo basado solo en tasa de error no vería nada. Lo que se degrada es el tiempo de respuesta y, como consecuencia, la capacidad: cada petición espera 3 000 ms por cada llamada a Mari y el coordinador hace dos llamadas seguidas (partidos y predicciones), de modo que ~6,4 s es el piso. Con usuarios virtuales que esperan la respuesta, el throughput cae de 29 a 1,7 req/s: el sistema no se cae, se queda sin capacidad.

Diagnóstico con el trace id: la petición `k6-8-3` (17:34:00Z) aparece en la línea de acceso del coordinador con `ms=6378`. La latencia se inyecta en el coordinador **antes** de llamar a Mari, así que en los logs de Mari el mismo `traceId` debe llegar con retraso y responder en su tiempo normal: es lo que se contrasta en su Cloud Logging (pendiente de que Mari lo confirme con ese trace id).

## Lo que hizo la ronda 1 (y lo que cuesta)

Con el timeout de 1 s, las primeras llamadas se cortan al segundo; tras varias llamadas lentas, el circuit breaker se abre y las siguientes ni siquiera se intentan: el coordinador responde de inmediato con la entidad de Mari en `null`. En los logs de la ronda 1: **66 llamadas cortadas por el timeout** y **11 856 avisos de circuito abierto** hacia Mari (≈ dos por petición): prácticamente todas las peticiones de la ronda se atendieron **sin los datos de Mari**.

Esto es lo esperado de un patrón de degradación elegante, pero hay que declararlo: la mejora recupera la **latencia y la disponibilidad del flujo** (p95 de 474 ms, 100 % de respuestas 200), no los datos de Mari mientras dure la falla. Que el p50 de la ronda 1 (300 ms) sea incluso menor que el del estado estable (367 ms) se explica porque con el circuito abierto el coordinador hace menos trabajo.

## Hallazgos

1. La latencia de una dependencia se paga **multiplicada por el número de llamadas seguidas**. Backlog: llamar a las dependencias en paralelo.
2. Una falla de latencia **no aparece en la tasa de error**: la alerta debe estar sobre latencia (p95) y no solo sobre errores.
3. El circuit breaker solo se cierra cuando las llamadas de sondeo vuelven a ser rápidas; mientras la falla persista, el flujo responde degradado.
4. Recuperación: tras apagar la inyección, el coordinador volvió a salud 200 y a su comportamiento normal (el experimento verificó la reversión en su `rollback`).
