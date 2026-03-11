# Sprint 1 Cost Guardrails (S1-07)

These guardrails keep the project within the `$140 / 6 months` budget envelope.

## 1) Budget alerts at $15 (warning) and $20 (critical)

```powershell
cd C:\Users\pavan\Desktop\ARGODREIGN
powershell -ExecutionPolicy Bypass -File .\scripts\setup-budget-guardrails.ps1 `
  -AccountId "061039801536" `
  -Region "us-east-1" `
  -BudgetName "argodreign-monthly-cost" `
  -BudgetLimitUsd 20 `
  -WarningThresholdUsd 15 `
  -CriticalThresholdUsd 20 `
  -NotificationEmail "your-email@example.com" `
  -SnsTopicArn "arn:aws:sns:ap-south-1:061039801536:iot-alerts"
```

## 2) Auto-stop EC2 when idle for 8 hours

```powershell
cd C:\Users\pavan\Desktop\ARGODREIGN
powershell -ExecutionPolicy Bypass -File .\scripts\setup-ec2-autostop-alarm.ps1 `
  -InstanceId "i-xxxxxxxxxxxxxxxxx" `
  -Region "ap-south-1" `
  -HoursIdleBeforeStop 8 `
  -CpuThresholdPct 1 `
  -SnsTopicArn "arn:aws:sns:ap-south-1:061039801536:iot-alerts"
```

This creates a CloudWatch alarm that triggers:
- EC2 stop action
- optional SNS notification

## 3) Mobile control workflows

- Start: `.github/workflows/start-server.yml`
- Stop: `.github/workflows/stop-server.yml`

Use from GitHub mobile app:
- `Actions -> START IoT Server -> Run workflow`
- `Actions -> STOP IoT Server -> Run workflow`

Always run STOP after demo.
