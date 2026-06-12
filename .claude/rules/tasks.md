---
paths:
  - ".claude/features/**"
---

[DICT: GROOM=backlog triage]

# Task tracker rules

Full procedure: `.claude/features/TRACKER.md`. Board (canonical list): `.claude/features/board.md`.

| # | Rule |
|---|------|
| 1 | `board.md` = canonical task LIST + status. Update in SAME change as any transition -- lagging board = wrong board |
| 2 | Folder == status. Task file lives in `backlog/`\|`todo/`\|`progress/`\|`closed/`; `status:` frontmatter MUST match folder. On move, change both |
| 3 | Lifecycle: `backlog -> todo -> progress -> closed` (or trashed/merged from backlog). Task in `progress/` MUST have a file (from `TASK_TEMPLATE.md`) |
| 4 | New task file = copy `TASK_TEMPLATE.md`. Frontmatter req: `id, title, status, priority, owner, created, updated`. IDs = UPPER-KEBAB (`T-*` feature, `BUG-*` defect, `M-*` maintenance, `EPIC-*` umbrella), never change |
| 5 | `backlog/` = ungated inbox. GROOM periodically: promote to `todo`, merge, or trash. !=leave groomed items behind |
| 6 | `.claude/tasks/` DEPRECATED (pointer only) -- never write tasks there |
| 7 | English only. Closing: record closing version/commit in `## Notes` |
| 8 | For non-trivial board work (GROOM pass, bulk transitions) invoke the `task-board` dashboard skill |
| 9 | After closing tasks, COMMIT + PUSH the `.claude/features/**` change -- closure !=done until pushed |
