param(
    [string]$AccountId = "",
    [string]$Region = "us-east-1",
    [string]$BudgetName = "argodreign-monthly-cost",
    [double]$BudgetLimitUsd = 20.0,
    [double]$WarningThresholdUsd = 15.0,
    [double]$CriticalThresholdUsd = 20.0,
    [string]$NotificationEmail = "",
    [string]$SnsTopicArn = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$Utf8NoBom = New-Object System.Text.UTF8Encoding($false)

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

if ([string]::IsNullOrWhiteSpace($AccountId)) {
    $AccountId = (aws sts get-caller-identity --query Account --output text).Trim()
}
if ([string]::IsNullOrWhiteSpace($AccountId)) {
    throw "Unable to resolve AWS account id. Pass -AccountId explicitly."
}

if ([string]::IsNullOrWhiteSpace($NotificationEmail) -and [string]::IsNullOrWhiteSpace($SnsTopicArn)) {
    throw "Provide at least one notification target: -NotificationEmail or -SnsTopicArn."
}

$subscribers = @()
if (-not [string]::IsNullOrWhiteSpace($NotificationEmail)) {
    $subscribers += [ordered]@{
        SubscriptionType = "EMAIL"
        Address = $NotificationEmail
    }
}
if (-not [string]::IsNullOrWhiteSpace($SnsTopicArn)) {
    $subscribers += [ordered]@{
        SubscriptionType = "SNS"
        Address = $SnsTopicArn
    }
}

$budget = [ordered]@{
    BudgetName = $BudgetName
    BudgetLimit = [ordered]@{
        Amount = ("{0:0.00}" -f $BudgetLimitUsd)
        Unit = "USD"
    }
    TimeUnit = "MONTHLY"
    BudgetType = "COST"
}

$notifications = @(
    [ordered]@{
        Notification = [ordered]@{
            NotificationType = "ACTUAL"
            ComparisonOperator = "GREATER_THAN"
            Threshold = $WarningThresholdUsd
            ThresholdType = "ABSOLUTE_VALUE"
        }
        Subscribers = $subscribers
    },
    [ordered]@{
        Notification = [ordered]@{
            NotificationType = "ACTUAL"
            ComparisonOperator = "GREATER_THAN"
            Threshold = $CriticalThresholdUsd
            ThresholdType = "ABSOLUTE_VALUE"
        }
        Subscribers = $subscribers
    }
)

$tmpDir = Join-Path $env:TEMP ("argodreign-budget-" + [guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Path $tmpDir -Force | Out-Null

try {
    $budgetPath = Join-Path $tmpDir "budget.json"
    $notificationsPath = Join-Path $tmpDir "notifications.json"
    [System.IO.File]::WriteAllText($budgetPath, ($budget | ConvertTo-Json -Depth 8), $Utf8NoBom)
    [System.IO.File]::WriteAllText($notificationsPath, ($notifications | ConvertTo-Json -Depth 10), $Utf8NoBom)

    $null = Invoke-AwsCli -Arguments @(
        "budgets", "describe-budget",
        "--account-id", "$AccountId",
        "--budget-name", "$BudgetName",
        "--region", "$Region"
    ) -IgnoreExitCode
    $budgetExists = ($LASTEXITCODE -eq 0)

    if ($budgetExists) {
        Write-Host "Budget '$BudgetName' already exists. Recreating it with required thresholds."
        Invoke-AwsCli -Arguments @(
            "budgets", "delete-budget",
            "--account-id", "$AccountId",
            "--budget-name", "$BudgetName",
            "--region", "$Region"
        ) | Out-Null
    }

    Write-Host "Creating budget '$BudgetName' for account $AccountId"
    Invoke-AwsCli -Arguments @(
        "budgets", "create-budget",
        "--account-id", "$AccountId",
        "--budget", "file://$budgetPath",
        "--notifications-with-subscribers", "file://$notificationsPath",
        "--region", "$Region"
    ) | Out-Null
}
finally {
    if (Test-Path $tmpDir) {
        Remove-Item -Path $tmpDir -Recurse -Force -ErrorAction SilentlyContinue
    }
}

Write-Host ""
Write-Host "Budget guardrails configured:"
Write-Host "- Budget Name: $BudgetName"
Write-Host "- Monthly Limit: $BudgetLimitUsd USD"
Write-Host "- Warning: $WarningThresholdUsd USD"
Write-Host "- Critical: $CriticalThresholdUsd USD"
