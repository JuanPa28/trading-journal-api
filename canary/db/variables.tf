variable "oci_tenancy_ocid" {
  description = "OCID del tenancy de Oracle Cloud."
  type        = string
}

variable "oci_user_ocid" {
  description = "OCID del usuario dueño de la llave API."
  type        = string
}

variable "oci_fingerprint" {
  description = "Huella de la llave API."
  type        = string
}

variable "oci_private_key_path" {
  description = "Ruta al archivo .pem de la llave API (en local)."
  type        = string
  default     = null
}

variable "oci_private_key" {
  description = "Contenido de la llave API (en el pipeline)."
  type        = string
  default     = null
  sensitive   = true
}

variable "oci_region" {
  description = "Región de Oracle Cloud."
  type        = string
  default     = "sa-bogota-1"
}

variable "oci_compartment_ocid" {
  description = "Compartimento donde se crea la base de datos (el tenancy sirve como raíz)."
  type        = string
}

variable "db_admin_password" {
  description = "Contraseña del usuario ADMIN (12 a 30 caracteres)."
  type        = string
  sensitive   = true

  validation {
    condition     = length(var.db_admin_password) >= 12 && length(var.db_admin_password) <= 30
    error_message = "La contraseña debe tener entre 12 y 30 caracteres."
  }
}
