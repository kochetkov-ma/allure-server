---
id: M-CFG-SUPPORT-OLD-FORMAT
title: Wire up or remove the orphaned allure.support-old-format property
status: todo
priority: P2
owner:
created: 2026-08-16
updated: 2026-08-16
tags: [config, v3.0.0, oss-hygiene]
links:
  - application.yaml
spec: none                 # none | pending | full | design-only (section 10; never blank)
---

## Context
`allure.support-old-format` is declared at `application.yaml:104` but `AllureProperties` has
no matching constructor parameter, so setting the property (or its env var) has zero effect.
Found during the v3.0.0 release-readiness pass (EPIC-V3-RELEASE); deliberately left out of the
actualized README because documenting a dead setting would be misleading. Needs a maintainer
call: wire it into `AllureProperties` and honor it, or delete it from `application.yaml`.

## Scope

| id | block | in/out | status |
|----|-------|--------|--------|
| S1 | decide: wire `support-old-format` into `AllureProperties` + consuming code, or delete the dead key | in | not-started |

## Acceptance
- [ ] `allure.support-old-format` either has a working constructor parameter + documented
      behavior, or is removed from `application.yaml`
- [ ] README settings reference matches the resolved state

## Notes
Filed 2026-08-16 by task-tracker during EPIC-V3-RELEASE board resync, per manager instruction
to capture this as an open task rather than lose it. No maintainer decision yet.
