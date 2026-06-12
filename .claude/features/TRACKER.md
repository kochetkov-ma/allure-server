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
  TRACKER.md          <- this procedure
  TASK_TEMPLATE.md    <- copy this to create a new task file
  backlog/            <- INBOX: ungroomed junk/ideas/dumps; not yet real tasks
    README.md
  todo/               <- accepted tasks, queued, not started (file optional here)
  progress/           <- WIP; a task file is MANDATORY here
  closed/             <- done/shipped (file optional; keep notable ones)
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
| progress -> closed | ship: MOVE the file into `closed/`, set `status: closed`, add a one-line outcome + the version/commit that closed it. |
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
---

## Context
Why this exists, what problem it solves.

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
4. Do the work. Keep `## Notes` in the task file current.
5. On done: move to `closed/`, record closing version/commit, update board counts + focus.
6. If new work surfaces mid-task, drop it in `backlog/` (do not derail).

## 9. Relationship to other surfaces

| Surface | Role now |
|---------|----------|
| `board.md` | canonical task list + status (THIS system) |
| `.claude/tasks/` | DEPRECATED -> thin pointer to the board (was plugin state/logs only) |
| `.claude/reports/` | generated analysis/report artifacts (unchanged; link from task `links:`) |
| `.claude/convention/` | architecture/pattern docs (link from task `links:`, do not duplicate) |

## 10. Ownership

- Skill `.claude/skills/task-board/` -- on-demand entry point to view/update the board and run a groom pass.
- Everyone else: when you start/finish/park a task, follow §3 + §8 and keep the board in sync.
