# Experimento 1: identidad de máquina del coordinador (16 de noviembre de 2026)

**Hipótesis de control.** Con la configuración de hoy, cuando alguien liste los secretos de la Container App esperamos una alerta en 15 minutos o menos (T1) y que la credencial expuesta no sea la de la cuenta con privilegio máximo (T2).

**Condición adversa.** Una llamada de administración `listSecrets` sobre la Container App `api-coordinador` (ATT&CK T1552.007, Unsecured Credentials: Container API). El script descarta los valores y solo cuenta cuántos secretos se listaron: **4** (contraseña de la base, URL de la base, llave de Oracle y autenticación de Grafana). Ejecutado con `seguridad/identidad/experimento.py`; la bitácora está en [`../bitacora.md`](../bitacora.md) y los resultados en `01-ronda-N.json`.

| | Ronda 0 (como está hoy) | Ronda 1 (alerta + privilegio mínimo) |
|---|---|---|
| Ataque (t0) | 17:57:23Z | 18:49:38Z |
| Registro de actividad en `log-caos` | no se recoge (la tabla `AzureActivity` no existe) | visible a los **252 s** |
| **Alerta (MTTD)** | **ninguna en 15 min 18 s de espera** | **522 s** (8 min 42 s), severidad 2 |
| Cumple T1 (≤ 15 min) | no | sí |
| Credencial que se lleva el atacante | la de `ADMIN` (privilegio máximo sobre toda la base) | la de `TJ_APP` (lectura y escritura de 3 tablas; sin crear ni alterar nada) |
| Cumple T2 | no | sí |
| Falsos positivos | no aplica | **0**: 1 alerta para 1 listado; 12 operaciones de administración de la ventana (`CONTAINERAPPS/WRITE` ×8, `DIAGNOSTICSETTINGS/WRITE` ×2, `ACTIONGROUPS/WRITE`, `SCHEDULEDQUERYRULES/WRITE`) no alertaron |
| Contención (MTTC) | no hubo quién contuviera | **no medida**: el experimento mide detección y daño posible, no ejecuta una respuesta |
| **Hipótesis** | **se refuta** | **se cumple** |

## Qué muestra

- **Ronda 0:** la acción pasa sin que nadie se entere y se lleva la llave de toda la base. El Registro de actividad de Azure sí registra la operación, pero no llega al espacio de trabajo donde está nuestra detección, así que es como si no existiera para el equipo.
- **Ronda 1, mejora 1:** la configuración de diagnóstico lleva el registro de actividad a `log-caos` y la regla [`deteccion.kql`](../identidad/deteccion.kql) alerta por correo. La detección tardó 522 s: 252 s hasta que el evento fue consultable y el resto, esperar a la siguiente evaluación de la regla (cada 5 minutos).
- **Ronda 1, mejora 2:** aunque el atacante lista los secretos, la contraseña ya no es la de `ADMIN`. Reduce el daño (D3-UAP) en lugar de impedir el acceso.

## Hallazgos

1. **La operación real es `MICROSOFT.APP/CONTAINERAPPS/LISTSECRETS/ACTION` y genera dos eventos: `Start` y `Success`.** La regla ignora `Start` y alerta solo por el resultado, de modo que cada listado produce una alerta, no dos. Coincide con los casos negativos de `probar_deteccion.py`.
2. **El MTTD de 522 s está dominado por la frecuencia de evaluación** (5 min) y la ingestión (252 s). Con evaluación cada minuto bajaría hacia los 5 minutos; se deja como mejora (más evaluaciones cuestan más cuando el espacio de trabajo crece).
3. **Los secretos de la plataforma son un solo repositorio para todo:** Grafana, la base y la llave de Oracle se listan con la misma llamada. Backlog: separar los secretos de observabilidad de los de datos y moverlos a un almacén de secretos con identidad administrada.
4. **El cambio de privilegios rompió el arranque dos veces** antes de funcionar: (a) Hibernate no valida un esquema ajeno (`Schema validation: missing table`); se pasó a `ddl-auto=none`; (b) Spring no puede convertir una variable de entorno en `hibernate.default_schema` y `az` en Windows mutila un valor con espacios (el SQL de inicio de Hikari). Se resolvió con un trigger de inicio de sesión en la base que fija el esquema `ADMIN` solo para `TJ_APP`. No hubo caída: Azure mantuvo la revisión anterior hasta que la nueva estuvo sana. Lección: un cambio de identidad de base de datos es un cambio de despliegue y se prueba con reversión lista.

## Límites que hay que declarar

- La contraseña de `ADMIN` sigue en el archivo de variables local de Terraform y en el estado del recurso de base de datos; la mejora solo la saca de la Container App.
- `TJ_APP` puede leer y escribir las tres tablas: un atacante con su credencial aún podría borrar o alterar datos. Sigue siendo mucho menos que `ADMIN`, pero no es cero.
- La alerta llega por correo, que es lo que hay en una cuenta gratuita; no hay respuesta automática.
- El correo de la alerta se envía a la cuenta del responsable y no se documenta aquí.

## Mapeo

ATT&CK T1552.007 y T1078.004; D3FEND D3-UAP (User Account Permissions) y D3-CRO (Credential Rotation: la contraseña de `TJ_APP` es nueva y aleatoria, y la de `ADMIN` sale del secreto); ISO/IEC 27001:2022 A.8.2, A.8.15 y A.8.16; NIST SP 800-53 AC-6, AU-6 y SI-4.
