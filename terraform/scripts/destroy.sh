#!/usr/bin/env bash
# Elimina TODA la infraestructura (OCI y Azure). Úselo al terminar la sustentación.
# Equivale a `make destroy`, para quien no tenga make (por ejemplo en Windows).
#
# Uso: bash scripts/destroy.sh            # pide confirmación
#      bash scripts/destroy.sh -auto-approve
set -euo pipefail

cd "$(dirname "$0")/.."
VARFILE="${TFVARS:-environments/dev.auto.tfvars}"

# OCI no deja borrar un bucket que todavía tiene objetos: se vacía primero.
BUCKET="$(terraform output -raw bucket_name 2>/dev/null || true)"
if [ -n "$BUCKET" ]; then
  bash scripts/empty-bucket.sh "$BUCKET"
fi

terraform destroy -var-file="$VARFILE" "$@"
