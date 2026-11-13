#!/usr/bin/env bash
# PENDIENTE (Pipe): deja sin servicio UNA instancia de la API de Pipe en AWS.
# Elige UNA de estas formas y reemplaza el contenido de este archivo:
#   a) Detener una tarea o instancia (segun como este desplegada):
#        aws ecs stop-task --cluster <cluster> --task <id-de-una-tarea>
#        aws ec2 stop-instances --instance-ids <id>
#   b) Activar el interruptor de fallos de la API con respuestas 5xx sostenidas:
#        poner FAULT_ERROR_RATE=1 en la API y reiniciarla
# El experimento llama a este script antes de correr la carga.
echo "PENDIENTE (Pipe): define en caos/experimentos/acciones/pipe-inyectar.sh como se deja sin servicio una instancia." >&2
exit 2
