# ARGODREIGN Cost, Limitations, and Live Readiness

## Your budget target
- Total credit: **$140**
- Duration: **6 months**
- Target monthly burn: **~$23.33**
- Target daily burn: **~$0.77**

For your use case (demo-only, no active users), the system should stay mostly OFF and start only when you trigger it.

## Already provisioned now
- GitHub OIDC provider is created in AWS account `061039801536`.
- IAM role created for Actions: `arn:aws:iam::061039801536:role/GitHubActionsArgodreignRole`.
- Mobile workflows now support `aws_role_to_assume` input (OIDC mode).

## Recommended operating model (lowest-risk + mobile-friendly)
1. Keep ECS service at `desiredCount=0` when idle.
2. Start from phone only when you need demo/showcase.
3. Stop from phone immediately after demo.
4. Keep DynamoDB/SQS/SNS as pay-per-use.
5. Keep CloudWatch retention short (7 or 14 days).

## Biggest cost risks to avoid
- NAT Gateway left running all month.
- ALB always on with no traffic.
- ECS tasks running 24x7.
- High CloudWatch retention and verbose logs.
- Unused elastic IP/load balancer/network resources.

## Cost-safe architecture checklist
- ECS service can run in public subnet (for demo) to avoid NAT Gateway.
- If using ALB, understand it creates fixed monthly cost.
- Prefer one small task, one environment, one region.
- Use on-demand only during demos.
- Set AWS Budget alerts at 25%, 50%, 75%, 90%.

## What is already mobile-ready in this repo
- Start/stop/restart/status ECS from GitHub mobile Actions.
- Deploy image from GitHub mobile Actions.
- Pull CloudWatch logs snapshot from GitHub mobile Actions.
- Commit from phone using GitHub app/Codespaces.

## Current limitations and how to overcome

### 1) Authentication security
Limitation:
- Static AWS keys in GitHub secrets are risky.

Fix:
- Use OIDC role (implemented support in workflows).
- Setup script added: `scripts/setup-github-oidc.ps1`

### 2) Local vs cloud control
Limitation:
- You can control AWS from phone, but local laptop Docker cannot be controlled from phone unless laptop is remotely reachable.

Fix:
- Treat ECS as your demo runtime.
- Optional: add Tailscale + SSH if you need remote local control.

### 3) Potential unnecessary infrastructure costs
Limitation:
- If ALB/NAT/RDS/ElastiCache are provisioned and always-on, credits drain fast.

Fix:
- For demo mode, keep stack minimal.
- Keep only what is strictly needed for your showcase.
- Tear down unused infra daily.

### 4) Data/cache components
Limitation:
- Your local stack uses MySQL + Redis, but cloud cost can increase if equivalents are always on.

Fix:
- Cloud-lite fallback is now added in app code:
  - SQL auto-config is disabled by default.
  - Redis writes/reads gracefully fall back to in-memory cache.
- For demo-only operation, avoid always-on managed DB/cache unless strictly required.

### 5) Operational safety
Limitation:
- Easy to forget stopping service after demo.

Fix:
- Use Mobile ECS Control workflow immediately after session (`action=stop`).
- Add a daily reminder/checklist or scheduled stop workflow if you want full automation.

## What may be missing before real public live
- Formal auth/user management for public users.
- Rate limiting/WAF if exposed publicly.
- SLO/SLA monitoring and on-call playbook.
- Disaster recovery and backup validation.
- Security hardening review (IAM least privilege, secrets handling).

For your current goal (portfolio + idea demo), these can stay lean, but should be planned before public launch.

## Live readiness decision matrix

Go with current setup if:
- You only demo occasionally.
- No active end users.
- You can manually start/stop from phone.

Refactor before scaling if:
- You expect continuous traffic.
- You need 24x7 uptime.
- You need strict security/compliance.

## 6-month practical execution plan (credit-friendly)

Month 1:
- Enable OIDC, remove static keys.
- Validate mobile workflows end-to-end.
- Create budget alarms and billing dashboard checks.

Month 2-3:
- Demo-only operation with service mostly OFF.
- Tune log retention and monitor monthly spend.

Month 4-5:
- Optimize any high-cost components discovered in billing.
- Keep deployment process streamlined from mobile.

Month 6:
- Decide scale path: remain demo mode or refactor for multi-user live.

## Daily operating runbook (simple)
Before demo:
1. Run `Mobile ECS Control` with `action=start`.
2. Run `status` and verify running count.
3. Check logs snapshot.

After demo:
1. Run `Mobile ECS Control` with `action=stop`.
2. Verify desired and running count trend to zero.
3. Check billing dashboard once daily.
