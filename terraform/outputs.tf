output "api_url" {
  description = "URL pública del API."
  value       = "http://${azurerm_container_group.api.fqdn}:8080"
}

output "api_health_url" {
  description = "Endpoint de salud, para comprobar que el API quedó arriba."
  value       = "http://${azurerm_container_group.api.fqdn}:8080/actuator/health"
}

output "azure_resource_group" {
  value = azurerm_resource_group.main.name
}

output "database_name" {
  value = oci_database_autonomous_database.main.display_name
}

output "database_id" {
  value = oci_database_autonomous_database.main.id
}

output "bucket_name" {
  value = oci_objectstorage_bucket.attachments.name
}

output "alerts_topic_id" {
  value = oci_ons_notification_topic.alerts.id
}

output "database_jdbc_url" {
  description = "URL JDBC walletless de la base de datos (para DBeaver)."
  value       = local.db_jdbc_url
  sensitive   = true
}
