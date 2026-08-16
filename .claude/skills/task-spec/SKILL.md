---
name: task-spec
description: "Authors the product spec and the system-design doc for a task on this repo's board, fanning out to the repo's own domain architect agents -- never designed solo. Writes .claude/features/specs/<ID>-spec.md and <ID>-design.md, then syncs the task frontmatter and board.md. Triggers: system design, architect this, design doc, design document, write the spec, spec out, spec this task, plan this task, architecture for, technical design, design review, needs a spec, системный дизайн, спека, спеку, напиши спеку, архитектура задачи, спланируй задачу, продумай архитектуру, распиши решение, дизайн-документ, с помощью архитектора, привлеки архитекторов. <example> user: продумай архитектуру для T-UI-SLUG <commentary>Plain prose, no skill named, but this is a design request for a board task -- run task-spec in design mode and fan out to the domain architects.</commentary> </example> <example> user: this one touches the API and the storage layer, write the spec before anyone codes <commentary>Multi-domain + explicit spec ask = the needs-spec heuristic; run task-spec full mode, one architect per touched domain.</commentary> </example> <example> user: scope changed on BUG-UI-SLUG, the spec is stale now <commentary>Existing docs plus a changed task Scope -> task-spec refresh, preserving D#, Q# and AQ# ids and the Scope status cells.</commentary> </example>"
argument-hint: "[prompt] [<TASK_ID>] [full|design|refresh] [-n|--noask]"
allowed-tools: Read, Write, Edit, Bash, Glob, Grep, Agent, AskUserQuestion
model: opus
doc_type: llm
version: "5.6.0"
content_version: "5.6.0"
generated_by: "brewtools:task-board-setup"
last_updated: "2026-08-13"
---

# task-spec (spec + system design)

Authors the two spec documents for one task of the file-based Kanban in `.claude/features/`.
Authoritative procedure: `.claude/features/TRACKER.md` section 10. This skill mirrors it -- !=invent rules.

Three-document model: the task file owns WHAT + WHY (context, links, `## Scope` with `S#` ids);
`specs/<ID>-spec.md` owns HOW (decisions, questions, coverage); `specs/<ID>-design.md` owns the
system design (components, data flow, contracts, reliability, complexity budget).

## Prompt contract

Position 1 of `$ARGUMENTS` is a **free-form prompt** (RU/EN) -- `<TASK_ID>`, mode and `-n`/`--noask`
are optional and may follow in any order. Nobody types keys: resolve the task id and the mode FROM
the prompt (P0.1 below).

| Mode | EN keywords | RU keywords | Mutates? |
|------|-------------|-------------|----------|
| `full` (default) | write the spec, spec out, spec this task, plan this task, needs a spec | спека, спеку, напиши спеку, спланируй задачу | yes |
| `design` | system design, architect this, design doc, design document, architecture for, technical design, design review, with the architects | системный дизайн, архитектура задачи, продумай архитектуру, дизайн-документ, с помощью архитектора, привлеки архитекторов | yes |
| `refresh` | stale, scope changed, out of date, re-sync the spec | устарела, скоуп поменялся, обнови спеку | yes |

1. Strip `-n`/`--noask` first -- a flag, never a mode or an id. An explicit mode token anywhere in
   the remaining arguments wins outright, no scoring.
2. Else score modes by distinct whole-word keyword hits (table above); highest unique score wins. A
   tie between two of these mutating modes falls to the keyword appearing first in the prompt --
   none of the three is read-only, so a tie never needs `AskUserQuestion` by itself.
3. All zero, or empty arguments -> `full` (the documented default).
4. Prose that does not parse as a mode is still input, never an error: P0.1 extracts `<TASK_ID>`
   from it by id pattern or title/slug match -- the first word of a sentence is never guessed as
   the id.
5. Outcome-changing ambiguity (P0.4 missing `## Scope`, P0.7 architecture questions) still gets ONE
   `AskUserQuestion` before work starts, per the existing rules in P0 and P5/P6-C below. `-n`/
   `--noask` suppresses those three asks only; P0.1's several-candidates STOP and P0.4's no-`## Scope`
   STOP are ground truth and are never suppressed.

Then print this block once, after P0 resolves the id, the mode and the touched domains (and any
P0.7 clarify), before P1 starts:

```
PLAN — task-spec
INPUT:  <arguments verbatim, or "(empty)">
MODE:   <resolved> -- <explicit | matched keyword: X | default>
SCOPE:  <ID> -- touched domains <d1, d2, ...>; architects: <domain -> agent, ...>
DO:     <2-5 imperative bullets>
RESULT: specs/<ID>-spec.md, specs/<ID>-design.md (per mode)
```

Labels are literal ASCII; values follow English.

## Invariants

- **Writes ONLY `.claude/features/**`.** Never app code. EXCLUSIONS (never written, never edited): src/, build/, gradle/, config/, allure/, .github/.
- **Flat filenames.** `specs/<ID>-spec.md`, `specs/<ID>-design.md`. !=`specs/<ID>/spec.md`.
- **Ids are permanent.** Scope `S#`, decisions `D#`, spec questions `Q#`, design questions `AQ#` -- once minted, never renumbered.
- **`status` is the tracker's column, never ours.** The task's `## Scope` `status` cell (`not-started` | `in-progress` | `done`; `--` on an `out` row) is written by `task-tracker` and `/task-board`. This skill READS it by id and !=write, !=normalize, !=clear it; the ONE exception is a `## Scope` table this skill itself mints (P0.4 -> P7.2), whose rows are written with initial values. Orthogonal to `## Scope coverage`: `covered` never implies `done`, `done` never implies `covered`.
- **Docs + task FM + board move together.** One change, or the board is wrong.
- **A spec is a plan, !=a code dump.** Document-level, both docs, every phase: NO large code blocks -- cite `file:line`. Research snippets serve understanding, they !=belong in the doc. Applies to every spawn's output too.
- **English only** in doc prose and headings.

## Modes

| Mode | Does |
|------|------|
| `full` (default) | design doc + spec doc, both written; `spec: full` |
| `design` | only `<ID>-design.md`; `spec: design-only` |
| `refresh` | re-sync both against the task's current `## Scope`; preserve every `D#`, `Q#` and `AQ#` id, and every `## Scope` `status` cell verbatim |

`-n` / `--noask` (any position, stripped before argument detection) is a FLAG, !=a mode: combines with
all three, suppresses EXACTLY three asks -- P0.7 clarify, P5 open questions, P6-C escalation -- and
those steps record the literal `Skipped (--noask mode)`; blocking questions stay `blocking: yes` and
still block G2. The two GROUND-TRUTH asks are NOT suppressed and !=guessed around: P0.1 (several
candidate ids) and P0.4 (no `## Scope`) STOP and report what is missing. There is no
fourth "simple spec" mode -- `design` (architecture only) and a single-domain `full` (one architect,
one spec) ARE the light paths; weight comes from the touched-domain count, !=from a mode.

## Domain agents in allure-server

| agent | domains covered | specialty |
|-------|-----------------|-----------|
| `rest-controller` | API | REST endpoints, validation, caching, `@ExceptionHandler` |
| `dto-model` | DTO | REST DTOs and value records under `model/`, bean validation, `@Schema` |
| `report-service` | REPORT | report lifecycle: `JpaReportService`, `ReportEntity`, cleanup scheduler |
| `result-service` | RESULT | upload intake: `ResultService`, ZIP extraction, UUID paths, filesystem moves |
| `generation-pipeline` | GEN, PLUGIN | Allure core wrapper `AllureReportGenerator` + `AllureServerPlugin` SPI |
| `plugin-youtrack` | TMS, PLUGIN | YouTrack TMS: `YouTrackPlugin`, Feign client, OpenAPI codegen |
| `config-security` | SEC, CFG | `@ConfigurationProperties`, `@Configuration`, `SecurityFilterChain`, OAuth2/DB auth |
| `persistence-jpa` | DB | JPA entities, repositories, `migration.sql`, datasource/dialect |
| `build-ci-qa` | BUILD | `build.gradle`, GitHub Actions, Dockerfile, release, test infra |
| `web-ui` | UI | JTE templates, HTMX, Alpine.js, Tailwind, `web/` controllers |
| `intent-guard` | -- | review-phase anti-drift check; explicit invocation only, no domain of its own |

Fallback chain, applied PER DOMAIN, in this order:

| # | Pick |
|---|------|
| 1 | team agent -- if `.claude/teams/` exists, read `team.md` and match its roster by domain |
| 2 | the project domain agent covering that domain (table above) |
| 3 | `Plan` -- no architecture-capable agent exists in `.claude/agents/`, so this rung collapses into row 4 |
| 4 | built-in `Plan` (system fallback) |

Whichever was used MUST be named per domain in the design's `## Evidence`. A touched domain with no
domain agent MUST be stated explicitly there -- silence is a defect. An agent that REFUSES the task
-> re-delegate to the colleague it names (max 2 retries), then drop a link; record it in `## Evidence`.

## Spawn brief (every P1/P2/P6 spawn)

One subagent = ONE bounded unit: ONE domain, ~<=5 files read, ~<=10 steps. Bigger -> split into N
spawns. All spawns of a phase go in ONE message. A bare one-line prompt is never enough.

| Field | Content |
|-------|---------|
| GOAL | the task being specced and why it exists -- the point beyond the doc |
| ROLE | the domain this agent owns; what it must NOT touch |
| SCOPE | exact paths/domains in bounds + explicit out-of-bounds |
| CONTEXT | task Scope ids, decisions already fixed, what runs in parallel -- trimmed to this agent |
| CONSUMER | who reads the output next and the shape it must fit |
| DONE | acceptance + the exact report shape |

## Phases

### P0 -- resolve the task, read the ground truth

1. Strip `-n` / `--noask` first -- a flag, never a mode or an id. Resolve MODE per `## Prompt
   contract` above (explicit token wins; else keyword score; else `full`). Resolve `<TASK_ID>`: an
   argument matching the id pattern (`[A-Z]+(-[A-Z]+)*-[A-Za-z0-9]+`, e.g. `T-HTML-SLUG`,
   `BUG-KV-42`) IS the id, taken literally. Anything else is PROSE, not a positional id: extract an
   id-shaped token from inside the sentence, else match a quoted or bare title/slug fragment against
   the `id`/`title` frontmatter of every `.claude/features/{backlog,todo,progress,closed}/*.md`
   (exact match on id, case-insensitive substring on title). No id recoverable (including empty
   arguments): `Glob` `.claude/features/progress/*.md` then `todo/*.md`; one WIP task -> use it;
   several -> `AskUserQuestion` with the candidate ids; `--noask` -> this ask is NOT skipped: STOP
   and report the candidates instead. Never guess -- treating the first word of a sentence as
   `<TASK_ID>` is a defect.
2. Locate the file: `Glob` `.claude/features/{backlog,todo,progress,closed}/<ID>.md`. Not found -> STOP, report "task <ID> not found".
3. Read, in this order: the task file, `.claude/features/TRACKER.md` (section 10), `specs/SPEC_TEMPLATE.md`, `specs/DESIGN_TEMPLATE.md`, and any existing `specs/<ID>-spec.md` / `specs/<ID>-design.md`.
4. Extract `## Scope` rows. No `## Scope` section -> derive `S1..Sn` from the task body, show the proposed table via `AskUserQuestion`, and write it into the task file in P7. Never proceed on an unstated scope -- under `--noask` this ask is NOT skipped either: STOP and report the derived `S#` rows for confirmation.
5. Derive TOUCHED DOMAINS: intersect the task text + scope blocks with UI, API, DTO, REPORT, RESULT, GEN, PLUGIN, TMS, SEC, CFG, DB, BUILD. Record the list; it drives P1, P2 and P6 fan-out width.
6. `refresh` mode: harvest existing `D#`, `Q#` and `AQ#` ids and their text, plus the `## Scope` `status` cell of every id. They are inputs, not drafts to overwrite.
7. **Clarify BEFORE research** (`--noask`: skip, record `Skipped (--noask mode)`, infer from the task file + code). Ambiguities that would change the architecture are asked NOW, not after both docs are drafted: `AskUserQuestion`, 3-5 questions, max 4 per call.

   | # | Category | Ask about |
   |---|----------|-----------|
   | 1 | Scope | what is in/out, which modules are affected |
   | 2 | Constraints | required libraries, backward compat, API contracts |
   | 3 | Edge cases | concurrent access, empty/null inputs, error recovery |

   The answers are SETTLED input for P1/P2 and are recorded verbatim into the spec's `## Original requirements` + `## User Q&A` (P4). !=re-open them later.
8. **Size advisory, report only.** >3 touched domains OR >12 `in` scope ids -> propose a split IN THE FINAL REPORT and hand it to `task-tracker`. This skill !=split, !=create, !=move tasks -- the board owns that.

### P1 -- parallel domain research (read-only)

One read-only agent per touched domain, ALL in ONE message. Purpose: how the domain works TODAY -- entry points, current contracts, constraints, prior art. Prefer the domain agent from the table; else `Explore`. Output per agent: findings with `file:line` pointers, plus risks. !=edits in this phase.

### P2 -- domain-architect design fan-out (MANDATORY)

Spawn ONE architect per touched domain, ALL in ONE message, using the fallback chain. This phase is not optional and is not replaceable by main-session reasoning (G4). Feed each agent the P1 findings for its domain only.

```
Task(subagent_type="<domain agent | Plan>", prompt="
GOAL: <ID> -- <task title>. We are designing before anyone codes, because the change spans
  <touched domains> and a wrong seam here costs a rewrite later.
ROLE: you own the <domain> slice of the architecture ONLY. Do NOT design other domains, do NOT
  write code, do NOT edit any file -- you return a proposal, this session writes the doc.
SCOPE: in -- <domain> sources and contracts (read-only), the task file
  .claude/features/{progress|todo}/<ID>.md, .claude/features/specs/DESIGN_TEMPLATE.md.
  Out -- other domains, src/, build/, gradle/, config/, allure/, .github/, any write anywhere.
CONTEXT: task Scope ids you must cover: S1 <block>, S3 <block>. Scope and constraints are already
  settled with the user -- do NOT re-open them: <P0.7 answers>. Already fixed: <decisions from an
  existing spec, or 'nothing fixed yet'>. Running in parallel: architects for <other domains> --
  assume their seams exist, name the interface you need from them, do not design their internals.
  Current-state findings for your domain: <P1 digest, file:line>.
CONSUMER: this session merges your proposal into .claude/features/specs/<ID>-design.md. Answer in
  the fixed section headings of DESIGN_TEMPLATE.md so it merges without rewriting.
DONE: components + responsibilities; the interface you expose and the ones you require from sibling
  domains; failure modes and their handling; a COMPLEXITY BUDGET -- what you deliberately do NOT
  build and why (reliable and project-sized beats clever); which Scope ids your slice covers and any
  it cannot; open architectural questions. Those headings, terse, English, file:line evidence, no code.
")
```

Guard: touched domains found but fewer architects spawned than domains -> STOP and fan out properly.

### P3 -- synthesize the design doc

Merge the P2 proposals into `specs/<ID>-design.md` following `DESIGN_TEMPLATE.md` section for section (do not restate them here, do not reorder, do not drop). Merge sources are mechanical, !=improvised (P3 = design rows, P4 = spec rows):

| doc + section | source |
|---------------|--------|
| spec `## Summary` / `## Original requirements` / `## User Q&A` | task title + body (3-5 lines) / the ask VERBATIM from the task file / P0.7 + P5 answers verbatim |
| spec `## Decisions` | P2 proposals the domains agreed on; conflicts resolved per P3.1 |
| spec `## Out of scope` | the task's `out` scope rows |
| design `## Architecture` .. `## Data model` | P2 proposals, one domain slice each |
| design `## Failure modes` / `## Complexity budget` | P2 failure modes + P1 risks / P2 budgets, merged |
| both `## Scope coverage` | task `## Scope` rows, one per id, `in` and `out` alike |
| both `## Evidence` | P1 `file:line` pointers + the agent used per domain |

1. Conflicting proposals between domains: resolve explicitly, record the loser under the relevant decision or as an `AQ#` under `## Open architectural questions`. !=silently pick one.
2. `## Complexity budget` is mandatory and never empty (what is deliberately NOT built, and why -- reliable and project-sized beats clever); `## Evidence` names the agent used PER DOMAIN and flags any domain that had none.
3. FM: `task`, `kind: design`, `status: draft`, `open_questions`, `created`, `updated` (2026-08-13 on first write; `created` preserved on refresh). Each FM field follows the template's own frontmatter comment.

### P4 -- synthesize the spec doc

`design` mode: skip. Otherwise write `specs/<ID>-spec.md` per `SPEC_TEMPLATE.md`.

1. `D#` decisions: decision / rationale / alternatives rejected. Refresh: keep existing ids and their meaning; a reversed decision is marked superseded in place; new ones take the next free number.
2. `Q#` open questions (spec namespace; the design doc's are `AQ#`) with `blocking: yes|no` and an owner. Anything answered moves to `## Resolved questions` with who/what settled it; its `Q#` is retired, never reused.
3. FM: `kind: spec`, `status: draft`, `open_questions`, `created`, `updated` -- same rules as P3.

### P5 -- ask the user about open questions

Blocking questions from BOTH docs -- `Q#` in the spec, `AQ#` in the design -- via `AskUserQuestion`, batched (max 4 per call). Answered `Q#` -> `## Resolved questions`; answered `AQ#` -> row dropped, the answer folded into the design section it settles, id retired and never reused. Decrement that doc's `open_questions`. Unanswered stays with `blocking: yes` and will block close under G2. !=invent an answer, !=downgrade a question to `blocking: no` to clear the gate. `--noask`: skip this phase entirely, record `Skipped (--noask mode)` in `## Resolved questions`, leave every question open and blocking.

### P6 -- two-phase review

**Phase A -- find.** One reviewer per touched domain, ALL in ONE message. Reviewers report findings only; they never edit and nothing is fixed on this pass.

```
Task(subagent_type="<domain agent | Plan>", prompt="
GOAL: <ID> -- <task title>. The design and spec drafts are written; we review before they are
  marked agreed and before implementation starts.
ROLE: adversarial reviewer for the <domain> slice. Find defects; do NOT fix them, do NOT edit any
  file, do NOT re-design what is already sound.
SCOPE: in -- .claude/features/specs/<ID>-design.md, .claude/features/specs/<ID>-spec.md, the task
  file, and <domain> sources read-only. Out -- other domains' internals, src/, build/, gradle/, config/, allure/, .github/, any write.
CONTEXT: your own P2 proposal for this domain was merged with <other domains>; the merge may have
  bent it. Task Scope ids: S1..Sn. Open now: Q1 <text> (spec, blocking yes), AQ1 <text> (design).
CONSUMER: this session re-verifies every finding in a second pass and only then edits the docs.
  A vague finding is unverifiable and will be dropped.
DONE: a table -- id | severity (blocker|major|minor) | doc + section | claim | evidence file:line
  | one-line suggested fix. Explicitly answer: is every in-scope id covered for your domain? is
  anything over-engineered against the Complexity budget? is any interface underspecified or any
  failure mode unhandled? Terse, English, no prose essay.
")
```

**Phase B -- verify.** Re-check every phase-A finding against the docs and the code BEFORE changing anything: confirmed | rejected (with the reason) | duplicate. Do this here in the main session for small sets; fan out a second, ONE-message verification round when a domain returned many findings. Only CONFIRMED findings are applied; blockers must be applied or explicitly converted into a `Q#` (spec) or `AQ#` (design) with `blocking: yes`. Rejected findings are listed in the final report, not in the docs.

**Phase C -- fix loop, bounded.** `WHILE a confirmed blocker or major finding remains: apply the fixes, re-run phase A for the AFFECTED domains only (ONE message), re-verify per phase B. MAX 3 iterations.` `minor` findings never drive an iteration. Survivors after 3 rounds go to the user via `AskUserQuestion`: accept as-is, or convert each into a `Q#` (spec) / `AQ#` (design) with `blocking: yes`. `--noask`: skip the escalation and convert every survivor into a blocking question. Iteration count and survivors are reported.

### P7 -- coverage gate G1, then write and sync (ONE change)

G1 reads `in` scope ids ONLY: every `in` id from the task must appear in BOTH `## Scope coverage` tables with status `covered`. `out` rows may appear in those tables; their status never affects G1. `design` mode checks the design table only.

| Result | Action |
|--------|--------|
| every `in` id `covered` | proceed; `status:` may become `agreed` |
| any `in` id `partial`/`uncovered` | `status:` stays `draft`; list the offending ids in the report |

Then, as ONE change:

1. Write `specs/<ID>-design.md` and (unless `design` mode) `specs/<ID>-spec.md`.
2. Task file FM: add both docs to `links:`; set `spec:` to `full` | `design-only`; bump `updated`. Write back the `## Scope` table if P0 minted it (before `## Acceptance`) -- a minted table's `in` rows start `not-started`, `out` rows `--`. An EXISTING `## Scope` table is left alone; if it is rewritten for any reason, every `status` cell carries through verbatim.
3. `board.md`: refresh the task's row and the Feature specs table in the SAME change. A lagging board is a wrong board.
4. Report -- emit EXACTLY this block. Prose !=a substitute; every row is filled or reads `--`.

```markdown
# Spec ready -- <ID> <title>

| field | value |
|-------|-------|
| mode | `full` / `design` / `refresh`, plus `--noask` when the flag was set |
| touched domains -> agent | api -> `api-expert`; infra -> `Plan` (no domain agent) |
| docs written, `spec:` | `specs/<ID>-spec.md` + `specs/<ID>-design.md`, `spec: full` |
| decisions / open questions | 4 (D1..D4) / spec 1 blocking of 3, design 0 blocking of 2 |
| G1 coverage | PASS -> `status: agreed` -- or FAIL: S3, S5 uncovered -> `status: draft` |
| review | 7 findings, 5 confirmed / 2 rejected, 2 fix iterations, 0 survivors |
| size advisory | none -- or: >3 domains touched, split proposed to `task-tracker` |
| synced in one change | task FM `links:` + `spec:` + `updated`, `board.md` row + Feature specs table |

## Next step

> Clear context (`/clear`), then hand it to the implementing agent:
> `Implement .claude/features/specs/<ID>-spec.md (design: <ID>-design.md). Rules: .claude/rules/tasks.md.`
```

## Guards

| # | Condition | Response |
|---|-----------|----------|
| G1 | any `in` scope id not `covered` in BOTH coverage tables (`out` rows never count) | spec `status:` stays `draft`, never `agreed`; name the ids |
| G2 | `progress -> closed` requested while a `blocking: yes` question is open in EITHER doc -- `Q#` in `<ID>-spec.md` `## Open questions`, `AQ#` in `<ID>-design.md` `## Open architectural questions` | BLOCKED. Only an explicit `SPEC WAIVER: <reason>` line in the task's `## Notes` overrides |
| G3 | the task's `## Scope` changed after the docs were written. Editing ONLY a `status` cell !=a scope change -- it never trips G3 | both docs `status: draft`; run `/task-spec <ID> refresh` |
| G4 | design authored without a per-domain architect fan-out | invalid -- redo P2, one agent per touched domain, ONE message |
| -- | task id not found | STOP, report; !=create a task here (that is `task-tracker`) |
| -- | task has no `## Scope` and the user declines to fix it | STOP -- coverage is unverifiable without scope ids |
| -- | `SPEC_TEMPLATE.md` / `DESIGN_TEMPLATE.md` missing | ERROR: spec templates not found under `.claude/features/specs/`. STOP |
| -- | a write targets anything outside `.claude/features/**` | refuse; src/, build/, gradle/, config/, allure/, .github/ are never written |
| -- | `D#` / `Q#` / `AQ#` / `S#` renumbered on refresh, or a `## Scope` `status` cell changed or dropped | invalid -- ids are permanent and `status` belongs to `task-tracker` / `/task-board`; restore them |
| -- | a subagent tries to spawn another subagent | forbidden; orchestrate from this session only |
| -- | the final report emitted as prose instead of the P7.4 block | re-emit the block; it is this skill's contract with the caller |
| -- | an argument that does not match the id pattern, and has no title/slug match, is nonetheless read as `<TASK_ID>` | defect -- re-run P0.1: it is PROSE, extract the id from it or fall through to no-id discovery |
| -- | the `PLAN` block is missing, or is printed after `.claude/features/**` is first written | defect -- print it once, right after P0 resolves id/mode/domains, before P1 |

## References

Procedure (authoritative): `.claude/features/TRACKER.md` section 10 | Templates: `.claude/features/specs/{SPEC,DESIGN}_TEMPLATE.md` | Rules: `.claude/rules/tasks.md` | Board + lifecycle: `/task-board`, agent `task-tracker`
