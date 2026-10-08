variable "project_name" {
  description = "Prefijo común de todos los recursos."
  type        = string
  default     = "trading-journal"

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{2,24}$", var.project_name))
    error_message = "project_name: 3 a 25 caracteres, minúsculas, números o guiones, empezando por letra."
  }
}

variable "environment" {
  description = "Nombre corto del ambiente."
  type        = string
  default     = "dev"
}

variable "tags" {
  description = "Etiquetas adicionales para los recursos que las soportan."
  type        = map(string)
  default     = {}
}

# ---------------------------------------------------------------- Oracle Cloud

variable "oci_tenancy_ocid" {
  description = "OCID del tenancy de Oracle Cloud."
  type        = string
}

variable "oci_user_ocid" {
  description = "OCID del usuario de Oracle Cloud dueño de la llave API."
  type        = string
}

variable "oci_fingerprint" {
  description = "Fingerprint de la llave API de OCI."
  type        = string
}

variable "oci_private_key" {
  description = "Contenido PEM de la llave privada API de OCI (lo usa el pipeline). Alternativa a oci_private_key_path."
  type        = string
  sensitive   = true
  default     = null
}

variable "oci_private_key_path" {
  description = "Ruta local a la llave privada API de OCI (uso local). Alternativa a oci_private_key."
  type        = string
  default     = null

  validation {
    condition     = var.oci_private_key != null || var.oci_private_key_path != null
    error_message = "Define oci_private_key (contenido) o oci_private_key_path (ruta)."
  }
}

variable "oci_region" {
  description = "Región de Oracle Cloud."
  type        = string
  default     = "sa-bogota-1"
}

variable "oci_compartment_ocid" {
  description = "OCID del compartimento donde se crean los recursos (puede ser el tenancy)."
  type        = string
}

variable "db_admin_password" {
  description = "Contraseña de ADMIN en la Autonomous Database (12 a 30 caracteres, con mayúscula, minúscula y número)."
  type        = string
  sensitive   = true

  validation {
    condition     = length(var.db_admin_password) >= 12 && length(var.db_admin_password) <= 30
    error_message = "La contraseña de la base de datos debe tener entre 12 y 30 caracteres."
  }
}

variable "alert_email" {
  description = "Correo que recibe las alertas de OCI Monitoring."
  type        = string
}

# ----------------------------------------------------------------------- Azure

variable "azure_subscription_id" {
  description = "ID de la suscripción de Azure."
  type        = string
}

variable "azure_location" {
  description = "Región de Azure para el grupo de recursos y el API."
  type        = string
  default     = "eastus"
}

# ------------------------------------------------------------------------- API

variable "api_image" {
  description = "Imagen Docker del API, sin etiqueta (pública en GHCR)."
  type        = string
  default     = "ghcr.io/juanpa28/trading-journal-api"
}

variable "api_image_tag" {
  description = "Etiqueta de la imagen. El pipeline pasa el SHA del commit que acaba de construir."
  type        = string
  default     = "prod-latest"
}

variable "api_cpu" {
  description = "vCPU del contenedor del API."
  type        = number
  default     = 1
}

variable "api_memory_gb" {
  description = "Memoria (GB) del contenedor del API."
  type        = number
  default     = 2
}

variable "mari_base_url" {
  description = "URL base del API de Mari (entidades de GCP para api/v2). Si no responde, el campo sale en null."
  type        = string
  default     = "http://34.57.56.128"
}

variable "pipe_base_url" {
  description = "URL base del API de Pipe (entidades de AWS para api/v2). Si no responde, el campo sale en null."
  type        = string
  default     = "http://k8s-default-ecommerc-233fe225e6-1328693903.us-east-2.elb.amazonaws.com"
}
