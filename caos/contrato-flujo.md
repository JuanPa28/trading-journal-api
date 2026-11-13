# Contrato común del flujo

Lo que las tres APIs deben cumplir para que el flujo se pueda medir, romper y reconstruir. Es la fuente de verdad: si algo cambia, se cambia aquí.

## Las tres APIs

| | Juanpa (coordinador) | Mari | Pipe |
|---|---|---|---|
| Nube | Azure Container Apps | Google Cloud (GKE) | AWS |
| URL base | `https://api-coordinador.jollybeach-fbb8b9ad.eastus.azurecontainerapps.io` | `http://34.68.14.217` | `http://ecommerce-api-dev-alb-1955683949.us-east-2.elb.amazonaws.com` |
| Rutas del flujo | `GET /api/v2/trades/{id}` (entrada) | `GET /api/v1/partidos`, `GET /api/v1/predicciones` | `GET /api/v1/commerce`, `GET /api/v1/products` |
| Salud | `/actuator/health` | `/health/ready` | `/health` |
| Dónde se ven sus logs | Azure Log Analytics (`ContainerAppConsoleLogs_CL`) | Cloud Logging (GKE) | CloudWatch Logs |
| `TRADE_ID` de las pruebas | `5540` | | |

## Requisitos

| # | Tema | Qué se acuerda | Estado |
|---|---|---|---|
| 1 | Endpoints del flujo | Las rutas de la tabla de arriba, que devuelven JSON (lista). Si alguien cambia una ruta, avisa a Juanpa. Cada lista debe tener **al menos un registro** | Juanpa sí · Mari sí · Pipe sí |
| 2 | Trace id | Cabecera `X-Trace-Id`. Si llega se usa; si no, se genera. Sale en **cada línea de log** de la petición y **vuelve en la respuesta** | Juanpa sí · Mari sí · Pipe pendiente |
| 3 | Identificación | Cabeceras `X-App-Name` (`juanpa`, `mari` o `pipe`) y `X-App-Version` en todas las respuestas | Juanpa sí · Mari sí · Pipe pendiente |
| 4 | Salud | Una ruta de salud que responde 200. En Kubernetes, liveness y readiness sobre rutas distintas | Juanpa sí · Mari sí · Pipe `/health` |
| 5 | Interruptor de fallos | Apagado por defecto. Cabecera `X-Inject-Fault: error \| latency`, solo si `FAULT_HEADER_ENABLED=true`; variables `FAULT_ERROR_RATE` (0 a 1) y `FAULT_LATENCY_MS`. Nunca afecta a la ruta de salud | Juanpa sí · Mari sí · Pipe pendiente |
| 6 | **Formato de log** | Ver la sección siguiente | Juanpa sí · Mari y Pipe por revisar |
| 7 | **Métricas** | Ver la sección siguiente | Juanpa sí · Mari y Pipe por revisar |
| 8 | Capacidad mínima | Cada API aguanta el perfil de carga: hasta 30 usuarios virtuales en el coordinador, es decir unas **50 peticiones por segundo hacia cada compañero** (2 llamadas por petición), sin errores | por confirmar |
| 9 | Ventanas | Antes de correr k6 o de tocar una API, se avisa en el chat la **hora de inicio y de fin** | acordado |
| 10 | Reversión | Cada integrante tiene documentado cómo apagar su interruptor de fallos y volver al estado normal | pendiente |
| 11 | Exportar logs de una ventana | Cada uno puede sacar los logs entre dos horas (UTC) filtrando por `traceId`, en texto, para el informe y el analista de IA | pendiente |
| 12 | Datos | Solo datos sintéticos del proyecto | acordado |

## 6. Formato de log

**Dónde vive:** en el código de cada API, en la configuración de sus logs. El contrato solo fija los campos; cada nube los muestra en su servicio de logs.

**Qué se acuerda:** además de las líneas que ya escriba cada API, **una línea de acceso por petición**, en una sola línea, con estos campos y la hora en **UTC con fecha**:

```text
2026-11-13T22:45:16.289Z  INFO [traceId=juanpa-mari-174517] ... : event=request api=juanpa traceId=juanpa-mari-174517 method=GET route=/api/v2/trades/5540 status=200 ms=1025
```

| Campo | Qué es |
|---|---|
| hora (al inicio) | ISO 8601 en UTC, con fecha y milisegundos |
| `event=request` | Marca de la línea de acceso, para filtrarla |
| `api` | `juanpa`, `mari` o `pipe` |
| `traceId` | El `X-Trace-Id` de la petición |
| `method`, `route` | Método y ruta |
| `status` | Código HTTP de la respuesta |
| `ms` | Duración de la petición en milisegundos |

Por qué importa: de estas líneas salen **la latencia y los errores por minuto** (las métricas del flujo) y la **cronología de un incidente** (el informe de seguridad). Además, con hora en UTC y el mismo `traceId`, se puede reconstruir una petición que pasó por las tres nubes.

Ejemplos de cómo escribirla (cada uno lo adapta a su lenguaje):

- **Spring (Juanpa):** `RequestLoggingFilter`, con el patrón de log en `application.yml`.
- **Node/Express (si aplica):** un `app.use` que mide el tiempo y, al terminar la respuesta (`res.on('finish')`), escribe la línea con `console.log`.
- **Python/FastAPI (si aplica):** un middleware `@app.middleware('http')` que mide el tiempo y escribe la línea.

## 7. Métricas

Hay **tres niveles**, y cada uno responde a una pregunta distinta:

| Nivel | Qué mide | Cómo se obtiene | Para qué sirve |
|---|---|---|---|
| **Extremo a extremo** (la métrica objetivo) | Latencia p95 y tasa de éxito del flujo, vistas por el cliente | **k6**: `--summary-export` y [`caos/gate/evaluar.py`](gate/evaluar.py) | Es la métrica que declara cada hipótesis y la que se compara entre la ronda 0 y la ronda 1 |
| **De cada API** | Latencia por ruta y conteo por código de estado, vistos por el servidor | Las **líneas de acceso** del log (consultas en [`tablero/consultas.kql`](tablero/consultas.kql)) o un endpoint Prometheus | El diagnóstico: en qué API y en qué momento se degrada |
| **De la plataforma** | CPU, memoria, réplicas y reinicios | El monitoreo nativo de cada nube (Azure Monitor, Cloud Monitoring, CloudWatch) | Explicar agotamientos de recurso y reinicios |

**Cómo se hacen:** no hay que instalar nada para el nivel 1 (k6 ya está). El nivel 2 sale de las líneas de acceso del requisito 6. El nivel 3 lo da cada nube. El **tablero** muestra a la vez la **ventana de inyección de la falla** y la métrica de las dos rondas, con las consultas de `tablero/` (Azure) y los equivalentes de cada nube.
