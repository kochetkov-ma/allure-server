---
id: BUG-BUILD-IMAGE-CVE
title: Address image CVEs surfaced by the first security-scan.yml run
status: todo
priority: P2
owner:
created: 2026-08-16
updated: 2026-08-16
tags: [build, security, docker, 3.0.0]
links: ["https://github.com/kochetkov-ma/allure-server/issues/100"]
spec: none                 # none | pending | full | design-only (section 10; never blank)
---

## Context
GitHub issue #100. Image CVEs pending the first `security-scan.yml` run, which the v3.0.0
release tag should have triggered. Findings not yet available; task tracks triage and
remediation once scan results land.

## Scope

| id | block | in/out | status |
|----|-------|--------|--------|
| S1 | Review the first security-scan.yml results for the 3.0.0 image | in | not-started |
| S2 | Remediate or explicitly accept-risk each finding | in | not-started |

## Acceptance
- [ ] security-scan.yml results reviewed
- [ ] Each CVE remediated or explicitly accepted with rationale
- [ ] Issue #100 closed or updated with the triage outcome

## Notes
Filed 2026-08-16 during v3.0.0 release closing pass. Blocked on the first security-scan.yml
run's output, currently unavailable.
