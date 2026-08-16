---
name: task-board
description: "Views and updates the allure-server file-based task board at .claude/features/. Triggers: show the board, task board, board status, what's in progress, add a task, create task, move task to progress, close task, dump to backlog, groom backlog. specs view, spec status, which tasks need a spec, spec coverage, blocking questions."
argument-hint: "[prompt] [view | add | move | backlog | groom]"
allowed-tools: Read, Write, Edit, Bash, Glob, Grep, Agent
doc_type: llm
version: "5.6.0"
content_version: "5.6.0"
generated_by: "brewtools:task-board-setup"
last_updated: "2026-08-13"
---

# Task Board (dashboard)

On-demand entry point for the allure-server file-based Kanban under `.claude/features/`.
Authoritative procedure: `.claude/features/TRACKER.md`. This skill mirrors it -- do not invent rules.

## Prompt contract

Position 1 of `$ARGUMENTS` is a **free-form prompt** (English) -- `view`/`add`/`move`/`backlog`/
`groom` and any flags are optional and may follow in any order. Nobody types keys: resolve the flow
FROM the prompt.

| Mode | EN keywords | RU keywords | Mutates? |
|------|-------------|-------------|----------|
| `view` (default) | *(empty)*, show the board, task board, board status, what's in progress | покажи доску, статус доски, что в работе | no |
| `add` | add a task, create task, new task | добавь задачу, создай задачу, новая задача | yes |
| `move` | move task to progress, close task, pick up, ship it | перемести в работу, возьми в работу, закрой задачу | yes |
| `backlog` | dump to backlog, backlog it, look into later | закинь в бэклог, в бэклог | yes |
| `groom` | groom backlog, groom the backlog, triage backlog | разбери бэклог, приведи бэклог в порядок | yes |

1. Strip flags. An explicit mode token anywhere wins outright, no scoring.
2. Else score modes by distinct whole-word keyword hits (table above). Highest unique score wins;
   a tie against `view` picks `view` (read-only); a tie of two mutating modes falls to the keyword
   appearing first in the prompt.
3. Empty arguments -> `view` (the documented default); a read-only run asks nothing.
4. Prose that is not a mode is still input: extract the task id, folder or row it names before
   entering the flow (VIEW/ADD/MOVE/BACKLOG/GROOM below) -- never guess the first word as a mode.
5. Outcome-changing ambiguity (which task, which folder) -> ONE `AskUserQuestion` before any write.

Then print this block once, before the first action:

```
PLAN — task-board
INPUT:  <arguments verbatim, or "(empty)">
MODE:   <resolved> -- <explicit | matched keyword: X | default>
SCOPE:  <task id / row / folder touched, or "read-only view">
DO:     <2-5 imperative bullets>
RESULT: <board.md row, task file, or the view/report the user ends up holding>
```

Labels are literal ASCII; values follow English.

## Invariants (always hold)

- **Folder == status.** A task file lives in `todo/` | `progress/` | `closed/` (or `backlog/`); its `status:` frontmatter MUST equal the folder. On a move, change both.
- **Board is canonical and never lags.** Edit `board.md` in the SAME change as any transition. A lagging board is a wrong board.
- **Ids never change.** UPPER-KEBAB, short, stable -- the filename stem and the board key.
- **A task in `progress/` MUST have a file** (from `TASK_TEMPLATE.md`). In `todo/`/`backlog/` a file is optional (a board row alone is enough).
- **English only.** Closing records the closing version/commit in `## Notes`.
- **`PROGRESS.md` tracks the SESSION, not the tasks.** Refresh `.claude/features/PROGRESS.md` in the SAME change as any transition -- five fields: `Updated`, `In flight`, `Moved since last update`, `Blocked`, `Next`, overwritten in place. !=a second board; rules in `.claude/rules/tasks.md`.
- **Scope change invalidates specs (G3).** Editing a task's `## Scope` -> spec `status: draft` + `/task-spec <ID> refresh`. Editing ONLY a `status` cell !=a scope change -- it never trips G3.

Layout: `board.md` (dashboard), `PROGRESS.md` (session progress), `TRACKER.md` (procedure), `TASK_TEMPLATE.md`, `backlog/` (ungated inbox), `todo/`, `progress/`, `closed/`, `specs/`.

## Flows

### 1. VIEW

1. Read `.claude/features/board.md` + `PROGRESS.md`.
2. Summarize: overall status (release line), counts (backlog | todo | progress | closed), current focus (1-3 lines), then the Progress (WIP) and Todo tables. Do not enumerate backlog noise. Close with `PROGRESS.md`'s `Blocked` / `Next` only -- `In flight` / `Moved` are already in the tables above; if its `Updated` predates the newest task `updated:`, say it is stale and rewrite it.

### 2. ADD task

1. Mint an UPPER-KEBAB id by prefix: `T-*` feature, `BUG-*` defect, `M-*` maintenance, `EPIC-*` umbrella.
2. Copy `TASK_TEMPLATE.md` into the target folder (usually `todo/`) as `<ID>.md`.
3. Fill frontmatter: `id`, `title`, `status` (== folder), `priority` (P1/P2/P3), `owner` (empty in todo/backlog), `created`, `updated` (today), `tags`, `links`.
4. Add a row to `board.md` in the matching table (`id | title | priority | owner | file | spec`). `file` links the file or `--` if table-only.
5. Set `spec:` -- ALWAYS, never blank. `pending` if ANY holds: >1 domain | >~5 files | new integration/dependency | schema/API/contract change | ambiguous requirements or open questions | user asked for a design/spec. Else `none`. The same value goes in the board row's `spec` cell. Every `## Scope` row is born with a `status`: an `in` row `not-started`, an `out` row `--`.
6. If `spec: pending`, end the flow with exactly: `NEXT: run /task-spec <ID> (spec required: <reason>)`. !=author the spec here.

### 3. MOVE / TRANSITION

`todo -> progress` (pick up) | `progress -> closed` (ship) | `progress -> todo` (re-queue/park).

1. `git mv` the task file between folders. If moving `todo -> progress` and only a board row exists, author a file from `TASK_TEMPLATE.md` first (progress requires a file).
2. Set `status:` to match the new folder; set `owner` (on pick-up); set `updated` to today.
3. On `-> closed`: add a one-line outcome + the closing version/commit in `## Notes`.
4. Update `board.md` in the SAME change: move the row between tables, refresh counts and current focus. Refresh `PROGRESS.md`'s five fields too. On `-> closed` with the `progress` count now `0` AND no `.claude/skills/task-spec/` in this repo, add ONE line: `NEXT: run /brewtools:task-board-setup upgrade <repo path>` (it retrofits the spec + design layer). Spec layer already present -> say nothing.
5. On `todo -> progress`: set `spec:` by the ADD-flow heuristic if missing or stale. `spec: pending` -> report `NEXT: run /task-spec <ID> (spec required: <reason>)`; the move still happens. The same value goes in the board row's `spec` cell.
6. On `-> closed`: enforce gate G2 BEFORE moving. Read BOTH docs -- `specs/<ID>-spec.md` `## Open questions` (ids `Q1..Qn`) and `specs/<ID>-design.md` `## Open architectural questions` (ids `AQ1..AQn`); a missing doc contributes nothing, it never waives the gate. If ANY row in EITHER table is `blocking: yes`, REFUSE the move and report the blocking ids (e.g. `Q1, AQ3`) instead. Move anyway ONLY if `## Notes` carries `SPEC WAIVER: <reason>`. Non-blocking questions warn, never block. Independently of G2: flip each `in` scope id's `status` to `in-progress` / `done` as that part lands, and on `-> closed` report LOUDLY every `in` id not `done` (list the ids) -- G2 is the only thing that refuses a close, status never is, and there is no waiver marker for it. G5, on the SAME two reads G2 just did: spec FM `status:` still `draft`, or an `in` id just marked `done` that is `uncovered`/`partial` in `## Scope coverage` -> report `SPEC STALE: <ID> ...` + ONE `NEXT: run /task-spec <ID> refresh`. Report-only -- !=write either doc, !=touch `## Scope coverage`, !=block the close.

### 4. BACKLOG dump

Drop an unclear/raw item into `.claude/features/backlog/<slug>.md` -- raw idea, pasted log, "look into X later". No format gate. It is NOT a task yet; it becomes one (or is trashed) during grooming.

### 5. GROOM backlog

1. `Glob` `.claude/features/backlog/*.md` (skip `README.md`).
2. For each item decide its fate: **promote** -> real `todo` task (run flow 2: mint id, create file/row, update board), **merge** -> fold into an existing task's `## Notes`, or **trash** -> delete the file.
3. Delete the backlog file once handled. Never leave a groomed item behind -- after a pass `backlog/` holds only un-triaged items.
4. Refresh the board backlog count.

### 6. SPECS view

1. `Glob` `.claude/features/{todo,progress,closed}/*.md` and `.claude/features/specs/*.md`.
2. One row per task: `id | spec: | spec.md | design.md | blocking Qs | uncovered scope | scope status`.
   - `spec.md` / `design.md`: `yes` | `--`, by existence of `specs/<ID>-spec.md`, `specs/<ID>-design.md`.
   - `blocking Qs`: count of `blocking: yes` rows across BOTH `<ID>-spec.md` `## Open questions` (`Q#`) and `<ID>-design.md` `## Open architectural questions` (`AQ#`).
   - `uncovered scope`: `in` scope ids marked `partial` or `uncovered` in either `## Scope coverage` table. `out` rows never count.
   - `scope status`: every `in` scope id with its `## Scope` `status`, e.g. `S1 done, S2 in-progress, S3 not-started`. `out` rows never count. Orthogonal to coverage -- `covered` !=`done`, `done` !=`covered`.
3. Report DRIFT first, ABOVE the table -- loudly, one line each:

| Drift | Call it |
|-------|---------|
| `spec: pending` + no docs | spec owed -> `NEXT: run /task-spec <ID> (spec required: <reason>)` |
| `spec: full` + a file missing | FM lies -> re-run `/task-spec <ID>` |
| `spec: design-only` + `<ID>-spec.md` exists | FM lies -> fix `spec:` |
| blocking Qs > 0 while in `progress/` | close is gated (G2) -> resolve, or record `SPEC WAIVER:` |
| any `in` scope id `partial` / `uncovered` (`out` rows never count) | G1 unmet -> spec `status:` must stay `draft` |
| an `in` scope id missing from a `## Scope coverage` table | G3: scope changed after the spec -> spec `status: draft` + `/task-spec <ID> refresh` |
| `spec:` blank or absent | !=allowed -> set it per the ADD heuristic |
| an `in` scope id not `done` while in `closed/` (`out` rows never count) | delivery gap -> say which ids; report only, !=a gate, no waiver |

4. This flow VIEWS and GATES only. Authoring or refreshing a spec routes to `/task-spec <ID>` (`design` | `refresh`). !=write spec or design content here.

## Delegation

A big task handed to one agent = an agent gone for an hour: you cannot observe it, cannot correct it, and it usually drifts off-target. One subagent = ONE bounded unit — ONE board pass (one groom run, or one status folder's transitions), ~<=5 files, ~<=10 steps. Bigger MUST be split into N tasks, all spawned in ONE message.

Every spawn prompt MUST carry:

| Field | Content |
|-------|---------|
| GOAL | the overall task and why it exists — the point beyond the file edit |
| ROLE | what this agent owns; what it must NOT touch |
| SCOPE | exact paths/commands in bounds + explicit out-of-bounds |
| CONTEXT | what is already done, by whom, what runs in parallel — trimmed to what THIS agent needs |
| CONSUMER | who or what uses the result next, and the shape it must fit |
| DONE | acceptance criteria + the exact report shape you want back |

A bare one-line task is never enough. Simple single-task view/add/move: do it directly here. For non-trivial passes (bulk transitions, large groom, migrating many rows) delegate to the `task-tracker` agent rather than hand-editing:

```
Task(subagent_type="task-tracker", prompt="
GOAL: keep .claude/features/ truthful — a lagging board.md is a wrong board, and every reader
  (this skill's VIEW flow, any status report) trusts it over the files.
ROLE: you own this groom pass. Promote / merge / trash each backlog item, then sync board.md.
  Do NOT touch source dirs, do NOT invent tasks no backlog item supports, do NOT rename existing ids.
SCOPE: in — .claude/features/backlog/*.md (skip README.md), the task files you promote into todo/,
  and board.md. Out — progress/, closed/, specs/, source code, CLAUDE.md.
CONTEXT: the authoritative procedure is .claude/features/TRACKER.md and the id convention is in
  TASK_TEMPLATE.md — read both first, do not reinvent them. Ids are UPPER-KEBAB and never change.
  Nothing else is editing the board right now; the current counts in board.md are the pre-groom ones.
CONSUMER: the VIEW flow reads board.md counts + tables next, and folder == status: a file whose
  status: frontmatter disagrees with its folder, or a task missing from board.md, is invisible.
DONE: every backlog file handled (promoted / merged / trashed — none left behind); board.md tables,
  counts and backlog count refreshed in the SAME change. Report a table: promoted ids | merged-into
  ids | trashed slugs, plus the new counts.
")
```

## Guards

| Condition | Response |
|-----------|----------|
| an argument that matches no mode keyword and names no task/row/folder is still read as a mode | defect -- it is prose: extract the target from it or ask, never guess |
| the `PLAN` block is missing, or is printed after the first file write | defect -- print it once, right after mode resolution, before entering the flow |

## References

- Procedure (authoritative): `.claude/features/TRACKER.md`
- Dashboard: `.claude/features/board.md`
- Template: `.claude/features/TASK_TEMPLATE.md`
- Rules: `.claude/rules/tasks.md`
- Control-file index: `.claude/features/INDEX.md`
- Spec + design authoring: `.claude/skills/task-spec/SKILL.md` (`/task-spec <ID> [full|design|refresh]`)
- Deprecated old location (pointer only): `.claude/tasks/DEPRECATED.md`
