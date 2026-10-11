
import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:18085';
const RUN_ID = __ENV.PERF_RUN_ID;

const allowedUrlPattern =
    /^http:\/\/(localhost|127\.0\.0\.1|host\.docker\.internal)(:\d{1,5})?$/;

if (!allowedUrlPattern.test(BASE_URL)) {
  throw new Error('Only local development BASE_URL permitted');
}

if (__ENV.PERF_ALLOW_WRITES !== 'YES_ISOLATED_DATABASE') {
  throw new Error('Race test requires an isolated database');
}

if (!RUN_ID) {
  throw new Error('PERF_RUN_ID is required');
}

const requestFailures = new Rate('race_request_failures');
const validatedRequests = new Counter('race_validated_requests');

export const options = {
  scenarios: {
    one_race: {
      executor: 'shared-iterations',
      vus: 1,
      iterations: 1,
      maxDuration: '30s'
    }
  },
  thresholds: {
    checks: ['rate==1'],
    race_request_failures: ['rate==0'],
    race_validated_requests: ['count==5'],
    http_reqs: ['count==5']
  }
};

export default function () {
  const idempotencyKey = `perf-race-${RUN_ID}`;
  const payload = JSON.stringify({
    amount: '10.00',
    currency: 'HUF'
  });

  const requests = Array.from({ length: 5 }, () => [
    'POST',
    `${BASE_URL}/api/payments`,
    payload,
    {
      headers: {
        'Content-Type': 'application/json',
        'Idempotency-Key': idempotencyKey
      },
      timeout: '10s'
    }
  ]);

  const responses = http.batch(requests);

  const ids = responses.map(response => {
    try {
      return response.json('id');
    } catch (_) {
      return null;
    }
  });

  const expectedId = ids[0];

  for (let i = 0; i < responses.length; i++) {
    const response = responses[i];

    const passed = check(response, {
      'POST returns 201': r => r.status === 201,
      'POST returns payment ID': () => !!ids[i],
      'Same payment ID for all requests': () =>
          !!expectedId && ids[i] === expectedId
    });

    requestFailures.add(!passed);
    validatedRequests.add(1);
  }

  console.log(
      `Race test: key=${idempotencyKey}, ` +
      `statuses=${responses.map(r => r.status).join(',')}, ` +
      `uniquePaymentIds=${new Set(ids.filter(Boolean)).size}`
  );
}
