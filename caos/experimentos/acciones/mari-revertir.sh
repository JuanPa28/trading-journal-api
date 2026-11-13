#!/usr/bin/env bash
# Mari: quita la saturacion de CPU (reversion del experimento).
set -euo pipefail
NAMESPACE="${NAMESPACE:-default}"
kubectl delete stresschaos mari-cpu-stress -n "$NAMESPACE" --ignore-not-found
echo "StressChaos eliminado. Comprueba que /health/ready responde 200."
