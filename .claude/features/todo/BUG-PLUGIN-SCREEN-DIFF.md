---
id: BUG-PLUGIN-SCREEN-DIFF
title: Screen diff not rendered despite screen-diff-plugin enabled
status: todo
priority: P2
owner:
created: 2026-08-16
updated: 2026-08-16
tags: [plugin, bug, 3.0.0]
links: ["https://github.com/kochetkov-ma/allure-server/issues/72"]
spec: pending               # none | pending | full | design-only (section 10; never blank)
---

## Context
GitHub issue #72. Screen diff does not render even though `screen-diff-plugin` has been
enabled since before the issue was filed -- root cause unknown. Three reporters, still open
after the v3.0.0 release.

## Scope

| id | block | in/out | status |
|----|-------|--------|--------|
| S1 | Reproduce and diagnose why screen diff fails to render with the plugin enabled | in | not-started |
| S2 | Fix rendering once root cause is identified | in | not-started |

## Acceptance
- [ ] Root cause identified and documented
- [ ] Screen diff renders correctly with screen-diff-plugin enabled
- [ ] Issue #72 closed with reference to the fix

## Notes
Filed 2026-08-16 during v3.0.0 release closing pass. Root cause unknown -- ambiguous,
needs investigation before a fix plan exists.
