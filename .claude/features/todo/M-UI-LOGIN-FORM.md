---
id: M-UI-LOGIN-FORM
title: Add form-based session login so the UI Logout button is meaningful
status: todo
priority: P3
owner:
created: 2026-07-16
updated: 2026-07-16
tags: [ui, security, auth]
links:
  - .claude/reports/20260716-153949_deep-review/
---

## Context
Finding CF-1 (deep review). The web UI authenticates via HTTP Basic only, so the Logout
button is a no-op: the browser keeps re-sending cached credentials. Add form-based session
login (session cookie) so logout actually clears the session and ends the authenticated state.

## Acceptance
- [ ] Form-based session login available for the web UI
- [ ] Logout terminates the session (subsequent page access requires re-login)
- [ ] API-token / Basic auth for `/api/**` unaffected

## Notes
Running log: decisions, blockers, PR/commit/report links.
