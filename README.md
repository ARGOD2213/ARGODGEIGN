# ARGODREIGN IoT Alert Engine

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
- Secure env template: `.env.example`
- Deployment scripts:
  - `scripts/deploy-ecs.ps1`
  - `scripts/bootstrap-aws.sh`
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

## 4. Deploy container to ECS

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

## 5. Run only on demo days (budget mode)

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

## 8. Mobile-first operations (start/stop/deploy/logs from phone)

This repo now includes GitHub Actions workflows you can run from the GitHub mobile app:

- `.github/workflows/mobile-ecs-control.yml`
- `.github/workflows/mobile-deploy-ecs.yml`
- `.github/workflows/mobile-cloudwatch-logs.yml`
- `.github/workflows/mobile-cost-snapshot.yml`

Recommended auth mode: OIDC role assumption (no long-lived AWS keys).
Setup helper script:

- `scripts/setup-github-oidc.ps1`

Fallback mode (if OIDC is not ready yet): add GitHub Actions secrets:

- `AWS_ACCESS_KEY_ID`
- `AWS_SECRET_ACCESS_KEY`

Then in GitHub app:

1. Open repo -> `Actions`
2. Pick workflow
3. Tap `Run workflow`

Detailed step-by-step mobile guide:
- `docs/MOBILE_OPERATIONS_PLAYBOOK.md`

Cost and live-readiness guide:
- `docs/COST_LIMITATIONS_AND_LIVE_READINESS.md`
