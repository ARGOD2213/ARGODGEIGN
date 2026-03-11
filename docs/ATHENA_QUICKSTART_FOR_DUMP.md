# Athena Quickstart for 1GB Industrial Dump

## Why this step
- Querying from Athena gives fast analysis without pushing all rows through live API.
- Cost stays low and predictable.

## Important
The generator now correctly quotes CSV text fields. Use the latest script and upload to a clean prefix.

## 1) Generate clean 1GB file
```powershell
cd C:\Users\pavan\Desktop\ARGODREIGN
powershell -ExecutionPolicy Bypass -File .\scripts\generate-industrial-dump.ps1 -OutputPath "C:\Users\pavan\Desktop\ARGODREIGN\data\industrial_dummy_1gb_quoted.csv" -TargetSizeMB 1024
```

## 2) Upload to dedicated clean prefix
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\upload-industrial-dump.ps1 -FilePath "C:\Users\pavan\Desktop\ARGODREIGN\data\industrial_dummy_1gb_quoted.csv" -BucketName "iot-alert-engine-mahindra" -S3Key "data/industrial_1gb_quoted/industrial_dummy_1gb_quoted.csv" -Region "ap-south-1" -SkipManifestUpload
```

Optional cleanup if old manifest/json files already exist in this prefix:
```powershell
aws s3 rm s3://iot-alert-engine-mahindra/data/industrial_1gb_quoted/ --recursive --exclude "*.csv" --region ap-south-1
```

## 3) Create Athena DB + table
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\setup-athena-for-dump.ps1 -Region "ap-south-1" -Database "argodreign_analytics" -Table "industrial_dummy_events" -DataLocation "s3://iot-alert-engine-mahindra/data/industrial_1gb_quoted/" -AthenaResults "s3://iot-alert-engine-mahindra/athena-results/" -DropTable
```

## 4) Sample Athena queries
```sql
SELECT status, count(*) AS c
FROM argodreign_analytics.industrial_dummy_events
GROUP BY status
ORDER BY c DESC;

SELECT machine_id, sensor_type, avg(value) AS avg_value
FROM argodreign_analytics.industrial_dummy_events
WHERE status IN ('WARNING','CRITICAL')
GROUP BY machine_id, sensor_type
ORDER BY avg_value DESC
LIMIT 20;

SELECT weather_condition, count(*) AS events, avg(ai_risk_score) AS avg_risk
FROM argodreign_analytics.industrial_dummy_events
GROUP BY weather_condition
ORDER BY events DESC;
```

## Cost note
- Athena scans about $5 per TB in ap-south-1.
- 1 GB full scan query costs roughly $0.005.
- Prefer filtered queries and limit selected columns.
