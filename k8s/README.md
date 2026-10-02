> **Histórico de la Entrega 2.** Estos manifiestos describen el despliegue en Azure AKS que se eliminó tras la sustentación (ver el README principal). Se conservan como evidencia; no hay ningún clúster activo.

# Despliegue — arquitectura de dos nubes

**El cómputo (el clúster de Kubernetes) corre en Azure (AKS). La base de datos y el Object Storage siguen en OCI**, donde ya estaban funcionando y probados. La API se conecta a ellos por internet, exactamente igual que lo haría desde cualquier otro lugar — no hay nada especial que dependa de estar "dentro" de OCI.

## Por qué está así

OCI se quedó sin capacidad gratuita de máquinas ARM para los nodos del clúster (OKE) — un problema de disponibilidad de Oracle, documentado con cientos de intentos automáticos fallidos, no un error de configuración nuestro. El profesor aprobó mover el cómputo a Azure (AKS) manteniendo el resto de la arquitectura en OCI tal como estaba. Esto es honesto y explicable en la sustentación: "el cómputo corre en Azure, los datos siguen en OCI donde ya los teníamos probados."

Los manifiestos de Kubernetes (`configmap.yaml`, `deployment.yaml`, `service.yaml`, `hpa.yaml`) son **exactamente los mismos** para cualquiera de las dos nubes — Kubernetes es Kubernetes, no hay nada específico de OCI o Azure en ellos. Solo cambia dónde vive el clúster.

## Secrets (no se committean, se crean directo en el clúster)

```bash
# Credenciales para que el clúster pueda descargar la imagen de OCIR
kubectl create secret docker-registry ocir-credentials \
  --docker-server=bog.ocir.io \
  --docker-username='<tenancy-namespace>/Default/<tu-correo>' \
  --docker-password='<tu-auth-token-de-OCIR>'

# Credenciales de conexión a Oracle Autonomous Database
kubectl create secret generic trading-journal-db-credentials \
  --from-literal=DB_URL='jdbc:oracle:thin:@(description= (retry_count=20)(retry_delay=3)(address=(protocol=tcps)(port=1521)(host=adb.sa-bogota-1.oraclecloud.com))(connect_data=(service_name=<tu-service-name>.adb.oraclecloud.com))(security=(ssl_server_dn_match=yes)))' \
  --from-literal=DB_USERNAME='ADMIN' \
  --from-literal=DB_PASSWORD='<password-del-admin-de-la-BD>'

# Credenciales de la llave API de OCI, para que el componente de Object
# Storage pueda guardar/leer adjuntos (misma llave API que ya usa el resto
# del proyecto — ver "My profile → API keys" en la consola de OCI)
kubectl create secret generic trading-journal-oci-credentials \
  --from-literal=OCI_TENANCY_ID='<tu-tenancy-ocid>' \
  --from-literal=OCI_USER_ID='<tu-user-ocid>' \
  --from-literal=OCI_FINGERPRINT='<fingerprint-de-la-llave-api>' \
  --from-file=OCI_PRIVATE_KEY=/ruta/a/tu/oci_api_key.pem

# Credenciales de Grafana Cloud (OTLP) — CONECTADO (2026-09-23), verificado
# en vivo: sin crash, sin 401, un solo exportador de trazas activo (no
# doble). El valor de los *_AUTH_HEADER es "Basic " + el string que Grafana
# llama "usuario" en su página de conexión OTLP — ese string YA es el
# base64 de "instanceId:token", no hay que codificarlo de nuevo.
#
# El nombre de la primera variable (MANAGEMENT_OPENTELEMETRY_..._ENDPOINT)
# no es arbitrario: es el binding automático de Spring hacia la propiedad
# management.opentelemetry.tracing.export.otlp.endpoint — la ruta correcta,
# no deprecada, de Spring Boot 4.1 (management.otlp.tracing.* SÍ existe
# pero nunca manda el header de autenticación, aunque parezca conectar).
# Deliberadamente no tiene default vacío en application.yml: esa ruta
# nueva no se queda inerte con "" como sí hace la vieja, crashea el pod
# con "Invalid endpoint" — por eso solo llega con este secret.
kubectl create secret generic trading-journal-grafana-credentials \
  --from-literal=MANAGEMENT_OPENTELEMETRY_TRACING_EXPORT_OTLP_ENDPOINT='https://otlp-gateway-prod-us-east-3.grafana.net/otlp/v1/traces' \
  --from-literal=MANAGEMENT_OPENTELEMETRY_TRACING_EXPORT_OTLP_HEADERS_AUTHORIZATION='Basic <el-string-que-dio-Pipe>' \
  --from-literal=OTEL_EXPORTER_OTLP_METRICS_ENABLED='true' \
  --from-literal=OTEL_EXPORTER_OTLP_METRICS_ENDPOINT='https://otlp-gateway-prod-us-east-3.grafana.net/otlp/v1/metrics' \
  --from-literal=GRAFANA_OTLP_AUTH_HEADER='Basic <el-string-que-dio-Pipe>' \
  --from-literal=MANAGEMENT_OPENTELEMETRY_LOGGING_EXPORT_OTLP_ENDPOINT='https://otlp-gateway-prod-us-east-3.grafana.net/otlp/v1/logs' \
  --from-literal=MANAGEMENT_OPENTELEMETRY_LOGGING_EXPORT_OTLP_HEADERS_AUTHORIZATION='Basic <el-string-que-dio-Pipe>'
```

Los dos últimos son para logs — mismo gateway OTLP, mismo token, solo cambia
la ruta final (`/v1/logs` en vez de `/v1/traces`). Igual que con tracing,
Spring Boot 4.1 sí crashea con endpoint vacío en esta ruta nueva (confirmado
por el mismo patrón de bug en tracing), así que estas dos variables
**tampoco** tienen default vacío en `application.yml` — solo existen si este
Secret las trae. A diferencia de las trazas, acá no hizo falta ningún bean
manual: Spring Boot arma el `SdkLoggerProvider` y el exportador OTLP solo con
la propiedad presente (`spring-boot-opentelemetry`, módulo nuevo en 4.1). Lo
que sí hubo que cablear a mano fue el puente Logback → OpenTelemetry: el
appender `OpenTelemetryAppender` (declarado en `logback-spring.xml`) no tiene
forma de recibir el bean `OpenTelemetry` de Spring en el propio XML, porque
Logback arranca antes que el contexto — se instala en código con
`OpenTelemetryAppender.install(...)` dentro de `OtelLogAppenderInitializer`,
en el evento `ApplicationReadyEvent`.

## Aplicar y verificar

```bash
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
kubectl apply -f k8s/hpa.yaml

kubectl get pods
kubectl get svc trading-journal-api   # EXTERNAL-IP es la URL pública
kubectl get hpa                        # columna TARGETS muestra el % de CPU actual vs el objetivo
```

## Conectarte al clúster de AKS

```bash
az login
az aks get-credentials --resource-group trading-journal-rg-eastus --name trading-journal-aks
```

## Escalamiento automático (HPA)

`hpa.yaml` mantiene entre 2 y 4 réplicas, escalando cuando el uso de CPU supera el 70%. AKS trae el Metrics Server instalado por defecto (a diferencia de OKE, donde había que activarlo aparte) — el HPA funciona sin configuración adicional.

Para demostrar el escalamiento en vivo: generar carga contra el Service (ej. con `hey` o `ab`) y observar con `kubectl get hpa -w` cómo sube el número de réplicas.

## Nota sobre OKE (OCI)

El clúster de OKE (`trading-journal-cluster`) se quedó creado en OCI, con el control plane activo pero sin nodos (por la falta de capacidad). El pipeline de CI/CD todavía intenta desplegar ahí también (con `continue-on-error`) — si algún día Oracle libera capacidad, se activaría solo, como un plan B gratuito. No es necesario borrarlo ni gestionarlo mientras tanto.
