# Prueba de la inyección por cabecera y de su reversión (14 de noviembre de 2026, UTC)

Comprueba, **antes de la primera ejecución**, que la falla del experimento 1 se activa, afecta solo a las peticiones marcadas y se revierte sola. Son 8 peticiones por condición (no es el perfil de k6, para no cargar las APIs de los compañeros).

- Coordinador: Azure Container Apps, versión `5.0.0-rc.2`, con `FAULT_HEADER_ENABLED=true` durante la prueba.
- Petición: `GET /api/v2/trades/5540`.
- Falla: cabeceras `X-Inject-Outbound-Latency-Ms: 3000` y `X-Inject-Outbound-Target: mari`.

| Condición | Mediana | Máximo |
|---|---|---|
| 1. Antes, sin cabecera | 662 ms | 1 340 ms |
| 2. **Con la falla** (3 000 ms hacia Mari) | **6 709 ms** | 6 810 ms |
| 3. Después, sin cabecera (reversión) | 659 ms | 696 ms |

**Lecturas**

- **La falla se activa:** 6 709 ms ≈ 2 × 3 000 ms + el tiempo normal. El coordinador hace **dos llamadas seguidas a Mari** (`partidos` y `predicciones`) y cada una paga los 3 s: con los patrones apagados, la latencia de una dependencia se suma entera.
- **El radio de impacto es solo la petición marcada:** las peticiones sin cabecera (condiciones 1 y 3) no se ven afectadas.
- **La reversión es inmediata:** al quitar la cabecera el flujo vuelve a ~660 ms, sin reiniciar ni desplegar nada.
- **Hay rastro:** el log del coordinador registró las 8 inyecciones (`event=outbound_fault_header`), entre las 00:06:37 y las 00:07:27 UTC.

**Interruptor de parada:** quitar la cabecera, o `python caos/azure/deploy.py env FAULT_HEADER_ENABLED=false`.
