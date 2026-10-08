# Evidencias de ejecución — Entrega 3 (Terraform)

Resultado de ejecutar la infraestructura como código de la carpeta [`terraform/`](../) el 8 de octubre de 2026.

| Archivo | Qué demuestra |
|---|---|
| [`02-pipeline-apply.log`](02-pipeline-apply.log) | Ejecución del pipeline con la acción **apply**, **desde cero**: inicializa, hace el plan, crea **8 recursos en 2 nubes** (`Apply complete! Resources: 8 added`) y espera hasta que el API responde `200` en `/actuator/health`. [Ver la ejecución en GitHub](https://github.com/JuanPa28/trading-journal-api/actions/runs/37801302939). |
| [`03-pipeline-destroy.log`](03-pipeline-destroy.log) | Ejecución del pipeline con la acción **destroy**: vacía el bucket de adjuntos y elimina toda la infraestructura (`Destroy complete! Resources: 8 destroyed`). [Ver la ejecución en GitHub](https://github.com/JuanPa28/trading-journal-api/actions/runs/37800222558). |
| [`01-terraform-outputs-y-estado.txt`](01-terraform-outputs-y-estado.txt) | `terraform output` y `terraform state list` con la infraestructura desplegada (los valores sensibles aparecen ocultos). |
| [`04-prueba-api.txt`](04-prueba-api.txt) | Pruebas contra la URL pública del API desplegado: salud, escritura y lectura en la base de datos Oracle, y guardado y recuperación de un adjunto en Object Storage. |
| [`05-recursos-en-la-nube.txt`](05-recursos-en-la-nube.txt) | Inventario de los recursos consultado **directamente** a OCI y Azure con sus CLI (no solo a Terraform). |

## Cómo se obtuvo cada evidencia

1. `Actions → Terraform - Desplegar o destruir → Run workflow → destroy`, y después `→ apply` (el ciclo completo, que prueba que todo se recrea desde cero).
2. Con la infraestructura arriba: `terraform output`, `terraform state list`, las pruebas con `curl` y las consultas con `oci` y `az`.
3. Al terminar de evidenciar, `destroy` otra vez para no gastar créditos.

Los logs conservan los pasos relevantes (crear el bucket de estado, inicializar, plan, apply, esperar al API, vaciar el bucket y destroy); se omiten los de preparación del runner. GitHub oculta los secrets en los logs (aparecen como `***`).

> La infraestructura de la prueba se destruyó al terminar; se vuelve a desplegar con el mismo pipeline el día de la sustentación.
