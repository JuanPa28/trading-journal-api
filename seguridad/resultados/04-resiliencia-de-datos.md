# Experimento 4: resiliencia de los datos en Oracle Object Storage (16 de noviembre de 2026)

**Hipótesis de control.** Con un borrado y una sobrescritura masiva del bucket, se recupera el 100 % de los objetos íntegros (comprobados por sha256) con un RTO de 10 minutos o menos y sin pérdida de datos.

**Condición adversa.** Un actor con acceso al bucket sobrescribe cada objeto con basura (cifrado simulado, ATT&CK T1486) y luego lo borra (T1485). El bucket de prueba guarda un objeto sintético por API del grupo: el adjunto de trading-journal, una predicción de StatsNostra y un pedido de ecommerce. Ejecutado con `seguridad/datos/experimento.py`; la bitácora está en [`../bitacora.md`](../bitacora.md) y los resultados en `04-ronda-N.json`.

| | Ronda 0 (como está hoy) | Ronda 1 (versionado + copia aislada) | Ronda 1, variante dura (el atacante borra también las versiones) |
|---|---|---|---|
| Estado del bucket | sin versionado, sin copia | versionado activado y copia en un bucket aislado | igual que la ronda 1 |
| Objetos recuperados | **0 de 3** | **3 de 3** | **3 de 3** |
| Íntegros (sha256 igual al original) | 0 | 3 | 3 |
| Origen de la restauración | ninguno | versión anterior | **copia aislada** |
| RTO (ataque a restauración completa) | no se pudo restaurar | 3 s | 3 s |
| Pérdida de datos | **total** | ninguna | ninguna |
| Hipótesis | **se refuta** | **se cumple** | **se cumple** |

## Qué muestra

- **Ronda 0:** con el bucket como lo dejó la entrega 3 (`versioning = "Disabled"`, sin copia), un borrado o una sobrescritura no se puede deshacer: se pierde el 100 % de los objetos. Es el mismo hueco que marca el inventario de controles.
- **Ronda 1:** activar el versionado basta para deshacer el borrado y la sobrescritura, porque la versión buena sigue existiendo. Cada restauración se acepta solo si el hash del contenido coincide con el original: una versión alterada no se da por buena.
- **Variante dura:** un atacante con permiso para borrar versiones destruye también esa defensa. La **copia en otro bucket** es la que lo cubre. Por eso son dos controles distintos y se necesitan los dos (defensa en profundidad).

## Límites que hay que declarar

- Los objetos son de pocos bytes, así que el RTO de 3 s **no es extrapolable**: con datos reales crece con el volumen. Sirve para demostrar el mecanismo, no para dimensionar el RTO de producción.
- La copia aislada es **otro bucket de la misma cuenta de Oracle**. Protege contra un borrado en el bucket principal, pero no contra la pérdida de la cuenta o de las credenciales con permiso sobre ambos buckets. Un aislamiento real exige otra cuenta o región con permisos separados (siguiente paso).
- La copia se hizo en el mismo comando de la mejora (`mejorar`); no hay copia periódica, así que el RPO real depende de cada cuánto se copie. Hoy es el instante de la última copia.
- No hay detección: el borrado masivo no genera alerta. Está en el inventario como hueco de la capa de detección y respuesta.
- Un primer intento de restauración de la ronda 1 falló porque el listado de versiones de Oracle devuelve la fecha vacía (lo que la simulación de las pruebas no reproducía). No hubo pérdida de datos (el versionado estaba activo); se corrigió el script, se agregó una prueba y la ronda 1 se repitió desde el ataque. Está en la bitácora.
- El aporte de StatsNostra fue el objeto por defecto: Mari propuso entregar un archivo con la forma real de su API y un hash, que se incorporará con `seguridad/datos/objetos/` si llega antes de la entrega.

## Mapeo

ATT&CK T1485 (Data Destruction) y T1486 (Data Encrypted for Impact); D3FEND D3-RO (Restore Object); ISO/IEC 27001:2022 A.8.13; NIST SP 800-53 CP-9 y CP-6.
