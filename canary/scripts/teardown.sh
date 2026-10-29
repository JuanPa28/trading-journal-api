#!/usr/bin/env bash
# Elimina el servicio de Cloud Run y las imágenes del despliegue canary para no
# dejar nada generando consumo. No toca la base de datos (se destruye con
# `terraform destroy` en canary/db) ni los secretos, que no tienen costo.
set -euo pipefail

PROJECT="${GCP_PROJECT_ID:-trading-journal-canary}"
REGION="${GCP_REGION:-us-central1}"
SERVICE="${CANARY_SERVICE:-api}"
REPOSITORY="${AR_REPOSITORY:-trading-journal}"

if gcloud run services describe "$SERVICE" --region "$REGION" --project "$PROJECT" >/dev/null 2>&1; then
  gcloud run services delete "$SERVICE" --region "$REGION" --project "$PROJECT" --quiet
  echo "Servicio $SERVICE eliminado."
else
  echo "El servicio $SERVICE no existe: nada que eliminar."
fi

if gcloud artifacts docker images list "$REGION-docker.pkg.dev/$PROJECT/$REPOSITORY" --project "$PROJECT" \
    --format="value(package)" 2>/dev/null | grep -q .; then
  for image in $(gcloud artifacts docker images list "$REGION-docker.pkg.dev/$PROJECT/$REPOSITORY" \
      --project "$PROJECT" --format="value(package)" | sort -u); do
    gcloud artifacts docker images delete "$image" --delete-tags --project "$PROJECT" --quiet
    echo "Imagen $image eliminada."
  done
else
  echo "No hay imágenes en el repositorio: nada que eliminar."
fi
