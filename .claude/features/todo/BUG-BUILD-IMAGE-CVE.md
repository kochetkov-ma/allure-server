---
id: BUG-BUILD-IMAGE-CVE
title: Address image CVEs surfaced by the first security-scan.yml run
status: todo
priority: P2
owner:
created: 2026-08-16
updated: 2026-08-16
tags: [build, security, docker, 3.0.0]
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

## Remediation paths (identified, none applied)

1. **Spring Boot train bump** off `3.4.13` (`build.gradle:11`) -- clears the tomcat
   (CVE-2026-41293/43512/43515) and spring-security (CVE-2026-22732) criticals. The fixed
   spring-security-web version sits on a newer Boot train, so this is a real upgrade, not a
   patch-level bump -- carries regression risk. Requires a full `./gradlew build` plus the
   e2e run at `.claude/scripts/e2e-api.sh` before it ships. Natural content of a 3.0.1 or
   3.1.0 release, not a hotfix.
2. **Matching Spring Cloud bump** off `2024.0.3` (`build.gradle:25`), tracking whichever
   train the Spring Boot bump lands on -- clears the bcprov critical (CVE-2025-14813).
3. **Replace the checked-in Allure plugin jars** at 2.29.0 in `src/main/resources/plugins/`
   -- clears 3 of the 32 HIGH findings.
4. **Base image rebuild** for the 3 OS-layer highs: libexpat and p11-kit (twice).

## Scope

| id | block | in/out | status |
|----|-------|--------|--------|
| S1 | Review the security-scan.yml results for the 3.0.0 image | in | done |
| S2 | Bump Spring Boot train off 3.4.13 (build.gradle:11) | in | not-started |
| S3 | Bump Spring Cloud off 2024.0.3 to match (build.gradle:25) | in | not-started |
| S4 | Replace checked-in Allure plugin jars (2.29.0, src/main/resources/plugins/) | in | not-started |
| S5 | Rebuild base image for the 3 OS highs (libexpat, p11-kit) | in | not-started |
| S6 | Full build + `.claude/scripts/e2e-api.sh` e2e run as the regression gate for the Boot bump | in | not-started |
| S7 | Update issue #100 with the remediation outcome | in | not-started |

## Acceptance
- [x] security-scan.yml results reviewed (85 findings: 5 CRITICAL, 32 HIGH, 48 MEDIUM)
- [ ] Spring Boot train bumped past 3.4.13, clearing the 3 tomcat + 1 spring-security criticals
- [ ] Spring Cloud bumped to match, clearing the bcprov critical
- [ ] Allure plugin jars replaced past 2.29.0, clearing 3 HIGH
- [ ] Base image rebuilt, clearing the 3 OS highs (libexpat, p11-kit)
- [ ] Full build + e2e green after the Boot train bump
- [ ] Issue #100 updated or closed with the remediation outcome, shipped as 3.0.1 or 3.1.0

## Notes
Filed 2026-08-16 during v3.0.0 release closing pass, as a placeholder blocked on the first
security-scan.yml run.

2026-08-16: first scan landed (run 31963225205) -- numbers and remediation paths above are
from that run's evidence, posted on issue #100. Re-triaged spec verdict from `none` to
`pending`: the Boot train bump crosses build-ci-qa (build.gradle, Dockerfile/base image),
config-security (spring-security-web) and generation-pipeline (Allure plugin SPI/jars)
domains, is a real train upgrade with regression risk, and gates a release (3.0.1/3.1.0) --
needs a spec before work starts.
