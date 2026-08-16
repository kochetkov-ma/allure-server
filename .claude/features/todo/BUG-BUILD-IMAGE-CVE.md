---
id: BUG-BUILD-IMAGE-CVE
title: Coordinated Allure 2.39.0 -> 2.45.0 bump to clear remaining plugin-jar HIGH CVEs
status: todo
priority: P2
owner:
created: 2026-08-16
updated: 2026-08-16
tags: [build, security, docker, generation-pipeline, 3.0.1, 3.1.0]
links: ["https://github.com/kochetkov-ma/allure-server/issues/100", "https://github.com/kochetkov-ma/allure-server/issues/100#issuecomment-5308895743"]
spec: pending               # none | pending | full | design-only (section 10; never blank)
---

## Context
GitHub issue #100. First Trivy scan in the project's history (run 31963225205, manual
dispatch, conclusion success) ran against the published `ghcr.io/kochetkov-ma/allure-server:3.0.0`
image, triage scan with `ignore-unfixed: false`, severity filter CRITICAL/HIGH/MEDIUM.
Evidence posted on the issue at the comment linked above. Issue stays open until remediated.

**Totals: 85 findings -- CRITICAL 5, HIGH 32, MEDIUM 48.** LOW/UNKNOWN were not measured by
this filter -- unknown, not zero. Every one of the 85 has an upstream fix available; zero
unfixed.

- OS layer (alpine 3.23.5): 15 findings -- 0 critical, 3 high, 12 medium.
- Java jars: 70 findings -- 5 critical, 29 high, 36 medium.

All 5 criticals are in the application jar layer, none in the base image:

| CVE | Component | Installed | Fixed | Source |
|-----|-----------|-----------|-------|--------|
| CVE-2026-41293 | `tomcat-embed-core` | 10.1.50 | 10.1.55 | Spring Boot BOM |
| CVE-2026-43512 | `tomcat-embed-core` | 10.1.50 | 10.1.55 | Spring Boot BOM |
| CVE-2026-43515 | `tomcat-embed-core` | 10.1.50 | 10.1.55 | Spring Boot BOM |
| CVE-2026-22732 | `spring-security-web` | 6.4.13 | 6.5.9 | Spring Boot BOM (newer Boot train, not a patch bump) |
| CVE-2025-14813 | `bcprov-jdk18on` | 1.78.1 | 1.80.2 | transitive via `spring-cloud-starter` 4.2.4 |

**Comparison to #100's original report:** #100 claimed 9 criticals on the 2.13.9 image;
3.0.0 has 5. Five of the original nine are resolved (two OpenSSL, tika CVE-2025-66516,
tomcat CVE-2025-24813, spring-security CVE-2024-38821). Four are still shipping at newer
but still-affected versions, and bcprov is new. 3.0.0 is better, not clean.

## v3.0.1 -- criticals resolved (shipped)
All 5 CRITICAL are resolved: Spring Boot 3.4.13 -> 3.5.16, Spring Cloud 2024.0.3 -> 2025.0.3
(`build.gradle:11,21-30`), verified-compatible from the real POMs (`spring-cloud-starter-
parent:2025.0.3` declares `spring-boot-starter-parent:3.5.15`; next train 2025.1.x needs Boot
4.0), `byteBuddyVersion` override dropped. Zero source files changed, 262 tests green, 30/30
e2e. Resolved: `tomcat-embed-core` 10.1.55, `spring-security-web` 6.5.11, `bcprov-jdk18on`
1.80.2. Merged `3b25d7b` via PR #116, tagged `v3.0.1`, released, published-artifact
verification 7/7 pass. Re-scan from inside the release (job "Scan the published image", run
31966977964) landed **CRITICAL 0, HIGH 13, MEDIUM 27, total 40** against the 3.0.0 baseline
of 5/32/48/85 -- exactly the locally predicted figures.

## Remaining -- plugin-jar HIGH CVEs (not shipped, needs a coordinated bump)
Four HIGH CVEs live in the checked-in Allure plugin jars at 2.29.0, deliberately excluded
from the v3.0.1 patch:

| CVE | Component | Installed | Fixed | Notes |
|-----|-----------|-----------|-------|-------|
| CVE-2025-52888 | xunit XML parser (XXE) | 2.29.0 | 2.34.1 | affects `junit-xml-plugin`, `trx-plugin`, `xunit-xml-plugin` |
| CVE-2026-54512 | jackson-databind/core | 2.17.0 | 2.21.4 | bundled in `jira-plugin/lib/`, `xray-plugin/lib/` |
| CVE-2026-54513 | jackson-databind/core | 2.17.0 | 2.21.4 | bundled in `jira-plugin/lib/`, `xray-plugin/lib/` |
| GHSA-r7wm-3cxj-wff9 | jackson-databind/core | 2.17.0 | 2.21.4 | bundled in `jira-plugin/lib/`, `xray-plugin/lib/` |

It cannot ship as a plugin-only swap. Allure 2.45.0 plugin jars no longer carry their own
`static/index.js` -- the plugin frontends moved into the generator bundle. Verified by
unpacking both generators: `allure-generator-2.39.0` `app.js` has zero occurrences of
`screen-diff`, `behaviors.json`, `packages.json`; `allure-generator-2.45.0` `assets/index-
*.js` has 28, 1 and 1. `screen-diff-plugin-2.45.0.jar` contains only its manifest. Dropping
the new jars onto the 2.39.0 generator would silently remove the Behaviors tab, the Packages
tab and screen-diff rendering, with no exception and no failing test. Matching the core
version at 2.39.0 does not help either -- it bundles jackson 2.21.2, below the 2.21.4 fix.

This also explains issue #72 (`BUG-PLUGIN-SCREEN-DIFF`): the `screen-diff-plugin` jar at
2.29.0 is empty apart from its manifest, so enabling it at
`src/main/resources/config/allure.yml:7` was never sufficient -- rendering lives entirely in
the generator frontend. The coordinated 2.45.0 bump may close #72 outright; the two tasks
are now cross-linked.

Required: coordinated bump of `gradle/dependencies.gradle:9` `allureVersion` 2.39.0 -> 2.45.0
together with all ten plugin directories, keeping the repo-branded `custom-logo-plugin`
assets (`src/main/resources/plugins/custom-logo-plugin/static/styles.css:2` and its svg).
Allure 2.45.0 verified current at the GitHub releases API, published 2026-08-06, sha256
`a0a840979b6d212e9eee031563d669985eb353cfde60557343b2903bf08570a6`; its jira/xray plugins
bundle jackson 2.22.1. This is a report-rendering change, so it ships as `3.1.0`, not a
patch.

Base image OS-layer highs (libexpat, p11-kit x2, 3 of the original 32 HIGH) are untouched by
v3.0.1 -- no Dockerfile/base-image change shipped. Status against the new 13-HIGH total is
unconfirmed; only the 4 plugin-jar CVEs above are itemized.

## Scope

| id | block | in/out | status |
|----|-------|--------|--------|
| S1 | Review the security-scan.yml results for the 3.0.0 image | in | done |
| S2 | Bump Spring Boot train off 3.4.13 (build.gradle:11) | in | done |
| S3 | Bump Spring Cloud off 2024.0.3 to match (build.gradle:25) | in | done |
| S4 | Coordinated Allure bump 2.39.0 -> 2.45.0: generator + all 10 plugin dirs (gradle/dependencies.gradle:9), keep custom-logo-plugin assets | in | not-started |
| S5 | Rebuild base image for the OS-layer highs (libexpat, p11-kit) | in | not-started |
| S6 | Full build + `.claude/scripts/e2e-api.sh` e2e run as the regression gate for the Boot bump | in | done |
| S7 | Update issue #100 with the v3.0.1 outcome and the remaining plugin-CVE plan | in | not-started |

## Acceptance
- [x] security-scan.yml results reviewed (85 findings: 5 CRITICAL, 32 HIGH, 48 MEDIUM on 3.0.0)
- [x] Spring Boot train bumped past 3.4.13, clearing the 3 tomcat + 1 spring-security criticals -- shipped v3.0.1
- [x] Spring Cloud bumped to match, clearing the bcprov critical -- shipped v3.0.1
- [x] Full build + e2e green after the Boot train bump -- 262 tests, 30/30 e2e (v3.0.1)
- [x] Re-scan confirms 0 CRITICAL -- CRITICAL 0, HIGH 13, MEDIUM 27, total 40 (run 31966977964)
- [ ] Coordinated Allure bump 2.39.0 -> 2.45.0 (generator + 10 plugin dirs), clearing the 4 plugin-jar HIGH CVEs, shipped as 3.1.0
- [ ] Base image rebuilt, clearing the OS-layer highs (libexpat, p11-kit) if still present
- [ ] Issue #100 updated with the v3.0.1 remediation outcome and the remaining plugin-CVE plan

## Notes
Filed 2026-08-16 during v3.0.0 release closing pass, as a placeholder blocked on the first
security-scan.yml run.

2026-08-16: first scan landed (run 31963225205) -- numbers and remediation paths above are
from that run's evidence, posted on issue #100. Re-triaged spec verdict from `none` to
`pending`: the Boot train bump crosses build-ci-qa (build.gradle, Dockerfile/base image),
config-security (spring-security-web) and generation-pipeline (Allure plugin SPI/jars)
domains, is a real train upgrade with regression risk, and gates a release (3.0.1/3.1.0) --
needs a spec before work starts.

2026-08-16: `v3.0.1` shipped, clearing S2/S3/S6 (all 5 CRITICAL). Merged `3b25d7b` via PR
#116, tagged `v3.0.1`, released, published-artifact verification 7/7 pass. Re-scoped: S4
rewritten from a plugin-jar-only swap to the coordinated Allure 2.45.0 generator+plugin bump
(the jar-only swap silently breaks Behaviors/Packages/screen-diff rendering), targeting
3.1.0. Cross-linked to `BUG-PLUGIN-SCREEN-DIFF` (#72) -- same root cause, same fix. Spec
verdict stays `pending`: still multi-domain (build-ci-qa + generation-pipeline), still
>5 files (10 plugin dirs + generator + dependencies.gradle), no spec doc written yet.
