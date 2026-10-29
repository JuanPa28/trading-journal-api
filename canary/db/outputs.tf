output "database_id" {
  description = "OCID de la base de datos."
  value       = oci_database_autonomous_database.canary.id
}

output "database_name" {
  value = oci_database_autonomous_database.canary.display_name
}

output "database_jdbc_url" {
  description = "URL JDBC (TLS, sin wallet) que se pasa a Cloud Run como DB_URL."
  value       = local.jdbc_url
  sensitive   = true
}
