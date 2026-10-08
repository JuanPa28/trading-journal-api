# Copie este archivo como environments/dev.auto.tfvars y reemplace los valores
# REEMPLAZAR. dev.auto.tfvars está en .gitignore: no lo confirme en Git.
project_name = "trading-journal"
environment  = "dev"

# --- Oracle Cloud ---
oci_tenancy_ocid     = "ocid1.tenancy.oc1..REEMPLAZAR"
oci_user_ocid        = "ocid1.user.oc1..REEMPLAZAR"
oci_fingerprint      = "REEMPLAZAR:FINGERPRINT"
oci_private_key_path = "~/.oci/oci_api_key.pem"
oci_region           = "sa-bogota-1"
oci_compartment_ocid = "ocid1.tenancy.oc1..REEMPLAZAR" # el tenancy sirve como compartimento raíz
db_admin_password    = "REEMPLAZAR_12_A_30_CARACTERES_CON_MAYUSCULA_Y_NUMERO"
alert_email          = "REEMPLAZAR@correo.com"

# --- Azure ---
azure_subscription_id = "REEMPLAZAR-ID-DE-SUSCRIPCION"
azure_location        = "eastus"

# --- API ---
api_image_tag = "prod-latest"

tags = {
  course = "enfasis-devops"
  owner  = "estudiante"
}
