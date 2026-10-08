locals {
  name = "${var.project_name}-${var.environment}"

  tags = merge(var.tags, {
    project     = var.project_name
    environment = var.environment
    managed_by  = "terraform"
  })

  # Nombre de la base: solo letras y números, máximo 14 caracteres.
  db_name = upper(substr(replace("${var.project_name}${var.environment}", "-", ""), 0, 14))

  # Sufijo estable (no aleatorio) para que la etiqueta DNS del API sea única en
  # la región de Azure sin necesidad de otro proveedor.
  dns_suffix = substr(md5(var.azure_subscription_id), 0, 6)

  # Llave privada de OCI: contenido directo (pipeline) o ruta a archivo (local).
  oci_private_key_pem = var.oci_private_key != null ? var.oci_private_key : file(pathexpand(var.oci_private_key_path))
}
