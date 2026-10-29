#!/usr/bin/env bash
# Prepara (una sola vez, es idempotente) lo que necesita el despliegue canary en
# Google Cloud: la cuenta de servicio con la que corre el API y los tres
# secretos que lee desde Secret Manager. Secret Manager y Cloud Run entran en la
# capa gratuita de Google Cloud con este uso.
#
# Requisitos: gcloud con sesión iniciada y el módulo canary/db ya aplicado.
# Los valores salen de canary/db/db.auto.tfvars y de la salida de Terraform;
# nunca se imprimen.
set -euo pipefail

PROJECT="${GCP_PROJECT_ID:-trading-journal-canary}"
RUNTIME_SA="api-runtime"
RUNTIME_EMAIL="${RUNTIME_SA}@${PROJECT}.iam.gserviceaccount.com"
DB_DIR="$(cd "$(dirname "$0")/../db" && pwd)"
TFVARS="$DB_DIR/db.auto.tfvars"

tfvar() { grep -E "^$1\b" "$TFVARS" | head -1 | sed -E 's/^[^=]*=[[:space:]]*"?([^"]*)"?[[:space:]]*$/\1/'; }

echo "Proyecto: $PROJECT"
gcloud services enable secretmanager.googleapis.com run.googleapis.com --project "$PROJECT" >/dev/null

# Cuenta de servicio con la que corre el API (solo puede leer sus secretos).
if ! gcloud iam service-accounts describe "$RUNTIME_EMAIL" --project "$PROJECT" >/dev/null 2>&1; then
  gcloud iam service-accounts create "$RUNTIME_SA" --display-name="API en Cloud Run" --project "$PROJECT" >/dev/null
  echo "Cuenta de servicio $RUNTIME_SA creada."
fi

# Crea el secreto si no existe y le agrega una versión nueva con el valor recibido por stdin.
put_secret() {
  local name="$1"
  if ! gcloud secrets describe "$name" --project "$PROJECT" >/dev/null 2>&1; then
    gcloud secrets create "$name" --replication-policy=automatic --project "$PROJECT" >/dev/null
  fi
  gcloud secrets versions add "$name" --data-file=- --project "$PROJECT" >/dev/null
  gcloud secrets add-iam-policy-binding "$name" --member="serviceAccount:$RUNTIME_EMAIL" \
    --role=roles/secretmanager.secretAccessor --project "$PROJECT" >/dev/null
  echo "Secreto $name actualizado."
}

printf '%s' "$(tfvar db_admin_password)" | put_secret db-password

KEY_PATH="$(tfvar oci_private_key_path)"
KEY_PATH="${KEY_PATH/#\~/$HOME}"
put_secret oci-private-key < "$KEY_PATH"

(cd "$DB_DIR" && terraform output -raw database_jdbc_url) | put_secret db-url

echo "Listo. Secretos activos:"
gcloud secrets list --project "$PROJECT" --format="value(name)"
