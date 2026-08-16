---
id: M-BOOTSTRAP-ADMIN-HARDENING
title: Harden admin bootstrap credential (random one-time password or refuse default login)
status: todo
priority: P2
owner:
created: 2026-07-16
updated: 2026-07-16
tags: [security, auth]
links:
  - .claude/reports/20260716-153949_deep-review/
spec: pending
---

## Context
Finding A1-4 (deep review). The bootstrap admin account ships with a predictable
`admin`/`admin` credential. Replace it with a random one-time password logged once at
startup, OR refuse interactive/API login until `BASIC_AUTH_PASSWORD` is set to a
non-default value. Overlaps existing `M-ENV-SECRETS` (remove hardcoded secrets/defaults) --
cross-linked; coordinate so the two do not diverge.

## Acceptance
- [ ] No usable login with the shipped default admin credential
- [ ] Random one-time password logged once at startup OR login refused until a non-default password is configured
- [ ] Behaviour documented for operators (first-login flow)
- [ ] Reconciled with M-ENV-SECRETS (no duplicated/contradictory handling)

## Notes
Running log: decisions, blockers, PR/commit/report links.
Cross-link: M-ENV-SECRETS (remove hardcoded secrets/defaults from application.yaml).
