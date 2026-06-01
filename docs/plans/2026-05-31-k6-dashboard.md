# k6 Dashboard Plan

## Goal

Add a local-only dashboard that can run the reservation hold k6 scripts from the browser and show the main numbers in a way that is easy to compare.

This dashboard is a development tool, not a production feature.

## Scope

- Add a Spring MVC API that starts predefined k6 scripts.
- Add a static dashboard page under `src/main/resources/static`.
- Keep execution limited to known scripts:
  - `scripts/k6/reservation-hold-sync.js`
  - `scripts/k6/reservation-hold-async.js`
- Show the main k6 metrics:
  - sync: `hold_success_rate`, `hold_http_duration`
  - async: `hold_request_accepted_rate`, `hold_request_completed_rate`, `hold_request_succeeded_rate`, `hold_request_accept_duration`, `hold_result_ready_duration`

## Safety Rules

- The dashboard is enabled only with the `local` Spring profile.
- The dashboard is also guarded by `reservation-lab.k6-dashboard.enabled=true`.
- The API does not accept arbitrary command text.
- The API only accepts simple numeric/string options and maps scenario names to fixed script paths.

## User Flow

1. Run local infrastructure with `docker compose up -d`.
2. Run the app with `SPRING_PROFILES_ACTIVE=local`.
3. Open `/performance-dashboard/index.html`.
4. Choose sync or async.
5. Set VUs and duration.
6. Start a run and watch status.
7. Compare the metric cards.

## Result

- [x] local-only k6 run API
- [x] dashboard page
- [x] documentation update
- [x] compile/test verification
