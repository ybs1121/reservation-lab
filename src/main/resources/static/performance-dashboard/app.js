const form = document.querySelector('#runForm');
const startButton = document.querySelector('#startButton');
const refreshButton = document.querySelector('#refreshButton');
const runStatus = document.querySelector('#runStatus');
const runMeta = document.querySelector('#runMeta');
const commandLine = document.querySelector('#commandLine');
const runOutput = document.querySelector('#runOutput');
const baseUrlInput = document.querySelector('#baseUrl');

const cards = {
  primary: document.querySelector('#primaryMetric'),
  success: document.querySelector('#successMetric'),
  secondary: document.querySelector('#secondaryMetric'),
  throughput: document.querySelector('#throughputMetric'),
};

let currentRunId = null;
let pollTimer = null;

baseUrlInput.value = window.location.origin;

form.addEventListener('submit', async (event) => {
  event.preventDefault();
  startButton.disabled = true;

  const response = await fetch('/api/performance/k6/runs', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(readForm()),
  });
  const body = await response.json();
  renderRun(body.data);
  startPolling(body.data.id);
});

refreshButton.addEventListener('click', async () => {
  if (currentRunId) {
    await loadRun(currentRunId);
    return;
  }
  await loadLatest();
});

loadLatest();

function readForm() {
  return {
    scenario: document.querySelector('input[name="scenario"]:checked').value,
    baseUrl: document.querySelector('#baseUrl').value,
    vus: Number(document.querySelector('#vus').value),
    duration: document.querySelector('#duration').value,
    slotCapacity: Number(document.querySelector('#slotCapacity').value),
    partySize: Number(document.querySelector('#partySize').value),
    pollIntervalMs: Number(document.querySelector('#pollIntervalMs').value),
    maxPolls: 60,
  };
}

async function loadLatest() {
  const response = await fetch('/api/performance/k6/runs/latest');
  const body = await response.json();
  if (body.data) {
    renderRun(body.data);
    if (body.data.status === 'RUNNING' || body.data.status === 'READY') {
      startPolling(body.data.id);
    }
  }
}

async function loadRun(id) {
  const response = await fetch(`/api/performance/k6/runs/${id}`);
  const body = await response.json();
  renderRun(body.data);
}

function startPolling(id) {
  currentRunId = id;
  clearInterval(pollTimer);
  pollTimer = setInterval(async () => {
    await loadRun(id);
  }, 500);
}

function renderRun(run) {
  if (!run) {
    return;
  }

  currentRunId = run.id;
  runStatus.textContent = run.status;
  runStatus.className = `status ${run.status.toLowerCase()}`;
  runMeta.textContent = `#${run.id} ${run.scenario} / started ${run.startedAt || '-'}`;
  commandLine.textContent = (run.command || []).join(' ');
  runOutput.textContent = (run.errorMessage || '') + tail(run.output || '', 9000);
  renderMetrics(run);

  if (run.status !== 'RUNNING' && run.status !== 'READY') {
    clearInterval(pollTimer);
    startButton.disabled = false;
  }
}

function renderMetrics(run) {
  const summary = run.summary || {};
  const metrics = summary.metrics || {};

  if (run.scenario === 'ASYNC') {
    setCard(cards.primary, 'Accept p95', trend(metrics, 'hold_request_accept_duration', 'p(95)'), 'POST request accepted');
    setCard(cards.success, 'Succeeded', rate(metrics, 'hold_request_succeeded_rate'), 'Final status SUCCEEDED');
    setCard(cards.secondary, 'Result Ready p95', trend(metrics, 'hold_result_ready_duration', 'p(95)'), 'Accepted to final status');
    setCard(cards.throughput, 'Completed', rate(metrics, 'hold_request_completed_rate'), 'Status polling finished');
    return;
  }

  setCard(cards.primary, 'Hold p95', trend(metrics, 'hold_http_duration', 'p(95)'), 'POST /reservation-holds');
  setCard(cards.success, 'Succeeded', rate(metrics, 'hold_success_rate'), 'Hold created');
  setCard(cards.secondary, 'HTTP p95', trend(metrics, 'http_req_duration', 'p(95)'), 'All HTTP requests');
  setCard(cards.throughput, 'Iterations', count(metrics, 'iterations'), 'Completed loops');
}

function setCard(card, label, value, hint) {
  card.querySelector('.label').textContent = label;
  card.querySelector('strong').textContent = value;
  card.querySelector('small').textContent = hint;
}

function trend(metrics, name, key) {
  const value = metricValues(metrics, name)[key];
  if (value === undefined || value === null) {
    return '-';
  }
  return `${Number(value).toFixed(2)} ms`;
}

function rate(metrics, name) {
  const values = metricValues(metrics, name);
  const value = values.rate ?? values.value;
  if (value === undefined || value === null) {
    return '-';
  }
  return `${(Number(value) * 100).toFixed(2)}%`;
}

function count(metrics, name) {
  const value = metricValues(metrics, name).count;
  if (value === undefined || value === null) {
    return '-';
  }
  return String(Math.round(Number(value)));
}

function metricValues(metrics, name) {
  const metric = metrics[name] || {};
  return metric.values || metric;
}

function tail(value, maxLength) {
  if (value.length <= maxLength) {
    return value;
  }
  return value.slice(value.length - maxLength);
}
