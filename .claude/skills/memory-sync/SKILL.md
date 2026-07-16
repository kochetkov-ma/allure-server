---
name: memory-sync
description: "Syncs allure-server instruction memory (CLAUDE.md + .claude/rules + .claude/convention + .claude/agents) against actual code: verifies every checkable fact, adds new facts from chosen scope, removes stale ones, dedupes, compresses to house style, keeps CLAUDE.md §4 Team table consistent with .claude/agents/, proposes new agent when scope introduces big new domain. Triggers: sync memory, memory sync, sync claude.md, sync rules, sync agents, update project memory, refresh memory, memory drift, stale docs, синхронизируй память."
user-invocable: true
argument-hint: "[scope: session|branch|recent[:N]|all] [free-form focus, e.g. 'only rules']"
allowed-tools: Read, Glob, Grep, Task, Bash, Edit
model: opus
---

# Memory Sync (allure-server)

**ROLE:** multi-agent coordinator keeping the project's instruction memory truthful vs code. Memory rots: code moves faster than CLAUDE.md/rules/conventions/agents. Diffs memory against reality for a chosen SCOPE and repairs it — facts FIRST, style second.

**DIRECTION:** code is truth; memory follows. NEVER "fix" source code to match a doc — a doc-vs-code mismatch is ALWAYS repaired on the doc side (if the code looks like the actual bug, REPORT it, don't touch it).

## Input resolution

Parse `$ARGUMENTS`: first token matching a scope keyword -> `{SCOPE}` (default `session`); remainder = free-form `{FOCUS}` narrowing the pipeline (e.g. "only rules", "focus on agents"). No focus -> full pipeline, all batches.

| `{SCOPE}` | Change-fact source |
|-----------|--------------------|
| `session` (default) | THIS conversation's changes: new endpoints/services/entities/templates/properties, renames, removals, behaviors. Coordinator writes it from own context — no agent for (a). |
| `branch` | `git diff origin/master...HEAD` (fallback `master...HEAD`). |
| `recent[:N]` | `git diff HEAD~N..HEAD` (default N=10); or since last release: `git diff $(git tag --list 'v*' --sort=-v:refname | head -1)..HEAD` when user says "since last tag". |
| `all` | No diff — EVERY checkable fact in every memory file verified against current code. |

ANNOUNCE — print this short PLAN block before any work:

```
[memory-sync] PLAN
Scope: {SCOPE} — {why: which token matched | "default (no scope token)"}
Focus: {focus interpretation | "full sync"}
Batches: {batch: N files, ...}
Fan-out: {N} gather (Explore) + {M} sync (general-purpose) agents intended
```

PHASE STATUS — ONE chat line at every phase boundary:
`[memory-sync] Phase {N} {name} — started|done: {key numbers}` (facts collected, files inventoried, batches spawned, files edited, verdicts ok).

## Sync targets — resolved DYNAMICALLY every run

NEVER hardcode file lists: agents/rules/conventions grow. Phase 1 globs the surface fresh each run; a new file in any batch dir is picked up automatically.

| Batch | Glob | Batch-specific checks |
|-------|------|----------------------|
| root | `CLAUDE.md` (+ `AGENTS.md`, `CLAUDE.local.md` — skip if absent) | §0 lazy-load index paths exist; §1 facts hold (main class, package `ru.iopump.qa.allure`, endpoints, `src/main/jte/`, auth/persistence claims); §2 command table matches `build.gradle` tasks + `Dockerfile` + `docker-compose*.yml`; §4 Team table per AGENT REGISTRY below; §5 git-identity claims verified via read-only `git config --local --get`. |
| rules | `.claude/rules/*.md` | `paths:` frontmatter globs match dirs/files that exist; rule facts (class names, config claims, tool behavior) hold in `src/` or named config; NO inline version literals — pins point to `.claude/convention/versions.md` (CLAUDE.md rule 4). |
| conventions | `.claude/convention/*.md` | Every pin in `versions.md` == the source file it names (`build.gradle`, `gradle/dependencies.gradle`, `gradle/wrapper/gradle-wrapper.properties`, `Dockerfile`, `.github/workflows/*`); refresh its "Verified against code {date}" stamp ONLY when pins were actually re-checked; etalon paths in `reference-patterns.md` exist; `project-architecture.md` claims match `src/main/java/ru/iopump/qa/allure/` layout; `testing-conventions.md` claims match `src/test/`. |
| agents | `.claude/agents/*.md` | Frontmatter (name/description/tools) sane; Domain paths exist (an agent whose entire Domain is gone -> mark LEGACY in its Mission line + report, don't delete the file); Triggers/EXCLUDES name agents that EXIST on disk (cross-check fresh glob); "Read First" pointers resolve; **"Immutable Traits" sections are NEVER edited**; refresh "Last Updated" only on files you edited. |

**EXEMPT — never edit:** ALL source code and configs (`src/`, `config/`, `build.gradle`, `settings.gradle`, `gradle.properties`, `gradle/**`, `Dockerfile`, `docker-compose*.yml`, `tailwind.config.js`, `migration.sql`, `.github/**`), generated/runtime dirs (`build/**`, `allure/**`), `README.md` (user docs — report drift only), `.claude/teams/**` (plugin-owned — report drift, fix via `/brewcode:teams update`), `.claude/features/**` (task board — `task-tracker` agent owns it; report drift only), `.claude/{tasks,logs,reports,brewtools,tmp}/**`, `.claude/settings.local.json`, `.claude/skills/**` (incl. this file). Never write a secret VALUE anywhere (tokens, passwords, API keys); non-secret ids/paths/env-var NAMES are fine.

## AGENT REGISTRY (root batch owns it)

CLAUDE.md §4 Team table registers `.claude/agents/*.md`; drifts as agents are added/retired:
- Every row MUST have a matching agent file; the Domain cell MUST match that file's Mission/Domain lines. Fix mismatches in CLAUDE.md.
- Agent file on disk, absent from table -> REPORT only (team membership is a user/plugin decision — never auto-add rows; suggest `/brewcode:teams update`).
- Row without file -> stale: remove row, report it.
- `.claude/teams/default/team.md` roster (incl. its `Agents: N` count) is plugin-owned: CROSS-CHECK it against the same glob, REPORT drift, never edit it.

## Phase 1 — GATHER (parallel, read-only)

Spawn read-only `Explore` agents IN ONE message to build:
- **(a) change-fact list.** Git scopes: agents read the scope diff, return a terse fact list — added/renamed/removed endpoints, services, entities, JTE templates, properties, pins, behaviors. `session`: coordinator writes the list itself (skip the agent). `all`: skip (a).
- **(b) target inventory.** Run the batch globs (filesystem, not `git ls-files` — a git-ignored `CLAUDE.local.md` must still be found), collect sizes (`wc -l`), flag broken references (a memory file pointing at a path that no longer exists), list `.claude/agents/*.md` names for the registry check.

Code lookups: `grepai_search` FIRST for semantic questions ("where is upload handled"); plain grep for exact literals (versions, class names, config keys).

If `{FOCUS}` narrows the surface, drop the other batches now.

## Phase 2 — SYNC (parallel batch agents)

Spawn ONE `general-purpose` agent PER non-empty batch, ALL in one message. Each gets the change-fact list, its file list, its batch-specific checks row + this contract:

```
## memory-sync batch: {BATCH} ({SCOPE})
Files you may EDIT (nothing else): {FILE_LIST}
Change facts from scope: {FACTS or "none — verify everything against current code"}
Batch checks: {BATCH_CHECKS}

Per file, in priority order:
1. PRIMARY — fact synchronization:
   - VERIFY every checkable fact against the repo (grep/read actual code): paths, class/endpoint
     names, Gradle tasks, JTE template names, @ConfigurationProperties keys, env vars, pins,
     described behaviors.
   - ADD facts the scope introduced that belong in this file.
   - REMOVE facts no longer true (deleted class, renamed endpoint, retired behavior).
2. SIDE effects (never at the expense of facts):
   - DEDUPLICATE: a fact repeated across files keeps ONE canonical home; replace copies with a
     pointer ("canonical: <file> §/#N"). Version numbers: canonical home is ALWAYS
     `.claude/convention/versions.md`.
   - COMPRESS verbose passages into the repo's dense table/pointer style; keep meaning intact.

HARD constraints:
- Code is truth; memory follows. Never edit source to match a doc; suspected code bug -> REPORT.
- Preserve [DICT: ...] headers and STABLE table row ids — rule numbers are cross-referenced
  repo-wide: NEVER renumber; gaps are intentional.
- Evergreen: no dates/statuses/versions-as-status. EXCEPTIONS (intentional, keep): agent files'
  "Last Updated" (refresh only on files you edited), versions.md "Verified against code" stamp
  (refresh only when pins re-checked), incident-source notes in rules.
- Agent files' "Immutable Traits" sections: read-only, always.
- English only. NEVER write a secret VALUE.
- Read-only git (diff/log/show/ls-files); never checkout/reset/restore.
- NEVER run `./gradlew` (slow, mutates build/) — verify build facts by READING build.gradle,
  gradle/*.gradle, workflow YAML.
- Edit ONLY your listed files; never source code, configs, or `.claude/{teams,features,tasks,skills}/**`.
- Uncertain whether a fact is stale? Verify in code; still uncertain -> leave it and REPORT it.

Return JSON: {"file": {"added": [...], "removed": [...], "fixed": [...], "dedup": [...],
"compressed": N, "broken_refs": [...], "uncertain": [...]}} — one entry per file, empty lists if clean.
```

Batches are disjoint -> parallel edits never collide. Cross-batch dedup (canonical home in another batch's file) -> the agent reports it; coordinator applies the cross-batch pointer itself afterwards.

## Phase 3 — VERIFY (independent check — gates the report)

After all batch agents return, spawn independent READ-ONLY checker agents IN ONE message — one per batch with edits. Each checker gets that batch's JSON + edited file list, must:
- (a) re-verify every ADDED/FIXED fact against the actual code (grep/read);
- (b) confirm every REMOVED fact is truly gone from REALITY (code/repo), not merely deleted from docs;
- (c) confirm DICT headers + stable rule ids + agent "Immutable Traits" intact, §4 Team table consistent with `.claude/agents/*.md`;
- (d) confirm via `git status --porcelain` + `git diff --name-only` that ONLY listed memory targets changed (if a git-ignored `CLAUDE.local.md` was edited, it never shows in the diff — re-read the file directly instead);
- (e) scan the diff for anything resembling a secret VALUE.

Checker returns per-file JSON: `{"ok": bool, "violations": [...]}`. Violations -> coordinator sends targeted fixes back to the responsible batch agent (or fixes trivial pointer issues itself), then re-verifies ONLY the fixed files. No report until every verdict is ok or remaining violations are explicitly listed as unresolved.

## Phase 4 — AGENT PROPOSAL (assess, never auto-create)

Judge whether the scope introduced a domain big enough for a NEW `.claude/agents/` agent — bar: a self-contained domain with its own surface and vocabulary (precedents: `plugin-youtrack` for TMS/Feign/codegen, `task-tracker` for the board; future candidates: web-ui engineer for JTE+HTMX+Tailwind replacing LEGACY `vaadin-gui`, observability/actuator, object-storage backend, user-admin), NOT a new endpoint/DTO/template (those belong to existing owners). If yes, PROPOSE in the final report: agent name, description gist, trigger phrases, EXCLUDES (existing agents owning neighboring seams — name them from fresh glob), a note to register via `/brewcode:teams update`. Do NOT create the file without explicit user confirmation.

## Phase 5 — REPORT (chat only, no report file)

```
memory-sync complete — SCOPE={SCOPE}, {N} files scanned, {M} edited.

| File | Added | Removed | Fixed | Dedup | Compressed | Verified |
|------|-------|---------|-------|-------|------------|----------|

Verification: {all edits confirmed | N unresolved (listed)}
Registry drift: {agents on disk not in §4 table | rows without files | team.md drift (report-only) | none}
Broken refs: {list or none}
Uncertain (left in place, verify manually): {list or none}
Suspected code bugs (memory was right, code looks wrong): {list or none}
Agent proposal: {name + triggers + excludes | none}
```

Nothing changed -> say exactly that ("memory already in sync for {SCOPE}") and stop.

## Safety (baked into every phase)

- Git READ-ONLY: `diff`/`log`/`show`/`ls-files`/`merge-base`/`tag`/`config --get` only; never `checkout`/`reset`/`restore`/`revert`.
- Build READ-ONLY: never execute `./gradlew`, `docker`, or any mutating command — all verification is file reading.
- Edits land ONLY in globbed memory targets — never source code, configs, plugin state, the task board, or this skill.
- Stable rule ids stay stable; DICT headers stay; Immutable Traits stay; files stay evergreen (listed exceptions only), English.
