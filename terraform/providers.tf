# Oracle Cloud: datos (base de datos, almacenamiento, alertas).
provider "oci" {
  tenancy_ocid     = var.oci_tenancy_ocid
  user_ocid        = var.oci_user_ocid
  fingerprint      = var.oci_fingerprint
  region           = var.oci_region
  private_key      = var.oci_private_key
  private_key_path = var.oci_private_key_path
}

# Azure: cómputo del API. En local se autentica con `az login`; en el pipeline
# con las variables de entorno ARM_CLIENT_ID / ARM_CLIENT_SECRET / ARM_TENANT_ID.
provider "azurerm" {
  features {}
  subscription_id = var.azure_subscription_id

  # Los proveedores de recursos (Microsoft.ContainerInstance y
  # Microsoft.OperationalInsights) se registran una sola vez a mano: ver README.
  resource_provider_registrations = "none"
}
