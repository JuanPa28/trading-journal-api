#!/usr/bin/env bash
# Crea (una sola vez, es idempotente) el bucket privado donde Terraform guarda
# su estado. Requiere el CLI de OCI configurado (~/.oci/config o variables OCI_CLI_*).
set -euo pipefail

OCI_BIN="${OCI_BIN:-oci}"
BUCKET="${STATE_BUCKET:-trading-journal-tfstate}"
NAMESPACE="${OCI_NAMESPACE:-$($OCI_BIN os ns get --query data --raw-output)}"
COMPARTMENT="${OCI_COMPARTMENT_OCID:-${OCI_CLI_TENANCY:-$(grep '^tenancy' ~/.oci/config | head -1 | cut -d= -f2)}}"

if $OCI_BIN os bucket get --namespace-name "$NAMESPACE" --bucket-name "$BUCKET" >/dev/null 2>&1; then
  echo "El bucket de estado '$BUCKET' ya existe."
else
  $OCI_BIN os bucket create --namespace-name "$NAMESPACE" --compartment-id "$COMPARTMENT" \
    --name "$BUCKET" --public-access-type NoPublicAccess >/dev/null
  echo "Bucket de estado '$BUCKET' creado."
fi
