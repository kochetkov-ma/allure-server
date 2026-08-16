---
id: M-SUPERREVIEW-SKILL
title: Install and tailor the project-local superreview deep-review skill
status: closed
priority: P2
owner: manager
created: 2026-08-13
updated: 2026-08-13
tags: [skills, review, agents, maintenance]
links:
  - .claude/skills/superreview/SKILL.md
  - .claude/agents/web-ui.md
  - .claude/features/progress/M-AGENT-ROSTER-REFRESH.md
---

## Context
allure-server had no project-local deep anti-regression review skill. Installed via
`/brewcode:superreview-setup install` (plugin brewcode 5.6.0) and tailored to this repo's
current stack (Java 25 / Spring Boot 3.4, JTE + HTMX + Alpine.js + Tailwind, no Vaadin/Node)
and its actual domain-agent roster.

## Acceptance
- [x] `.claude/skills/superreview/` installed: `SKILL.md`, `references/agent-prompt.md`,
  `references/scope.md`, `references/report-template.md`, `references/java-kotlin.md`,
  plus git-ignored `.template-baseline/`
- [x] Routing table has 12 rows covering rest-api, dto, report-lifecycle, result-intake,
  generation, youtrack, config-security, persistence, web-ui, report-branding, build-ci, tests, docs
- [x] 12 rule/convention pointers wired into the skill
- [x] Mechanical gates set to `./gradlew compileJava compileTestJava` + `./gradlew test`
- [x] Scope baseline = `.claude/features` board + GitHub issues (read-only); scope passes
  A = `task-tracker`, B = `Explore`
- [x] Arbiter + validator = built-in `general-purpose` (DEGRADED axis noted: no architect
  agent in this repo)
- [x] New domain expert `.claude/agents/web-ui.md` created (owns `web/**`, `src/main/jte/**`,
  `input.css`, `tailwind.config.js`, `static/**`) so the routing table has a live target
  instead of the stale `vaadin-gui`; 11 domain experts wired total
- [x] `.claude/agents/intent-guard.md` reused byte-untouched (already existed, fixed
  review-only member)
- [x] `generate.sh validate` exits 0
- [ ] Working tree committed -- deliberately left open; per repo rule 3 (git safety) commits
  require explicit user go-ahead, none given yet

## Notes
2026-08-13: CLOSED. Delivered via `/brewcode:superreview-setup install`. Overlaps
`M-AGENT-ROSTER-REFRESH` on one point only -- the new `web-ui.md` agent, which also
satisfies that task's acceptance item "vaadin-gui replaced ... as a web-ui agent". That
task stays open regardless: a separate grep found a stale `vaadin-gui` cross-ref still in
`.claude/agents/config-security.md` (line ~280), so its own acceptance is not yet fully met.
All changes uncommitted on branch `feature/phase-1-vaadin-removal`; no ver/commit to record
beyond that branch name.
