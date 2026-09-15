# Despliegue en OKE

Los manifiestos de este directorio (`configmap.yaml`, `deployment.yaml`, `service.yaml`) son seguros de commitear — no contienen secretos.

Las credenciales de la base de datos y del registro de imágenes (OCIR) **no se committean**. Se crean directamente en el clúster con estos comandos, una sola vez:

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
```

Después de crear los secrets, aplicar los manifiestos:

```bash
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
```

Verificar:

```bash
kubectl get pods
kubectl get svc trading-journal-api   # EXTERNAL-IP es la URL pública
```
