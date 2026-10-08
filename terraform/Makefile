TFVARS ?= environments/dev.auto.tfvars

.PHONY: bootstrap init fmt validate plan apply output empty-bucket destroy

# Crea una sola vez el bucket donde Terraform guarda su estado.
bootstrap:
	bash scripts/bootstrap-state.sh

init:
	terraform init

fmt:
	terraform fmt -recursive

validate:
	terraform validate

plan:
	terraform plan -var-file="$(TFVARS)"

apply:
	terraform apply -var-file="$(TFVARS)"

output:
	terraform output

# OCI no borra un bucket con objetos: se vacía antes de destruir.
empty-bucket:
	-bash scripts/empty-bucket.sh "$$(terraform output -raw bucket_name 2>/dev/null)"

# Elimina TODA la infraestructura (OCI y Azure). Úselo al terminar la sustentación.
destroy: empty-bucket
	terraform destroy -var-file="$(TFVARS)"
