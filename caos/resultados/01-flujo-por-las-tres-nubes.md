# El flujo recorre las tres nubes (13 de noviembre de 2026)

`GET /api/v2/trades/5540` sobre el coordinador (Azure) con `X-Trace-Id: flujo-3-nubes-001`.

| Respuesta | Valor |
|---|---|
| Código y tiempo | 200 en 1 167 ms (primera petición tras desplegar la versión `5.0.0-rc.1`) |
| Trace id devuelto | `flujo-3-nubes-001` |
| `partido` y `prediccion` | recibidos de la API de Mari (Google Cloud, GKE) |
| `commerce` y `product` | recibidos de la API de Pipe (AWS) |

## Estado de las APIs del flujo

| Integrante | Nube | Salud | Rutas del flujo | `X-App-Name` | Eco de `X-Trace-Id` |
|---|---|---|---|---|---|
| Juanpa (coordinador) | Azure Container Apps | `/actuator/health` | `/api/v2/trades/{id}` | sí (`juanpa`) | sí |
| Mari | Google Cloud (GKE) | `/health/ready` | `/api/v1/partidos`, `/api/v1/predicciones` | sí | sí |
| Pipe | AWS | `/health` | `/api/v1/commerce`, `/api/v1/products` | pendiente | pendiente |

El coordinador llama en cadena a cuatro rutas (dos de cada compañero) y reenvía el `X-Trace-Id` en cada llamada. La verificación de que ese identificador aparece en los logs de Mari y de Pipe queda a cargo de cada uno.
