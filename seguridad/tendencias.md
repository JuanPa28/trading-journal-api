# Tendencias verificables

Cada tendencia cita su fuente, su fecha de consulta y qué experimento del game day la pone a prueba. Fecha de consulta de todas: **16 de noviembre de 2026**. Se citó solo lo que la fuente dice, sin extrapolar.

| # | Tendencia | Fuente | Qué dice (resumen) | Experimento que la ejercita |
|---|---|---|---|---|
| 1 | Las credenciales robadas siguen entre las causas más comunes de brechas, aunque ya no son la primera vía de entrada | Verizon, *Data Breach Investigations Report* (edición 2026), https://www.verizon.com/business/resources/reports/dbir/ | El informe indica que el 31 % de las brechas empiezan con la explotación de vulnerabilidades de software, por delante de las contraseñas robadas como vía principal de acceso, y lista las credenciales robadas entre las causas más comunes de brechas (sin dar una cifra en esa página) | **Experimento 1** (identidad): proteger y limitar las credenciales (D3-UAP, D3-CRO) sigue siendo relevante, pero no es la única puerta; por eso se combina con detección |
| 2 | La inyección de instrucciones es el riesgo número uno de las aplicaciones con modelos de lenguaje | OWASP, *Top 10 for LLM Applications* 2025, riesgo **LLM01:2025 Prompt Injection**, https://genai.owasp.org/llmrisk/llm01-prompt-injection/ | Define la inyección de instrucciones como la que cambia el comportamiento o la salida del modelo de formas no previstas, incluso con entradas que un humano no ve, siempre que el modelo las procese | **Experimento 3** y analista de IA: telemetría envenenada con instrucciones plantadas (MITRE ATLAS AML.T0051, forma indirecta) |
| 3 | Las copias de seguridad deben estar aisladas, cifradas y probadas, porque el *ransomware* intenta borrarlas | CISA, *#StopRansomware Guide* (septiembre de 2023), https://www.cisa.gov/stopransomware/ransomware-guide | Recomienda mantener copias de seguridad **fuera de línea y cifradas** de los datos críticos y **probar con regularidad** su disponibilidad e integridad, porque muchas variantes intentan eliminar o cifrar las copias accesibles. Señala que el almacenamiento inmutable protege los datos pero debe usarse con cuidado por posibles requisitos regulatorios | **Experimento 4** (datos): la variante dura (el atacante borra también las versiones) se resolvió con una copia en otro bucket; la copia de este proyecto es **del mismo proveedor y cuenta**, no fuera de línea, y por eso se declara como límite |

## Cómo se usan en el informe

- La tendencia 1 justifica por qué el experimento 1 mide **detección** y no solo prevención: si una credencial se roba, hay que verlo.
- La tendencia 2 justifica que la telemetría se trate como no confiable antes de dársela a una IA (`ia/analista.py`) y que cada cifra de la respuesta se verifique.
- La tendencia 3 justifica el diseño de dos controles del experimento 4 (versionado y copia aislada) y obliga a declarar su límite: la copia no está fuera de línea ni en otra cuenta.
