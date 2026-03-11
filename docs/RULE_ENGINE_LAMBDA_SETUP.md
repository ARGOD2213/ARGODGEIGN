# Lambda Rule Engine Setup (Sprint 1 - S1-03)

This keeps rule-based safety alerts active even when EC2 dashboards are stopped.

## 1) Deploy/update Lambda + upload rules

```powershell
cd C:\Users\pavan\Desktop\ARGODREIGN
powershell -ExecutionPolicy Bypass -File .\scripts\deploy-rule-engine-lambda.ps1 `
  -FunctionName "argodreign-rule-engine" `
  -Region "ap-south-1" `
  -SnsTopicArn "arn:aws:sns:ap-south-1:061039801536:iot-alerts" `
  -DedupTable "argodreign-dedup"
```

If Lambda does not exist yet:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\deploy-rule-engine-lambda.ps1 `
  -CreateIfMissing `
  -RoleArn "arn:aws:iam::061039801536:role/iot-lambda-role"
```

## 2) Create/verify SQS -> Lambda trigger

Recommended script:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\setup-rule-engine-trigger.ps1 `
  -FunctionName "argodreign-rule-engine" `
  -QueueArn "arn:aws:sqs:ap-south-1:061039801536:iot-events.fifo" `
  -Region "ap-south-1" `
  -BatchSize 10
```

Manual alternative:

```powershell
aws lambda create-event-source-mapping `
  --function-name argodreign-rule-engine `
  --event-source-arn arn:aws:sqs:ap-south-1:061039801536:iot-events.fifo `
  --batch-size 10 `
  --enabled `
  --region ap-south-1
```

If mapping already exists, AWS will return a duplication error; this is fine.

## 3) Create dedup table (one time)

Recommended script:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\setup-dedup-table.ps1 `
  -TableName "argodreign-dedup" `
  -Region "ap-south-1"
```

Manual alternative:

```powershell
aws dynamodb create-table `
  --table-name argodreign-dedup `
  --attribute-definitions AttributeName=dedup_key,AttributeType=S `
  --key-schema AttributeName=dedup_key,KeyType=HASH `
  --billing-mode PAY_PER_REQUEST `
  --region ap-south-1
```

Enable TTL:

```powershell
aws dynamodb update-time-to-live `
  --table-name argodreign-dedup `
  --time-to-live-specification "Enabled=true,AttributeName=expires_at" `
  --region ap-south-1
```

## 4) Test event flow

- Send one CRITICAL test event into `iot-events.fifo`.
- Confirm SNS topic `iot-alerts` receives a message.
- Send the same event again within 10 minutes and confirm dedup suppresses duplicate SNS alerts.

## 5) Configure DLQ alarm (S1-05)

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\setup-dlq-alarm.ps1 `
  -Region "ap-south-1" `
  -DlqName "iot-events-dlq.fifo" `
  -SnsTopicArn "arn:aws:sns:ap-south-1:061039801536:iot-alerts"
```

## Files

- Lambda handler: `lambda/rule_engine/handler.py`
- Rule config: `lambda/rule_engine/config/rules.json`
- Deploy script: `scripts/deploy-rule-engine-lambda.ps1`
