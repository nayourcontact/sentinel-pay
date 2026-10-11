import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';
import exec from 'k6/execution';

const baseUrl = __ENV.BASE_URL || 'http://localhost:8085';
const mode = __ENV.MODE || 'read';
if (!['read', 'write'].includes(mode)) throw new Error('MODE must be read or write');
if (!/^http:\/\/(localhost|127\.0\.0\.1|host\.docker\.internal)(:\d{1,5})?$/.test(baseUrl)) throw new Error('Only local development BASE_URL permitted');
if (mode === 'write' && (!/^http:\/\/host\.docker\.internal:18085$/.test(baseUrl) || __ENV.PERF_ALLOW_WRITES !== 'YES_ISOLATED_DATABASE')) throw new Error('Write tests require PERF_ALLOW_WRITES=YES_ISOLATED_DATABASE');
if (mode === 'read' && !__ENV.PAYMENT_ID) throw new Error('Read test requires PAYMENT_ID of a test payment');

const requestFailures = new Rate('payment_request_failures');
export const options = {
  scenarios: {
    payment: {
      executor: 'constant-arrival-rate', rate: mode === 'read' ? 5 : 1,
      timeUnit: '1s', duration: '30s', preAllocatedVUs: 5, maxVUs: 20,
    },
  },
  thresholds: {
    payment_request_failures: ['rate<0.01'],
    http_req_duration: ['p(95)<1000'],
    dropped_iterations: ['count==0'],
  },
};

export default function () {
  let res;
  if (mode === 'read') {
    res = http.get(`${baseUrl}/api/payments/${__ENV.PAYMENT_ID}`, { tags: { operation: 'payment-read' } });
    const passed = check(res, { 'GET returns 200': r => r.status === 200, 'GET returns requested payment': r => { try { return r.json('id') === __ENV.PAYMENT_ID; } catch (_) { return false; } } });
    requestFailures.add(!passed);
  } else {
    const idempotencyKey = `perf-${exec.vu.idInTest}-${exec.scenario.iterationInTest}`;
    res = http.post(`${baseUrl}/api/payments`, JSON.stringify({ amount: '10.00', currency: 'HUF' }), {
      headers: { 'Content-Type': 'application/json', 'Idempotency-Key': idempotencyKey },
      tags: { operation: 'payment-create' },
    });
    const passed = check(res, { 'POST returns 201': r => r.status === 201, 'POST returns payment id': r => { try { return !!r.json('id'); } catch (_) { return false; } } });
    requestFailures.add(!passed);
  }
}
