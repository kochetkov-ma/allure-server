# Allure-Server Task Board

> Canonical task list + status. Procedure: [`TRACKER.md`](TRACKER.md). New-task template:
> [`TASK_TEMPLATE.md`](TASK_TEMPLATE.md). Ungroomed inbox: [`backlog/`](backlog/).
> Old `.claude/tasks/` is a deprecated pointer here.

## Overall status

- **Release line:** last tag `v2.13.9`; branch `feature/phase-1-vaadin-removal` (not yet merged to `master`) carries the unreleased modernization wave (Vaadin UI replaced with htmx + JTE + Tailwind; Java 25 / Spring Boot 3.4 / Gradle 9, exact pins in `../convention/versions.md`; CI publishes branch images to GHCR, tags to Docker Hub + GHCR).
- **Counts:** backlog 0 | todo 11 | progress 0 | closed 6.
- **Current focus:** M-AGENT-ROSTER-REFRESH is CLOSED -- `/brewcode:teams-setup upgrade` refreshed the 10 legacy domain agents + `team.md` for the post-Vaadin stack, added `web-ui` (replacing `vaadin-gui`) and review-only `intent-guard`; `verify-team.sh` -> VERIFY: PASS, 12/12 agents OK. M-SUPERREVIEW-SKILL is CLOSED -- project-local `superreview` deep-review skill installed and tailored (12-row routing table, new `web-ui` domain agent). M-DEEP-REVIEW-COMPAT is CLOSED -- deep anti-regression review of `feature/phase-1-vaadin-removal` vs `master v2.13.9` done, `./gradlew clean build` GREEN (251 tests). Branch additionally carries the uncommitted M-UI-BRAND-POLISH visual batch (canonical BrewPage brand + light palette + wrap/charset fixes) on top of the pushed deep-review commits. Branch is ready to commit, then merge to `master`. Deferred hardening queued: M-BOOTSTRAP-ADMIN-HARDENING + M-ENV-SECRETS (secret/default hygiene), M-FLYWAY-MIGRATIONS, M-SETTINGS-CLUSTER-COHERENCE, M-UI-LOGIN-FORM, M-CURRENTUSER-REQUEST-CACHE.

## Progress (WIP)

| id | title | priority | owner | file | spec |
|----|-------|----------|-------|------|------|
| _none_ | -- | -- | -- | -- | -- |

## Todo

| id | title | priority | owner | file | spec |
|----|-------|----------|-------|------|------|
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
