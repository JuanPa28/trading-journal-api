# Servicio 4 — OCI Monitoring: alarma de CPU alta en la base de datos, que
# notifica por el tópico de notifications.tf.
resource "oci_monitoring_alarm" "db_cpu" {
  compartment_id        = var.oci_compartment_ocid
  metric_compartment_id = var.oci_compartment_ocid
  namespace             = "oci_autonomous_database"
  display_name          = "${local.name}-db-cpu-alta"
  is_enabled            = true
  severity              = "WARNING"
  query                 = "CpuUtilization[1m]{resourceId = \"${oci_database_autonomous_database.main.id}\"}.mean() > 80"
  pending_duration      = "PT5M"
  destinations          = [oci_ons_notification_topic.alerts.id]
  body                  = "La CPU de la base de datos ${local.name}-db superó el 80 % por más de 5 minutos."
  freeform_tags         = local.tags
}
