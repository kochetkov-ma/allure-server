---
doc_type: llm
version: "5.6.0"
content_version: "5.6.0"
generated_by: "brewtools:task-board-setup"
last_updated: "2026-08-13"
---

# Features -- control-file index

> `board.md` is the **canonical** task list + status. This index just maps the control
> surfaces; it never duplicates the board.

## Control files

| File | Role |
|------|------|
| [`board.md`](board.md) | Canonical task LIST + status (dashboard: overall status, progress/todo/backlog/closed/specs tables). Every task = a board row. |
| [`PROGRESS.md`](PROGRESS.md) | SESSION progress against the board: in flight / moved / blocked / next. Five fields, overwritten in place. !=a second board -- rules in `.claude/rules/tasks.md`. |
| [`TRACKER.md`](TRACKER.md) | The procedure: layout, lifecycle state machine, task-file format, id convention, grooming loop. |
| [`TASK_TEMPLATE.md`](TASK_TEMPLATE.md) | Copy this to create a new task file. |
| [`INDEX.md`](INDEX.md) | This file. |
| [`specs/SPEC_TEMPLATE.md`](specs/SPEC_TEMPLATE.md) | Copy to create `specs/<ID>-spec.md` -- product spec: decisions, resolved + open questions, scope coverage. |
| [`specs/DESIGN_TEMPLATE.md`](specs/DESIGN_TEMPLATE.md) | Copy to create `specs/<ID>-design.md` -- architecture, data flow, interfaces, reliability, complexity budget. |

## Folders (folder name == task `status:`)

| Folder | Holds |
|--------|-------|
| [`backlog/`](backlog/) | Ungroomed inbox -- raw ideas/dumps; groomed into `todo/` or trashed. |
| [`todo/`](todo/) | Accepted, queued, not started. |
| [`progress/`](progress/) | WIP -- a task file is MANDATORY here. |
| [`closed/`](closed/) | Done / shipped. |
| [`specs/`](specs/) | Per-task implementation/design specs, linked from a task's `links:`. Not a status folder. |
