# Servicio 5 — Azure Resource Group: contenedor lógico de todo lo que corre en Azure.
# Borrarlo (terraform destroy) elimina todo lo que contiene.
resource "azurerm_resource_group" "main" {
  name     = "${local.name}-rg"
  location = var.azure_location
  tags     = local.tags
}
