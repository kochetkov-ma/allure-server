---
id: M-FLYWAY-MIGRATIONS
title: Introduce Flyway migrations; stop relying on ddl-auto update
status: todo
priority: P2
owner:
created: 2026-06-11
updated: 2026-06-11
tags: [db, tech-debt, critical]
links:
  - .claude/convention/project-architecture.md
spec: pending
---

## Context
Flagged CRITICAL in `.claude/convention/project-architecture.md` §4: no migration tool
(Flyway/Liquibase) is present. Schema is managed by `spring.jpa.hibernate.ddl-auto: update`
(`application.yaml`), and the setting is propagated to `docker-compose.yml`
(`SPRING_JPA_HIBERNATE_DDL-AUTO: update`) and the Helm chart. `update` silently mutates
production schemas, cannot express destructive/ordered changes, and is unacceptable in an
open-source release artifact. A stray root-level `migration.sql` exists with no tooling around it.

## Acceptance
- [ ] Flyway dependency added and enabled (H2 + PostgreSQL both supported)
- [ ] `V1__init.sql` baseline generated from the current entity schema
- [ ] `spring.jpa.hibernate.ddl-auto` switched to `validate` in `application.yaml`
- [ ] docker-compose / docker-compose-h2 / Helm chart env overrides updated in lockstep (no `update` left anywhere)
- [ ] Upgrade path from an existing pre-Flyway database documented (baseline-on-migrate strategy)
- [ ] Build + integration tests green against a fresh DB and a pre-existing DB

## Notes
Running log: decisions, blockers, PR/commit/report links.

2026-07-16 (M-DEEP-REVIEW-COMPAT): review reconfirmed the `ddl-auto: update` multi-replica CREATE-TABLE race (A3-4) and the never-executed root `migration.sql` (A3-2; its header is now marked manual/reference-only). Report: .claude/reports/20260716-153949_deep-review/.
