# Inventario de controles por capa (trading-journal: coordinador en Azure y datos en Oracle)

Cada control tiene su estado **tal como está configurado hoy** (la base de la ronda 0), la señal que produce y su correspondencia con MITRE D3FEND (identificadores comprobados en d3fend.mitre.org) y con las normas de la trazabilidad: ISO/IEC 27001:2022 Anexo A, NIST SP 800-53 y NIST SP 800-207. Los huecos están marcados porque son los que cierran las mejoras de las rondas. Mari y Pipe llenan las filas de sus nubes en sus repositorios; el inventario de StatsNostra está en su carpeta `seguridad/`.

## Identidad

| Control | Dónde | Estado hoy | Señal | D3FEND / normas |
|---|---|---|---|---|
| Contraseña de la base como secreto de la Container App, no en el código | `caos/azure/deploy.py` | Activo | Registro de actividad de Azure | ISO 5.17; NIST IA-5 |
| Usuario de la base con privilegio mínimo | secreto `db-password` | **Hueco**: la API usa `ADMIN` (privilegio máximo); quien liste los secretos se lleva la llave de toda la base | Ninguna | D3-UAP (User Account Permissions); ISO 8.2; NIST AC-6 |
| Rotación de la credencial | secreto `db-password` | **Hueco**: no hay rotación configurada | Ninguna | D3-CRO (Credential Rotation); ISO 5.17; NIST IA-5 |
| Detección de acceso a secretos | Registro de actividad de Azure | **Hueco**: la operación `listSecrets` no se recoge en `log-caos` ni hay alerta | Ninguna hoy; con la mejora, `AzureActivity` y una alerta | ISO 8.15, 8.16; NIST AU-6, SI-4 |
| Despliegue de la imagen sin llaves de nube en el repositorio | GitHub Actions y GHCR | Activo | Registros de GitHub | ISO 8.4; NIST CM-5 |

## Red

| Control | Dónde | Estado hoy | Señal | D3FEND / normas |
|---|---|---|---|---|
| Entrada solo por HTTPS al coordinador | Container Apps (ingress externo) | Activo | Registros de consola de la app | D3-ITF (Inbound Traffic Filtering); ISO 8.20 |
| Lista de IP permitidas de la base de datos Oracle | `canary/db/database.tf` | **Hueco**: abierta a `0.0.0.0/0`; solo protegida por contraseña y TLS (sin mTLS) | Ninguna | D3-ITF; ISO 8.20, 8.22; NIST SC-7; NIST SP 800-207 |
| Salida del coordinador hacia las otras nubes | Container Apps | **Hueco**: sin filtrado de salida; la API puede llamar a cualquier destino | Registros de la app | ISO 8.23; NIST SC-7 |

## Carga de trabajo

| Control | Dónde | Estado hoy | Señal | D3FEND / normas |
|---|---|---|---|---|
| Imagen mínima de ejecución (JRE de Alpine, build en dos etapas) | `Dockerfile` | Activo | | ISO 8.9; NIST CM-7 |
| Proceso sin root | `Dockerfile` | **Hueco**: no define `USER`; el proceso corre como root | Ninguna | D3-ACH (Application Configuration Hardening); ISO 8.9; NIST CM-6 |
| Endpoints de administración expuestos | `application.yml` | **Hueco menor**: `/actuator/prometheus` está abierto en el ingress público (solo se exponen `health` y `prometheus`) | Registros de la app | D3-ACH; ISO 8.9; NIST CM-7 |
| Pruebas automáticas y análisis en CI | GitHub Actions | Activo (102 pruebas) | Resultado del pipeline | ISO 8.28; NIST SA-11 |

## Datos

| Control | Dónde | Estado hoy | Señal | D3FEND / normas |
|---|---|---|---|---|
| Datos sintéticos solamente | todo el proyecto | Activo | | ISO 8.11 |
| Cifrado en reposo en Oracle (Autonomous Database y Object Storage) | Oracle Cloud | Activo (predeterminado del proveedor) | | ISO 8.24; NIST SC-28 |
| Versionado del bucket de adjuntos | `terraform/storage.tf` | **Hueco**: `versioning = "Disabled"`; un borrado o una sobrescritura no se puede deshacer | Ninguna | D3-RO (Restore Object); ISO 8.13; NIST CP-9 |
| Copia aislada de los objetos | Oracle Object Storage | **Hueco**: no hay copia en otro bucket ni otra cuenta | Ninguna | D3-RO; ISO 8.13; NIST CP-9, CP-6 |
| Bucket sin acceso público | `terraform/storage.tf` | Activo (`NoPublicAccess`) | Registros de auditoría de Oracle | D3-ITF; ISO 8.3 |

## Detección y respuesta

| Control | Dónde | Estado hoy | Señal | D3FEND / normas |
|---|---|---|---|---|
| Línea de acceso por petición con trace id, estado y duración | `RequestLoggingFilter` | Activo | Log Analytics, `ContainerAppConsoleLogs_CL` | ISO 8.15; NIST AU-3 |
| Métricas del coordinador en Log Analytics y en Grafana Cloud | Workbook y OTLP | Activo | Tablero | ISO 8.16; NIST SI-4 |
| Alerta sobre acciones de administración (registro de actividad) | Azure Monitor | **Hueco**: no hay regla ni grupo de acciones | Ninguna hoy | ISO 5.24, 8.16; NIST IR-4, SI-4 |
| Detección de borrado masivo de objetos | Oracle Object Storage | **Hueco**: no hay eventos ni alarmas sobre el bucket | Ninguna | ISO 8.16; NIST SI-4 |
| Reglas de detección como código con casos positivos y negativos | `seguridad/identidad/deteccion.kql` | Activo (7 casos) | Prueba `probar_deteccion.py` | ISO 5.24; NIST SI-4 |

## Resumen de huecos que cierran las rondas

| Hueco | Ronda 1 lo cierra con |
|---|---|
| Sin detección de `listSecrets` | Diagnóstico del registro de actividad + regla de alerta + correo (experimento 1, mejora 1) |
| API con usuario `ADMIN` | Usuario `TJ_APP` con privilegios mínimos (experimento 1, mejora 2) |
| Bucket sin versionado ni copia | Versionado + copia aislada (experimento 4) |
| Base abierta a `0.0.0.0/0`, proceso como root, `/actuator/prometheus` público | Quedan **fuera** del alcance de las rondas y se documentan como riesgos residuales |
