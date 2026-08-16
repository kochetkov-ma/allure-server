# Allure-Server Task Board

> Canonical task list + status. Procedure: [`TRACKER.md`](TRACKER.md). New-task template:
> [`TASK_TEMPLATE.md`](TASK_TEMPLATE.md). Ungroomed inbox: [`backlog/`](backlog/).
> Old `.claude/tasks/` is a deprecated pointer here.

## Overall status

- **Release line:** `v3.0.0` tagged and pushed, release workflow green; `feature/phase-1-vaadin-removal` squash merged to `master` as `a45cd8a` via PR #102 (Vaadin UI replaced with htmx + JTE + Tailwind; Java 25 / Spring Boot 3.4 / Gradle 9, exact pins in `../convention/versions.md`). Helm ingress defaults fixed to `example.com` placeholders in `5256fbb`. Published-artifact verification is running separately -- result still pending.
- **Counts:** backlog 0 | todo 15 | progress 1 | closed 6.
- **Current focus:** EPIC-V3-RELEASE is IN PROGRESS -- owner manager, closing pass done. Waves A, B, C, E, F, G, H are DONE (merge to master, 262 tests green, 30/30 e2e, secret scan clean, governance docs + GitHub templates, SBOM/signing/scanning wired, compose + Helm chart verified, README actualized, version lockstep to 3.0.0, review phase 2 with 4 independent verifiers -- refuted 5 findings, confirmed and fixed 2: a missing 2.x auth-compat matrix entry and a real report-authorization-path security defect with a regression test). Wave D (tag/release) is the sole open item: `v3.0.0` tagged, release workflow completed, but published-artifact verification is running separately and not yet confirmed -- epic stays `progress` until that lands. Repo brought to OSS-program standard (metadata, security settings, Discussions, labels), `origin/feature/tms` deleted, 10 issues closed / 7 labelled and left open (#18, #48, #72, #73, #94, #98, #100). Three real defects from triage filed as tasks: BUG-PLUGIN-SUMMARY-500 (#98, P1, highest-value open bug), BUG-PLUGIN-SCREEN-DIFF (#72, P2), BUG-BUILD-IMAGE-CVE (#100, P2, blocked on first security-scan.yml run). Spec still pending -- route to `/task-spec EPIC-V3-RELEASE`. Open item for maintainer decision: `allure.support-old-format` dead property, tracked as M-CFG-SUPPORT-OLD-FORMAT. Deferred hardening still queued: M-BOOTSTRAP-ADMIN-HARDENING + M-ENV-SECRETS (secret/default hygiene), M-FLYWAY-MIGRATIONS, M-SETTINGS-CLUSTER-COHERENCE, M-UI-LOGIN-FORM, M-CURRENTUSER-REQUEST-CACHE.

## Progress (WIP)

| id | title | priority | owner | file | spec |
|----|-------|----------|-------|------|------|
| EPIC-V3-RELEASE | Merge phase-1-vaadin-removal, ship v3.0.0 with full 2.x REST compat, bring repo to OSS-program standard | P1 | manager | [progress/EPIC-V3-RELEASE.md](progress/EPIC-V3-RELEASE.md) | pending |

## Todo

| id | title | priority | owner | file | spec |
|----|-------|----------|-------|------|------|
| BUG-PLUGIN-SUMMARY-500 | Unhandled 500 in CustomReportMetaPlugin when report has no widgets/summary.json | P1 | | [todo/BUG-PLUGIN-SUMMARY-500.md](todo/BUG-PLUGIN-SUMMARY-500.md) | none |
| BUG-PLUGIN-SCREEN-DIFF | Screen diff not rendered despite screen-diff-plugin enabled | P2 | | [todo/BUG-PLUGIN-SCREEN-DIFF.md](todo/BUG-PLUGIN-SCREEN-DIFF.md) | pending |
| BUG-BUILD-IMAGE-CVE | Address image CVEs surfaced by the first security-scan.yml run | P2 | | [todo/BUG-BUILD-IMAGE-CVE.md](todo/BUG-BUILD-IMAGE-CVE.md) | none |
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
