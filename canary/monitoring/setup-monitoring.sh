#!/usr/bin/env bash
# Crea (es idempotente) lo que se ve en Cloud Monitoring: la métrica de negocio
# basada en logs, el tablero stable vs canary y la alerta de errores 5xx del canary.
# Todo esto entra en la capa gratuita de Cloud Monitoring y Cloud Logging.
set -euo pipefail

PROJECT="${GCP_PROJECT_ID:-trading-journal-canary}"
ALERT_EMAIL="${ALERT_EMAIL:-}"
DIR="$(cd "$(dirname "$0")" && pwd)"

# Métrica de negocio: cuenta las líneas "event=trade_created" que escribe la API.
if ! gcloud logging metrics describe trades_created --project "$PROJECT" >/dev/null 2>&1; then
  gcloud logging metrics create trades_created --project "$PROJECT" \
    --description="Trades creados con éxito (métrica de negocio)" \
    --log-filter='resource.type="cloud_run_revision" AND textPayload:"event=trade_created"'
fi

# Tablero: se reemplaza si ya existe uno con el mismo nombre.
EXISTING="$(gcloud monitoring dashboards list --project "$PROJECT" \
  --filter='displayName="Canary: stable vs canary"' --format='value(name)')"
if [ -n "$EXISTING" ]; then
  gcloud monitoring dashboards delete "$EXISTING" --project "$PROJECT" --quiet
fi
gcloud monitoring dashboards create --config-from-file="$DIR/dashboard.json" --project "$PROJECT"

# Alerta: necesita un canal de notificación (correo). Si no se indica ALERT_EMAIL, se omite.
if [ -n "$ALERT_EMAIL" ]; then
  CHANNEL="$(gcloud beta monitoring channels list --project "$PROJECT" \
    --filter="labels.email_address=$ALERT_EMAIL" --format='value(name)' | head -1)"
  if [ -z "$CHANNEL" ]; then
    CHANNEL="$(gcloud beta monitoring channels create --project "$PROJECT" --display-name="Correo canary" \
      --type=email --channel-labels="email_address=$ALERT_EMAIL" --format='value(name)')"
  fi
  gcloud alpha monitoring policies create --project "$PROJECT" --policy-from-file="$DIR/alert-policy.json" \
    --notification-channels="$CHANNEL"
else
  echo "ALERT_EMAIL no está definido: se omite la alerta (el tablero y la métrica sí se crearon)."
fi
