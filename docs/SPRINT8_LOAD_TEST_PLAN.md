# Sprint 8 Load Test Plan

## Goal

Demonstrate that ARGUS can sustain bursty ingest traffic without breaking alert flow, dashboard responsiveness, or cache-backed reads.

## Harness

- Script: `scripts/load-test-ingest.js`
- Tool: `k6`
- Primary target: `POST /api/v1/sensor/ingest`
- Supporting checks:
  - `GET /api/v1/health`
  - `GET /api/platform/status`
  - `GET /api/v1/dashboard/overview`

## Scenarios

1. Baseline steady-state
   - 10 virtual users for 5 minutes
2. Alert-heavy burst
   - 40 virtual users for 3 minutes
   - 30% WARNING, 10% CRITICAL payload mix
3. Recovery phase
   - 5 virtual users for 2 minutes

## Success Criteria

- `POST /api/v1/sensor/ingest` p95 under 1200 ms
- Error rate under 1%
- `GET /api/v1/health` remains available throughout run
- No unbounded memory growth observed in app logs
- Platform status endpoint remains responsive

## Evidence To Capture

- k6 summary output
- Application logs during run
- Queue depth before/after run
- Screenshot of platform/compliance dashboards after run

## Notes

- This repo includes the harness and thresholds.
- Actual Sprint 8 load-test completion still requires executing the test against a running environment and archiving the evidence.
