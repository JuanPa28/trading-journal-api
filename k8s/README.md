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
```

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
