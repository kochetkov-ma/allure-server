---
id: M-AGENT-ROSTER-REFRESH
title: Refresh 10 legacy domain agents + team.md for the post-Vaadin stack
status: todo
priority: P2
owner:
created: 2026-06-11
updated: 2026-06-11
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
- [ ] Every agent's stale stack claims fixed against `.claude/convention/versions.md`.
- [ ] `vaadin-gui` replaced (or rewritten) as a web-ui agent covering `web/` + `src/main/jte/` + `src/main/frontend/`.
- [ ] Cross-refs in the other 9 agents updated.
- [ ] `.claude/teams/default/team.md` row synced with the CLAUDE.md roster.

## Notes
Running log: decisions, blockers, PR/commit/report links.
