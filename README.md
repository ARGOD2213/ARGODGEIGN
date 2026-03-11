# ARGODREIGN IoT Alert Engine

## 📱 Start/Stop from Phone

Step 1 (one time on laptop):
```bash
bash scripts/setup-ec2.sh
```
- Note the `EC2_INSTANCE_ID` printed by script.
- Add all secrets listed in `docs/GITHUB_SECRETS_SETUP.md`.

Step 2 (from phone anytime):
- GitHub app -> Actions -> `📱 START IoT Server` -> Run workflow
- Wait ~3 minutes -> open URL from workflow summary

Step 3 (after demo — ALWAYS DO THIS):
- GitHub app -> Actions -> `📱 STOP IoT Server` -> Run workflow
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

> Budget note: For this repository's demo budget mode, use EC2 start/stop workflows and avoid always-on ECS/ALB.

Production IoT monitoring backend with:
- 22 sensor threshold evaluation
- Weather-aware enrichment (OpenWeatherMap)
- AI risk analysis (Gemini + rule fallback)
- Alerting via SNS + SQS
- Persistence in DynamoDB
- Live cache in Redis
- Built-in dashboard UI

## What was added now
- New API: `GET /api/v1/dashboard/overview`
- New API: `GET /api/v1/device/{deviceId}/prediction?forecastSteps=6`
- New built-in UI: `http://localhost:8080/` (live dashboard)
- Cloud-lite runtime defaults:
  - SQL auto-config disabled by default (no mandatory MySQL in cloud demo mode)
  - Redis cache has in-memory fallback for low-cost demo operation
- Secure env template: `.env.example`
- Deployment scripts:
  - `scripts/deploy-ecs.ps1`
  - `scripts/bootstrap-aws.sh`
  - `scripts/setup-github-oidc.ps1`
  - `scripts/start-demo.ps1`
  - `scripts/stop-demo.ps1`

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

## 2. Connect your S3 dummy data through Lambda

Your file path mentioned: `C:\Users\pavan\Desktop\iot_sensor_data_100days.csv`

Flow:
1. Upload CSV to S3 bucket key like `data/iot_sensor_data_100days.csv`
2. Deploy Lambda from `lambda/lambda_function.py`
3. Set Lambda env vars:
- `S3_BUCKET`
- `CSV_KEY`
- `SQS_URL`
- `BATCH_SIZE`
4. Trigger Lambda with payload `{ "command": "CONTINUE" }`

Spring service polls SQS every 10 seconds and ingests into the platform.

## 3. AWS infra bootstrap

If resources are not created yet:
```bash
bash scripts/bootstrap-aws.sh
```

This creates:
- DynamoDB table `iot-sensor-events`
- SNS topic `iot-alerts`
- SQS FIFO queue + DLQ

## 4. Legacy ECS deployment (not recommended for this budget mode)

PowerShell example:
```powershell
./scripts/deploy-ecs.ps1 \
  -AwsRegion ap-south-1 \
  -AwsAccountId 123456789012 \
  -EcrRepo iot-alert-engine \
  -EcsCluster iot-cluster \
  -EcsService iot-api \
  -ImageTag v2
```

Script will:
1. Build Docker image
2. Push image to ECR
3. Force ECS rolling deployment

## 5. Legacy ECS demo control scripts

Start demo service:
```powershell
./scripts/start-demo.ps1 \
  -AwsRegion ap-south-1 \
  -EcsCluster iot-cluster \
  -EcsService iot-api \
  -DesiredCount 1 \
  -WaitForStable
```

Stop demo service:
```powershell
./scripts/stop-demo.ps1 \
  -AwsRegion ap-south-1 \
  -EcsCluster iot-cluster \
  -EcsService iot-api \
  -WaitForStable
```

Note:
- `stop-demo` sets ECS `desiredCount=0`.
- ALB, NAT Gateway, and public IPv4 can still incur charges while they exist.

## 6. Recommended production setup

- Use ECS Task IAM Role instead of static AWS keys
- Put API keys in AWS Secrets Manager or SSM Parameter Store
- Add CloudWatch alarms for:
  - ECS service unhealthy tasks
  - SQS queue depth
  - DynamoDB throttling
- Add WAF + ALB if public internet traffic is expected

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
