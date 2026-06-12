---
name: task-board
description: "Views and updates the allure-server file-based task board at .claude/features/. Triggers: show the board, task board, board status, what's in progress, add a task, create task, move task to progress, close task, dump to backlog, groom backlog."
argument-hint: "[view | add | move | backlog | groom]"
allowed-tools: Read, Write, Edit, Bash, Glob, Grep, Task
---

# Task Board (dashboard)

On-demand entry point for the allure-server file-based Kanban under `.claude/features/`.
Authoritative procedure: `.claude/features/TRACKER.md`. This skill mirrors it -- do not invent rules.

## Invariants (always hold)

- **Folder == status.** A task file lives in `todo/` | `progress/` | `closed/` (or `backlog/`); its `status:` frontmatter MUST equal the folder. On a move, change both.
- **Board is canonical and never lags.** Edit `board.md` in the SAME change as any transition. A lagging board is a wrong board.
- **Ids never change.** UPPER-KEBAB, short, stable -- the filename stem and the board key.
- **A task in `progress/` MUST have a file** (from `TASK_TEMPLATE.md`). In `todo/`/`backlog/` a file is optional (a board row alone is enough).
- **English only.** Closing records the closing version/commit in `## Notes`.

Layout: `board.md` (dashboard), `TRACKER.md` (procedure), `TASK_TEMPLATE.md`, `backlog/` (ungated inbox), `todo/`, `progress/`, `closed/`.

## Flows

### 1. VIEW

1. Read `.claude/features/board.md`.
2. Summarize: overall status (release line), counts (backlog | todo | progress | closed), current focus (1-3 lines), then the Progress (WIP) and Todo tables. Do not enumerate backlog noise.

### 2. ADD task

1. Mint an UPPER-KEBAB id by prefix: `T-*` feature, `BUG-*` defect, `M-*` maintenance, `EPIC-*` umbrella.
2. Copy `TASK_TEMPLATE.md` into the target folder (usually `todo/`) as `<ID>.md`.
3. Fill frontmatter: `id`, `title`, `status` (== folder), `priority` (P1/P2/P3), `owner` (empty in todo/backlog), `created`, `updated` (today), `tags`, `links`.
4. Add a row to `board.md` in the matching table (`id | title | priority | owner | file`). `file` links the file or `--` if table-only.

### 3. MOVE / TRANSITION

`todo -> progress` (pick up) | `progress -> closed` (ship) | `progress -> todo` (re-queue/park).

1. `git mv` the task file between folders. If moving `todo -> progress` and only a board row exists, author a file from `TASK_TEMPLATE.md` first (progress requires a file).
2. Set `status:` to match the new folder; set `owner` (on pick-up); set `updated` to today.
3. On `-> closed`: add a one-line outcome + the closing version/commit in `## Notes`.
4. Update `board.md` in the SAME change: move the row between tables, refresh counts and current focus.

### 4. BACKLOG dump

Drop an unclear/raw item into `.claude/features/backlog/<slug>.md` -- raw idea, pasted log, "look into X later". No format gate. It is NOT a task yet; it becomes one (or is trashed) during grooming.

### 5. GROOM backlog

1. `Glob` `.claude/features/backlog/*.md` (skip `README.md`).
2. For each item decide its fate: **promote** -> real `todo` task (run flow 2: mint id, create file/row, update board), **merge** -> fold into an existing task's `## Notes`, or **trash** -> delete the file.
3. Delete the backlog file once handled. Never leave a groomed item behind -- after a pass `backlog/` holds only un-triaged items.
4. Refresh the board backlog count.

## References

- Procedure (authoritative): `.claude/features/TRACKER.md`
- Dashboard: `.claude/features/board.md`
- Template: `.claude/features/TASK_TEMPLATE.md`
- Rules: `.claude/rules/tasks.md`
- Deprecated old location (pointer only): `.claude/tasks/DEPRECATED.md`
