# Allure-Server Task Board

> Canonical task list + status. Procedure: [`TRACKER.md`](TRACKER.md). New-task template:
> [`TASK_TEMPLATE.md`](TASK_TEMPLATE.md). Ungroomed inbox: [`backlog/`](backlog/).
> Old `.claude/tasks/` is a deprecated pointer here.

## Overall status

- **Release line:** `v3.0.1` tagged and pushed (patch, shipped 2026-08-16), superseding `v3.0.0`. `feature/phase-1-vaadin-removal` squash merged to `master` as `a45cd8a` via PR #102 (Vaadin UI replaced with htmx + JTE + Tailwind; Java 25 / Gradle 9, exact pins in `../convention/versions.md`). Helm ingress defaults fixed to `example.com` placeholders in `5256fbb`. `v3.0.1` cleared all 5 CRITICAL CVEs from the first Trivy scan: Spring Boot 3.4.13 -> 3.5.16, Spring Cloud 2024.0.3 -> 2025.0.3 (`build.gradle:11,21-30`, verified-compatible from the real POMs, `byteBuddyVersion` override dropped), zero source files changed, 262 tests green, 30/30 e2e, merged `3b25d7b` via PR #116, published-artifact verification 7/7 pass (SBOM, both registries, cosign, healthy pull at `Implementation-Version: 3.0.1`). Re-scan from inside the release (job "Scan the published image", run 31966977964) landed CRITICAL 0/HIGH 13/MEDIUM 27/total 40, down from the 3.0.0 baseline of 5/32/48/85 -- the locally predicted figures. Published-artifact verification for 3.0.0 completed earlier: `ghcr.io/kochetkov-ma/allure-server:3.0.0` verified correct, two defects found and fixed in `88b0014` (Docker Hub description truncated past 25000 chars, now `docs/DOCKERHUB.md`; `security-scan.yml` never fired on `release: published`, now called from `release.yml` via `workflow_call` -- the fix that produced the first Trivy run, filed as BUG-BUILD-IMAGE-CVE). `SECURITY.md` requires cosign 3.0+ (2.x reports false negatives against OCI 1.1 referrer signatures).
- **Counts:** backlog 0 | todo 15 | progress 1 | closed 6.
- **Current focus:** EPIC-V3-RELEASE is IN PROGRESS -- owner manager, closing pass done, `v3.0.1` patch shipped under it. Waves A-H are all DONE, including Wave D (tag/release + published-artifact verification, closed out 2026-08-16). Merge to master, 262 tests green, 30/30 e2e, secret scan clean, governance docs + GitHub templates, SBOM/signing/scanning wired, compose + Helm chart verified, README actualized, review phase 2 with 4 independent verifiers (refuted 5 findings, confirmed and fixed 2: a missing 2.x auth-compat matrix entry and a real report-authorization-path security defect with a regression test), plus the two post-publish defects above. Epic stays `progress`, not `closed`: spec is still `pending`, and two real defects from triage remain open as tasks (one, BUG-BUILD-IMAGE-CVE, partially closed by `v3.0.1`). Repo brought to OSS-program standard (metadata, security settings, Discussions, labels), `origin/feature/tms` deleted, 10 issues closed / 7 labelled and left open (#18, #48, #72, #73, #94, #98, #100). BUG-PLUGIN-SUMMARY-500 (#98, P1, highest-value open bug) unchanged. BUG-BUILD-IMAGE-CVE (#100, P2) re-scoped: the 5 CRITICAL are resolved by `v3.0.1`; what remains is 4 HIGH CVEs in the checked-in Allure 2.29.0 plugin jars (CVE-2025-52888 xunit XXE; CVE-2026-54512/54513 + GHSA-r7wm-3cxj-wff9 jackson in jira/xray plugins), which need a coordinated Allure generator+plugin bump to 2.45.0 (dropping jars onto the old generator would silently remove Behaviors/Packages/screen-diff rendering) -- that is report-rendering scope, so it targets `3.1.0`, not a patch. This may also close BUG-PLUGIN-SCREEN-DIFF (#72, P2): the `screen-diff-plugin` jar at 2.29.0 is empty apart from its manifest, rendering lives in the generator frontend that only exists from 2.45.0 -- the two tasks are now cross-linked. Spec still pending on both -- route to `/task-spec EPIC-V3-RELEASE` and `/task-spec BUG-BUILD-IMAGE-CVE`. Open item for maintainer decision: `allure.support-old-format` dead property, tracked as M-CFG-SUPPORT-OLD-FORMAT. Deferred hardening still queued: M-BOOTSTRAP-ADMIN-HARDENING + M-ENV-SECRETS (secret/default hygiene), M-FLYWAY-MIGRATIONS, M-SETTINGS-CLUSTER-COHERENCE, M-UI-LOGIN-FORM, M-CURRENTUSER-REQUEST-CACHE.

## Progress (WIP)

| id | title | priority | owner | file | spec |
|----|-------|----------|-------|------|------|
| EPIC-V3-RELEASE | Merge phase-1-vaadin-removal, ship v3.0.0 with full 2.x REST compat, bring repo to OSS-program standard | P1 | manager | [progress/EPIC-V3-RELEASE.md](progress/EPIC-V3-RELEASE.md) | pending |

## Todo

| id | title | priority | owner | file | spec |
|----|-------|----------|-------|------|------|
| BUG-PLUGIN-SUMMARY-500 | Unhandled 500 in CustomReportMetaPlugin when report has no widgets/summary.json | P1 | | [todo/BUG-PLUGIN-SUMMARY-500.md](todo/BUG-PLUGIN-SUMMARY-500.md) | none |
| BUG-PLUGIN-SCREEN-DIFF | Screen diff not rendered despite screen-diff-plugin enabled | P2 | | [todo/BUG-PLUGIN-SCREEN-DIFF.md](todo/BUG-PLUGIN-SCREEN-DIFF.md) | pending |
| BUG-BUILD-IMAGE-CVE | Coordinated Allure 2.39.0 -> 2.45.0 bump to clear remaining plugin-jar HIGH CVEs (5 CRITICAL resolved in v3.0.1; ships as 3.1.0) | P2 | | [todo/BUG-BUILD-IMAGE-CVE.md](todo/BUG-BUILD-IMAGE-CVE.md) | pending |
| M-CFG-SUPPORT-OLD-FORMAT | Wire up or remove the orphaned allure.support-old-format property | P2 | | [todo/M-CFG-SUPPORT-OLD-FORMAT.md](todo/M-CFG-SUPPORT-OLD-FORMAT.md) | none |
| M-FLYWAY-MIGRATIONS | Introduce Flyway migrations; stop relying on ddl-auto update | P2 | | [todo/M-FLYWAY-MIGRATIONS.md](todo/M-FLYWAY-MIGRATIONS.md) | pending |
| M-ENV-SECRETS | Remove hardcoded secrets/defaults from application.yaml (`my-token`, admin/admin) | P2 | | [todo/M-ENV-SECRETS.md](todo/M-ENV-SECRETS.md) | pending |
| M-BOOTSTRAP-ADMIN-HARDENING | Harden admin bootstrap credential (random one-time password or refuse default login) | P2 | | [todo/M-BOOTSTRAP-ADMIN-HARDENING.md](todo/M-BOOTSTRAP-ADMIN-HARDENING.md) | pending |
| M-SETTINGS-CLUSTER-COHERENCE | Make require-api-auth setting cluster-coherent (stop per-JVM cache staleness) | P3 | | [todo/M-SETTINGS-CLUSTER-COHERENCE.md](todo/M-SETTINGS-CLUSTER-COHERENCE.md) | pending |
| M-UI-LOGIN-FORM | Add form-based session login so the UI Logout button is meaningful | P3 | | [todo/M-UI-LOGIN-FORM.md](todo/M-UI-LOGIN-FORM.md) | pending |
| M-CURRENTUSER-REQUEST-CACHE | Resolve the current user once per request (drop 3-4x findByUsername per page) | P3 | | [todo/M-CURRENTUSER-REQUEST-CACHE.md](todo/M-CURRENTUSER-REQUEST-CACHE.md) | none |
| M-APP-PROFILES | Add application-dev/prod profile YAMLs (env-specific config split) | P3 | | -- | -- |
| M-LOGBACK-CONFIG | Add logback-spring.xml (explicit logging config instead of defaults) | P3 | | -- | -- |
| T-JIRA-INTEGRATION | Jira integration (README "coming soon" since 2.14.0) | P3 | | -- | -- |
| T-HTTP-HOOKS | Custom HTTP hooks (README "coming soon" since 2.15.0) | P3 | | -- | -- |
| T-PLUGIN-API-MAVENCENTRAL | Publish Plugin API to MavenCentral with docs (README "coming soon" since 2.14.0) | P3 | | -- | -- |

## Backlog

0 ungroomed items -- see [`backlog/`](backlog/). Groom per [`TRACKER.md`](TRACKER.md) §7.

## Closed (recent)

| id | title | priority | owner | file |
|----|-------|----------|-------|------|
| M-AGENT-ROSTER-REFRESH | Refresh 10 legacy domain agents + team.md for the post-Vaadin stack | P2 | manager | [closed/M-AGENT-ROSTER-REFRESH.md](closed/M-AGENT-ROSTER-REFRESH.md) |
| M-SUPERREVIEW-SKILL | Install and tailor the project-local superreview deep-review skill | P2 | manager | [closed/M-SUPERREVIEW-SKILL.md](closed/M-SUPERREVIEW-SKILL.md) |
| M-UI-BRAND-POLISH | Canonical BrewPage brand rollout + light palette + UI wrap/charset fixes | P2 | manager | [closed/M-UI-BRAND-POLISH.md](closed/M-UI-BRAND-POLISH.md) |
| M-DEEP-REVIEW-COMPAT | Deep anti-regression review of feature/phase-1-vaadin-removal vs master + fix waves | P1 | manager | [closed/M-DEEP-REVIEW-COMPAT.md](closed/M-DEEP-REVIEW-COMPAT.md) |
| M-MEMORY-SYNC-SKILL | Port memory-sync skill from finagra-site and adapt to allure-server | P2 | build-ci-qa | [closed/M-MEMORY-SYNC-SKILL.md](closed/M-MEMORY-SYNC-SKILL.md) |
| M-QUORUM-REVIEW | Multi-agent quorum code review + iterative fixes of feature/phase-1-vaadin-removal working tree | P1 | manager | [closed/M-QUORUM-REVIEW.md](closed/M-QUORUM-REVIEW.md) |

## Feature specs

| task | spec | design |
|------|------|--------|
