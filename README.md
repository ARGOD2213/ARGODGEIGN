# ARGUS — Event-Driven Industrial IoT Monitoring Platform

## Sprint Status

- Sprint 1: `CLOSED`
- Sprint 2: `CLOSED` (Day 1 to Day 14 evidence committed)
- Sprint 3: `CLOSED` (RB-09, RB-15, all dashboards fully wired)
- Sprint 4: `PENDING`
- Active dashboards:
  - `/index.html` (overview)
  - `/machine.html` (machine-wise)
  - `/safety.html` (human safety)
  - `/plant.html` (plant-wide)
  - `/compliance.html` (compliance)

## Mobile Browser Space

- Open this from phone for live handoff updates:
  - `docs/MOBILE_CHAT_SPACE.md`
- Async no-desktop mode:
  - `docs/ASYNC_OFFICE_MODE.md`
  - `docs/REVIEW_INBOX.md`

## Start/Stop from Phone

Step 1 (one time on laptop):
```bash
bash scripts/setup-ec2.sh
```
- Note the `EC2_INSTANCE_ID` printed by script.
- Add all secrets listed in `docs/GITHUB_SECRETS_SETUP.md`.

Step 2 (from phone anytime):
- GitHub app -> Actions -> `START IoT Server` -> Run workflow
- Wait ~3 minutes -> open URL from workflow summary

Step 3 (after demo - ALWAYS DO THIS):
- GitHub app -> Actions -> `STOP IoT Server` -> Run workflow
- Stops billing immediately

Cost target: `~$7 total for 6 months` at `~1 hr/day` usage.

## Sprint 1 foundation (current)

- Data contract and validation:
  - `docs/schema/sensor_event_schema.json`
  - `docs/schema/source_to_canonical_mapping.json`
  - `scripts/validate-industrial-csv.ps1`
  - `docs/SPRINT1_API_ENDPOINTS.md`
- Partitioned analytics path (S3 + Athena):
  - `scripts/partition-industrial-csv.ps1`
  - `scripts/setup-athena-partitioned.ps1`
  - `docs/SPRINT1_DATA_FOUNDATION_RUNBOOK.md`
- Serverless safety rule engine:
  - `lambda/rule_engine/handler.py`
  - `lambda/rule_engine/config/rules.json`
  - `scripts/deploy-rule-engine-lambda.ps1`
  - `scripts/setup-rule-engine-trigger.ps1`
  - `docs/RULE_ENGINE_LAMBDA_SETUP.md`
- Guardrails:
  - `scripts/setup-budget-guardrails.ps1`
  - `scripts/setup-ec2-autostop-alarm.ps1`
  - `docs/SPRINT1_GUARDRAILS.md`
- Architecture decisions (ADRs):
  - `docs/adr/ADR-001-rule-engine-lambda.md`
  - `docs/adr/ADR-002-no-ot-write-path.md`
  - `docs/adr/ADR-003-llm-advisory-labels-mandatory.md`
  - `docs/adr/ADR-004-athena-partitioning-strategy.md`

> Budget note: For this repository's demo budget mode, use EC2 start/stop workflows and avoid always-on infrastructure.

Production IoT monitoring backend with:
- 22 sensor threshold evaluation
- Weather-aware enrichment (OpenWeatherMap)
- AI risk analysis (Gemini + rule fallback)
- Alerting via SNS + SQS
- Persistence in DynamoDB
- In-memory response cache with S3 LLM analysis cache
- Built-in dashboard UI

## What was added now
- New API: `GET /api/v1/dashboard/overview`
- New API: `GET /api/v1/device/{deviceId}/prediction?forecastSteps=6`
- New built-in UI: `http://localhost:8080/` (live dashboard)
- Cloud-lite runtime defaults:
  - SQL auto-config disabled by default
  - In-memory cache (no Redis dependency)
- Secure env template: `.env.example`
- Deployment scripts:
  - `scripts/bootstrap-aws.sh`
  - `scripts/setup-github-oidc.ps1`
  - `scripts/setup-ec2.sh`

## 1. Local run with Docker

1. Copy env template:
```bash
cp .env.example .env
```

2. Fill required values in `.env`:
- `AWS_REGION`
- `AWS_SNS_TOPIC_ARN`
- `AWS_SQS_QUEUE_URL`
- `AWS_SQS_DLQ_URL`
- optional: `WEATHER_API_KEY`, `GEMINI_API_KEY`

3. Start stack:
```bash
docker-compose up --build
```

4. Open:
- Dashboard UI: `http://localhost:8080/`
- Swagger: `http://localhost:8080/swagger-ui.html`
- Health: `http://localhost:8080/api/v1/health`

## 2. Connect S3 sensor data through Lambda

Upload your CSV to S3:
`s3://iot-alert-engine-mahindra/data/raw/your_file.csv`
Deploy Lambda from `lambda/rule_engine/handler.py`
Set env vars: `S3_BUCKET`, `CSV_KEY`, `SQS_URL`, `BATCH_SIZE`
Trigger: `{ "command": "CONTINUE" }`

## 3. AWS infra bootstrap

If resources are not created yet:
```bash
bash scripts/bootstrap-aws.sh
```

This creates:
- DynamoDB table `iot-sensor-events`
- SNS topic `iot-alerts`
- SQS FIFO queue + DLQ

## 4. AWS infrastructure (EC2 t3.micro)
This project runs on EC2 t3.micro with start/stop via GitHub Actions.
No ECS, no ALB, no NAT Gateway. Cost = $0 when stopped.
Setup: `bash scripts/setup-ec2.sh`

## 5. Demo control (from phone)
Start: GitHub app -> Actions -> START IoT Server -> Run workflow
Stop:  GitHub app -> Actions -> STOP IoT Server -> Run workflow

## 6. Architecture constraints
- Rule engine: AWS Lambda only (always-on, serverless)
- App server: EC2 t3.micro (start/stop for cost control)
- Storage: S3 + Athena for history. DynamoDB for alerts only.
- No ECS, no ALB, no Redis, no MySQL, no NAT Gateway.
- LLM: Gemini advisory only. AiAdvisoryWrapper on all outputs.

## 7. Useful API checks

Ingest:
```bash
curl -X POST http://localhost:8080/api/v1/sensor/ingest \
  -H "Content-Type: application/json" \
  -d '{"deviceId":"MOTOR-01","sensorType":"TEMPERATURE","value":47.2,"unit":"C","location":"Line-A"}'
```

Overview:
```bash
curl http://localhost:8080/api/v1/dashboard/overview
```

Prediction:
```bash
curl "http://localhost:8080/api/v1/device/MOTOR-01/prediction?forecastSteps=8"
```

Sprint 1 operations endpoints:
```bash
curl http://localhost:8080/api/platform/status
curl http://localhost:8080/api/admin/dlq-status
curl http://localhost:8080/api/machines
curl "http://localhost:8080/api/machines/MOTOR-01/alerts?limit=10"
curl "http://localhost:8080/api/machines/MOTOR-01/trend?hours=24"
curl "http://localhost:8080/api/kpi/oee/MOTOR-01?hours=24"
```

## 8. Mobile workflows (recommended)

Use these workflows from GitHub mobile app:

- `.github/workflows/start-server.yml`
- `.github/workflows/stop-server.yml`

Detailed one-time secrets setup:
- `docs/GITHUB_SECRETS_SETUP.md`
