---
id: M-CURRENTUSER-REQUEST-CACHE
title: Resolve the current user once per request (drop 3-4x findByUsername per page)
status: todo
priority: P3
owner:
created: 2026-07-16
updated: 2026-07-16
tags: [performance, web]
links:
  - .claude/reports/20260716-153949_deep-review/
spec: none
---

## Context
Finding CF-2 (deep review). The current user is resolved 3-4x per authenticated page
render: `GlobalModelAdvice` calls `isAdmin()` / `signInRequired()` / `currentUser()` and
`ForcePasswordChangeFilter` each hit `findByUsername`. Resolve the user once per request
(request-scoped cache) and reuse it across these call sites.

## Acceptance
- [ ] Current user resolved at most once per request
- [ ] GlobalModelAdvice and ForcePasswordChangeFilter share the single resolution
- [ ] No behavioural change to admin/sign-in/force-password-change logic

## Notes
Running log: decisions, blockers, PR/commit/report links.
