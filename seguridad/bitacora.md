# Bitácora de los experimentos de seguridad (trading-journal)

| Hora (Colombia) | Quién | Acción | Resultado |
|---|---|---|---|
| 12:57:25 | Juan Pablo | Ronda 0: listSecrets sobre api-coordinador | 4 secretos listados (valores descartados) a las 2026-11-16T17:57:23Z |
| 12:58:14 | Juan Pablo | Preparación: creo los buckets de prueba y de copia aislada (sin versionado) | listos |
| 12:58:16 | Juan Pablo | Poblar: subo los objetos sintéticos | 3 objetos |
| 13:00:21 | Juan Pablo | Ronda 0: medición | no hubo alerta en la ventana de espera |
| 13:00:54 | Juan Pablo | Ronda 0: sobrescribo y borro los objetos del bucket | 3 objetos afectados a las 2026-11-16T18:00:54Z |
| 13:00:56 | Juan Pablo | Ronda 0: restauración | 0/3 objetos recuperados (ninguno) |
| 13:00:59 | Juan Pablo | Ronda 0: medición | {"ronda": 0, "objetos_total": 3, "objetos_recuperados": 0, "integros": 0, "rto_s": null, "rpo": "pérdida total: no había copia"} |
| 13:01:28 | Juan Pablo | Mejoras: versionado del bucket y copia a un bucket aislado | aplicadas |
| 13:01:30 | Juan Pablo | Ronda 1: sobrescribo y borro los objetos del bucket | 3 objetos afectados a las 2026-11-16T18:01:30Z |
| 13:02:24 | Juan Pablo | Ronda 1: restauración | 3/3 objetos recuperados (versión anterior) |
| 13:02:25 | Juan Pablo | Ronda 1: el primer intento de restauración falló por la fecha vacía del listado de versiones de Oracle; sin pérdida de datos (versionado activo). Corrijo el script y repito desde el ataque | se repite la ronda 1 para un RTO limpio |
| 13:02:27 | Juan Pablo | Ronda 1: sobrescribo y borro los objetos del bucket | 3 objetos afectados a las 2026-11-16T18:02:27Z |
| 13:02:30 | Juan Pablo | Ronda 1: restauración | 3/3 objetos recuperados (versión anterior) |
| 13:02:32 | Juan Pablo | Ronda 1: medición | {"ronda": 1, "objetos_total": 3, "objetos_recuperados": 3, "integros": 3, "rto_s": 3, "rpo": "0 objetos perdidos (última versión íntegra)"} |
| 13:03:01 | Juan Pablo | Ronda 2: sobrescribo y borro los objetos del bucket | 3 objetos afectados a las 2026-11-16T18:03:00Z |
| 13:03:03 | Juan Pablo | Ronda 2: restauración | 3/3 objetos recuperados (copia aislada) |
| 13:03:06 | Juan Pablo | Ronda 2: medición | {"ronda": 2, "objetos_total": 3, "objetos_recuperados": 3, "integros": 3, "rto_s": 3, "rpo": "0 objetos perdidos (última versión íntegra)"} |
