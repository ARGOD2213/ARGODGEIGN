param(
  [Parameter(Mandatory = $true)] [string] $AwsRegion,
  [Parameter(Mandatory = $true)] [string] $AwsAccountId,
  [Parameter(Mandatory = $true)] [string] $EcrRepo,
  [Parameter(Mandatory = $true)] [string] $EcsCluster,
  [Parameter(Mandatory = $true)] [string] $EcsService,
  [string] $ImageTag = "latest"
)

$ErrorActionPreference = "Stop"

$repoUri = "$AwsAccountId.dkr.ecr.$AwsRegion.amazonaws.com/$EcrRepo"
$imageUri = "$repoUri:$ImageTag"

Write-Host "Building Docker image..."
docker build -t "$EcrRepo:$ImageTag" .

Write-Host "Logging into ECR..."
aws ecr get-login-password --region $AwsRegion | docker login --username AWS --password-stdin "$AwsAccountId.dkr.ecr.$AwsRegion.amazonaws.com"

Write-Host "Pushing image to ECR: $imageUri"
docker tag "$EcrRepo:$ImageTag" $imageUri
docker push $imageUri

Write-Host "Triggering ECS rolling deployment..."
aws ecs update-service --cluster $EcsCluster --service $EcsService --force-new-deployment --region $AwsRegion | Out-Null

Write-Host "Deployment triggered successfully."
