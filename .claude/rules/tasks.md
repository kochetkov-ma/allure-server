---
paths:
  - ".claude/features/**"
doc_type: llm
version: "5.6.0"
content_version: "5.6.0"
generated_by: "brewtools:task-board-setup"
last_updated: "2026-08-13"
---

[DICT: GROOM=backlog triage, FM=frontmatter, TT=task-tracker agent]

# Task tracker rules

Full procedure: `.claude/features/TRACKER.md`. Board (canonical list): `.claude/features/board.md`.

| # | Rule |
|---|------|
| 1 | `board.md` = canonical task LIST + status. Update in SAME change as any transition -- lagging board = wrong board |
| 2 | Folder == status. Task file lives in `backlog/`\|`todo/`\|`progress/`\|`closed/`; `status:` frontmatter MUST match folder. On move, change both |
| 3 | Lifecycle: `backlog -> todo -> progress -> closed` (or trashed/merged from backlog). Task in `progress/` MUST have a file (from `TASK_TEMPLATE.md`) |
| 4 | New task file = copy `TASK_TEMPLATE.md`. Frontmatter req: `id, title, status, priority, owner, created, updated, spec`. IDs = UPPER-KEBAB (`T-*` feature, `BUG-*` defect, `M-*` maintenance, `EPIC-*` umbrella), never change |
| 5 | `backlog/` = ungated inbox. GROOM periodically: promote to `todo`, merge, or trash. !=leave groomed items behind |
| 6 | `.claude/tasks/` DEPRECATED (pointer only) -- never write tasks there |
| 7 | English only. Closing: record closing version/commit in `## Notes` |
| 8 | For non-trivial board work (GROOM pass, bulk transitions) invoke the `task-board` dashboard skill |
| 9 | After closing tasks, COMMIT + PUSH the `.claude/features/**` change -- closure !=done until pushed |
| 10 | First kebab segment after the prefix = a repo domain { UI, API, DTO, REPORT, RESULT, GEN, PLUGIN, TMS, SEC, CFG, DB, BUILD }. e.g. `T-UI-SLUG`, `BUG-API-SLUG`, `M-BUILD-SLUG` |
| 11 | Three docs per non-trivial task: task file (WHAT/WHY + `## Scope` ids `S1..Sn`), `.claude/features/specs/<ID>-spec.md` (HOW: decisions, open questions, scope coverage), `.claude/features/specs/<ID>-design.md` (architecture). FLAT names; !=`specs/<ID>/spec.md`. Detail: TRACKER.md section 10 |
| 12 | `spec:` = REQ FM, ALWAYS written, never blank: `none` (deliberate, small task) \| `pending` (owed, not written yet) \| `full` (both docs exist + listed in `links:`) \| `design-only` (design doc only) |
| 13 | Needs a spec if ANY: >1 domain \| >~5 files \| new integration/dependency \| schema/API/contract change \| ambiguous requirements or open questions \| user asked for a design/spec. Else `spec: none` |
| 14 | G1 coverage: every `in` scope id must be `covered` in BOTH `## Scope coverage` tables. Any `in` id `partial` or `uncovered` -> spec `status:` stays `draft`, never `agreed`. `out` rows never affect G1 |
| 15 | G2 close gate: `progress -> closed` BLOCKED while any open question is `blocking: yes` in EITHER `<ID>-spec.md` `## Open questions` (`Q1..Qn`) or `<ID>-design.md` `## Open architectural questions` (`AQ1..AQn`) -- a missing doc never waives it; report the blocking `Q#`/`AQ#` ids instead of moving. Only escape = an explicit `SPEC WAIVER: <reason>` line in the task's `## Notes`. Non-blocking questions warn only |
| 16 | G3 sync: editing a task's `## Scope` invalidates BOTH docs -> set spec `status: draft` and run `/task-spec <ID> refresh`. Scope ids, once minted, are never renumbered. Editing ONLY a `status` cell is !=a scope change -- it never trips G3 |
| 17 | G4 no solo design: the design doc is NEVER authored by one generalist agent -- `/task-spec` fans out to this repo's `.claude/agents/` domain architects (TRACKER.md section 10) |
| 18 | Task needs a spec and has none -> route to `/task-spec <ID>` (`design` \| `refresh` modes). TT cannot call a skill for the main session, so it ends its report with exactly: `NEXT: run /task-spec <ID> (spec required: <reason>)` |
| 19 | `## Scope` = `id \| block \| in/out \| status`. `status` = EXECUTION axis of that one id, enum exactly `not-started` \| `in-progress` \| `done` (`out` row -> `--`), `S#` only (`D#`/`Q#`/`AQ#` carry none). Written by TT + the `task-board` ADD/MOVE flows; both spec docs only READ it by id. No gate: an `in` id not `done` at a transition is reported LOUDLY, never refuses it, no waiver. Orthogonal to coverage: `covered` !=`done`, `done` !=`covered`. !=replace `## Acceptance` -- keep both |
| 20 | G5 staleness -- REPORT-ONLY, fired at close on the two docs G2 already opened (zero extra reads): spec FM `status:` still `draft`, or an `in` id just marked `done` that is `uncovered`/`partial` in `## Scope coverage` -> `SPEC STALE: <ID> ...` + ONE `NEXT: run /task-spec <ID> refresh`. Never blocks the close (G2 alone refuses), never writes a spec doc, never renumbers an id |

## Session progress (`.claude/features/PROGRESS.md`)

`board.md` owns the task LIST + status. `PROGRESS.md` owns what THIS SESSION did about it -- !=a second board (no task table), !=per-task detail (that is the task's `## Notes`). Five fields, overwritten in place, never appended: `Updated`, `In flight`, `Moved since last update`, `Blocked`, `Next`.

| # | Rule |
|---|------|
| P1 | It ALWAYS exists -- created at board init. Missing -> recreate it from `board.md` before anything else |
| P2 | The MAIN SESSION keeps it current: refresh it in the SAME change as any transition, and before ending any turn that moved work. Stale `PROGRESS.md` = the session cannot say where it is |
| P3 | **Plan mode:** a plan that touches any task MUST carry an explicit final step `update .claude/features/PROGRESS.md`. A plan without it is incomplete -- write the step into the plan, do not rely on remembering |
| P4 | `task-tracker` WATCHES it: every run it rewrites the five fields from `board.md` + the task files and reports staleness in one line. It cannot run a skill for you -- act on its `NEXT:` line yourself |
