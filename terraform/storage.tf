# Servicio 2 — OCI Object Storage: bucket privado de adjuntos del API
# (POST/GET /api/v2/attachments).
data "oci_objectstorage_namespace" "current" {
  compartment_id = var.oci_compartment_ocid
}

resource "oci_objectstorage_bucket" "attachments" {
  compartment_id = var.oci_compartment_ocid
  namespace      = data.oci_objectstorage_namespace.current.namespace
  name           = "${local.name}-attachments"
  access_type    = "NoPublicAccess"
  storage_tier   = "Standard"
  versioning     = "Disabled"
  freeform_tags  = local.tags
}
