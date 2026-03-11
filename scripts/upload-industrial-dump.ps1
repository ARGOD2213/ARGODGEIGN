param(
    [Parameter(Mandatory = $true)]
    [string]$FilePath,
    [string]$BucketName = "iot-alert-engine-mahindra",
    [string]$S3Key = "data/industrial_dummy_1gb.csv",
    [string]$Region = "ap-south-1",
    [switch]$SkipManifestUpload
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if (-not (Test-Path $FilePath)) {
    throw "File not found: $FilePath"
}

$fileItem = Get-Item $FilePath
Write-Host ("Uploading {0} ({1:N2} MB) to s3://{2}/{3}" -f $fileItem.FullName, ($fileItem.Length / 1MB), $BucketName, $S3Key)

aws s3 cp "$FilePath" "s3://$BucketName/$S3Key" --region "$Region"

$manifestPath = "$FilePath.manifest.json"
if ((-not $SkipManifestUpload) -and (Test-Path $manifestPath)) {
    $manifestKey = "$S3Key.manifest.json"
    Write-Host ("Uploading manifest to s3://{0}/{1}" -f $BucketName, $manifestKey)
    aws s3 cp "$manifestPath" "s3://$BucketName/$manifestKey" --region "$Region"
}

Write-Host "Upload complete"
Write-Host ("S3 Object: s3://{0}/{1}" -f $BucketName, $S3Key)
