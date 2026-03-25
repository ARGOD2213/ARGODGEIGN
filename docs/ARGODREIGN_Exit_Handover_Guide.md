# ARGODREIGN Exit Handover Guide

Generated on: 2026-03-10 09:18:14 +05:30
Project path: C:\Users\pavan\Desktop\ARGODREIGN

## 0) Immediate status (done now)
- Last-night leftover process was stopped.
- Running containers check: currently NONE.
- Previously running legacy containers found and stopped: iot-alert-engine, iot-mysql, iot-redis.
- Auto-restart disabled for iot-alert-engine (estart=no) so it will not auto-start again.

## 1) What this system is
ARGODREIGN is an industrial IoT alert engine that ingests sensor events, applies threshold logic, enriches with weather context, runs AI risk analysis, stores events, and triggers alerts.

Primary flow:
1. Event comes from API or SQS (including Lambda-driven CSV simulation).
2. Threshold engine marks NORMAL/WARNING/CRITICAL.
3. Weather enrichment is applied (if enabled).
4. AI risk analysis is applied through the local predictive ensemble, with rule-based fallback if needed.
5. Event is saved to DynamoDB.
6. Live data and alerts are cached in Redis.
7. Warning/Critical alerts are pushed to SNS + SQS.
8. Dashboard/UI and APIs expose live state, history, and prediction.

## 2) Tech stack used (how + why)
- Java 17 + Spring Boot 3.2: main backend runtime and REST APIs.
  Why: stable enterprise framework with scheduling, validation, and DI.
- Maven: build + dependency management.
  Why: standard Java build tool.
- Docker + Docker Compose: local reproducible environment.
  Why: single-command startup with app + MySQL + Redis.
- Redis: live dashboard/event cache + latest alert hash.
  Why: fast in-memory reads for real-time UI.
- DynamoDB (AWS SDK v2 Enhanced Client): sensor event persistence.
  Why: scalable key-value store with simple deviceId+timestamp access.
- SNS: outbound alert notifications.
  Why: fan-out notifications to email/SMS/subscribers.
- SQS (FIFO + DLQ): queue ingest + decoupling + retries.
  Why: resilient asynchronous processing.
- OpenWeatherMap API: weather context enrichment.
  Why: better root-cause insight for field conditions.
- Local predictive ensemble: in-process incident reasoning without paid runtime inference APIs.
  Why: richer risk summary and recommendation.
- Python Lambda: CSV from S3 -> SQS message simulator.
  Why: controlled replay/demo of historical sensor data.
- ECS/ECR scripts: container deployment automation.
  Why: repeatable push-to-cloud flow.

## 3) Exact project file map (where to edit for new development)
- API endpoints: src/main/java/com/mahindra/iot/controller/SensorController.java
- Ingestion core pipeline: src/main/java/com/mahindra/iot/service/SensorEventService.java
- Threshold values/rules: src/main/java/com/mahindra/iot/config/ThresholdConfig.java
- Sensor catalog/types: src/main/java/com/mahindra/iot/enums/SensorType.java
- AI logic/fallback: src/main/java/com/mahindra/iot/service/PredictiveAnalysisService.java
- Weather enrichment logic: src/main/java/com/mahindra/iot/service/WeatherCorrelationService.java
- Weather API client: src/main/java/com/mahindra/iot/service/WeatherService.java
- SQS poll + enqueue: src/main/java/com/mahindra/iot/service/SqsService.java
- SNS publish format: src/main/java/com/mahindra/iot/service/SnsAlertService.java
- Prediction endpoint logic: src/main/java/com/mahindra/iot/service/PredictiveMaintenanceService.java
- Dashboard aggregation: src/main/java/com/mahindra/iot/service/DashboardService.java
- Static dashboard UI: src/main/resources/static/index.html
- App config: src/main/resources/application.properties
- Local infra orchestration: docker-compose.yml
- Image build: Dockerfile
- AWS bootstrap/deploy scripts: scripts/*.sh and scripts/*.ps1
- Lambda simulator: lambda/lambda_function.py

## 4) Start this project in a code editor (VS Code recommended)
1. Open VS Code.
2. File -> Open Folder -> C:\Users\pavan\Desktop\ARGODREIGN
3. Install extensions:
   - Extension Pack for Java
   - Spring Boot Extension Pack
   - Docker
4. Open terminal inside VS Code (PowerShell).
5. Verify tools:
   - docker --version
   - docker compose version (or docker-compose --version)
   - mvn -v
6. Copy env template if needed:
   - Copy-Item .env.example .env
7. Fill required values in .env:
   - AWS_REGION
   - AWS_SNS_TOPIC_ARN
   - AWS_SQS_QUEUE_URL
   - AWS_SQS_DLQ_URL
   - optional WEATHER_API_KEY

## 5) Start locally with Docker
From C:\Users\pavan\Desktop\ARGODREIGN:
- docker compose up --build
(If your Docker supports old syntax, use docker-compose up --build)

Open:
- Dashboard: http://localhost:8080/
- Swagger: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/api/v1/health

Stop:
- docker compose down

## 6) Docker refresh options (exact meaning)
A) Quick app restart (no rebuild):
- docker compose restart app

B) Rebuild app only (keep DB/cache volumes):
- docker compose up -d --build app

C) Full stack restart (keep named volume data):
- docker compose down
- docker compose up -d --build

D) Full clean reset including MySQL data wipe:
- docker compose down -v
- docker compose up -d --build

E) Force-stop all old legacy containers if they appear:
- docker stop iot-alert-engine iot-mysql iot-redis
- docker update --restart=no iot-alert-engine

## 7) GitHub push setup from this repo (first-time remote)
Current git state found:
- Branch: master
- Remote: none configured

Steps:
1. Create a new empty repo on GitHub (do NOT add README from GitHub UI).
2. In terminal:
   - git remote add origin https://github.com/<your-user>/<your-repo>.git
3. Push current branch:
   - git push -u origin master

Optional (if you want main instead of master):
- git branch -M main
- git push -u origin main

If remote already exists in future:
- git remote -v
- git push

## 8) Day-to-day development workflow (where to edit + commit)
1. Pull latest:
   - git pull
2. Create feature branch:
   - git checkout -b feature/<short-name>
3. Make changes in relevant files (see Section 3 file map).
4. Test locally:
   - docker compose up -d --build
   - curl http://localhost:8080/api/v1/health
5. Review changes:
   - git status
   - git diff
6. Commit:
   - git add .
   - git commit -m "feat: <what changed>"
7. Push:
   - git push -u origin feature/<short-name>
8. Create PR on GitHub.

## 9) API quick checks
Ingest test:
- curl -X POST http://localhost:8080/api/v1/sensor/ingest -H "Content-Type: application/json" -d '{"deviceId":"MOTOR-01","sensorType":"TEMPERATURE","value":47.2,"unit":"C","location":"Line-A"}'

Overview:
- curl http://localhost:8080/api/v1/dashboard/overview

Prediction:
- curl "http://localhost:8080/api/v1/device/MOTOR-01/prediction?forecastSteps=8"

## 10) Connect to AWS locally (recommended + alternatives)
Option A (recommended for local host tools): AWS CLI profile
1. aws configure --profile argodreign
2. aws sts get-caller-identity --profile argodreign
3. In terminal session:
   -  = "argodreign"

Option B (what Docker compose app currently uses): environment variables in .env
- AWS_ACCESS_KEY_ID
- AWS_SECRET_ACCESS_KEY
- AWS_REGION
- AWS_SNS_TOPIC_ARN
- AWS_SQS_QUEUE_URL
- AWS_SQS_DLQ_URL
Then restart app container:
- docker compose up -d --build app

Important:
- Inside Docker, this project is currently wired to explicit env vars.
- For production, use IAM task roles (not static keys).

## 11) Disconnect from AWS locally (clean and safe)
1. Stop app and queues from local runtime:
   - docker compose down
2. Remove AWS env vars from active PowerShell session:
   - Remove-Item Env:AWS_ACCESS_KEY_ID -ErrorAction SilentlyContinue
   - Remove-Item Env:AWS_SECRET_ACCESS_KEY -ErrorAction SilentlyContinue
   - Remove-Item Env:AWS_SESSION_TOKEN -ErrorAction SilentlyContinue
   - Remove-Item Env:AWS_PROFILE -ErrorAction SilentlyContinue
3. If using AWS SSO profile:
   - aws sso logout
4. In .env, clear credentials for safety (leave blank) and restart only when needed.
5. Validate disconnection (should fail if no credentials available):
   - aws sts get-caller-identity

## 12) How to check logs (local + AWS)
Local Docker logs:
- docker compose logs -f app
- docker compose logs -f mysql
- docker compose logs -f redis

Single container logs:
- docker logs -f argodreign-app

Filter for alerts/errors in logs:
- docker compose logs app | Select-String -Pattern "ALERT|ERROR|WARN|SQS|SNS|DynamoDB"

AWS CloudWatch logs (if ECS configured):
- aws logs describe-log-groups --region <region>
- aws logs tail /ecs/<service-log-group> --follow --region <region>

ECS service/task checks:
- aws ecs describe-services --cluster <cluster> --services <service> --region <region>
- aws ecs list-tasks --cluster <cluster> --service-name <service> --region <region>

SQS depth + DLQ checks:
- aws sqs get-queue-attributes --queue-url <queue-url> --attribute-names ApproximateNumberOfMessages ApproximateNumberOfMessagesNotVisible
- aws sqs receive-message --queue-url <dlq-url> --max-number-of-messages 10

## 13) Production go-live checklist (must-do)
Security:
- Rotate any exposed AWS keys immediately.
- Do NOT store long-lived AWS keys in .env for production.
- Move secrets to AWS Secrets Manager or SSM Parameter Store.
- Use ECS task IAM role with minimum permissions.

Reliability:
- Enable health checks and autoscaling for ECS service.
- Set CloudWatch alarms for ECS unhealthy task count, SQS queue depth, DynamoDB errors/throttles.
- Verify DLQ redrive and replay plan.
- Validate rollback strategy to previous image tag.

Performance:
- Load test ingest endpoint and SQS poll throughput.
- Check Redis memory usage and TTL behavior.

Observability:
- Structured logs retained in CloudWatch.
- Alerting on 5xx rates, high latency, and queue backlog.

Cost control:
- Stop non-production ECS service when idle (desiredCount=0).
- Remember ALB/NAT/public IPv4 may still bill even with ECS tasks at zero.

Compliance/operations:
- Backup/retention policy defined for DynamoDB.
- Runbook available for on-call (incident and recovery steps).

## 14) ECS operations (start/stop demo + deploy)
Start demo:
- .\scripts\start-demo.ps1 -AwsRegion ap-south-1 -EcsCluster <cluster> -EcsService <service> -DesiredCount 1 -WaitForStable

Stop demo:
- .\scripts\stop-demo.ps1 -AwsRegion ap-south-1 -EcsCluster <cluster> -EcsService <service> -WaitForStable

Deploy new image:
- .\scripts\deploy-ecs.ps1 -AwsRegion ap-south-1 -AwsAccountId <account-id> -EcrRepo iot-alert-engine -EcsCluster <cluster> -EcsService <service> -ImageTag v2

## 15) Known risks observed right now
- This repo local .env currently contains real-looking AWS/API secrets.
  Action: rotate credentials and regenerate keys immediately.
- App config points to Docker hostnames (mysql, redis) by default.
  If running app directly on host (not in container), override datasource/redis host to localhost.
- There is an unusual local folder name: {scripts,k8s,lambda,.github
  Action: verify if needed; keep repo clean before release.

## 16) Final clear-cut exit steps (handover)
1. Ensure nothing is running locally unless needed:
   - docker compose down
   - docker ps (should be empty)
2. Ensure credentials are not left active:
   - clear env vars and optionally aws sso logout
3. Push code to GitHub remote (Section 7).
4. Share this document with next developer.
5. Next developer starts from Sections 4, 5, and 8.

End of document.
