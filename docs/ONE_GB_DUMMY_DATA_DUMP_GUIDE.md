# 1GB Industrial Dummy Data Dump Guide

This guide generates realistic synthetic sensor data for your fertilizer-complex model and uploads it to S3.

## Files Added
- `scripts/generate-industrial-dump.ps1`
- `scripts/upload-industrial-dump.ps1`

## Step 1: Open PowerShell in repo
```powershell
cd C:\Users\pavan\Desktop\ARGODREIGN
```

## Step 2: Quick test (10 MB)
Run this first to verify script and schema:
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\generate-industrial-dump.ps1 -OutputPath "C:\Users\pavan\Desktop\ARGODREIGN\data\industrial_dummy_test_10mb.csv" -TargetSizeMB 10
```

Expected outputs:
- CSV file in `data\`
- Manifest JSON: `industrial_dummy_test_10mb.csv.manifest.json`

## Step 3: Full generation (1 GB)
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\generate-industrial-dump.ps1 -OutputPath "C:\Users\pavan\Desktop\ARGODREIGN\data\industrial_dummy_1gb.csv" -TargetSizeMB 1024
```

## Step 4: Upload to S3
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\upload-industrial-dump.ps1 -FilePath "C:\Users\pavan\Desktop\ARGODREIGN\data\industrial_dummy_1gb.csv" -BucketName "iot-alert-engine-mahindra" -S3Key "data/industrial_dummy_1gb.csv" -Region "ap-south-1"
```

For Athena-specific prefixes, add `-SkipManifestUpload` to avoid non-CSV files in table location:
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\upload-industrial-dump.ps1 -FilePath "C:\Users\pavan\Desktop\ARGODREIGN\data\industrial_dummy_1gb.csv" -BucketName "iot-alert-engine-mahindra" -S3Key "data/industrial_1gb_quoted/industrial_dummy_1gb_quoted.csv" -Region "ap-south-1" -SkipManifestUpload
```

This uploads:
- `s3://iot-alert-engine-mahindra/data/industrial_dummy_1gb.csv`
- `s3://iot-alert-engine-mahindra/data/industrial_dummy_1gb.csv.manifest.json`

## Step 5: Verify upload
```powershell
aws s3 ls s3://iot-alert-engine-mahindra/data/industrial_dummy_1gb.csv --region ap-south-1
aws s3 ls s3://iot-alert-engine-mahindra/data/industrial_dummy_1gb.csv.manifest.json --region ap-south-1
```

## Notes
- Data includes machine/cell/area metadata, weather context, risk scores, and alert IDs.
- Scenarios rotate automatically: `NORMAL`, `HEATWAVE`, `HEAVY_RAIN`, `STORM`, `COMPRESSOR_DEGRADE`, `GAS_LEAK`.
- Keep this as synthetic data only (never mix with real plant data).
