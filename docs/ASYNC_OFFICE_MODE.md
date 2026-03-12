# ARGODREIGN Async Office Mode (Phone-First)

Use this when you are away from desktop.

## Goal

- You post reviewer feedback from phone.
- Repo auto-syncs it into one inbox file.
- Codex picks that file and continues implementation.

## One-Time Setup (already committed)

- Reviewer issue template:
  - `.github/ISSUE_TEMPLATE/reviewer-drop.yml`
- Auto-sync workflow:
  - `.github/workflows/review-inbox-sync.yml`
- Synced files:
  - `docs/REVIEW_INBOX.md`
  - `docs/REVIEW_LOG.md`

## Daily Office Flow (Phone)

1. Open new issue with template:
   - `https://github.com/ARGOD2213/ARGODGEIGN/issues/new?template=reviewer-drop.yml`
2. Paste reviewer output in issue.
3. Submit issue.
4. Wait 1-2 minutes for workflow `Review Inbox Sync`.
5. Open:
   - `docs/REVIEW_INBOX.md`
6. Send this one message to Codex:
   - `Process docs/REVIEW_INBOX.md and implement all P1 then P2 fixes. Commit and push.`

## If reviewer adds new comments later

1. Add a comment on the same `[REVIEW]` issue.
2. Workflow syncs latest comment to `docs/REVIEW_INBOX.md`.
3. Send Codex:
   - `Reprocess docs/REVIEW_INBOX.md delta only and push updates.`

## Notes

- This avoids pasting long reviews repeatedly in chat.
- It gives one source of truth in repo.
- Keep EC2 stopped when not demoing to save cost.
