# TRACKER -- allure-server task/feature tracker procedure

> Canonical procedure for the `.claude/features/` task board. The board (`board.md`)
> is the single source of truth for the task LIST + status. A task file (when present)
> is the source of truth for that task's DETAIL. Read this before touching any task.

[DICT: WIP=work in progress, GROOM=backlog triage]

## 1. What this is

A lightweight, file-based Kanban for allure-server (Java 25 / Spring Boot 3.4 Allure
report server). No external tool. Everything lives in `.claude/features/` and is versioned
with the repo. It supersedes the old `.claude/tasks/` directory (now a deprecated pointer)
as the canonical task tracker.

## 2. Layout

```
.claude/features/
  board.md            <- DASHBOARD: overall status + index table of EVERY task (canonical list)
  PROGRESS.md         <- SESSION progress snapshot (5 fields, overwritten in place); !=a second board
  TRACKER.md          <- this procedure
  TASK_TEMPLATE.md    <- copy this to create a new task file
  INDEX.md            <- maps the control files
  backlog/            <- INBOX: ungroomed junk/ideas/dumps; not yet real tasks
    README.md
  todo/               <- accepted tasks, queued, not started (file optional here)
  progress/           <- WIP; a task file is MANDATORY here
  closed/             <- done/shipped (file optional; keep notable ones)
  specs/              <- per-task implementation specs (linked from task links:)
```

Folder name == task status. A task file always lives in the folder matching its status.

## 3. Lifecycle (state machine)

```
            groom (promote)        pick up            ship
 backlog  ------------------>  todo --------> progress --------> closed
   |  \                          ^               |
   |   \  groom (trash)          |  re-queue     |  blocked/parked
   |    -----> [deleted]         +---------------+
   |
   +--> groom (merge into existing task)
```

| Transition | Action |
|------------|--------|
| backlog -> todo | groom: item is a real, scoped task. Give it an id, create a task file (or a board row), move/author under `todo/`. |
| backlog -> deleted | groom: noise, done already, or out of scope. Delete the backlog file. Note nothing -- it was junk. |
| backlog -> merge | groom: duplicates/extends an existing task. Fold notes into that task, delete the backlog file. |
| todo -> progress | pick up: MOVE the file into `progress/` (create one from template if it was table-only), set `status: progress`, set `owner`, set `updated`. |
| progress -> closed | ship: MOVE the file into `closed/`, set `status: closed`, add a one-line outcome + the version/commit that closed it. GATE G2 (section 10): blocked while `<ID>-spec.md` or `<ID>-design.md` has an open question with `blocking: yes`, unless `## Notes` carries `SPEC WAIVER: <reason>`. |
| progress -> todo | re-queue/park: MOVE back, set `status: todo`, note why parked. |

Always update `board.md` in the SAME change as any transition. The board lags reality = the board is wrong.

## 4. Task file format

Copy `TASK_TEMPLATE.md`. Frontmatter is required; body sections are recommended.

```markdown
---
id: T-JIRA-INTEGRATION     # unique; see id convention below
title: Implement Jira integration
status: progress           # backlog | todo | progress | closed (MUST match folder)
priority: P1               # P1 (now) | P2 (soon) | P3 (nice-to-have)
owner: report-service      # agent name or person; empty in todo/backlog
created: 2026-06-11
updated: 2026-06-11
tags: [integration, plugin]
links:
  - README.md
spec: none                 # none | pending | full | design-only (section 10; never blank)
---

## Context
Why this exists, what problem it solves.

## Scope

| id | block | in/out | status |
|----|-------|--------|--------|
| S1 | one-line scope block | in | not-started |
| S2 | explicitly excluded thing | out | -- |

## Acceptance
- [ ] concrete, checkable outcome 1
- [ ] concrete, checkable outcome 2

## Notes
Running log: decisions, blockers, links to PRs/commits/reports.
```

Invariants:
- `status` frontmatter MUST equal the folder. On any move, change both.
- A task in `progress/` MUST have a file. In `todo/`/`backlog/` a file is optional (board row alone is enough until it has detail).
- Closing a task: keep `updated` current and record the closing version/commit in `## Notes`.

## 5. ID convention

| Prefix | Use |
|--------|-----|
| `T-*`   | feature / product task |
| `BUG-*` | defect |
| `M-*`   | maintenance / refactor / tech-debt |
| `EPIC-*`| umbrella over several tasks |

Id = UPPER-KEBAB, short, stable. Once minted it never changes (it is the filename stem and the board key).

## 6. The board (`board.md`)

`board.md` is the canonical LIST. It holds:
1. **Overall status** -- release line, headline counts (backlog/todo/progress/closed), current focus (1-3 lines).
2. **Progress table** -- every WIP task.
3. **Todo table** -- every queued task (incl. rows with no file yet).
4. **Backlog table** -- count + pointer to `backlog/` (do not enumerate noise here).
5. **Closed (recent)** -- last N notable closes; older ones live as files in `closed/` only.

Table columns: `id | title | priority | owner | file`. The `file` cell links the task file or says `--` when table-only.
CORRECTION to the column list above: Progress + Todo have SIX columns, `id | title | priority | owner | file | spec` -- a 5-cell row corrupts them. The `spec` cell is `full` | `design-only` | `none` | `pending` (`--` if there is no task file yet, or the task file carries no `spec:` key -- e.g. migrated docs) -- see section 10. The `Feature specs` table is `task | spec | design`, the last two linking `specs/<ID>-spec.md` and `specs/<ID>-design.md` (`--` when absent).

Rule: if a task exists anywhere (file or row), it is on the board. The board is regenerated/edited by hand on every transition. Keep it terse.

## 7. Backlog grooming (do this periodically)

`backlog/` is the dumping ground -- raw ideas, pasted error logs, "look into X later", half-thoughts.
Drop anything there fast as a `*.md` file; do not gate it.

Groom on a regular cadence (e.g. start of a work session, or when backlog > ~10 items):

1. Read each `backlog/*.md`.
2. Decide its fate per §3: **promote** to a real `todo` task (mint id, create file/row, update board), **merge** into an existing task, or **trash** (delete).
3. Never leave a groomed item in `backlog/`. After grooming, `backlog/` holds only un-triaged items.
4. Log nothing for trashed junk; for promoted items the new task file carries the context.

The dashboard skill (`.claude/skills/task-board/`) knows this loop -- invoke it to run a groom pass.

## 8. Working procedure (per session)

1. Open `board.md` -> read overall status + progress table.
2. (Optional) groom `backlog/` per §7.
3. Pick a `todo` task (respect priority). Move it to `progress/`, set owner, update board.
4. Do the work. Keep `## Notes` in the task file current, and refresh `PROGRESS.md` whenever something moves.
5. On done: move to `closed/`, record closing version/commit, update board counts + focus.
6. If new work surfaces mid-task, drop it in `backlog/` (do not derail).

## 9. Relationship to other surfaces

| Surface | Role now |
|---------|----------|
| `board.md` | canonical task list + status (THIS system) |
| `.claude/tasks/` | DEPRECATED -> thin pointer to the board (was plugin state/logs only) |
| `.claude/reports/` | generated analysis/report artifacts (unchanged; link from task `links:`) |
| `.claude/convention/` | architecture/pattern docs (link from task `links:`, do not duplicate) |

## 10. Spec layer

Non-trivial tasks get THREE documents. Flat filenames -- !=`specs/<ID>/spec.md`.

| Doc | Path | Owns |
|-----|------|------|
| Task | `{backlog,todo,progress,closed}/<ID>.md` | WHAT + WHY: context, quotes, links, the ask, and the `## Scope` blocks with ids |
| Product spec | `specs/<ID>-spec.md` | Decisions (`D1..Dn`), resolved questions, OPEN questions (`Q1..Qn`), scope coverage |
| Design spec | `specs/<ID>-design.md` | Architecture, data flow, interfaces, data model, failure modes + reliability, complexity budget (what we deliberately do NOT build), non-goals, scope coverage, OPEN architectural questions (`AQ1..AQn`) |

Scope ids are `S1..Sn`, task-local, cited globally as `<ID>#S1`. Once minted, never renumbered -- retire a block by flipping it to `out`, !=reuse its id.

EXECUTION STATUS. The `## Scope` table has exactly four columns, `id | block | in/out | status`. `status` is the EXECUTION axis of that one id, enum exactly `not-started` | `in-progress` | `done`; an `out` row carries `--`. Written by the `task-tracker` agent and by the `task-board` skill's ADD/MOVE flows -- both spec docs are READ-ONLY consumers that reference a status by id, and the tracker still !=write anything under `specs/**`. `S#` only: `D#`, `Q#` and `AQ#` carry no status. No gate: an `in` id still `not-started`/`in-progress` at a transition is reported LOUDLY and never refuses the transition, and there is no waiver marker for it. It !=replace `## Acceptance` -- the acceptance checkboxes are the task's outcome checklist, `status` is per scope id; keep both, !=unify.

The task never holds architecture; the specs never redefine scope. One fact, one owner.

Templates: `specs/SPEC_TEMPLATE.md`, `specs/DESIGN_TEMPLATE.md`.

### 10.1 Does this task need a spec?

Required if ANY holds:
- touches >1 domain
- expected to touch >~5 files
- new external integration / new dependency
- schema, API or contract change
- requirements ambiguous, or the task carries open questions
- user asked for a design/spec

Otherwise `spec: none`. Whichever branch is taken, `spec:` is ALWAYS written -- never left blank.

| `spec:` | Meaning |
|---------|---------|
| `none` | deliberately no spec (small task). An explicit decision, !=an omission |
| `pending` | a spec IS required but is not written yet. Triggers the redirect (10.4) |
| `full` | both `<ID>-spec.md` and `<ID>-design.md` exist and are listed in `links:` |
| `design-only` | only `<ID>-design.md` exists (pure architecture change, no product ambiguity) |

### 10.2 Gates

| Gate | Rule |
|------|------|
| G1 coverage | `in` scope ids ONLY: every `in` scope id of the task appears in BOTH `## Scope coverage` tables with status `covered`. Status enum is exactly `covered` \| `partial` \| `uncovered`. Any `uncovered`/`partial` `in` id -> spec `status:` stays `draft`, never `agreed`. `out` rows MAY appear in the tables; their status NEVER affects G1. `covered` is the SPEC axis and NEVER implies execution `done`; `done` NEVER implies `covered` -- orthogonal (see 10) |
| G2 close | `progress -> closed` is BLOCKED while an open question with `blocking: yes` stands in EITHER doc: `<ID>-spec.md` `## Open questions` (ids `Q1..Qn`) or `<ID>-design.md` `## Open architectural questions` (ids `AQ1..AQn`). Both are scanned -- in `design-only` mode there is no spec file, so a spec-only check is unenforceable. Override ONLY by an explicit line in the task's `## Notes`: `SPEC WAIVER: <reason>` -- a deliberate, recorded act. No waiver line = no close |
| G3 sync | changing the task's `## Scope` invalidates BOTH specs -> set spec `status: draft` and run `/task-spec <ID> refresh`. Editing ONLY a `status` cell !=a scope change -- it never trips G3 |
| G4 no solo design | the design doc is NEVER authored by a single generalist agent. See 10.3 |
| G5 staleness | REPORT-ONLY, fired at close on the two docs G2 already opened -- no extra reads. Spec FM `status:` still `draft`, or an `in` id just marked `done` that is `uncovered`/`partial` in `## Scope coverage` -> `SPEC STALE: <ID> ...` + ONE `NEXT: run /task-spec <ID> refresh`. It NEVER blocks the close (G2 is the only blocker), never writes a spec doc, never touches `## Scope coverage`, never renumbers an id |

### 10.3 The design is never authored solo

The DESIGN phase fans out to THIS repo's own domain agents in `.claude/agents/` -- at minimum ONE agent
per domain the task touches, all spawned in ONE message. Authoring the architecture from the main
session alone is forbidden. The same fan-out runs again for design review.

Fallback order, per domain: a project domain agent for that domain -> a project architecture-capable
agent -> the built-in `Plan` agent. Whichever was used MUST be named per domain in the design's
`## Evidence`. If a touched domain had no domain agent, the design MUST say so explicitly --
silence is a defect.

### 10.4 How to invoke

| Path | Form |
|------|------|
| Explicit | `/task-spec <ID> [full\|design\|refresh]`. `full` (default) = spec + design; `design` = design only; `refresh` = re-sync both against the task's current `## Scope`, preserving `D#`, `Q#` and `AQ#` ids and the `## Scope` `status` cells |
| Prose | plain request -- "system design for <ID>", "architect this", "write the spec". The skill is model-invoked; naming it is not required |
| Redirect | the `task-tracker` agent cannot call a skill for the main session. When a task needs a spec and has none, it ends its report with exactly: `NEXT: run /task-spec <ID> (spec required: <reason>)` -- run that |

## 11. Ownership

- Skill `.claude/skills/task-board/` -- on-demand entry point to view/update the board and run a groom pass.
- Everyone else: when you start/finish/park a task, follow §3 + §8 and keep the board in sync.
