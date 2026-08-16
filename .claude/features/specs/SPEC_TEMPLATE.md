---
task: T-UI-REPLACE-ME
kind: spec
status: draft            # draft | agreed | superseded
open_questions: 0        # count of UNRESOLVED rows in this doc's "Open questions", blocking or not
created: 2026-08-13
updated: 2026-08-13
---

# Spec -- T-UI-REPLACE-ME

> Product spec: HOW we solve it. The task file owns WHAT + WHY. Architecture lives in
> `T-UI-REPLACE-ME-design.md`. Procedure: [`../TRACKER.md`](../TRACKER.md).

## Summary

*3-5 lines: the ask, the chosen approach, the one thing that could go wrong.*

EXAMPLE: allure-server needs per-user rate limiting on the public API. We add a token-bucket
check in the existing request middleware, backed by the cache layer already in the stack.
Limits are config-driven, not hard-coded. Main risk: the cache layer is single-node today,
so a limit is per-node until it is clustered.

## Original requirements

*The ask, VERBATIM from the task file / the user's own words. !=paraphrase, !=summarise, !=fix. It exists so nobody re-litigates what was asked.*

EXAMPLE: "Rate-limit the public API per user. Limits config-driven. Response shapes must not change."

## User Q&A

*Clarifications asked BEFORE research (`/task-spec` P0.7), verbatim -- and ONLY those. Open questions answered while authoring (P5) belong to `## Resolved questions`, !=here. A `--noask` run records exactly one row: `Skipped (--noask mode)`.*

| asked | answer |
|-------|--------|
| EXAMPLE: authenticated routes only? | No -- all public routes; admin routes exempt. |
| EXAMPLE: may we add response headers? | Yes, `X-RateLimit-*`; the body must not change. |

## Decisions

*One block per decision, ids `D1..Dn`. Each carries decision / rationale / alternatives rejected. Once minted an id is never reused or renumbered.*

### D1 -- EXAMPLE: token bucket, not fixed window

- **Decision:** token bucket, 60 tokens, refill 1/s, per `user_id`.
- **Rationale:** absorbs short bursts that fixed windows reject at the boundary; refill math is one subtraction.
- **Alternatives rejected:** fixed window (boundary bursts, 2x limit at the seam); leaky queue (needs a scheduler we do not have).

## Resolved questions

*Questions that WERE open and are now settled -- every `Q#` answered in `/task-spec` P5 lands HERE, not in `## User Q&A`. question -> answer -> what settled it (agent, file, person, experiment). Keep them: they stop a later reader from re-litigating.*

| question | answer | settled by |
|----------|--------|------------|
| EXAMPLE: do we limit anonymous traffic too? | Yes, by client IP, at 1/6 of the user limit. | product owner, task `## Notes` 2026-02-11 |

## Open questions

*Ids `Q1..Qn`, never renumbered; the design doc's questions are `AQ#` and are counted separately. `blocking: yes` BLOCKS progress -> closed (gate G2, which scans BOTH documents); override only with a `SPEC WAIVER: <reason>` line in the task's `## Notes`.*

| id | question | blocking | owner |
|----|----------|----------|-------|
| Q1 | EXAMPLE: what response code on limit hit -- 429 or 503? | yes | product owner |
| Q2 | EXAMPLE: do we expose remaining quota in a response header? | no | api domain agent |

## Scope coverage

*One row per scope id from the task's `## Scope` table, `in` and `out` alike. `covered by` cites a decision id (`D1`) or a section of this doc. Gate G1 reads `in` ids ONLY: any `in` id not `covered` keeps `status: draft`; an `out` row's status never affects G1.*

| scope id | block | covered by | status |
|----------|-------|------------|--------|
| S1 | EXAMPLE: per-user limit on public API routes | D1 | covered |
| S2 | EXAMPLE: anonymous traffic limited by IP | D1, Resolved questions | covered |
| S3 | EXAMPLE: admin routes exempt | -- | uncovered |
| S4 | EXAMPLE: cross-node shared counters (out of scope) | Out of scope | covered |

Status values: `covered` | `partial` | `uncovered`. Nothing else. This is the SPEC-coverage axis, !=the task's execution `status` (`not-started` | `in-progress` | `done`) -- orthogonal, !=add an execution column here, !=merge the two.

## Out of scope

*What this spec deliberately does NOT answer, so the next reader stops looking. One line each, mirror the task's `out` scope rows.*

- EXAMPLE: cross-node shared counters -- deferred until the cache layer is clustered.

## Evidence

*What was actually read or consulted. Files with paths, agents by name, external sources by URL. "I assumed" is !=evidence.*

| source | kind | what it gave |
|--------|------|--------------|
| EXAMPLE: `src/api/middleware.ts` | file | existing middleware chain; the insertion point |
| EXAMPLE: `UI-expert` | agent | confirmed the cache layer is single-node today |
