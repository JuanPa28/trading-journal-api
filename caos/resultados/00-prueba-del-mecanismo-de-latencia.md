# Prueba del mecanismo de latencia saliente (13 de noviembre de 2026)

No es un experimento: comprueba que el interruptor de fallo funciona y que se puede activar y revertir sobre el coordinador desplegado en Azure.

- Coordinador: Azure Container Apps, versión `5.0.0-dev`.
- Petición: `GET /api/v2/trades/5540` (8 repeticiones por condición, desde un equipo externo; no es el perfil de k6).
- Compañeros conectados: Pipe (AWS); la API de Mari todavía no estaba configurada (falla rápido y su campo queda en `null`).

| Condición | Mediana | Máximo |
|---|---|---|
| Sin fallo | 573 ms | 872 ms |
| `python caos/azure/deploy.py fault --latency-ms 2000 --target pipe` | **4 632 ms** | 5 157 ms |
| Después de `fault --latency-ms 0` (reversión) | 502 ms | 962 ms |

**Lectura.** Con 2 000 ms de latencia hacia Pipe, la petición tarda unos 4 600 ms: casi exactamente 2 × 2 000 ms más el tiempo normal. Es la señal de que el coordinador hace **dos llamadas seguidas a Pipe** (`commerce` y `products`) y de que ninguna tiene un tiempo de espera ni un circuit breaker que las acote: la latencia de un compañero se suma completa a la de la petición. Es el punto de partida de la ronda 0.

**Reversión verificada:** al poner la latencia en 0, el flujo vuelve a ~500 ms. Cambiar una variable crea una revisión nueva en Azure y tarda unos 3 minutos en quedar lista; el script espera a que la revisión nueva responda antes de dar el cambio por aplicado.
