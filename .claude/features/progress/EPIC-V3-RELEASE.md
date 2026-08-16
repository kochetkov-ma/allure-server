---
id: EPIC-V3-RELEASE
title: Merge feature/phase-1-vaadin-removal, ship v3.0.0 with full 2.x REST compat, bring repo to open-source-program standard
status: progress
priority: P1
owner: manager
created: 2026-08-16
updated: 2026-08-16
tags: [release, vaadin-removal, oss-hygiene, shipped]
links: []
spec: pending               # none | pending | full | design-only (section 10; never blank)
---

## Context
`feature/phase-1-vaadin-removal` carries the Vaadin -> htmx/JTE/Tailwind modernization
(Java 25 / Spring Boot 3.4 / Gradle 9) and is review-clean (M-DEEP-REVIEW-COMPAT,
M-QUORUM-REVIEW, M-AGENT-ROSTER-REFRESH, M-SUPERREVIEW-SKILL, M-UI-BRAND-POLISH all
closed; `./gradlew clean build` green, 251 tests). Remaining work: merge the branch to
`master`, cut `v3.0.0` while preserving full 2.x REST compatibility, and lift the repo
to open-source-program standard (SECURITY.md, CONTRIBUTING.md, CODEOWNERS, issue/PR
templates, SBOM, signed images, dependency scanning, working docker-compose examples,
actualized README). Umbrella over a 27-task graph in waves A-H; per-wave/per-task
breakdown owned by the manager's TaskCreate graph, not duplicated here.

## Scope

| id | block | in/out | status |
|----|-------|--------|--------|
| S1 | Wave A -- pre-merge branch hygiene (commit outstanding working tree, final CI green) | in | done |
| S2 | Wave B -- merge feature/phase-1-vaadin-removal into master | in | done |
| S3 | Wave C -- v2.x REST compatibility verification (contract/regression pass) | in | done |
| S4 | Wave D -- v3.0.0 versioning, changelog, tag, release build | in | done |
| S5 | Wave E -- OSS governance docs (SECURITY.md, CONTRIBUTING.md, CODEOWNERS, issue/PR templates) | in | done |
| S6 | Wave F -- supply-chain hardening (SBOM, signed images, dependency scanning in CI) | in | done |
| S7 | Wave G -- docker-compose examples verified working + README actualized | in | done |
| S8 | Wave H -- final board sync (task H3) and release retrospective | in | done |

## Acceptance
- [x] `feature/phase-1-vaadin-removal` merged into `master` -- PR #102 squash merged as `a45cd8a`
- [x] `v3.0.0` tagged and released -- tag pushed, release workflow completed, published-artifact verification complete (see Notes)
- [x] SECURITY.md, CONTRIBUTING.md, CODEOWNERS, issue/PR templates present
- [x] SBOM generated, images signed, dependency scanning wired into CI
- [x] docker-compose examples (Postgres + H2) verified working; README actualized
- [x] `.claude/features/board.md` synced by wave H task H3

## Notes
Claimed 2026-08-16 by task-tracker on manager request; moved todo(none existed) -> progress
directly. Manager owns the 27-task graph detail via TaskCreate/TaskUpdate; this task is the
board's single umbrella row for the whole release effort. Spec required: multi-domain
(build/CI, security, docs, docker, release process), >5 files, contract-compat concerns --
route to /task-spec before wave B (merge) proceeds; spec still `pending`, not yet written.

2026-08-16: board resynced against actual release-wave state (was showing all S1-S8
`not-started` after nearly all of it landed -- flagged by review as a defect). Verified done:
secret scan clean (gitleaks + trufflehog, full history + image layers), local build green
(Gradle Java 25 toolchain + foojay resolver, `./gradlew build`/`test` 260 tests), README
actualized (quickstart, inline compose, admin/admin default stated plainly, full settings
reference, Upgrading from 2.x, badges, `docs/img/` assets), `docs/COMPATIBILITY.md` authored
(full endpoint/DTO/status/auth/config matrix, two-ref citations, Upgrading section), Docker
image built linux/amd64 + linux/arm64 with healthcheck and new `APP_VERSION` build ARG,
`.claude/scripts/e2e-api.sh` 30/30 against the local image and both compose files, both
compose files rewritten to build/run this code with three auth modes proven, Helm chart
fixed (ingress template, `/actuator/health` probes, null-volume guard, chart+appVersion
3.0.0), governance docs (SECURITY.md, CODE_OF_CONDUCT.md, CONTRIBUTING.md, SUPPORT.md,
GOVERNANCE.md), GitHub templates (CODEOWNERS, 2 issue forms, config.yml, PR template, all
schema-valid), supply chain (CycloneDX SBOM in `build`, `release.yml` SHA-pinned with
`sbom: true`, `provenance: mode=max`, cosign keyless signing on both registries, generated
release notes, new `dependabot.yml`/`codeql.yml`/`security-scan.yml`, `actionlint` clean +
a real `act` run), version lockstep to 3.0.0 (compose, chart, values, `versions.md`),
two-phase review (3 reviewers + 4 independent verifiers, confirmed findings fixed in this
same wave: a security matcher fix, six compatibility-matrix/README corrections, three
CI/docs fixes).

Deliberately NOT started -- gated on maintainer decision, not incomplete work: open the PR
into `master`; squash merge; delete `origin/feature/tms`; repo metadata + enabling private
vulnerability reporting/secret scanning/push protection/Dependabot security updates; issue
triage across the 17 open issues (close/label proposal needs sign-off before anything
closes); tag `v3.0.0` and verify the published release.

Open items carried forward (not lost):
- `allure.support-old-format` is set in `application.yaml:104` but `AllureProperties` has no
  matching constructor parameter -- no env var can reach it. Deliberately omitted from the
  README until wired up or removed. Tracked as `todo/M-CFG-SUPPORT-OLD-FORMAT.md`.
- Discussions link resolved this pass: GitHub Discussions has been enabled on the repo, so the
  `.github/ISSUE_TEMPLATE/config.yml` link no longer 404s.

2026-08-16 closing pass -- v3.0.0 shipped:
Review phase 2 (four independent verifiers re-checked every phase-1 finding) refuted five
findings, including a claimed cosign failure-masking bug that was wrong about shell semantics.
Two confirmed findings were fixed: a sixth breaking change missing from
`docs/COMPATIBILITY.md` (was marked KEPT -- 2.x clients sending HTTP Basic `admin`/`admin` to
`/api/**` now get 403, any other wrong password gets 401, no-credentials unaffected), and a
real security defect new in 3.0.0 -- report-content authorization matched a hardcoded
`/allure/**` while reports are served from the configurable `allure.reports.dir`, so
customizing that path silently disabled access control even with `require-api-auth=true`;
fixed with a regression test. Final verification: 262 tests green, 30/30 e2e, both directions
of the matcher fix proven with curl, actionlint clean, image healthy at 3.0.0.

PR #102 opened, all four checks green including the repo's first CodeQL run, squash merged to
`master` as `a45cd8a`. A merge conflict with `master` (maintainer's LinkedIn vanity-URL commit
touching a Vaadin file this branch deletes) was resolved by keeping the deletion and carrying
the link forward to the About page. `origin/feature/tms` deleted.

Repo brought to OSS-program standard: description rewritten, `vaadin` topic dropped and seven
accurate topics added, dead homepage cleared, private vulnerability reporting, secret
scanning, push protection and Dependabot security updates all enabled, Discussions enabled,
`security` and `kubernetes` labels created. Helm ingress defaults changed from the dead
`iopump.ru` to `example.com` placeholders (`5256fbb`).

`v3.0.0` tagged and pushed; release workflow completed successfully. Published-artifact
verification is running separately and its result is still pending -- Wave D (S4) stays
`in-progress` and Acceptance item 2 stays unchecked until that completes.

Issue triage: 10 issues closed with cited comments, 7 labelled and left open. Remaining open
issues: #18, #48, #72, #73, #94, #98, #100. Three carry real defects and were filed as tasks
this pass: `todo/BUG-PLUGIN-SUMMARY-500.md` (#98, highest-value open bug),
`todo/BUG-PLUGIN-SCREEN-DIFF.md` (#72), `todo/BUG-BUILD-IMAGE-CVE.md` (#100, blocked on the
first `security-scan.yml` run).

2026-08-16: published-artifact verification complete. `v3.0.0` verified correct on
`ghcr.io/kochetkov-ma/allure-server:3.0.0`, with two defects found and fixed after the fact,
both landed in `88b0014`:
- Docker Hub description was truncated at 25000 characters and lost all three screenshots --
  now served from a purpose-built `docs/DOCKERHUB.md` instead of the truncated inline text.
- `security-scan.yml` could never fire on `release: published` because events raised by the
  default `GITHUB_TOKEN` do not cascade to other workflows -- now invoked from `release.yml`
  via `workflow_call`. This is what produced the first-ever Trivy run (31963225205) against
  the 3.0.0 image, filed as `todo/BUG-BUILD-IMAGE-CVE.md` (5 CRITICAL, 32 HIGH, 48 MEDIUM,
  all with upstream fixes).
- `SECURITY.md` now states verification requires cosign 3.0 or later: 3.x attaches signatures
  as OCI 1.1 referrers, 2.x reports "no signatures found" against a correctly signed image.

Wave D (S4) flipped to `done`, Acceptance item 2 checked. This umbrella stays `progress`
rather than `closed`: `spec:` is still `pending` (never written) and three real defects
found during triage remain open as tasks (`BUG-PLUGIN-SUMMARY-500`,
`BUG-PLUGIN-SCREEN-DIFF`, `BUG-BUILD-IMAGE-CVE`) -- closing the epic is a separate decision
for whoever owns that call, not implied by verification landing.

2026-08-16: `v3.0.1` patch shipped under this release line, sole purpose clearing the 5
CRITICAL CVEs `BUG-BUILD-IMAGE-CVE` filed against `v3.0.0`. Spring Boot 3.4.13 -> 3.5.16,
Spring Cloud 2024.0.3 -> 2025.0.3 (verified-compatible from the real POMs: `spring-cloud-
starter-parent:2025.0.3` declares `spring-boot-starter-parent:3.5.15`; next train 2025.1.x
needs Boot 4.0), `byteBuddyVersion` override dropped (superseded by the new BOM). Zero
source files changed, 262 tests green, 30/30 e2e. Read-only Spring Security 6.4 -> 6.5
review found no behaviour change (PathPattern matcher stays opt-in, `AuthorizationManager`/
CSRF/filter-order classes byte-identical, `oauth` profile hand-verified: starts, `/oauth2/
authorization/google` returns 302). Debt noted, not a defect: `DaoAuthenticationProvider`
no-arg constructor + `setUserDetailsService` deprecated in 6.5 (Spring Security 7 prep,
`security/SecurityConfiguration.java:268-269`). Merged `3b25d7b` via PR #116, tagged
`v3.0.1`, released; published-artifact verification 7/7 pass (SBOM, both registries, cosign,
healthy pull at 3.0.1/Boot 3.5.16). Re-scan from inside the release (job "Scan the published
image", run 31966977964) landed CRITICAL 0/HIGH 13/MEDIUM 27/total 40 against the 3.0.0
baseline of 5/32/48/85 -- exactly the locally predicted figures.

`BUG-BUILD-IMAGE-CVE` is re-scoped, not closed: the remaining 4 HIGH CVEs live in the
checked-in Allure 2.29.0 plugin jars and need a coordinated generator+plugin bump to 2.45.0,
which is report-rendering scope (Behaviors/Packages/screen-diff tabs) and therefore targets
`3.1.0`, not another patch. Cross-linked to `BUG-PLUGIN-SCREEN-DIFF` (#72): the same bump
may close it outright. Two real defects from triage now remain open as tasks
(`BUG-PLUGIN-SUMMARY-500`, and the linked pair `BUG-BUILD-IMAGE-CVE`/`BUG-PLUGIN-SCREEN-
DIFF`).
