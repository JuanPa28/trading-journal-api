# Base de datos del Canary Deployment: Oracle Autonomous Database Always Free,
# el mismo servicio de las entregas anteriores. Las dos revisiones del API
# (stable y canary) comparten ESTA base, por eso el cambio de esquema de la
# versión nueva es solo aditivo (Expand/Contract): ver canary/README.md.
#
# Conexión TLS sin wallet: no hay archivos que descargar ni copiar. El acceso
# se controla con la lista de IP permitidas; Cloud Run no tiene una IP de salida
# fija, así que queda abierta (0.0.0.0/0) y se protege con TLS y contraseña.
resource "oci_database_autonomous_database" "canary" {
  compartment_id = var.oci_compartment_ocid
  display_name   = "trading-journal-canary-db"
  db_name        = "TRADINGCANARY"
  db_workload    = "OLTP"
  admin_password = var.db_admin_password

  is_free_tier                = true
  is_mtls_connection_required = false
  whitelisted_ips             = ["0.0.0.0/0"]

  freeform_tags = {
    project = "trading-journal"
    purpose = "canary-deployment"
  }
}

locals {
  tls_profile = one([
    for p in oci_database_autonomous_database.canary.connection_strings[0].profiles : p
    if p.consumer_group == "MEDIUM" && p.tls_authentication == "SERVER" && p.protocol == "TCPS" && p.syntax_format == "LONG"
  ])

  jdbc_url = "jdbc:oracle:thin:@${local.tls_profile.value}"
}
