# ARGODREIGN Mobile Chat Space

This file is the phone-first handoff bridge for the current repo state.

## How to Use From Phone

1. Open this file in GitHub mobile app or browser.
2. Read `Latest Update` first.
3. Run `START IoT Server` only after repository secrets are configured.
4. Use the `Reviewer Prompt` block when you want an external review.
5. Run `STOP IoT Server` after the demo to stop billing.

## Mobile Start Notes

- Workflow: `.github/workflows/start-server.yml`
- Default login if no dashboard secrets are set: `argus` / `changeme`
- Optional secrets for custom login:
  - `ARGUS_DASHBOARD_USER`
  - `ARGUS_DASHBOARD_PASS`
- The start workflow now waits for:
  - EC2 instance status checks
  - local app health on EC2
  - public health reachability from the runner

## Latest Update (IST)

- Sprint 8 completed in repo scope:
  - AI advisory evidence ledger added for alert-backed advisories
  - Human approval workflow added: `DRAFT -> REVIEWED -> APPROVED/REJECTED`
  - Alert evidence endpoints added under `/api/v1/alerts/*`
  - Dashboard overview now exposes advisory approval summary
  - Mobile start workflow hardened for real health checks
- README and mobile operations docs updated to match current workflow and login defaults
- Integrated commits on current `master` line:
  - `5092830` `feat(sprint8): add advisory evidence workflow and mobile start hardening`
  - `f655226` `docs(mobile): refresh sprint 8 handoff`

## Completed Tasks

- Backend governance:
  - `src/main/java/com/mahindra/iot/service/AdvisoryWorkflowService.java`
  - `src/main/java/com/mahindra/iot/controller/AlertWorkflowController.java`
  - `src/main/java/com/mahindra/iot/model/SensorEvent.java`
- Mobile runtime hardening:
  - `.github/workflows/start-server.yml`
  - `docs/GITHUB_SECRETS_SETUP.md`
  - `docs/MOBILE_OPERATIONS_PLAYBOOK.md`
- Repo status docs:
  - `README.md`

## Quick API Checks

- Health:
  - `GET /api/v1/health`
- Recent evidence:
  - `GET /api/v1/alerts/evidence`
- Single alert evidence:
  - `GET /api/v1/alerts/{alertId}/evidence`
- Review:
  - `POST /api/v1/alerts/{alertId}/review`
- Approve:
  - `POST /api/v1/alerts/{alertId}/approve`
- Reject:
  - `POST /api/v1/alerts/{alertId}/reject`

## Reviewer Prompt (Copy/Paste)

```text
Please audit the latest ARGODREIGN master branch for Sprint 8 completion.

Focus on:
1) Advisory evidence ledger: every alert-backed AI advisory should carry input tags, rule version, model version, confidence, reviewer metadata, and evidence ID
2) Approval workflow: transitions must be DRAFT -> REVIEWED -> APPROVED/REJECTED with invalid transitions blocked
3) API coverage: /api/v1/alerts/{alertId}/review, approve, reject, and evidence endpoints should behave coherently
4) Mobile GitHub Actions start path: START IoT Server should wait for EC2 readiness and HTTP health before reporting success
5) Docs alignment: README and mobile docs should describe the same workflow and default login behavior

Return:
- PASS/FAIL by checkpoint
- exact gaps with file paths and line references
- only actionable fixes in priority order (P1 -> P3)
```

## Notes

- Do not paste AWS or API secrets into issues or reviewer notes.
- Keep EC2 stopped when not actively demoing.
