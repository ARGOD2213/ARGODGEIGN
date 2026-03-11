param(
    [string]$FunctionName = "argodreign-rule-engine",
    [string]$QueueArn = "arn:aws:sqs:ap-south-1:061039801536:iot-events.fifo",
    [string]$Region = "ap-south-1",
    [int]$BatchSize = 10
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$existingJson = aws lambda list-event-source-mappings `
    --function-name "$FunctionName" `
    --event-source-arn "$QueueArn" `
    --region "$Region" `
    --output json

$existing = $existingJson | ConvertFrom-Json
if ($existing.EventSourceMappings -and $existing.EventSourceMappings.Count -gt 0) {
    $uuid = $existing.EventSourceMappings[0].UUID
    Write-Host "Event source mapping already exists. Updating batch size and enabling."
    aws lambda update-event-source-mapping `
        --uuid "$uuid" `
        --batch-size $BatchSize `
        --enabled `
        --region "$Region" | Out-Null
    Write-Host "Updated mapping UUID: $uuid"
} else {
    Write-Host "Creating new SQS -> Lambda event source mapping..."
    $created = aws lambda create-event-source-mapping `
        --function-name "$FunctionName" `
        --event-source-arn "$QueueArn" `
        --batch-size $BatchSize `
        --enabled `
        --region "$Region" `
        --output json | ConvertFrom-Json
    Write-Host "Created mapping UUID: $($created.UUID)"
}
