---
doc_type: llm
version: "5.6.0"
content_version: "5.6.0"
generated_by: "brewcode:superreview-setup"
last_updated: "2026-08-13"
---

# Scope Discipline Reference (superreview — allure-server)

SINGLE home of: sanctioned-scope resolution, sanction sources + precedence, the ownership map, the scope-creep
taxonomy + severity map, the full-scope DELIVERY map, the CLOSEOUT map, the "not creep" exclusion list, and the
Phase 3b user gate. `SKILL.md` and `references/agent-prompt.md` POINT here (path only) and never restate any of it.

**WHY this ranks as a principle, not a style note:** allure-server is a PUBLISHED open-source artifact with one maintainer and occasional drive-by external PRs, so the blast radius of a change lands on DOWNSTREAM CONSUMERS rather than on a co-worker: the REST API `/api/result` + `/api/report` is called by other people's CI, the `AllureServerPlugin` SPI is implemented by external JARs dropped into `/ext`, `application*.yaml` key + env-var names are every deployment's config contract, and `ddl-auto: update` turns an entity edit into a live schema migration on somebody's Postgres. Every file touched outside the task's sanctioned
surface is a merge conflict, a contract changed under someone else's feet, or work nobody agreed to.
**Minimal blast radius is a hard project principle.**

---

## 1. Resolve the SANCTIONED SCOPE baseline (Phase 0, before the fan-out)

Tracker for this project: **.claude/features board (canonical, file-based Kanban) + GitHub issues (read-only)**.

`{RANGE}` = the commit range `SKILL.md` resolved for `{MODE}`. EXPORT it before running this block; unset, step `e`
records "not read" rather than reading a range this run never chose. `FULL_PROJECT` / `UNCOMMITTED` have no range —
leaving it empty is correct there.

```bash
# --- SANCTIONED SCOPE baseline (READ-ONLY; degrade to UNKNOWN, never invent) -------------------
BR="$(git rev-parse --abbrev-ref HEAD)"; echo "branch: $BR"

# a. issue id from the branch — ANCHORED. This repo names branches <type>/<slug>
#    (feature/phase-1-vaadin-removal), so a NUMERIC id is the exception; never take a digit run
#    from the middle of a slug (phase-1-... -> 1 is exactly the false match to refuse).
ISSUE=""
printf '%s\n' "$BR" | grep -qE '^[a-z]+/[0-9]+(-.*)?$' \
  && ISSUE="$(printf '%s\n' "$BR" | sed -E 's|^[a-z]+/([0-9]+)(-.*)?$|\1|')"
echo "issue-from-branch: ${ISSUE:-none (slug-named branch — board task is the baseline)}"

# b. task file on the .claude/features board = the CANONICAL baseline. Ids are M-<UPPER-KEBAB>.
#    Match order: branch slug mentioned in a task file -> the single WIP task in progress/ -> none.
SLUG="${BR#*/}"
TASK="$(grep -rlF -- "$SLUG" .claude/features/progress .claude/features/todo .claude/features/closed 2>/dev/null | head -1)"
[ -z "$TASK" ] && TASK="$(ls .claude/features/progress/*.md 2>/dev/null | head -1)"
if [ -n "$TASK" ]; then
  echo "task: $TASK"; sed -n '1,60p' "$TASK"     # frontmatter (id/status/owner/links) + Context + Acceptance
else
  echo "task: NOT MATCHED -> baseline UNKNOWN (scope findings cap at P2, PERMANENT for this run)"
fi

# c. board 'Overall status' + 'Current focus' — the project's own statement of what is in flight
sed -n '/^## Overall status/,/^## Todo/p' .claude/features/board.md 2>/dev/null

# d. scope ids — SILENT no-op unless the matched task file really carries a '## Scope' table
[ -n "$TASK" ] && awk '/^## Scope$/{f=1;next} f&&/^## /{exit} f&&/^\|[[:space:]]*S[0-9]/{print}' "$TASK" 2>/dev/null

# e. GitHub issue + PR, READ-ONLY, only when an id was resolved. This repo pins the gh account
#    (CLAUDE.md §5) — never run a bare `gh` here, and never a write verb.
if [ -n "$ISSUE" ]; then
  GH_TOKEN="$(gh auth token --user kochetkov-ma 2>/dev/null)" gh issue view "$ISSUE" \
    --json number,title,body,labels,assignees 2>/dev/null || echo "issue: not reached"
fi
GH_TOKEN="$(gh auth token --user kochetkov-ma 2>/dev/null)" gh pr view \
  --json number,title,body,headRefName 2>/dev/null || echo "pr: none open for $BR"

# f. commit intent over the resolved range — RANGE unset => 'not read', never a guessed range
[ -n "${RANGE:-}" ] && git log --format='%h %s' "$RANGE" || echo "commit intent: not read (RANGE unset)"

# g. recorded decisions: the task file's '## Notes' running log is this project's decision log
[ -n "$TASK" ] && awk '/^## Notes$/{f=1} f' "$TASK" 2>/dev/null
```

**Scope ids (only when the matched task file HAS them).** A task file may carry a `## Scope` table whose rows are
`| id | block | in/out | status |` — ids `S1..Sn`, task-local, cited globally as `<TASK-ID>#S1`, never renumbered;
`status` is exactly `not-started` | `in-progress` | `done`, and an `out` row carries `--`. When that section exists,
the resolution block ALSO records every row into `{SCOPE_BASELINE}`: the `in` ids with their claimed status (the
delivery surface pass B scores, section 3b) and the `out` ids (explicitly excluded — touching one is shape 2).
**No `## Scope` section, no task file, no board at all -> record NOTHING and move on. Silent no-op: no WARN, no cap,
no report line, no finding.** Most repos have no such board; their scope axis works exactly as before.

> `## Scope` statuses are EXECUTION state. The `## Scope coverage` table in a spec doc
> (`covered` | `partial` | `uncovered`) is SPEC COVERAGE, a different axis: `covered` != `done`, `done` != `covered`.
> !=conflate them, !=derive one from the other.

**Sanction sources, highest authority first** (a lower row can never widen what a higher row bounded):

| # | Source in allure-server | Sanctions? |
|---|-------------------------|------------|
| 1 | The USER's directive in this session (verbatim prompt, follow-ups) | YES — absolute; overrides every artifact below |
| 2 | A recorded DECISION: the matched task file's `## Notes` running log, or a comment on the linked GitHub issue | YES — a decision recorded BEFORE the work makes the overlap legitimate |
| 3 | The task file's `## Context` + `## Acceptance` boxes, and the linked issue body | YES — this is the default baseline; the `## Acceptance` list IS the delivery contract |
| 4 | Project docs as a decision log: `.claude/convention/**` (architecture/patterns/versions), `.claude/features/board.md` `Current focus` | YES, but only for HOW, not for a new WHAT |
| 5 | The PR body, commit messages, `## Notes` lines written DURING or AFTER the change | **NO — sanctions nothing.** These are the artefact under review; a change that only justifies itself in its own commit message is unsanctioned by definition |

**Degradation (never invent a baseline):**

| Condition | Action |
|-----------|--------|
| Issue tracker unreachable / unauthenticated / issue not found | baseline = local task + docs only; record `issue: not reached` |
| No task file AND no issue resolvable | baseline = `UNKNOWN`; report scope findings at **P2 max** and raise the Phase 3b gate. The cap is PERMANENT for the run — the gate may lower a finding further but NEVER restores a pre-cap P0/P1 while the baseline is still `UNKNOWN` |
| Branch carries no issue id in the agreed `<type>/<issue>-<slug>` position | match the task by branch NAME in the board; else `UNKNOWN`. Never take a digit run from elsewhere in the branch name (`fix/oauth2-login` -> 2 is the classic false match) |
| Branch issue id disagrees with the matched task's recorded link | the branch id is unreliable -> baseline = `UNKNOWN`, recorded as `issue N from branch not in task links`. Cross-check ALWAYS |
| Task matched by MENTION only (no explicit link) | usable as CONTEXT; treat the baseline as `UNKNOWN` for severity (P2 cap) unless the issue body independently confirms the deliverable |
| MODE = `FULL_PROJECT` | scope axis is INFORMATIONAL only — a full-project sweep has no single task; report shapes 3/4 only. `SKILL.md` runs scope pass A alone there and SKIPS pass B: with no criteria and no single PR, sections 3b + 4b have nothing to score |

---

## 2. Ownership map (who else gets hit) — derived at RUNTIME, never hardcoded

```bash
# TRUNCATION BOUND (state it in the report when it trips): first 40 changed files, last 5 authors each.
# NOTE for allure-server: authorship is a WEAK signal here — one maintainer wrote most of the tree, so a
# "different author" hit is usually an old external PR, not a live parallel owner. The load-bearing signals
# are (a) another BOARD task claiming the file and (b) the always-shared surfaces below.
printf '%s\n' "$FILES" | head -40 | while IFS= read -r f; do
  [ -n "$f" ] || continue
  printf '%s :: authors=' "$f"
  git log -5 --format='%an' -- "$f" 2>/dev/null | sort -u | paste -sd, -
  CLAIM="$(grep -rlF -- "$f" .claude/features/progress .claude/features/todo 2>/dev/null | head -3 | paste -sd, -)"
  [ -n "$CLAIM" ] && printf '   claimed-by-task: %s\n' "$CLAIM"
done
```

Owners come from: the board's owner column, the task file's `owner:` field, the issue `assignees`, and recent
authorship. A file whose recent authorship, or whose owning task, belongs to someone else = **another owner's
surface** -> shape 1 below, UNLESS the overlap is correctness-driven (section 4: not a finding when recorded,
shape 6 when only unrecorded).

**Always-shared surfaces** (touching them widens blast radius across the whole team regardless of authorship):

| Surface | Why an edit widens blast radius |
|---------|---------------------------------|
| `src/main/java/**/controller/**` + `src/main/java/**/model/**` | the PUBLIC REST contract (`/api/result`, `/api/report`) and its DTO shapes — external CI clients and the Swagger consumers break on a rename, a required-field addition or a status-code change |
| `src/main/java/**/helper/plugin/AllureServerPlugin.java` (+ the SPI types it exposes) | the PUBLIC plugin SPI: external JARs loaded from `/ext` via `-Dloader.path` implement it. Any signature change breaks third-party plugins silently at runtime |
| `src/main/resources/application*.yaml` | every deployment's config contract — property names AND the `${ENV_VAR}` names users already set in Compose/Helm/CI |
| `src/main/java/**/entity/**` + `migration.sql` | with `spring.jpa.hibernate.ddl-auto: update`, an entity edit IS a live schema change on users' H2 and Postgres databases; destructive/ordered changes have no migration tool to express them yet (`M-FLYWAY-MIGRATIONS`) |
| `build.gradle`, `gradle/dependencies.gradle`, `gradle/testing.gradle` | the dependency + PIN surface; must move in lockstep with `.claude/convention/versions.md`, and a transitive bump changes what every downstream image ships |
| `.github/workflows/**` | release + publishing pipeline (branch images -> GHCR, tags -> Docker Hub + GHCR); a broken job is a broken release, not a broken build |
| `Dockerfile`, `docker-compose.yml`, `docker-compose-h2.yml` | the deployment contract users copy verbatim — base image, non-root user, `EXPOSE 8080`, volume layout, env names |
| `src/main/jte/layout/**`, `src/main/jte/partials/**`, `src/main/frontend/input.css` | shared UI layout + Tailwind `@layer components` tokens: one edit repaints EVERY page. Canonical tokens are fixed by `.claude/rules/frontend-design.md` |
| `src/main/resources/static/openapi-youtrack.json` | codegen INPUT for the YouTrack Feign client (`openApiGenerate`) — editing it regenerates `build/generated/` API surface |
| `src/main/java/**/security/**` + `SecurityConfiguration` | one matcher change can expose or lock out every endpoint, including the framework-internal bypass paths |

> The instruction tree (`.claude/rules/**`, `.claude/convention/**`, `.claude/agents/**`, `.claude/skills/**`,
> `CLAUDE.md`, `AGENTS.md`) is NOT on this list: it is the AUTHORITY reviewers cite. Whether it is a review target
> at all is decided by the corpus rule in `SKILL.md` (git-IGNORED = OUT) — a path that is never reviewed cannot
> carry a review severity. Task-board files are the baseline INPUT, likewise never a finding target.

---

## 3. Scope-creep taxonomy (finding category `scope-creep`, rule ids `scope#1`..`scope#6`)

| # | Shape | Example | Default severity / priority |
|---|-------|---------|-----------------------------|
| 1 | **foreign-surface** | a shared contract / schema / migration / design token / registry / CI / lint config edited while task + issue never mention it, or a file owned by another task AND not needed for correctness | `blocker` / **P0** |
| 2 | **unsanctioned-feature** | behaviour beyond the acceptance criteria — extra endpoint, extra role, extra flag, "while I was here" capability | `critical` / **P1** |
| 3 | **drive-by-refactor** | unrelated rename / reorg / format churn in files the task did not need | `major` / P2 |
| 4 | **opportunistic-dependency** | new dependency / config / tooling not required by the acceptance criteria | `major` / P2 |
| 5 | **silent-doc-mutation** | documentation edited to MATCH the code instead of the code following the doc | `critical` / **P1** |
| 6 | **sanctioned-but-unrecorded** | genuinely needed, but the decision exists only in chat or a commit body — not in the issue or the task notes | `minor` / P2 + "record the decision" |

**Overlap is NOT automatically shape 1.** Scope MAY expand into a neighbouring task's surface where correctness
requires it (section 4). Shape 1 fires only on an UNSANCTIONED, correctness-irrelevant touch. Correctness-driven
overlap that is merely UNRECORDED is **shape 6**, P2 + "record the boundary" — never a shape-1 blocker.

**Inverse:** under-delivery is equally reportable and gets its own severity map — section 3b.

Every scope finding MUST carry, in `description`: the baseline source it was checked against (issue id / task id /
decision id, or `UNKNOWN`), the shape or rule id, and — for shape 1 — the other owner or shared surface hit.

---

## 3b. Full-scope DELIVERY (category `scope-creep`, rule ids `scope#D*`)

Full sanctioned scope must be DELIVERED. Reducing it is legitimate ONLY against a real blocker, and only when that
blocker is RECORDED and NAMED (task notes / issue comment). Under-delivery outranks over-delivery.

| Rule | Condition | Default severity / priority |
|------|-----------|-----------------------------|
| `scope#D1` | acceptance criterion / issue done-when with NO corresponding code | `blocker` / **P0** |
| `scope#D2` | criterion partially met, or met only by fixture/stub where the done-when says real | `critical` / **P1** |
| `scope#D3` | criterion met but not provable — no test, no evidence cited | `major` / P2 |
| `scope#D4` | scope reduced with NO recorded blocker — title `unrecorded scope reduction` | `blocker` / **P0** |
| `scope#D5` | a `## Scope` row's claimed `status` disagrees with what landed — code for the id is in the corpus while the row still says `not-started`/`in-progress` (stale bookkeeping) | `minor` / P3 (P2 when the stale row is the task's only delivery record) |
| — | scope reduced WITH the blocker recorded + named | NOT a finding — report as an **accepted reduction**, citing where it is recorded |

### Scope ids as the delivery keys (only when section 1 recorded a `## Scope` table)

Walk EVERY `in` id and score it — the id list is the delivery checklist, alongside the acceptance criteria:

| Claimed status | Reality in the corpus | Outcome |
|----------------|-----------------------|---------|
| `done` | the block is there | not a finding — count it toward the covered tally |
| `done` | nothing implements it | **`scope#D1`** (or `D2` when only a stub/fixture exists), citing `<TASK-ID>#S<n>` and the false `done`. Full PROOF OF ABSENCE below still applies — a `done` claim is not proof either way |
| `not-started` / `in-progress` | the block IS implemented | **`scope#D5`** — the code is fine, the record is stale |
| `not-started` / `in-progress` | nothing implements it | not a finding — the task is honestly unfinished. Say so in the tally, never as a D1 |
| `--` (`out` row) | the block was implemented anyway | NOT a delivery row — it is an explicitly excluded surface: shape **2** (`unsanctioned-feature`), section 3 |

Every scope-id finding cites the id as `<TASK-ID>#S<n>` in `description`. `S<n>` is a scope-id CITATION, never a
rule id: **`scope#S*` is RESERVED and emittable by NOBODY** — the emittable spaces stay `scope#1`..`scope#6`
(pass A) and `scope#D*` / `scope#C*` (pass B). The reservation is stated in every prompt that carries a `rule`
field, so an agent reading its own contract will not invent one; nothing rejects it mechanically, so the Phase 3
validator drops a `scope#S*` row on sight.

**Where a `scope#D5` finding is ANCHORED.** `file` + `lineStart`/`lineEnd` are MANDATORY on every finding, and the
task board is INPUT, never a review subject — it may NOT be a finding location. So a D5 anchors on the
**IMPLEMENTING code** for the id: the hunk that proves the block landed (the most representative one when several).
The stale row is cited in the finding TEXT — `<TASK-ID>#S<n>` plus the `status` cell verbatim — never in `file`.
A `done` id with NOTHING implementing it has no such anchor and is not a D5 at all: it is D1/D2, whose PROOF OF
ABSENCE rules below govern both its evidence and where it points. !=guess a location, !=point at the board.

**Task file with no `## Scope` table -> this whole subsection is a silent no-op:** score the acceptance criteria as
before, report no id tally, raise nothing. An absent board is the `PR: none` case (section 4b) — SKIP, invent nothing.

### PROOF OF ABSENCE — mandatory for D1 and D4 (the only P0s built on a negative)

D1 and D4 assert that something is NOT there. A criterion delivered under a different NAME looks identical to a
criterion never built, and the resulting false P0 flips the verdict to REWORK without anyone questioning it. So:

| Requirement | Detail |
|-------------|--------|
| Quote | the criterion VERBATIM from the task acceptance list / issue done-when, plus its baseline source |
| Search proof | the exact command run and its empty result — by CONCEPT and by SYNONYM, across the source tree AND the tests (e.g. `rg -n 'readiness\|Readiness\|ready_at' src` -> 0 hits). Reading the diff alone is NOT proof: the criterion may be met by code the diff never touched |
| Missing either | you may NOT claim P0. Report at **P2** with `"deliveryProofMissing": true`; the Phase 3b gate then asks the user, and a `Not sanctioned` answer restores P0 |

With quote + search proof present, D1/D4 stay P0 and bypass the gate. Every other `scope#D*` finding still quotes
the criterion verbatim and names the baseline source it came from.

---

## 4. NOT scope creep — do NOT flag

- Files the acceptance criteria imply: a use-case + its test + its interface + its wiring + its route.
- A fix REQUIRED to make the sanctioned change compile / pass the gates.
- An expansion covered by a recorded decision (cite it — decision id, issue comment, docs section).
- Board / task-file bookkeeping for THIS task.
- Anything the user explicitly asked for in this run.
- A trivial one-liner fix in a file already inside the baseline surface.
- **Overlap into a neighbouring task's surface** when BOTH hold: (a) the sanctioned deliverable cannot be CORRECT
  without it, and (b) the boundary is recorded — which part we own, which part stays theirs. Both hold -> not a
  finding. (a) only -> **shape 6**, P2 + "record the boundary". Neither -> shape 1.

---

## 4b. Closeout artefacts — PR/MR body + issue comments (rule ids `scope#C*`)

Runs ONLY when a PR/MR exists for the branch (read-only). No PR -> record `PR: none`, SKIP, invent nothing. What
shipped must be DISCOVERABLE from these artefacts by the next person who touches the surface.

| Rule | Check | Default priority |
|------|-------|------------------|
| `scope#C1` | PR body states what SHIPPED, what is deliberately NOT built, what consumers may rely on | P2 |
| `scope#C2` | `Closes #nn` only for an issue we FULLY own; `Refs #nn` for a shared or partially delivered one. Closing a co-assigned issue is a finding | **P1** |
| `scope#C3` | every issue this work touches carries a closeout comment — what landed, what remains | P2 |
| `scope#C4` | AI attribution in a PR body, commit message or issue comment | P2 |

**Quality bar for both artefacts: maximally short and clear.** Flag an essay-length body, restated rationale,
marketing tone, or a wall that buries the one fact a reader needs — and equally the opposite, a body so thin a
colleague cannot tell what shipped. **P1** instead of P2 whenever the artefact would actively MISLEAD a colleague
(wrong `Closes`, or a claim the diff does not support).

---

## 5. Phase 3b user gate (AskUserQuestion) — MANDATORY

**Trigger:** >=1 CONFIRMED scope-creep of shape **1, 2 or 5** whose sanction could NOT be found in section 1, OR a
`scope#D1` / `scope#D4` row carrying `deliveryProofMissing: true` (section 3b — an absence claim with no verbatim
criterion and no cited search must be asked about before it can weigh as a P0).

| Rule | Detail |
|------|--------|
| Batch | ONE `AskUserQuestion` call, one question per distinct expansion, max 4 (merge the rest into the largest) |
| Question | name the FILES + the surface + who else owns it + the baseline checked; ask whether it is sanctioned. For a `deliveryProofMissing` row: quote the criterion and ask whether it was delivered (possibly under another name) |
| Options | `Sanctioned — decision exists` (-> demote to P3 "record the decision") / `Not sanctioned — report as {P0\|P1}` (recommended, first) / `Intentional — accept for now` (-> P2, marked `accepted-scope`) |
| Never ask | shapes 3, 4, 6, every `scope#C*` row, every `scope#D5` stale-status row, and any `scope#D*` row whose proof IS present — report those directly |
| **Baseline UNKNOWN** | the P2 cap set in section 1 SURVIVES this gate. `Not sanctioned` restores the mapped P0/P1 **only when the baseline was known** — the user confirmed the expansion, not the baseline |
| Non-interactive | gate unavailable -> report at the priority the finding ENTERED with (full severity when the baseline was known, the P2 cap when it was `UNKNOWN`), flagged `unconfirmed-sanction`. Never silently downgrade, never silently upgrade past a cap |
| Record | the question and the user's answer go into the report VERBATIM, and into the chat summary |

The gate runs AFTER Phase 3 validation (only survivors are worth asking about) and BEFORE the report is written.
superreview is READ-ONLY: the gate never edits the board, an issue, or a PR.
