---
id: BUG-PLUGIN-SUMMARY-500
title: Unhandled 500 in CustomReportMetaPlugin when report has no widgets/summary.json
status: todo
priority: P1
owner:
created: 2026-08-16
updated: 2026-08-16
tags: [plugin, bug, 3.0.0]
links: ["https://github.com/kochetkov-ma/allure-server/issues/98"]
spec: none                 # none | pending | full | design-only (section 10; never blank)
---

## Context
GitHub issue #98. `helper/plugin/CustomReportMetaPlugin.java:68-71` throws an unhandled
exception (500) when a generated Allure report is missing `widgets/summary.json`. Reported
by two users, still reproducible on 3.0.0. Highest-value open defect from the v3.0.0
post-release triage.

## Scope

| id | block | in/out | status |
|----|-------|--------|--------|
| S1 | Handle a missing widgets/summary.json without a 500 (graceful fallback or clear 4xx) | in | not-started |

## Acceptance
- [ ] Report generation/view with no widgets/summary.json no longer returns 500
- [ ] Regression test covers the missing-file case
- [ ] Issue #98 closed with reference to the fix

## Notes
Filed 2026-08-16 during v3.0.0 release closing pass. Real defect, reproducible on 3.0.0,
two independent reporters, still open after release.
