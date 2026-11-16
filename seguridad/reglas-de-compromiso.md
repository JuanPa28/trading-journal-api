# Reglas de compromiso del game day

Equipo: Juan Pablo, Mari y Pipe. **Deben estar aceptadas por los tres antes de ejecutar cualquier experimento.** Cada API completa en su repositorio los valores de sus experimentos con este mismo formato. Esta versión es la de trading-journal (coordinador en Azure y datos en Oracle): experimentos 1 y 4.

## 1. Ventana

Hora de Colombia. Propuesta, a confirmar en el grupo antes de cada ronda. Durante la ventana nadie manda carga ni reinicia nada contra el coordinador.

| Ronda | Experimento | Inicio | Fin |
|---|---|---|---|
| Medición de línea base | 1 y 4 | por confirmar en el grupo | +30 min |
| Ronda 0 | 1 y 4 | por confirmar en el grupo | +40 min |
| Ronda 1 | 1 y 4 | por confirmar en el grupo | +40 min |

Fuera de la ventana no se crea, modifica ni borra ningún recurso del experimento.

## 2. Alcance

Solo las cuentas, proyectos y suscripciones del grupo en Azure, Google Cloud, AWS y Oracle. Para trading-journal: la suscripción de Azure `136496ef-81dd-486c-9679-5310e237f796`, el grupo de recursos `rg-caos-trading-journal` y el compartimento del grupo en Oracle Cloud.

**Queda prohibido** actuar sobre servicios, cuentas, redes o datos de terceros, de la Universidad o de otros grupos. No se hacen accesos desde otros orígenes con cuentas de directorios de empresa, no se crean identidades ni entidades de servicio en el directorio y no se tocan políticas, grupos de administración ni Defender: se simula una configuración no prevista, no un inicio de sesión ajeno.

## 3. Datos

Solo datos sintéticos del proyecto. Los experimentos no leen, copian ni envían datos reales, y los **valores** de los secretos nunca se imprimen ni se guardan: solo se cuenta cuántos secretos se listaron.

## 4. Radio de impacto (trading-journal)

**Experimento 1, identidad de máquina.**
- Una llamada de administración, `listSecrets`, sobre la Container App `api-coordinador`. Se descarta la respuesta y solo se registra el número de secretos.
- En la ronda 1: una regla de alerta, un grupo de acciones con correo y la configuración de diagnóstico del registro de actividad hacia `log-caos`.
- No se rotan ni se leen valores en la ronda 0. La rotación de secretos y el usuario de base de datos con privilegios mínimos se aplican solo en la ronda 1.

**Experimento 4, resiliencia de los datos (grupal).**
- Un bucket de Oracle Object Storage creado para el experimento, con objetos sintéticos que representan lo que cada API guarda del flujo (un archivo por API). No se usa ningún bucket existente de otros servicios.
- El ataque borra y sobrescribe esos objetos del bucket de prueba; la restauración sale de la versión anterior o de una copia aislada.
- Máximo 50 objetos y 1 MB en total.

## 5. Interruptor de parada

Lo acciona quien esté a cargo de la ronda; lo puede accionar cualquiera de los tres con acceso a la nube correspondiente.

```bash
python caos/azure/deploy.py env FAULT_HEADER_ENABLED=false     # apaga la inyección de fallas
python seguridad/identidad/experimento.py revertir              # deja el estado de la ronda 0
python seguridad/datos/experimento.py restaurar                 # restaura los objetos desde la copia
```

## 6. Reversión

- Experimento 1: el único cambio de la ronda 0 es una consulta de solo lectura de los nombres de secretos. En la ronda 1, `experimento.py revertir` elimina la regla, el grupo de acciones y la configuración de diagnóstico.
- Experimento 4: `restaurar` devuelve el bucket al contenido original y se compara por suma de verificación. Se prueba la restauración antes de la ronda 0.
- Toda la infraestructura del coordinador se destruye al final con `python caos/azure/deploy.py teardown`.

Se verifica con `GET /health` del coordinador (200) y una consulta del flujo completo con un trace id nuevo. La reversión se prueba antes de la ronda 0 y el resultado se anota en la bitácora.

## 7. Bitácora

Cada acción con hora (UTC), responsable y resultado, en [`resultados/bitacora.md`](resultados/bitacora.md).

## 8. Condiciones de aborto

Se ejecuta el interruptor de parada y se anota la causa si ocurre cualquiera de estas:

- `/health` del coordinador no responde 200 durante más de 60 s.
- El flujo completo falla en una prueba de humo.
- Se detecta tráfico o un recurso fuera del alcance de la sección 2.
- Se llega al fin de la ventana con el experimento sin terminar.
- El gasto de la suscripción deja de ser cero.
- Cualquiera de las tres APIs pide parar.

## Aceptan

| Integrante | Acepta | Fecha |
|---|---|---|
| Juan Pablo | sí | 2026-11-16 |
| Mari | pendiente de confirmar esta versión (aceptó la de StatsNostra) | |
| Pipe | pendiente de confirmar esta versión | |
