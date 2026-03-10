param(
  [Parameter(Mandatory = $true)] [string] $AwsRegion,
  [Parameter(Mandatory = $true)] [string] $AwsAccountId,
  [Parameter(Mandatory = $true)] [string] $GitHubOwner,
  [Parameter(Mandatory = $true)] [string] $GitHubRepo,
  [string] $GitHubBranch = "master",
  [string] $RoleName = "GitHubActionsArgodreignRole",
  [string] $PolicyName = "GitHubActionsArgodreignPolicy"
)

$ErrorActionPreference = "Stop"

function Write-Step([string] $msg) {
  Write-Host "==> $msg" -ForegroundColor Cyan
}

$oidcProviderArn = "arn:aws:iam::${AwsAccountId}:oidc-provider/token.actions.githubusercontent.com"
$roleArn = "arn:aws:iam::${AwsAccountId}:role/$RoleName"
$subCondition = "repo:${GitHubOwner}/${GitHubRepo}:ref:refs/heads/${GitHubBranch}"

Write-Step "Checking GitHub OIDC provider in IAM"
$oldEap = $ErrorActionPreference
$ErrorActionPreference = "Continue"
aws iam get-open-id-connect-provider `
  --open-id-connect-provider-arn $oidcProviderArn 2>$null | Out-Null
$providerExists = ($LASTEXITCODE -eq 0)
$ErrorActionPreference = $oldEap

if (-not $providerExists) {
  Write-Step "Creating GitHub OIDC provider"
  aws iam create-open-id-connect-provider `
    --url "https://token.actions.githubusercontent.com" `
    --thumbprint-list "6938fd4d98bab03faadb97b34396831e3780aea1" `
    --client-id-list "sts.amazonaws.com" *> $null
} else {
  Write-Host "OIDC provider already exists."
}

$trustPolicy = @"
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Federated": "$oidcProviderArn"
      },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "token.actions.githubusercontent.com:aud": "sts.amazonaws.com"
        },
        "StringLike": {
          "token.actions.githubusercontent.com:sub": "$subCondition"
        }
      }
    }
  ]
}
"@

$permissionsPolicy = @"
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "EcsControl",
      "Effect": "Allow",
      "Action": [
        "ecs:DescribeServices",
        "ecs:UpdateService",
        "ecs:DescribeTasks",
        "ecs:ListTasks"
      ],
      "Resource": "*"
    },
    {
      "Sid": "CloudWatchLogsRead",
      "Effect": "Allow",
      "Action": [
        "logs:DescribeLogGroups",
        "logs:DescribeLogStreams",
        "logs:FilterLogEvents",
        "logs:GetLogEvents"
      ],
      "Resource": "*"
    },
    {
      "Sid": "Ec2NetworkRead",
      "Effect": "Allow",
      "Action": [
        "ec2:DescribeNetworkInterfaces"
      ],
      "Resource": "*"
    },
    {
      "Sid": "EcrPushPull",
      "Effect": "Allow",
      "Action": [
        "ecr:GetAuthorizationToken",
        "ecr:BatchCheckLayerAvailability",
        "ecr:CompleteLayerUpload",
        "ecr:UploadLayerPart",
        "ecr:InitiateLayerUpload",
        "ecr:PutImage",
        "ecr:BatchGetImage",
        "ecr:GetDownloadUrlForLayer"
      ],
      "Resource": "*"
    },
    {
      "Sid": "CostExplorerRead",
      "Effect": "Allow",
      "Action": [
        "ce:GetCostAndUsage"
      ],
      "Resource": "*"
    }
  ]
}
"@

$trustFile = Join-Path $env:TEMP "argodreign-trust-policy.json"
$permFile = Join-Path $env:TEMP "argodreign-permissions-policy.json"
Set-Content -Path $trustFile -Value $trustPolicy -Encoding Ascii
Set-Content -Path $permFile -Value $permissionsPolicy -Encoding Ascii

Write-Step "Creating or updating IAM role: $RoleName"
$oldEap = $ErrorActionPreference
$ErrorActionPreference = "Continue"
aws iam get-role --role-name $RoleName 2>$null | Out-Null
$roleExists = ($LASTEXITCODE -eq 0)
$ErrorActionPreference = $oldEap

if ($roleExists) {
  aws iam update-assume-role-policy `
    --role-name $RoleName `
    --policy-document ("file://{0}" -f $trustFile) *> $null
  Write-Host "Role exists. Trust policy updated."
} else {
  aws iam create-role `
    --role-name $RoleName `
    --assume-role-policy-document ("file://{0}" -f $trustFile) *> $null
  Write-Host "Role created."
}

Write-Step "Attaching inline policy: $PolicyName"
aws iam put-role-policy `
  --role-name $RoleName `
  --policy-name $PolicyName `
  --policy-document ("file://{0}" -f $permFile) *> $null

Write-Step "Done"
Write-Host ""
Write-Host "Role ARN for GitHub Actions:"
Write-Host $roleArn -ForegroundColor Green
Write-Host ""
Write-Host "Use this ARN in GitHub Actions workflow input: aws_role_to_assume"
Write-Host "Then you can remove AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY repository secrets."
