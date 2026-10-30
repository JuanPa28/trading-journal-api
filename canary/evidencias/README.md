# Evidencias de ejecución — Entrega 4 (Canary Deployment)

Resultado de ejecutar el despliegue canary en Google Cloud Run el 29 de octubre de 2026.

| Archivo | Qué demuestra |
|---|---|
| [`01-promocion-hasta-100.log`](01-promocion-hasta-100.log) | **Promoción exitosa.** Pipeline completo: prueba de humo al 0 %, etapa 1 (10 %), etapa 2 (50 %) y etapa 3 (100 %). En cada etapa se comparan stable y canary bajo la misma carga y la puerta de calidad decide PROMOTE. [Ver la ejecución](https://github.com/JuanPa28/trading-journal-api/actions/runs/40997205603). |
| [`02-rollback-por-falla-inyectada.log`](02-rollback-por-falla-inyectada.log) | **Rollback inmediato.** Desde la etapa 2 el canary responde 500 a cerca del 40 % de las peticiones (regresión simulada con `X-Inject-Fault`). La puerta de calidad decide ABORT y el tráfico vuelve a 100 % stable. El paso del pipeline termina en rojo a propósito: un rollback es una versión rechazada. [Ver la ejecución](https://github.com/JuanPa28/trading-journal-api/actions/runs/40997762252). |
| [`03-trafico-y-versiones.txt`](03-trafico-y-versiones.txt) | **Acceso al canary al 0 %** por su URL propia, **el cambio funcional** (`setupScore` y `setupGrade` solo en el canary), **el reparto real** de tráfico (100 peticiones con 30 % configurado: 37 canary y 63 stable) y el estado final de las revisiones. |

## Cómo se obtuvo cada evidencia

1. **Pipeline:** *Actions → Canary - Desplegar y promover → Run workflow*. La primera ejecución construye las dos imágenes y las publica por digest; las siguientes pueden reutilizarlas (`skip_build`).
   - Promoción: `bake_seconds = 60`, `fault_rate = 0`.
   - Rollback: `bake_seconds = 60`, `fault_rate = 0.4`, `fault_from_stage = etapa-2`.
2. **Reparto y versiones:** `python canary/rollout.py traffic --canary-percent 30` y 100 peticiones con `curl` leyendo la cabecera `X-App-Track`.
3. **Tablero:** *Cloud Monitoring → Dashboards → «Canary: stable vs canary»*, con una línea por revisión.

Los logs conservan los pasos del despliegue y de cada etapa; se omiten los de preparación del runner. GitHub oculta los secrets (aparecen como `***`).

## Un hallazgo real que quedó corregido

La primera ejecución completa abortó al llegar al 100 % con una latencia p95 de 924 ms contra un límite de 885 ms. No era una regresión: la línea base se había medido en la etapa del 50 %, cuando cada versión atendía la mitad de la carga, y la latencia sube con la carga. Se corrigió comparando contra la medición de la estable con **mayor carga** (la etapa del 10 %, con el 90 % del tráfico); ver `gate.pick_baseline` y su prueba.
