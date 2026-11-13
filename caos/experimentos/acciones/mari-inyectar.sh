#!/usr/bin/env bash
# Mari: satura la CPU de un pod de su API con Chaos Mesh (requiere kubectl apuntando a su cluster
# de GKE y Chaos Mesh instalado). Ajusta NAMESPACE y APP_LABEL a su despliegue.
#
# Regla acordada en el equipo: se avisa en el chat la hora de inicio y de fin ANTES de ejecutar.
set -euo pipefail
NAMESPACE="${NAMESPACE:-default}"
APP_LABEL="${APP_LABEL:-CAMBIAR-por-la-etiqueta-app-de-la-API}"
DIR="$(cd "$(dirname "$0")" && pwd)"

if [[ "$APP_LABEL" == CAMBIAR-* ]]; then
  echo "PENDIENTE (Mari): define APP_LABEL (la etiqueta app de los pods de su API) y NAMESPACE." >&2
  exit 2
fi
sed -e "s/\${NAMESPACE}/$NAMESPACE/g" -e "s/\${APP_LABEL}/$APP_LABEL/g" "$DIR/mari-stress-cpu.yaml" | kubectl apply -f -
echo "StressChaos aplicado en $NAMESPACE sobre pods con app=$APP_LABEL (3 minutos)."
