// Perfil de carga FIJO de los experimentos de caos. Es el mismo en la ronda 0 y en la ronda 1:
// comparar mediciones con cargas distintas invalida el resultado.
//
//   Escalones: 5 usuarios virtuales (60 s), luego 15 (60 s) y luego 30 (60 s). Total: 3 minutos.
//   Petición:  GET {BASE_URL}/api/v2/trades/{TRADE_ID}, con un X-Trace-Id distinto por iteración.
//   Umbrales:  menos de 1 % de fallos y p95 por debajo de P95_MS (1600 ms, fijado al medir el estado estable).
//   Aborto:    si el éxito cae por debajo de 50 % o el p95 pasa de 15 s, k6 detiene la prueba.
//
// Inyección de falla opcional (el perfil NO cambia, solo se añaden cabeceras a cada petición):
//   FAULT_LATENCY_MS=3000  añade esa latencia a las llamadas del coordinador hacia un compañero
//   FAULT_TARGET=mari      a cuál (mari, pipe o all)
// Solo surten efecto si el coordinador tiene FAULT_HEADER_ENABLED=true; el efecto es solo
// para las peticiones de esta prueba.
//
// Uso:
//   k6 run -e BASE_URL=https://... -e TRADE_ID=5540 [-e FAULT_LATENCY_MS=3000 -e FAULT_TARGET=mari] \
//          --summary-export caos/resultados/ronda0.json caos/carga/perfil.js
import http from 'k6/http';
import { check } from 'k6';

const P95_MS = Number(__ENV.P95_MS || 1600);
const FAULT_LATENCY_MS = __ENV.FAULT_LATENCY_MS || '';
const FAULT_TARGET = __ENV.FAULT_TARGET || 'all';

export const options = {
  // Las estadísticas del resumen van aquí y no en la línea de comandos: los paréntesis de p(95) rompen el comando
  // cuando Chaos Toolkit lo ejecuta con /bin/sh en Linux (GitHub Actions).
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
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
    http_req_failed: ['rate<0.01', { threshold: 'rate<0.5', abortOnFail: true, delayAbortEval: '20s' }],
    http_req_duration: [`p(95)<${P95_MS}`, { threshold: 'p(95)<15000', abortOnFail: true, delayAbortEval: '20s' }],
  },
};

export default function () {
  const headers = { 'X-Trace-Id': `k6-${__VU}-${__ITER}` };
  if (FAULT_LATENCY_MS) {
    headers['X-Inject-Outbound-Latency-Ms'] = FAULT_LATENCY_MS;
    headers['X-Inject-Outbound-Target'] = FAULT_TARGET;
  }
  const response = http.get(`${__ENV.BASE_URL}/api/v2/trades/${__ENV.TRADE_ID}`, { headers });
  check(response, { 'status 200': (r) => r.status === 200 });
}
