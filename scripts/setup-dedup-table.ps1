param(
    [string]$TableName = "argodreign-dedup",
    [string]$Region = "ap-south-1",
    [string]$PartitionKey = "dedup_key",
    [string]$TtlAttribute = "expires_at"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Invoke-AwsCli {
    param(
        [Parameter(Mandatory = $true)] [string[]]$Arguments,
        [switch]$IgnoreExitCode
    )

    & aws @Arguments
    $exitCode = $LASTEXITCODE
    if (-not $IgnoreExitCode -and $exitCode -ne 0) {
        throw "AWS CLI command failed (exit $exitCode): aws $($Arguments -join ' ')"
    }
    return $exitCode
}

$null = Invoke-AwsCli -Arguments @(
    "dynamodb", "describe-table",
    "--table-name", "$TableName",
    "--region", "$Region",
    "--output", "json"
) -IgnoreExitCode
$exists = ($LASTEXITCODE -eq 0)

if (-not $exists) {
    Write-Host "Creating DynamoDB table: $TableName"
    Invoke-AwsCli -Arguments @(
        "dynamodb", "create-table",
        "--table-name", "$TableName",
        "--attribute-definitions", "AttributeName=$PartitionKey,AttributeType=S",
        "--key-schema", "AttributeName=$PartitionKey,KeyType=HASH",
        "--billing-mode", "PAY_PER_REQUEST",
        "--region", "$Region"
    ) | Out-Null

    Write-Host "Waiting for table to become ACTIVE..."
    Invoke-AwsCli -Arguments @(
        "dynamodb", "wait", "table-exists",
        "--table-name", "$TableName",
        "--region", "$Region"
    ) | Out-Null
}
else {
    Write-Host "Table already exists: $TableName"
}

Write-Host "Enabling TTL on attribute: $TtlAttribute"
Invoke-AwsCli -Arguments @(
    "dynamodb", "update-time-to-live",
    "--table-name", "$TableName",
    "--time-to-live-specification", "Enabled=true,AttributeName=$TtlAttribute",
    "--region", "$Region"
) | Out-Null

Write-Host "Done. Table '$TableName' is ready for rule-engine dedup."
