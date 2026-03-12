# ARGODREIGN Mobile Chat Space

User note preserved:
- `hi this is just for a test if you can see this give me reply in codex`

This is your online browser space to continue work from phone.

Important:
- The exact Codex desktop chat thread is not available on mobile.
- This file is the live handoff bridge between desktop work and mobile updates.
- This page itself is not a chat box. It is a document.

## Why You Cannot Type Here

GitHub file view is read-only by default.

To type from phone, use one of these:
1. Tap the pencil icon (`Edit`) on this file, add your note, and commit.
2. Open or create a GitHub Issue and type comments there (best for chat-like updates).
3. Keep using this Codex thread when desktop is available.

## How to Use From Phone

1. Open this file in GitHub mobile browser/app.
2. Read `Latest Update` first.
3. Copy the `Reviewer Prompt` section if you need external review.
4. Run START/STOP workflows from GitHub Actions as usual.
5. Come back here for next actions.

## Async Office Mode (No Desktop Needed)

1. Open reviewer issue template:
   - `https://github.com/ARGOD2213/ARGODGEIGN/issues/new?template=reviewer-drop.yml`
2. Paste reviewer output and submit.
3. Wait for action `Review Inbox Sync` to finish.
4. Open:
   - `docs/REVIEW_INBOX.md`
5. Send Codex:
   - `Process docs/REVIEW_INBOX.md and implement all P1 then P2 fixes. Commit and push.`
6. If sync does not update in 2 minutes:
   - Run workflow `Review Inbox Sync` manually from Actions.
   - Pass `issue_number` if needed.

## Latest Update (IST)

- Auto-sync trigger hardened:
  - Works for normal `Review` issue titles too.
  - Added manual Action run fallback (`Review Inbox Sync` with `issue_number`).
- Synced current Issue `#1` into `docs/REVIEW_INBOX.md`.
- Sprint 1: Closed.
- Sprint 2 progress in repo: machine, safety, plant, compliance dashboards added.
- LLM confidence, advisory wrapper, and day-by-day evidence files are committed.
- Remaining work should now follow reviewer checkpoint feedback and integration hardening.
- Copy-paste packet for reviewer is available at:
  - `docs/REVIEWER_COPY_PACKET.md`

## Reviewer Prompt (Copy/Paste)

Use this with your reviewer:

```text
Please audit the latest ARGODREIGN master branch for Sprint 2 progress.
Focus on:
1) Dashboard functionality: /machine.html, /safety.html, /plant.html, /compliance.html
2) AI governance: AiAdvisoryWrapper present and confidence is computed (not hardcoded)
3) Safety architecture: rule engine remains Lambda-only (no control logic in Spring Boot)
4) Cost guardrails: budget + cache + no expensive always-on services
5) Evidence docs under /docs for Day 7/9/11/12/13/14

Return:
- PASS/FAIL by checkpoint
- exact gaps with file paths and line references
- only actionable fixes in priority order (P1 -> P3)
```

## Mobile Quick Links

- Actions: `START IoT Server`, `STOP IoT Server`
- Review inbox:
  - `docs/REVIEW_INBOX.md`
  - `docs/ASYNC_OFFICE_MODE.md`
- Dashboards:
  - `/machine.html`
  - `/safety.html`
  - `/plant.html`
  - `/compliance.html`

## Notes

- Never paste AWS/API secrets in issues or chat.
- Keep EC2 stopped when not actively using dashboards.
