# Postman de la entrega 5 (caos y seguridad)

Importar los dos archivos en Postman (**Import**) y seleccionar el entorno **Entorno Entrega 5**. Correr las carpetas en orden con el **Collection Runner**; cada petición trae sus pruebas automáticas.

| Carpeta | Qué demuestra |
|---|---|
| 0. Antes de empezar | que las tres APIs (Azure, Google Cloud y AWS) están sanas |
| 1. El flujo completo con trace id | una petición recorre las tres nubes y el mismo `X-Trace-Id` vuelve en la respuesta; también el caso sin trace id |
| 2. Caos: la falla y la mejora | la latencia inyectada hacia StatsNostra y sus dos rondas; las pruebas cambian según la variable `modo` |
| 3. Las otras dos APIs | StatsNostra y ecommerce directamente, con su `X-App-Name` |
| 4. Seguridad | un hueco declarado en el inventario: `/actuator/prometheus` es público |

## La carpeta 2 necesita la inyección activa
La falla solo funciona si el coordinador tiene `FAULT_HEADER_ENABLED=true` (tarda ~2 min en aplicarse, porque crea una revisión nueva):

```bash
python caos/azure/deploy.py env FAULT_HEADER_ENABLED=true                                   # ronda 0: con falla y sin protección
python caos/azure/deploy.py env COMPANIONS_CALL_TIMEOUT_MS=1000 RESILIENCE_CB_ENABLED=true  # ronda 1: con timeout y circuit breaker
python caos/azure/deploy.py env FAULT_HEADER_ENABLED=false COMPANIONS_CALL_TIMEOUT_MS=0 RESILIENCE_CB_ENABLED=false   # apagar todo
```
Y cambiar la variable `modo` del entorno a `normal`, `ronda0` o `ronda1`. Con `modo = ronda0` la petición 2.1 debe tardar más de 5 000 ms; con `ronda1`, menos de 3 500 ms. Si con `ronda0` tarda menos de 1,5 s, la inyección **no** está activa.

Las carpetas 0, 1, 3 y 4 y la petición de control 2.2 se verificaron contra las APIs reales en modo `normal`. Los modos `ronda0` y `ronda1` siguen la lógica de las rondas medidas (6 523 ms y 474 ms) pero **no se ejecutaron con la inyección activa** en esta colección.
