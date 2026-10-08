# Estado remoto en un bucket de OCI Object Storage (necesario para que el
# pipeline y tu máquina compartan el mismo estado y para poder destruir luego).
# El bucket NO lo crea Terraform (no puede guardar su propio estado dentro de
# sí mismo): lo crea una vez scripts/bootstrap-state.sh. Ver README.
terraform {
  backend "oci" {
    bucket    = "trading-journal-tfstate"
    namespace = "axxhzvxjrmcl"
    region    = "sa-bogota-1"
    key       = "entrega3/terraform.tfstate"
  }
}
