---
id: M-MEMORY-SYNC-SKILL
title: Port memory-sync skill from finagra-site and adapt to allure-server
status: closed
priority: P2
owner: build-ci-qa
created: 2026-07-16
updated: 2026-07-16
tags: [skills, memory, maintenance]
links: []
---

## Context
finagra-site has a `memory-sync` skill (`.claude/skills/memory-sync/SKILL.md`) that keeps
the project memory surface consistent. allure-server needs the same capability, adapted to
its own memory surface:

- `CLAUDE.md` (project root)
- `.claude/rules/` (auto-loaded rules)
- `.claude/convention/` (versions, architecture, reference patterns, testing conventions)
- `.claude/agents/` (agent definitions)
- Team registry: CLAUDE.md §4 + `.claude/teams/default/team.md`

Adaptations required beyond a straight copy: sync targets/batches/checks rewired to the
files above, default branch `master`, Gradle/Java 25 build facts instead of finagra-site
stack facts.

## Acceptance
- [x] `.claude/skills/memory-sync/SKILL.md` exists in allure-server, copied from finagra-site
- [x] Sync targets/batches/checks reference allure-server memory surface only (CLAUDE.md, `.claude/rules`, `.claude/convention`, `.claude/agents`, team registry)
- [x] Branch references use `master`; build facts reflect Gradle wrapper + Java 25
- [x] Team registry coverage: CLAUDE.md §4 table + `.claude/teams/default/team.md` both in scope
- [x] No leftover finagra-site-specific paths, names, or stack facts
- [x] English only

## Notes
2026-07-16: task created directly in progress; owner build-ci-qa.
2026-07-16: CLOSED. Delivered `.claude/skills/memory-sync/SKILL.md` -- full port of the
finagra-site memory-sync skill adapted to allure-server: batches cover root CLAUDE.md /
rules / conventions / agents; diffs against `master`; Gradle + Spring + JTE fact checks;
`versions.md` as canonical pin home; Immutable Traits guard; CLAUDE.md §4 registry check;
`team.md` report-only; no `./gradlew` execution during verification. Also added a
"Memory sync" row to the CLAUDE.md §0 lazy-load index. Skill verified registered
(appears in the available-skills list). Closing ref: branch
`feature/phase-1-vaadin-removal`, working tree not yet committed (last commit 2e38cdd).
