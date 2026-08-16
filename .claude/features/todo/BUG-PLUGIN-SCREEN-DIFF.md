---
id: BUG-PLUGIN-SCREEN-DIFF
title: Screen diff not rendered despite screen-diff-plugin enabled
status: todo
priority: P2
owner:
created: 2026-08-16
updated: 2026-08-16
tags: [plugin, bug, 3.0.0, cross-linked:BUG-BUILD-IMAGE-CVE]
links: ["https://github.com/kochetkov-ma/allure-server/issues/72"]
spec: pending               # none | pending | full | design-only (section 10; never blank)
---

## Context
GitHub issue #72. Screen diff does not render even though `screen-diff-plugin` has been
enabled since before the issue was filed -- root cause unknown. Three reporters, still open
after the v3.0.0 release.

**Likely root cause found via `BUG-BUILD-IMAGE-CVE` (2026-08-16):** the `screen-diff-plugin`
jar at 2.29.0 (current `allureVersion`) is empty apart from its manifest -- enabling it at
`src/main/resources/config/allure.yml:7` was never sufficient, rendering lives entirely in
the generator frontend. From Allure 2.45.0, plugin frontends moved into the generator
bundle: `allure-generator-2.45.0` `assets/index-*.js` contains 28 occurrences of
`screen-diff` vs zero in `allure-generator-2.39.0`. The coordinated Allure 2.39.0 -> 2.45.0
bump scoped on `BUG-BUILD-IMAGE-CVE` (generator + all 10 plugin dirs, ships as 3.1.0, needed
anyway to clear 4 plugin-jar HIGH CVEs) may close this issue outright. The two tasks are
cross-linked -- confirm on pickup before starting independent root-cause work here.

## Scope

| id | block | in/out | status |
|----|-------|--------|--------|
| S1 | Reproduce and diagnose why screen diff fails to render with the plugin enabled | in | done |
| S2 | Fix rendering once root cause is identified | in | not-started |

## Acceptance
- [x] Root cause identified and documented -- `screen-diff-plugin` 2.29.0 jar is empty apart from its manifest, rendering lives in the generator frontend (see `BUG-BUILD-IMAGE-CVE`)
- [ ] Screen diff renders correctly with screen-diff-plugin enabled
- [ ] Issue #72 closed with reference to the fix

## Notes
Filed 2026-08-16 during v3.0.0 release closing pass. Root cause unknown -- ambiguous,
needs investigation before a fix plan exists.

2026-08-16: likely root cause identified as a side effect of the `BUG-BUILD-IMAGE-CVE`
plugin-jar CVE investigation -- see Context. Not independently reproduced/confirmed here yet
and S2 (fix) is untouched; the coordinated Allure 2.45.0 bump on `BUG-BUILD-IMAGE-CVE` is the
candidate fix. Whoever picks up either task should check the other first.
