# Inventario de controles por capa

Cada control tiene un propietario, la señal que debería producir y su correspondencia con MITRE D3FEND (los identificadores se verifican en d3fend.mitre.org antes de citarlos en el informe). La columna *Estado hoy* describe el control **tal como está configurado**, sin mejoras: es la base de la ronda 0.

## Capa de identidad (coordinador, Juan)

| Control | Estado hoy | Señal esperada | D3FEND (por verificar) |
|---|---|---|---|
| Secreto de la contraseña de la base de datos, guardado como secreto de la Container App | El usuario es `ADMIN` (privilegio máximo); sin rotación | Registro de actividad de Azure al listar secretos | Credential Rotation · User Account Permissions |
| Lista de IP permitidas de la base de datos | Abierta a cualquier origen (`0.0.0.0/0`), protegida por TLS y contraseña | Ninguna hoy | Inbound Traffic Filtering |

## Capa de red, de carga de trabajo, de datos y de detección y respuesta

*Pendiente: lo completa cada integrante para su nube (Mari: GKE y NetworkPolicy; Pipe: AWS y CloudWatch; datos: bucket de Oracle).*
