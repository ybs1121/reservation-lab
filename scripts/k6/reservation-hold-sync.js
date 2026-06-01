import http from 'k6/http';
import { check } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const RUN_ID = __ENV.RUN_ID || `k6-${Date.now()}`;
const VUS = Number(__ENV.VUS || 20);
const DURATION = __ENV.DURATION || '30s';
const SLOT_CAPACITY = Number(__ENV.SLOT_CAPACITY || 10000);
const PARTY_SIZE = Number(__ENV.PARTY_SIZE || 1);
const SLOT_DATE = __ENV.SLOT_DATE || '2026-06-01';
const SLOT_TIME = __ENV.SLOT_TIME || '18:00';
const PHONE_RUN_PREFIX = String(Date.now() % 100000).padStart(5, '0');

const HEADERS = {
  'Content-Type': 'application/json',
};

const holdSuccessRate = new Rate('hold_success_rate');
const holdHttpDuration = new Trend('hold_http_duration', true);

export const options = {
  scenarios: {
    sync_hold_load: {
      executor: 'constant-vus',
      vus: VUS,
      duration: DURATION,
    },
  },
  thresholds: {
    hold_success_rate: ['rate>0.95'],
    hold_http_duration: ['p(95)<1000'],
  },
};

export function setup() {
  const restaurantId = `${RUN_ID}-restaurant`;
  const slotId = `${RUN_ID}-slot`;

  postJson('/restaurants', {
    restaurantId,
    name: 'k6 sync restaurant',
    address: 'k6 sync address',
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
  // 같은 user + slot 요청은 기존 hold를 반환하므로, 부하 테스트에서는 매 요청마다 user를 새로 만든다.
  const userId = `${RUN_ID}-user-${__VU}-${__ITER}`;

  postJson('/users', {
    userId,
    name: 'k6 sync user',
    phone: phoneFor(__VU, __ITER),
    createdBy: 'k6',
  }, { api: 'setup-user' });

  const response = postJson('/reservation-holds', {
    slotId: data.slotId,
    userId,
    partySize: PARTY_SIZE,
  }, { api: 'hold-sync' });

  holdHttpDuration.add(response.timings.duration);

  const body = response.json();
  const success = check(response, {
    'sync hold request returned 200': (res) => res.status === 200,
    'sync hold request succeeded': () => body.success === true,
    'sync hold id exists': () => Boolean(body.data && body.data.holdId),
  });

  holdSuccessRate.add(success);
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
