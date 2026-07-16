---
id: M-SETTINGS-CLUSTER-COHERENCE
title: Make require-api-auth setting cluster-coherent (stop per-JVM cache staleness)
status: todo
priority: P3
owner:
created: 2026-07-16
updated: 2026-07-16
tags: [security, config, cluster]
links:
  - .claude/reports/20260716-153949_deep-review/
---

## Context
Findings C1-4 / A3-4 (deep review). `require-api-auth` is cached per-JVM in an
`AtomicReference`, so a runtime toggle on one node stays stale on other replicas of a
shared-DB cluster until restart. Add a DB-poll / short-TTL re-read or pub/sub invalidation
so the effective setting converges across nodes. Relates to `M-FLYWAY-MIGRATIONS`
(multi-replica DDL race) -- both are shared-DB multi-replica coherence issues.

## Acceptance
- [ ] A require-api-auth toggle on one node becomes effective on all replicas without restart
- [ ] Mechanism chosen (short-TTL DB re-read or pub/sub invalidation) and documented
- [ ] No per-request DB hit regression on the hot auth path

## Notes
Running log: decisions, blockers, PR/commit/report links.
Relates to M-FLYWAY-MIGRATIONS (multi-replica coherence).
