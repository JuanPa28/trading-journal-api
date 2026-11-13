// Perfil de carga FIJO de los experimentos de caos. Es el mismo en la ronda 0 y en la ronda 1:
// comparar mediciones con cargas distintas invalida el resultado.
//
//   Escalones: 5 usuarios virtuales (60 s), luego 15 (60 s) y luego 30 (60 s). Total: 3 minutos.
//   Petición:  GET {BASE_URL}/api/v2/trades/{TRADE_ID}, con un X-Trace-Id distinto por iteración.
//   Umbrales:  menos de 1 % de fallos y p95 por debajo de P95_MS (1600 ms, fijado al medir el estado estable).
//
// Uso:
//   k6 run -e BASE_URL=https://... -e TRADE_ID=5540 -e P95_MS=1500 \
//          --summary-export caos/resultados/ronda0.json caos/carga/perfil.js
import http from 'k6/http';
import { check } from 'k6';

const P95_MS = Number(__ENV.P95_MS || 1600);

export const options = {
  scenarios: {
    perfil: {
      executor: 'ramping-vus',
      startVUs: 0,
      gracefulRampDown: '10s',
      stages: [
        { duration: '60s', target: 5 },
        { duration: '60s', target: 15 },
        { duration: '60s', target: 30 },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: [`p(95)<${P95_MS}`],
  },
};

export default function () {
  const response = http.get(`${__ENV.BASE_URL}/api/v2/trades/${__ENV.TRADE_ID}`, {
    headers: { 'X-Trace-Id': `k6-${__VU}-${__ITER}` },
  });
  check(response, { 'status 200': (r) => r.status === 200 });
}
