# Trading Journal API

Proyecto semestral de la materia de Desarrollo de Software — **Actividad 1 (primera entrega)**.

API RESTful para el registro y seguimiento de operaciones de trading (journal), con checklist de confluencias y métricas por trader.

## Entidades

- **Trader**: usuario que registra sus operaciones (nombre, usuario, correo, fondos disponibles). Sin autenticación en esta entrega.
- **Strategy**: checklist de 7 confluencias de trading (HTF PD Array, IFVG, CISD, seguí mis reglas, continuación, reversal, riesgo correcto). Cada Trade crea su propia Strategy — nunca se reutiliza una existente.
- **Trade**: operación individual (contrato, tamaño, dirección, entrada/salida, P&L), asociada a un Trader y a su propia Strategy (relación obligatoria).

## API

13 endpoints REST: CRUD completo de Trader y Trade, lectura de Strategy, y un endpoint de búsqueda que usa el **verbo HTTP `QUERY` literal** (no un POST disfrazado) — `QUERY /api/trades/query`, filtra por trader y/o resultado (ganadora/perdedora).

Colección de Postman completa, con los 2 ambientes, en [`postman/`](./postman).

## Stack

- Java 21 (Temurin) + Spring Boot 4.1.0
- Maven
- PostgreSQL 17
- Docker / Docker Compose (build multi-stage)
- JUnit 5 + Mockito + JaCoCo — 51 pruebas, 97.1% de cobertura de líneas
- GitHub Actions (CI/CD) — 2 pipelines independientes

## Ambientes

2 ambientes locales completamente independientes — BD, puerto y red propios cada uno, pueden correr al mismo tiempo:

| Ambiente | Rama trigger | Base de datos | Puerto app | Puerto Postgres |
|---|---|---|---|---|
| Pruebas | `develop` | `journal_test` | `8080` | `5433` |
| Producción | `main` | `journal_prod` | `8081` | `5434` |

## Cómo correr el proyecto

### Con Docker (recomendado)

```bash
docker compose -f docker-compose.yml up --build -d       # Ambiente Pruebas
docker compose -f docker-compose.prod.yml up --build -d  # Ambiente Producción
```

Los dos pueden correr a la vez. Probar con:

```bash
curl http://localhost:8080/api/traders
curl http://localhost:8081/api/traders
```

Para apagar:

```bash
docker compose -f docker-compose.yml down
docker compose -f docker-compose.prod.yml down
```

### Local, sin Docker

```bash
mvn spring-boot:run
```

(Requiere una instancia de PostgreSQL disponible y las variables de entorno `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`.)

## Pruebas y cobertura

```bash
mvn clean verify
```

Corre las 51 pruebas y valida el gate de cobertura de JaCoCo — 60% con el perfil `test-env`, 85% con `prod-env` (activo por defecto). Si alguna prueba falla o la cobertura no alcanza el mínimo, el build se detiene con `BUILD FAILURE`.

## CI/CD

2 pipelines en GitHub Actions ([`.github/workflows/`](./.github/workflows)): build → pruebas → quality gate de cobertura → publicación de la imagen Docker en GitHub Container Registry. El pipeline nunca publica nada si algún paso anterior falla.

## Convención de commits

Este repo usa [GitMoji](https://gitmoji.dev/) en los mensajes de commit — muchos commits pequeños, cada uno enfocado en un solo cambio.
