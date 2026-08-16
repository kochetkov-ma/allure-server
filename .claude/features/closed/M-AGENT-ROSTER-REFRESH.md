---
id: M-AGENT-ROSTER-REFRESH
title: Refresh 10 legacy domain agents + team.md for the post-Vaadin stack
status: closed
priority: P2
owner: manager
created: 2026-06-11
updated: 2026-08-13
tags: [agents, docs, tech-debt]
links:
  - .claude/convention/versions.md
  - .claude/teams/default/team.md
---

## Context
A consistency review (2026-06-11) found all 10 legacy domain agents in `.claude/agents/` stale after the Vaadin -> JTE+HTMX migration:

- `vaadin-gui` -- 37 stale refs, entirely Vaadin.
- `build-ci-qa` -- claims Node 20.13.1 / JDK 21 / Gradle 8.8 / Vaadin pnpm bundler.
- `config-security` -- CustomRequestCache, `/VAADIN/**`, Vaadin CSRF rationale.
- `dto-model` -- `gui.dto`, `GenerateDto`.
- `rest-controller`, `result-service` (ResultUploadDialog), `generation-pipeline`, `persistence-jpa`, `plugin-youtrack`, `report-service` -- each cross-refs `vaadin-gui`/`gui/`.

Also `.claude/teams/default/team.md` lists "vaadin-gui ... active", contradicting the CLAUDE.md roster's LEGACY marker.

## Acceptance
- [x] Every agent's stale stack claims fixed against `.claude/convention/versions.md`.
- [x] `vaadin-gui` replaced (or rewritten) as a web-ui agent covering `web/` + `src/main/jte/` + `src/main/frontend/`.
- [x] Cross-refs in the other 9 agents updated.
- [x] `.claude/teams/default/team.md` row synced with the CLAUDE.md roster.

## Notes
Running log: decisions, blockers, PR/commit/report links.
- 2026-08-13: refresh executed by `/brewcode:teams-setup upgrade` (brewcode 5.6.0) on team `default`; also adds new fixed review-only member `intent-guard` and installs project-local tracer `.claude/teams/default/trace-ops.sh`.
- 2026-08-13: Result -- all 10 legacy agents refreshed to the 5.6.0 metadata standard (trimmed `description`, trailing metadata block, Return Contract, repointed `trace-ops.sh` paths); `vaadin-gui` deleted and replaced by `web-ui`; new review-only `intent-guard` added; `team.md` rewritten to 5.6.0 with 11-agent roster + `intent-guard` row; `CLAUDE.md` §4 updated. Gate `verify-team.sh default` -> `VERIFY: PASS`, 12/12 agents OK, 0 stale `vaadin` refs anywhere incl. `config-security.md`. Closed uncommitted, pending user-approved commit on `feature/phase-1-vaadin-removal`.
