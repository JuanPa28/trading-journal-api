# Servicio 7 — Azure Container Instances: ejecuta el API (la imagen Docker del
# proyecto) con la base de datos y el bucket de OCI ya cableados por variables
# de entorno. Esto es lo que "instala el API" en la infraestructura.
resource "azurerm_container_group" "api" {
  name                = "${local.name}-api"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  os_type             = "Linux"
  restart_policy      = "Always"
  ip_address_type     = "Public"
  dns_name_label      = "${local.name}-api-${local.dns_suffix}"
  tags                = local.tags

  container {
    name   = "api"
    image  = "${var.api_image}:${var.api_image_tag}"
    cpu    = var.api_cpu
    memory = var.api_memory_gb

    ports {
      port     = 8080
      protocol = "TCP"
    }

    environment_variables = {
      SPRING_PROFILES_ACTIVE        = "prod"
      SPRING_JPA_HIBERNATE_DDL_AUTO = "update" # crea las tablas en la base de datos vacía
      DB_URL                        = local.db_jdbc_url
      DB_USERNAME                   = "ADMIN"
      OCI_TENANCY_ID                = var.oci_tenancy_ocid
      OCI_USER_ID                   = var.oci_user_ocid
      OCI_FINGERPRINT               = var.oci_fingerprint
      OCI_REGION                    = var.oci_region
      OCI_OBJECT_STORAGE_NAMESPACE  = data.oci_objectstorage_namespace.current.namespace
      OCI_OBJECT_STORAGE_BUCKET     = oci_objectstorage_bucket.attachments.name
      MARI_BASE_URL                 = var.mari_base_url
      PIPE_BASE_URL                 = var.pipe_base_url
      OTEL_SERVICE_NAME             = "api-oci"
    }

    secure_environment_variables = {
      DB_PASSWORD     = var.db_admin_password
      OCI_PRIVATE_KEY = local.oci_private_key_pem
    }

    # El arranque con Hibernate + Oracle tarda; sin esta espera los probes
    # reiniciarían el contenedor antes de que termine de levantar.
    liveness_probe {
      http_get {
        path   = "/actuator/health/liveness"
        port   = 8080
        scheme = "http"
      }
      initial_delay_seconds = 120
      period_seconds        = 30
      failure_threshold     = 5
    }

    readiness_probe {
      http_get {
        path   = "/actuator/health/readiness"
        port   = 8080
        scheme = "http"
      }
      initial_delay_seconds = 60
      period_seconds        = 15
      failure_threshold     = 5
    }
  }

  diagnostics {
    log_analytics {
      workspace_id  = azurerm_log_analytics_workspace.main.workspace_id
      workspace_key = azurerm_log_analytics_workspace.main.primary_shared_key
    }
  }
}
