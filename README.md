# Trading Journal API

Proyecto semestral de la materia de Desarrollo de Software. **Actividad 2 (Entrega 2, Énfasis DevOps)** — arquitectura multicloud, sobre la base de la Actividad 1 (API RESTful de registro y seguimiento de operaciones de trading).

Grupo cerrado de 3 integrantes, cada uno con su propio repositorio y su propia nube de cómputo, reutilizando entidades entre las 3 API en tiempo real (sin datos quemados):

| Integrante | Repositorio | Nube de cómputo | Componente transversal propio |
|---|---|---|---|
| **Juan Pablo (este repo)** | `JuanPa28/trading-journal-api` | **Azure (AKS)** — cómputo movido de OCI por falta de capacidad gratuita, datos y storage se quedaron en OCI | **Object Storage** (OCI) |
| Mariana Pineda ("Mari") | `MarianaPineda0/StatsNostra` | GCP (GKE) | **Caché distribuida** |
| Juan Felipe ("Pipe") | `jfpa11100/ecommerce-api` | AWS | **MS Orquestador + cola de mensajería** |

## Diagrama de arquitectura

_Pendiente: diagrama compartido del grupo._

**Por qué el cómputo de Juan Pablo está en Azure y no en OCI (la nube originalmente asignada):** OCI se quedó sin capacidad gratuita de máquinas ARM para los nodos de Kubernetes (OKE) — más de 300 intentos automáticos fallidos durante varios días, un problema real de disponibilidad de Oracle, no un error de configuración. El profesor aprobó mover solo el cómputo del clúster a Azure (AKS), dejando la base de datos (Oracle Autonomous DB) y el Object Storage exactamente donde ya estaban funcionando y probados en OCI. El clúster de OKE en OCI se dejó creado (control plane activo, sin nodos) por si Oracle libera capacidad más adelante — el pipeline lo sigue intentando en cada despliegue, sin bloquear nada.

## Entidades propias

- **Trader**: usuario que registra sus operaciones (nombre, usuario, correo, fondos disponibles). Sin autenticación en esta entrega.
- **Strategy**: checklist de 7 confluencias de trading (HTF PD Array, IFVG, CISD, seguí mis reglas, continuación, reversal, riesgo correcto). Cada Trade crea su propia Strategy — nunca se reutiliza una existente.
- **Trade**: operación individual (contrato, tamaño, dirección, entrada/salida, P&L), asociada a un Trader y a su propia Strategy (relación obligatoria).

## API

- **`/api/...`** — CRUD original de la Actividad 1 (13 endpoints), incluye el verbo HTTP `QUERY` literal para búsqueda (`QUERY /api/trades/query`).
- **`/api/v2/...`** — capa nueva de la Entrega 2:
  - CRUD completo espejado para `Trader`, `Strategy` y `Trade` (`TraderV2Controller`, `StrategyV2Controller`, `TradeV2Controller`); el `GetById` de cada una viene enriquecido con **una entidad de Mari** (`Partido` o `Predicción`, elegida al azar de su lista completa — "GET RANDOM") y **una de Pipe** (`Commerce` o `Product`, mismo patrón), obtenidas **en tiempo real por HTTP**, nunca copiadas ni quemadas.
  - `POST`/`GET /api/v2/attachments` — el componente de Object Storage.
  - Cada petición entrante genera o recibe un `X-Trace-Id`, que queda en los logs (MDC) y se reenvía a Mari y a Pipe cuando este repo los llama — permite seguir una misma petición a través de las 3 nubes.

Colección de Postman completa en [`postman/`](./postman).

## Stack

- Java 21 (Temurin) + Spring Boot 4.1.0
- Maven
- **Oracle Autonomous Database** (producción, walletless TLS) / PostgreSQL 17 (local, Docker)
- **Kubernetes** (AKS) — Deployment (2-4 réplicas vía HPA), Service LoadBalancer, ConfigMap, 3 Secrets, liveness/readiness probes, requests/limits de recursos
- **OCI Object Storage** — SDK oficial de Java
- **Observabilidad**: Micrometer + Prometheus (`/actuator/prometheus`, histograma de latencia p50/p95 por endpoint) + OpenTelemetry (trazas distribuidas, exportador a logs hoy, listo para Grafana Cloud con una sola variable de entorno) + monitoreo nativo de OCI (métricas + logs del bucket)
- Docker / Docker Compose (build multi-stage)
- JUnit 5 + Mockito + JaCoCo — 68 pruebas (el reporte de cobertura se genera, sin gate mínimo obligatorio)
- GitHub Actions (CI/CD) — build → test → build & push de imagen → despliegue automático a Kubernetes

## Cómo correr el proyecto

### Local (Docker, contra Postgres — para desarrollo)

```bash
docker compose -f docker-compose.yml up --build -d       # Ambiente Pruebas, puerto 8080
docker compose -f docker-compose.prod.yml up --build -d  # Ambiente Producción, puerto 8081
```

### Producción real (Azure AKS + Oracle + OCI)

```bash
az login
az aks get-credentials --resource-group trading-journal-rg-eastus --name trading-journal-aks
kubectl get svc trading-journal-api   # columna EXTERNAL-IP es la URL pública actual
```

Ver [`k8s/README.md`](./k8s/README.md) para la lista completa de Secrets y el paso a paso de despliegue manual.

## Pruebas

```bash
mvn clean verify
```

Corre las 68 pruebas y genera el reporte de cobertura de JaCoCo (`target/site/jacoco`). Si alguna prueba falla, el build se detiene con `BUILD FAILURE` antes de publicar cualquier imagen — no hay un porcentaje mínimo de cobertura exigido en esta entrega.

## CI/CD

**No es un requisito de esta entrega** (la rúbrica de Entrega 2 no lo pide explícitamente), pero se mantuvo porque sigue siendo la forma más confiable de desplegar sin pasos manuales — y porque el componente transversal (Object Storage) sí debe "versionarse... con su propio contenedor, su pipeline de despliegue y su documentación de uso" según la rúbrica, y al vivir dentro de esta misma API, hereda el mismo pipeline.

Dos workflows en GitHub Actions ([`.github/workflows/`](./.github/workflows)):

- **`prod-pipeline.yml`**, en cada push a `main`: build + pruebas → construye la imagen Docker y la publica en GHCR y en OCIR → intenta desplegar a OKE (OCI, `continue-on-error`, hoy sin nodos) → despliega a AKS (Azure), el despliegue real, confirmado funcionando en cada push.
- **`test-pipeline.yml`**, en cada push a `develop`: build + pruebas → publica la imagen en GHCR (sin desplegarla a ningún clúster).

## Convención de commits

GitMoji ([gitmoji.dev](https://gitmoji.dev/)) en cada commit — muchos commits pequeños, cada uno enfocado en un solo cambio. Versionado semántico con tags de Git (`v1.0.0` al cierre de la Actividad 1; `v2.0.0` al cierre de esta entrega).
