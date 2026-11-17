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
| 13:12:41 | Juan Pablo | Ronda 0: medición | no hubo alerta en la ventana de espera |
| 13:22:42 | Juan Pablo | Mejora 1a: configuración de diagnóstico del registro de actividad hacia log-caos | creada |
| 13:22:45 | Juan Pablo | Mejora 1b: grupo de acciones con correo | creado |
| 13:22:57 | Juan Pablo | Mejora 1c: regla de alerta sobre deteccion.kql (cada 5 min, ventana de 10 min) | creada |
| 13:24 (aprox.) | Juan Pablo | Mejora 2: creo TJ_APP con lectura y escritura de las 3 tablas y 3 secuencias, cambio el secreto db-password y DB_USERNAME (registro retroactivo a partir de las revisiones de la Container App) | la revisión 19 no arranca: Hibernate no valida un esquema ajeno («missing table»); la API sigue sirviendo con la revisión anterior |
| 13:33 (aprox.) | Juan Pablo | Mejora 2: ddl-auto=none y esquema por variable de entorno (registro retroactivo) | ORA-00942 en las consultas: Spring no convierte la variable en hibernate.default_schema |
| 13:35 (aprox.) | Juan Pablo | Mejora 2: esquema por SQL de inicio de Hikari (registro retroactivo) | la revisión 22 no arranca: az en Windows mutila un valor con espacios |
| 13:40 (aprox.) | Juan Pablo | Mejora 2: creo un trigger de inicio de sesión que fija el esquema ADMIN para TJ_APP y retiro la variable (registro retroactivo) | API sana como TJ_APP; el flujo trae los datos de las tres APIs |
| 13:49:40 | Juan Pablo | Ronda 1: listSecrets sobre api-coordinador | 4 secretos listados (valores descartados) a las 2026-11-16T18:49:38Z |
| 13:59:03 | Juan Pablo | Ronda 1: medición | alerta a los 522 s |
| 14:04:31 | Juan Pablo | Reversión: elimino regla, grupo de acciones y diagnóstico | estado de la ronda 0 |
| 14:06:15 | Juan Pablo | Reversión del experimento 1: API de vuelta a ADMIN; elimino TJ_APP y su trigger (esperé a que cerraran las sesiones de la revisión anterior) | estado de la ronda 0 verificado: sin regla, sin grupo de acciones, sin diagnóstico, sin TJ_APP |
| 16:36:32 | Juan Pablo | Experimento 4, repetición con el archivo real de StatsNostra (statsnostra-prediccion.json, 270 bytes, sha256 4ccc709d…7915 verificado): recreo los buckets para volver a la ronda 0 | se repiten las rondas 0, 1 y 2; la primera ejecución (objeto por defecto) queda en el historial de git |
| 16:36:39 | Juan Pablo | Limpieza: elimino los buckets de prueba | eliminados |
| 16:36:40 | Juan Pablo | Preparación: creo los buckets de prueba y de copia aislada (sin versionado) | listos |
| 16:36:42 | Juan Pablo | Poblar: subo los objetos sintéticos | 3 objetos |
| 16:36:45 | Juan Pablo | Ronda 0: sobrescribo y borro los objetos del bucket | 3 objetos afectados a las 2026-11-17T21:36:44Z |
| 16:36:47 | Juan Pablo | Ronda 0: restauración | 0/3 objetos recuperados (ninguno) |
| 16:36:48 | Juan Pablo | Ronda 0: medición | {"ronda": 0, "umbral_rto_s": 600, "objetos_total": 3, "objetos_recuperados": 0, "integros": 0, "rto_s": null, "rpo": "pérdida total: no había copia"} |
| 16:36:53 | Juan Pablo | Mejoras: versionado del bucket y copia a un bucket aislado | aplicadas |
| 16:36:55 | Juan Pablo | Ronda 1: sobrescribo y borro los objetos del bucket | 3 objetos afectados a las 2026-11-17T21:36:55Z |
| 16:36:57 | Juan Pablo | Ronda 1: restauración | 3/3 objetos recuperados (versión anterior) |
| 16:36:59 | Juan Pablo | Ronda 1: medición | {"ronda": 1, "umbral_rto_s": 600, "objetos_total": 3, "objetos_recuperados": 3, "integros": 3, "rto_s": 2, "rpo": "0 objetos perdidos (última versión íntegra)"} |
| 16:37:02 | Juan Pablo | Ronda 2: sobrescribo y borro los objetos del bucket | 3 objetos afectados a las 2026-11-17T21:37:01Z |
| 16:37:04 | Juan Pablo | Ronda 2: restauración | 3/3 objetos recuperados (copia aislada) |
| 16:37:06 | Juan Pablo | Ronda 2: medición | {"ronda": 2, "umbral_rto_s": 600, "objetos_total": 3, "objetos_recuperados": 3, "integros": 3, "rto_s": 3, "rpo": "0 objetos perdidos (última versión íntegra)"} |
