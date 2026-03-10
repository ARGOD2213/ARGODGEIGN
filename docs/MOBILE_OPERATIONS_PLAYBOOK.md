# ARGODREIGN Mobile Operations Playbook

This setup lets you control your AWS server from your phone.

## What is possible from phone
- Start ECS service
- Stop ECS service
- Restart ECS deployment
- Deploy latest code to ECS (build + push + rollout)
- Pull CloudWatch logs snapshot
- Make commits directly on GitHub from phone

## What is not direct from phone
- Starting/stopping your **local laptop Docker** is not direct unless the laptop is online and you expose remote access (SSH/Tailscale/remote desktop).

If your main requirement is server control, keep production on ECS and use the workflows below.

## One-time setup (5-10 minutes)
1. Open your GitHub repo:
   - `https://github.com/ARGOD2213/ARGODGEIGN`
2. Recommended (secure): configure OIDC role from your laptop once:
   - `.\scripts\setup-github-oidc.ps1 -AwsRegion ap-south-1 -AwsAccountId <account-id> -GitHubOwner ARGOD2213 -GitHubRepo ARGODGEIGN -GitHubBranch master`
3. Go to:
   - `Settings -> Secrets and variables -> Actions -> New repository secret`
4. Add fallback secrets only if you are not using OIDC:
   - `AWS_ACCESS_KEY_ID`
   - `AWS_SECRET_ACCESS_KEY`
5. In each workflow run, pass `aws_role_to_assume` with your IAM role ARN for OIDC mode.

## Mobile workflows added in this repo
- `.github/workflows/mobile-ecs-control.yml`
- `.github/workflows/mobile-deploy-ecs.yml`
- `.github/workflows/mobile-cloudwatch-logs.yml`
- `.github/workflows/mobile-cost-snapshot.yml`

## How to start/stop server from phone
1. Open GitHub mobile app.
2. Open repo `ARGODGEIGN`.
3. Tap `Actions`.
4. Choose `Mobile ECS Control`.
5. Tap `Run workflow`.
6. Fill:
   - `action`: `start` or `stop` or `restart` or `status`
   - `aws_region`: `ap-south-1`
   - `aws_role_to_assume`: your OIDC role ARN (recommended)
   - `ecs_cluster`: your cluster (default `iot-cluster`)
   - `ecs_service`: your service (default `iot-api`)
   - `desired_count`: usually `1` for start
7. Run it and check summary in the run output.

## How to deploy from phone
1. In GitHub app -> `Actions`.
2. Choose `Mobile Deploy To ECS`.
3. Tap `Run workflow`.
4. Fill:
   - `aws_region`
   - `aws_role_to_assume`
   - `ecs_cluster`
   - `ecs_service`
   - `ecr_repo`
   - `image_tag` (example `release-2026-03-10`)
5. Run and watch step summary.

## How to check AWS logs from phone
Option A: GitHub workflow snapshot
1. Actions -> `Mobile CloudWatch Logs Snapshot`.
2. Run with:
   - `aws_role_to_assume`
   - `log_group` (example `/ecs/iot-api`)
   - `lookback_minutes` (example `120`)
   - `limit` (example `200`)
3. Open summary and artifact (`logs.txt`).

Option B: Direct AWS app/console
- Use AWS Console mobile browser for CloudWatch logs.
- Faster for live investigation if you already have IAM access on phone.

## How to check current AWS cost from phone
1. Actions -> `Mobile Cost Snapshot`.
2. Run with:
   - `aws_role_to_assume`
   - `monthly_budget_usd` (use `23.33` for your 6-month target)
3. Open summary for:
   - total month spend so far
   - budget used percent
   - top cost-driving AWS services

## How to commit from phone
Option A (quick edits)
1. Open file in GitHub app.
2. Tap edit icon.
3. Make change.
4. Commit directly to branch.
5. Open PR.

Option B (real coding from phone)
1. Open GitHub in mobile browser (desktop mode if needed).
2. Launch Codespaces for repo.
3. Edit/test/commit/push from Codespace.

## Safety checklist before mobile ops
- Use least-privilege IAM user/role for GitHub Actions.
- Enable MFA on GitHub and AWS account.
- Keep ECS stop workflow available to cut cost quickly.
- Rotate AWS keys periodically.
- Do not store secrets in code or `.env` committed files.

## Emergency stop from phone
Run `Mobile ECS Control` with:
- `action=stop`
- `desired_count=0`

Then verify in run summary:
- Desired count = `0`
- Running count moving to `0`
