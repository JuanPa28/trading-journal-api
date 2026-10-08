# Servicio 3 — OCI Notifications: tópico y suscripción por correo para las alertas.
resource "oci_ons_notification_topic" "alerts" {
  compartment_id = var.oci_compartment_ocid
  name           = "${local.name}-alerts"
  description    = "Alertas de ${local.name}"
  freeform_tags  = local.tags
}

# OCI envía un correo de confirmación: la suscripción queda activa al aceptarlo.
resource "oci_ons_subscription" "email" {
  compartment_id = var.oci_compartment_ocid
  topic_id       = oci_ons_notification_topic.alerts.id
  protocol       = "EMAIL"
  endpoint       = var.alert_email
  freeform_tags  = local.tags
}
