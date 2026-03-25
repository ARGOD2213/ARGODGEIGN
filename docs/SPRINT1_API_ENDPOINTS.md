# Sprint 1 API Endpoints

These endpoints are added for the Sprint 1 foundation milestone.

## Platform and Admin

- `GET /api/platform/status`
  - Returns runtime status, rule engine mode, predictive intelligence availability, DLQ depth, and last alert timestamp.

- `GET /api/admin/dlq-status`
  - Returns DLQ queue depth from SQS attributes.
  - Includes a note to use CloudWatch for `ApproximateAgeOfOldestMessage`.

## Machine Endpoints

- `GET /api/machines`
  - Returns machine list with latest status per machine.

- `GET /api/machines/{id}/alerts?limit=10`
  - Returns last warning/critical alerts for a machine.

- `GET /api/machines/{id}/trend?hours=24&machineClass=compressor`
  - Returns trend points from Athena (fallback to DynamoDB if Athena unavailable).
  - `machineClass` is optional but recommended for partition-pruned Athena queries.
  - Cached for 5 minutes (Caffeine cache).

## KPI Endpoint

- `GET /api/kpi/oee/{machineId}?hours=24&machineClass=compressor`
  - Returns OEE components: availability, performance, quality, and OEE%.
  - Uses Athena primary path with DynamoDB fallback.
  - Cached for 5 minutes (Caffeine cache).
