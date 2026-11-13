# Generador de carga (k6)

Un único perfil fijo, `perfil.js`, que se usa **igual** antes y después de cada mejora.

| Parámetro | Valor |
|---|---|
| Escalones | 5 usuarios virtuales 60 s → 15 durante 60 s → 30 durante 60 s (3 min) |
| Petición | `GET {BASE_URL}/api/v2/trades/{TRADE_ID}` con `X-Trace-Id` distinto por iteración |
| Umbrales | fallos < 1 % y p95 < `P95_MS` (se fija al medir el estado estable) |

## Ejecutarlo

Con k6 instalado:

```bash
k6 run -e BASE_URL=https://api-coordinador.jollybeach-fbb8b9ad.eastus.azurecontainerapps.io \
       -e TRADE_ID=<id de un trade existente> -e P95_MS=1500 \
       --summary-export caos/resultados/estado-estable-1.json caos/carga/perfil.js
```

O con Docker, sin instalar nada:

```bash
docker run --rm -i -v "$PWD:/work" -w /work grafana/k6 run \
  -e BASE_URL=... -e TRADE_ID=... --summary-export caos/resultados/estado-estable-1.json caos/carga/perfil.js
```

Si un umbral no se cumple, k6 termina con código distinto de cero: es lo que usa la puerta de calidad (chaos gate) del pipeline.

## Medir el estado estable

1. Con todo funcionando y sin fallos, corre el perfil **tres veces** (a la misma hora, con los mismos recursos).
2. Guarda cada resumen en `caos/resultados/` y anota éxito, p50, p95, p99 y throughput.
3. Declara el estado estable con la **mediana**: «éxito ≥ 99 % y p95 ≤ X ms», con X = p95 medido + 25 %.
