param(
  [Parameter(Mandatory = $true)] [string] $AwsRegion,
  [Parameter(Mandatory = $true)] [string] $EcsCluster,
  [Parameter(Mandatory = $true)] [string] $EcsService,
  [switch] $WaitForStable
)

$ErrorActionPreference = "Stop"

Write-Host "Stopping demo service..."
Write-Host "Region=$AwsRegion, Cluster=$EcsCluster, Service=$EcsService, DesiredCount=0"

aws ecs update-service `
  --region $AwsRegion `
  --cluster $EcsCluster `
  --service $EcsService `
  --desired-count 0 | Out-Null

if ($WaitForStable) {
  Write-Host "Waiting for ECS service to become stable at desiredCount=0..."
  aws ecs wait services-stable `
    --region $AwsRegion `
    --cluster $EcsCluster `
    --services $EcsService
}

Write-Host "Demo service is stopped (desiredCount=0)."
Write-Host "Cost reminder: ALB/NAT/public IPv4 may still bill while provisioned."
