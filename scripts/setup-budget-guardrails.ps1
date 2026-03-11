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
    $budget | ConvertTo-Json -Depth 8 | Set-Content -Path $budgetPath -Encoding UTF8
    $notifications | ConvertTo-Json -Depth 10 | Set-Content -Path $notificationsPath -Encoding UTF8

    $budgetExists = $true
    try {
        aws budgets describe-budget --account-id "$AccountId" --budget-name "$BudgetName" --region "$Region" | Out-Null
    } catch {
        $budgetExists = $false
    }

    if (-not $budgetExists) {
        Write-Host "Creating budget '$BudgetName' for account $AccountId"
        aws budgets create-budget `
            --account-id "$AccountId" `
            --budget "file://$budgetPath" `
            --notifications-with-subscribers "file://$notificationsPath" `
            --region "$Region" | Out-Null
    } else {
        Write-Host "Budget '$BudgetName' already exists. Updating budget limit only."
        aws budgets update-budget `
            --account-id "$AccountId" `
            --new-budget "file://$budgetPath" `
            --region "$Region" | Out-Null

        Write-Host "Ensuring warning and critical notifications exist..."
        foreach ($notificationWithSubscribers in $notifications) {
            $singlePath = Join-Path $tmpDir ("notification-" + [guid]::NewGuid().ToString("N") + ".json")
            $notificationWithSubscribers | ConvertTo-Json -Depth 10 | Set-Content -Path $singlePath -Encoding UTF8
            try {
                aws budgets create-notification `
                    --account-id "$AccountId" `
                    --budget-name "$BudgetName" `
                    --notification-with-subscribers "file://$singlePath" `
                    --region "$Region" | Out-Null
            } catch {
                Write-Host "Notification may already exist for threshold $($notificationWithSubscribers.Notification.Threshold) USD. Skipping."
            }
        }
    }
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
