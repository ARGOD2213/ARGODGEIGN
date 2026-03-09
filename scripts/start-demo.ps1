param(
  [Parameter(Mandatory = $true)] [string] $AwsRegion,
  [Parameter(Mandatory = $true)] [string] $EcsCluster,
  [Parameter(Mandatory = $true)] [string] $EcsService,
  [int] $DesiredCount = 1,
  [switch] $WaitForStable
)

$ErrorActionPreference = "Stop"

if ($DesiredCount -lt 1) {
  throw "DesiredCount must be >= 1 for start-demo."
}

Write-Host "Starting demo service..."
Write-Host "Region=$AwsRegion, Cluster=$EcsCluster, Service=$EcsService, DesiredCount=$DesiredCount"

aws ecs update-service `
  --region $AwsRegion `
  --cluster $EcsCluster `
  --service $EcsService `
  --desired-count $DesiredCount | Out-Null

if ($WaitForStable) {
  Write-Host "Waiting for ECS service to become stable..."
  aws ecs wait services-stable `
    --region $AwsRegion `
    --cluster $EcsCluster `
    --services $EcsService
}

Write-Host "Demo service is started."
Write-Host "Reminder: ALB/public IPv4 can still incur charges even when tasks are low."
