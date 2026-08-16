---
id: EPIC-V3-RELEASE
title: Merge feature/phase-1-vaadin-removal, ship v3.0.0 with full 2.x REST compat, bring repo to open-source-program standard
status: progress
priority: P1
owner: manager
created: 2026-08-16
updated: 2026-08-16
tags: [release, vaadin-removal, oss-hygiene]
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
| S2 | Wave B -- merge feature/phase-1-vaadin-removal into master | in | not-started |
| S3 | Wave C -- v2.x REST compatibility verification (contract/regression pass) | in | done |
| S4 | Wave D -- v3.0.0 versioning, changelog, tag, release build | in | in-progress |
| S5 | Wave E -- OSS governance docs (SECURITY.md, CONTRIBUTING.md, CODEOWNERS, issue/PR templates) | in | done |
| S6 | Wave F -- supply-chain hardening (SBOM, signed images, dependency scanning in CI) | in | done |
| S7 | Wave G -- docker-compose examples verified working + README actualized | in | done |
| S8 | Wave H -- final board sync (task H3) and release retrospective | in | in-progress |

## Acceptance
- [ ] `feature/phase-1-vaadin-removal` merged into `master` -- gated on maintainer confirmation (PR + squash merge)
- [ ] `v3.0.0` tagged and released -- 2.x REST endpoint compatibility already verified (`docs/COMPATIBILITY.md`, e2e script 30/30); tag + publish still gated on maintainer confirmation
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
- Open question for the maintainer (no task filed, needs a decision not code): the repo's
  `.github/ISSUE_TEMPLATE/config.yml` links to GitHub Discussions, which is currently disabled
  on the repo -- that link 404s until Discussions is enabled (or the link is removed).
