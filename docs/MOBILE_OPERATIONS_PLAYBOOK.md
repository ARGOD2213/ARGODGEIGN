# ARGODREIGN Mobile Operations Playbook (EC2 Mode)

This is the correct phone-first playbook for current architecture.

## What you can do from phone

- Start server using GitHub Action `START IoT Server`
- Stop server using GitHub Action `STOP IoT Server`
- Open dashboards in browser
- Post reviewer feedback using GitHub issue template
- Trigger async review sync to `docs/REVIEW_INBOX.md`

## Current architecture reminder

- Compute runtime: EC2 `t3.micro`
- Rule engine: Lambda `argodreign-rule-engine` (always serverless)
- Dashboards/API: Spring Boot on EC2 (start/stop to save cost)
- Data: S3 + Athena + DynamoDB alerts

## Start/Stop from phone

1. Open GitHub app -> repository -> Actions.
2. Run `START IoT Server`.
3. Wait for workflow success and open URLs from step summary.
4. Login with `argus` / `changeme` unless you configured `ARGUS_DASHBOARD_USER` and `ARGUS_DASHBOARD_PASS` secrets.
5. The workflow now waits for EC2 status checks plus local/public app health before reporting success.
6. After demo, run `STOP IoT Server`.

## Reviewer flow from phone (no desktop)

1. Open:
   - `https://github.com/ARGOD2213/ARGODGEIGN/issues/new?template=reviewer-drop.yml`
2. Paste reviewer output and submit issue.
3. Wait for workflow `Review Inbox Sync` to complete.
4. Open:
   - `docs/REVIEW_INBOX.md`
5. Tell Codex:
   - `Process docs/REVIEW_INBOX.md and implement all P1 then P2 fixes. Commit and push.`

## Cost protection

- Keep EC2 stopped when not actively demoing.
- Use Lambda for safety rule evaluation even when EC2 is stopped.
- Do not enable always-on ALB/NAT for this budget mode.

## Useful docs

- `docs/MOBILE_CHAT_SPACE.md`
- `docs/ASYNC_OFFICE_MODE.md`
- `docs/GITHUB_SECRETS_SETUP.md`
