# Trading Journal API

Proyecto semestral de la materia de Desarrollo de Software — **Actividad 1 (primera entrega)**.

API RESTful para el registro y seguimiento de operaciones de trading (journal), con estrategias asociadas y métricas por trader.

## Entidades

- **Trader**: usuario que registra sus operaciones.
- **Strategy**: estrategia de trading (reglas, tags) que puede aplicarse a varias operaciones.
- **Trade**: operación individual (par, dirección, entrada/salida, resultado), asociada a un Trader y opcionalmente a una Strategy.

## Stack

- Java 21 (Temurin) + Spring Boot 4.1.0
- Maven
- PostgreSQL 17
- Docker / Docker Compose
- JUnit + Mockito + JaCoCo
- GitHub Actions (CI/CD)

## Convención de commits

Este repo usa [GitMoji](https://gitmoji.dev/) en los mensajes de commit.

## Ambientes

| Ambiente | Rama trigger | Base de datos |
|---|---|---|
| Pruebas | `develop` | `journal_test` |
| Producción | `main` | `journal_prod` |

## Cómo correr localmente

```bash
mvn spring-boot:run
```

(Requiere una instancia de PostgreSQL disponible — ver `docker-compose.yml`.)
