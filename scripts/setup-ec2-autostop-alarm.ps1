param(
    [Parameter(Mandatory = $true)]
    [string]$InstanceId,
    [string]$Region = "ap-south-1",
    [string]$AlarmName = "argodreign-ec2-idle-autostop",
    [double]$CpuThresholdPct = 1.0,
    [int]$HoursIdleBeforeStop = 8,
    [string]$SnsTopicArn = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if ($HoursIdleBeforeStop -lt 1) {
    throw "-HoursIdleBeforeStop must be >= 1"
}

$alarmActions = @("arn:aws:automate:$Region:ec2:stop")
if (-not [string]::IsNullOrWhiteSpace($SnsTopicArn)) {
    $alarmActions += $SnsTopicArn
}

Write-Host "Configuring EC2 idle auto-stop alarm..."
Write-Host "Instance: $InstanceId"
Write-Host "Idle hours before stop: $HoursIdleBeforeStop"
Write-Host "CPU threshold: <= $CpuThresholdPct%"

$alarmActionArgs = @()
foreach ($action in $alarmActions) {
    $alarmActionArgs += @("--alarm-actions", $action)
}

$cmd = @(
    "cloudwatch", "put-metric-alarm",
    "--alarm-name", $AlarmName,
    "--alarm-description", "ARGODREIGN guardrail: auto-stop EC2 when idle CPU remains low for several hours",
    "--namespace", "AWS/EC2",
    "--metric-name", "CPUUtilization",
    "--dimensions", "Name=InstanceId,Value=$InstanceId",
    "--statistic", "Average",
    "--period", "3600",
    "--evaluation-periods", "$HoursIdleBeforeStop",
    "--datapoints-to-alarm", "$HoursIdleBeforeStop",
    "--threshold", "$CpuThresholdPct",
    "--comparison-operator", "LessThanOrEqualToThreshold",
    "--treat-missing-data", "notBreaching",
    "--region", $Region
) + $alarmActionArgs

aws @cmd

Write-Host ""
Write-Host "Auto-stop alarm configured:"
Write-Host "- Alarm Name: $AlarmName"
Write-Host "- Action: EC2 stop"
if (-not [string]::IsNullOrWhiteSpace($SnsTopicArn)) {
    Write-Host "- Notification: $SnsTopicArn"
}
