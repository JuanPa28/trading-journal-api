#!/usr/bin/env bash
# Vacía el bucket de adjuntos antes de `terraform destroy`: OCI no deja borrar
# un bucket que todavía tiene objetos (los que se guardaron durante la demo).
set -euo pipefail

OCI_BIN="${OCI_BIN:-oci}"
BUCKET="${1:?uso: empty-bucket.sh <nombre-del-bucket>}"
NAMESPACE="${OCI_NAMESPACE:-$($OCI_BIN os ns get --query data --raw-output)}"

if ! $OCI_BIN os bucket get --namespace-name "$NAMESPACE" --bucket-name "$BUCKET" >/dev/null 2>&1; then
  echo "El bucket '$BUCKET' no existe: nada que vaciar."
  exit 0
fi
$OCI_BIN os object bulk-delete --namespace-name "$NAMESPACE" --bucket-name "$BUCKET" --force >/dev/null
echo "Bucket '$BUCKET' vacío."
