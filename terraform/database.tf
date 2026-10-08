# Servicio 1 — Oracle Autonomous Database (Always Free), la base de datos del API.
#
# Conexión "walletless" por TLS (sin descargar el wallet), igual que en la
# Entrega 2: por eso mTLS va desactivado y el acceso se controla con la lista
# de IPs permitidas (ACL). 0.0.0.0/0 es una simplificación de laboratorio;
# el API de Azure Container Instances no tiene una IP de salida fija.
resource "oci_database_autonomous_database" "main" {
  compartment_id = var.oci_compartment_ocid
  display_name   = "${local.name}-db"
  db_name        = local.db_name
  db_workload    = "OLTP"
  admin_password = var.db_admin_password

  is_free_tier                = true
  is_mtls_connection_required = false
  whitelisted_ips             = ["0.0.0.0/0"]

  freeform_tags = local.tags
}

locals {
  # Perfil de conexión TLS (sin wallet) del servicio MEDIUM, en formato descriptor.
  db_tls_profile = one([
    for p in oci_database_autonomous_database.main.connection_strings[0].profiles : p
    if p.consumer_group == "MEDIUM" && p.tls_authentication == "SERVER" && p.protocol == "TCPS" && p.syntax_format == "LONG"
  ])

  db_jdbc_url = "jdbc:oracle:thin:@${local.db_tls_profile.value}"
}
