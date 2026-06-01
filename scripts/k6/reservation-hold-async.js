import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const RUN_ID = __ENV.RUN_ID || `k6-${Date.now()}`;
const VUS = Number(__ENV.VUS || 20);
const DURATION = __ENV.DURATION || '30s';
const SLOT_CAPACITY = Number(__ENV.SLOT_CAPACITY || 10000);
const PARTY_SIZE = Number(__ENV.PARTY_SIZE || 1);
const SLOT_DATE = __ENV.SLOT_DATE || '2026-06-01';
const SLOT_TIME = __ENV.SLOT_TIME || '18:00';
const POLL_INTERVAL_MS = Number(__ENV.POLL_INTERVAL_MS || 500);
const MAX_POLLS = Number(__ENV.MAX_POLLS || 60);
const PHONE_RUN_PREFIX = String(Date.now() % 100000).padStart(5, '0');

const HEADERS = {
  'Content-Type': 'application/json',
};

const requestAcceptedRate = new Rate('hold_request_accepted_rate');
const requestCompletedRate = new Rate('hold_request_completed_rate');
const requestSucceededRate = new Rate('hold_request_succeeded_rate');
const requestAcceptDuration = new Trend('hold_request_accept_duration', true);
const resultReadyDuration = new Trend('hold_result_ready_duration', true);

export const options = {
  scenarios: {
    async_hold_load: {
      executor: 'constant-vus',
      vus: VUS,
      duration: DURATION,
    },
  },
  thresholds: {
    hold_request_accepted_rate: ['rate>0.95'],
    hold_request_completed_rate: ['rate>0.95'],
    hold_request_accept_duration: ['p(95)<500'],
  },
};

export function setup() {
  const restaurantId = `${RUN_ID}-restaurant`;
  const slotId = `${RUN_ID}-slot`;

  postJson('/restaurants', {
    restaurantId,
    name: 'k6 async restaurant',
    address: 'k6 async address',
    status: 'OPEN',
    createdBy: 'k6',
  }, { api: 'setup-restaurant' });

  postJson('/reservation-slots', {
    slotId,
    restaurantId,
    slotDate: SLOT_DATE,
    slotTime: SLOT_TIME,
    capacity: SLOT_CAPACITY,
    status: 'AVAILABLE',
    createdBy: 'k6',
  }, { api: 'setup-slot' });

  return { slotId };
}

export default function (data) {
  // 비동기 API는 접수(PENDING)와 실제 처리 완료(SUCCEEDED/FAILED)를 분리해서 봐야 한다.
  const userId = `${RUN_ID}-user-${__VU}-${__ITER}`;

  postJson('/users', {
    userId,
    name: 'k6 async user',
    phone: phoneFor(__VU, __ITER),
    createdBy: 'k6',
  }, { api: 'setup-user' });

  const startedAt = Date.now();
  const response = postJson('/reservation-hold-requests', {
    slotId: data.slotId,
    userId,
    partySize: PARTY_SIZE,
  }, { api: 'hold-request-accept' });

  requestAcceptDuration.add(response.timings.duration);

  const body = response.json();
  const accepted = check(response, {
    'async hold request returned 200': (res) => res.status === 200,
    'async hold request accepted': () => body.success === true && body.data && body.data.status === 'PENDING',
    'async request id exists': () => Boolean(body.data && body.data.requestId),
  });

  requestAcceptedRate.add(accepted);

  if (!accepted) {
    requestCompletedRate.add(false);
    requestSucceededRate.add(false);
    return;
  }

  const result = waitUntilFinished(body.data.requestId, startedAt);
  requestCompletedRate.add(Boolean(result));
  requestSucceededRate.add(Boolean(result && result.status === 'SUCCEEDED'));
}

function waitUntilFinished(requestId, startedAt) {
  for (let i = 0; i < MAX_POLLS; i += 1) {
    const response = http.get(`${BASE_URL}/reservation-hold-requests/${requestId}`, {
      tags: { api: 'hold-request-status' },
    });

    if (response.status === 200) {
      const body = response.json();
      const status = body.data && body.data.status;

      if (status === 'SUCCEEDED' || status === 'FAILED') {
        resultReadyDuration.add(Date.now() - startedAt);
        return body.data;
      }
    }

    sleep(POLL_INTERVAL_MS / 1000);
  }

  return null;
}

function postJson(path, body, tags) {
  return http.post(`${BASE_URL}${path}`, JSON.stringify(body), {
    headers: HEADERS,
    tags,
  });
}

function phoneFor(vu, iter) {
  return `010-${PHONE_RUN_PREFIX}${String(vu).padStart(2, '0')}-${String(iter).padStart(7, '0').slice(-7)}`;
}
