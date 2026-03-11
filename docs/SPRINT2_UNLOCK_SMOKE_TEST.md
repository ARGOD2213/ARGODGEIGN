# Sprint 2 Unlock Smoke Test (EC2-OFF path)

Run from repository root on Windows PowerShell.

## 1) Stop EC2 (mandatory for unlock)

aws ec2 stop-instances --instance-ids i-0761b14159f2e2a3f --region ap-south-1
aws ec2 wait instance-stopped --instance-ids i-0761b14159f2e2a3f --region ap-south-1

## 2) Push CRITICAL test event to SQS

New-Item -ItemType Directory -Force -Path .\tmp | Out-Null
$body='{"timestamp":"2026-03-11T18:12:00Z","machine_id":"SMOKE-COMP-FILE-01","machine_class":"Compressor","sensor_type":"VIBRATION","sensor_value":9.4,"sensor_unit":"mm_s","plant_zone":"Ammonia-Unit"}'
$dedup='smoke-'+[DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$obj=[ordered]@{QueueUrl='https://sqs.ap-south-1.amazonaws.com/061039801536/iot-events.fifo';MessageBody=$body;MessageGroupId='smoke-test';MessageDeduplicationId=$dedup}
$json=$obj | ConvertTo-Json -Compress
$path=Join-Path (Get-Location) 'tmp\send-message.json'
[System.IO.File]::WriteAllText($path,$json,(New-Object System.Text.UTF8Encoding($false)))
aws sqs send-message --cli-input-json file://tmp/send-message.json --region ap-south-1 --output json

## 3) Verify Lambda published SNS alert

aws logs filter-log-events \
  --log-group-name /aws/lambda/argodreign-rule-engine \
  --start-time ([DateTimeOffset]::UtcNow.AddMinutes(-5).ToUnixTimeMilliseconds()) \
  --region ap-south-1 \
  --query 'events[?contains(message,`Published alert`)][].message' \
  --output text

## 4) Verify DynamoDB alert record exists

[System.IO.File]::WriteAllText((Join-Path (Get-Location) 'tmp\expr-values.json'),'{":d":{"S":"SMOKE-COMP-FILE-01"}}',(New-Object System.Text.UTF8Encoding($false)))
aws dynamodb query \
  --table-name iot-sensor-events \
  --key-condition-expression "deviceId = :d" \
  --expression-attribute-values file://tmp/expr-values.json \
  --no-scan-index-forward --limit 1 \
  --region ap-south-1 --output json

## 5) Optional: bring EC2 up and verify API step

aws ec2 start-instances --instance-ids i-0761b14159f2e2a3f --region ap-south-1
aws ec2 wait instance-running --instance-ids i-0761b14159f2e2a3f --region ap-south-1
# Deploy latest jar, then:
# GET http://<public-ip>:8080/api/machines/SMOKE-COMP-FILE-01/alerts

## 6) Stop EC2 again (cost control)

aws ec2 stop-instances --instance-ids i-0761b14159f2e2a3f --region ap-south-1
