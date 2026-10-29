# Estado remoto en el mismo bucket de OCI que usó la Entrega 3, con otra llave
# para no mezclar ambos estados. Ese bucket lo crea terraform/scripts/bootstrap-state.sh.
terraform {
  backend "oci" {
    bucket    = "trading-journal-tfstate"
    namespace = "axxhzvxjrmcl"
    region    = "sa-bogota-1"
    key       = "canary/db/terraform.tfstate"
  }
}
