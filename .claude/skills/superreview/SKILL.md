---
name: superreview
description: "Deep allure-server code review the MODEL or the USER can invoke: routes changed files to domain-expert agents, runs the mechanical gates, checks correctness + architecture + reuse + version pins + scope discipline (blast radius vs the sanctioned task/issue) against the project rules, reverse-validates every finding, writes one merged report. Triggers: review code, deep review, superreview, super review, validate changes, check architecture, check standards, check reuse, check scope, blast radius, scope creep, quick review, did I build what was asked, drift check."
user-invocable: true
argument-hint: "[prompt] [scope: commit|branch|folder]"
allowed-tools: Read, Glob, Grep, Agent, Bash, Write, AskUserQuestion
model: opus
doc_type: llm
version: "5.6.0"
content_version: "5.6.0"
generated_by: "brewcode:superreview-setup"
last_updated: "2026-08-13"
---

# Super Review (allure-server)

**ROLE:** Self-contained deep-review coordinator + final validator.
**OUTPUT:** ONE merged, de-duplicated, severity-prioritized, validated report.

---

## Prompt contract

Position 1 of `$ARGUMENTS` is a **free-form prompt** (RU/EN). Two orthogonal axes resolve from it: `{MODE}`
(WHAT is reviewed — deterministic, resolved by git state + explicit scope tokens, see Mode Detection below,
never keyword-scored) and `{DEPTH}` (HOW THOROUGH — semantic, resolved from the keywords below).

| Depth | EN keywords | RU keywords | Mutates? |
|-------|-------------|-------------|----------|
| `EXTENDED` | `deep`, `go deep`, `thorough`, `properly`, `full review`, `full audit`, `audit`, `expert review`, `review everything`, `all the experts`, `before release`, `before merge`, `pre-merge review`, `serious review` | `глубоко`, `глубокий`, `по полной`, `тщательно`, `полный ревью`, `аудит`, `перед релизом` | no (read-only review) |
| `QUICK` | `quick`, `quick look`, `fast`, `just check`, `sanity check`, `did I do what was asked`, `am I on track` | `быстро`, `по-быстрому`, `просто проверь`, `то ли я сделал` | no (read-only review) |

1. `{MODE}` resolves first, deterministically, per Mode Detection below — never from these keywords.
2. `{DEPTH}` then scores by distinct whole-word keyword hits in the remaining prompt (table above); depth
   words are CONSUMED and stripped before the remainder becomes `{FOCUS}`. No match -> `QUICK` (the default).
3. Neither axis is ever asked about via `AskUserQuestion` — both are computed per Mode/Depth Detection below;
   this skill never guesses interactively.
4. Empty arguments -> `{MODE}` per rule 3a/3b (git state) below, `{DEPTH} = QUICK`.
5. Prose that is not a mode/scope token is still input: it becomes `{FOCUS}` after depth words are stripped.

Then print the PLAN block ONCE, before any review work (Phase 0 step 7, ANNOUNCE, below):

```
PLAN — allure-server superreview
INPUT:  <arguments verbatim, or "(empty)">
MODE:   <resolved MODE> — <which rule>; Depth <resolved DEPTH> — <which signal>
SCOPE:  <branch, commit range/folder, focus, file count>
DO:     <gates, intent pass, and — EXTENDED only — expert fan-out, scope passes, validation>
RESULT: <the merged report + chat summary>
```

Labels are literal; values follow the conversation language.

---

| # | Step | QUICK | EXTENDED |
|---|------|-------|----------|
| 0 | Deterministically resolve ONE review MODE **and one DEPTH**; ANNOUNCE both (mode + depth + branch + concrete scope) before any review work — no interactive guessing | yes | yes |
| 1 | Run the MECHANICAL GATES (build/lint/test) — execution output is `CONFIRMED-BY-EXECUTION`, the ONE verdict needing no adversarial pass | yes | yes |
| 2 | The INTENT pass — spawn `intent-guard`: what was ASKED vs what was DELIVERED. The anti-drift check | yes | yes |
| 3 | SELECT domain experts at RUNTIME from the live agent roster (`.claude/agents/*.md`) and route each changed-file group to its best-matching owner — a newly added agent is picked up automatically | **skipped** | yes |
| 4 | Check the focus ordering below: principle/architecture conformance -> correctness -> reuse -> pins -> style | **skipped** | yes |
| 5 | Resolve the SANCTIONED SCOPE baseline (task + issue + recorded decisions); audit scope creep / blast radius / under-delivery — an unsanctioned touch is a first-class finding, not a nitpick | **skipped** | yes |
| 6 | Run ONE targeted parallel fan-out — only the agents the changed files actually need | **skipped** | yes |
| 7 | VALIDATION: per-finding adversarial re-verification of EVERY candidate, then an AskUserQuestion gate on unsanctioned scope expansion, then merge + consistency check | adversarial part **skipped**; merge/rank/consistency run in-session | yes |
| 8 | ONE merged report + chat summary, drift verdict first | yes (short) | yes |

> **Bash-first:** some macOS builds ship no native `Grep`/`Glob` tool. Every command here is shell-based
> (`git`, `rg`/`grep`); agents are told the same.

> **Single-source rule (applies to this file):** the domain-owner prompt contract + the runtime expert-selection
> procedure live ONCE in `references/agent-prompt.md`; the sanctioned-scope resolution, ownership map, creep
> taxonomy, delivery/closeout maps and the user gate live ONCE in `references/scope.md`; stack specifics live ONCE
> in `references/java-kotlin.md`. This file POINTS at them. Where a subagent needs one, pass the PATH, never pasted prose.

All agents are project-local (`.claude/agents/`) or a built-in (`Explore`/`Plan`/`general-purpose`). This skill
never invokes any sibling skill, NO plugin cache; the validator/arbiter fallback is the
built-in `general-purpose`.

---

## Mode Detection (deterministic — run FIRST, then ANNOUNCE)

**Arguments:** `$ARGUMENTS`

At launch, resolve EXACTLY ONE review `{MODE}` by the strict priority order below, then PRINT the resolved mode +
current branch + concrete scope to the user BEFORE any review. The mode is COMPUTED, never guessed interactively;
the same rule applies on `master`/`main` and on feature branches.

| # | Condition (checked in order) | Resolved `{MODE}` | Scope reviewed |
|---|------------------------------|-------------------|----------------|
| 1 | User prompt asks for the WHOLE project, in any language ("whole project", "entire project", "everything", "all the code", or the same intent in another language) | `FULL_PROJECT` | the WHOLE corpus matching `PATHSPEC` — tracked + untracked-but-not-ignored source, config, docs |
| 2 | User passed an explicit scope token (commit SHA, branch name, or folder path) | `EXPLICIT` | that commit / branch-vs-main / folder |
| 3a | DEFAULT + working tree has UNCOMMITTED changes (`git status --porcelain` non-empty) | `UNCOMMITTED` | working-tree diff vs `HEAD` |
| 3b | DEFAULT + clean tree (everything committed) | `LAST_COMMITS` | last 1-2 commits: `HEAD~2..HEAD` if it exists, else `HEAD~1..HEAD`, else the single root commit |

> A text prompt that is NOT a whole-project request and NOT a scope token is treated as a `{FOCUS}` directive
> (see Focus below) — after the DEPTH words are stripped from it (see Depth Detection); the mode still resolves
> via rule 3.
> Scope-token test (deterministic, this order): `[ -d "$TOK" ]` -> EXPLICIT folder; `[ -f "$TOK" ]` -> EXPLICIT
> single file; `git rev-parse -q --verify "$TOK^{commit}"` succeeds -> EXPLICIT commit/branch; else -> `{FOCUS}`.

### What the review corpus IS — the single rule: git TRACKING, not commit status

**IGNORED = OUT. Everything else = IN.** One rule, applied everywhere:

| | Rule |
|---|------|
| **OUT** | anything git does not track and never will — ignored by `.gitignore` OR by `.git/info/exclude`. Both matter equally, and `.git/info/exclude` is invisible in the tree, so it is the one people forget. Never enumerate either list from memory — ask git |
| **IN** | everything git tracks OR will track. Commit status is irrelevant: staged, modified and untracked-but-not-ignored files are all IN |
| Instrument | `git ls-files --others --exclude-standard` honours `.gitignore` AND `.git/info/exclude` in one call — **never drop that flag** to reach an ignored path |

Consequence: a git-ignored instruction file (`.claude/**`, `CLAUDE.md`, an ignored task board) is never a review
TARGET — it is the AUTHORITY reviewers cite, and the board is the scope BASELINE input. Tracked docs are reviewed
normally, and there is no separate ignored-path sweep anywhere.

**Mode-resolution commands** (runnable as-written; `PATHSPEC` = the file globs every mode reviews):

```bash
# leading * is REQUIRED on nested patterns: git pathspecs are root-anchored.
PATHSPEC=('*.java' '*.jte' '*.gradle' '*.css' 'src/main/resources/**' 'Dockerfile*' 'docker-compose*.yml' '.github/workflows/*.yml')

BRANCH=$(git rev-parse --abbrev-ref HEAD)
PORCELAIN=$(git status --porcelain)
# MAIN = default-branch name; the fallback binds to git, never to a pipeline stage
MAIN=$(git symbolic-ref --short refs/remotes/origin/HEAD 2>/dev/null); MAIN=${MAIN##*/}; MAIN=${MAIN:-main}

# Resolve FILES for the chosen mode, then derive an EXACT count (no head-truncation of the count).
# CORPUS = tracked + untracked-but-not-ignored (the single tracking rule above).
CORPUS() { { git ls-files -- "${PATHSPEC[@]}"; git ls-files --others --exclude-standard -- "${PATHSPEC[@]}"; } | sort -u; }

# FULL_PROJECT (rule 1): the whole corpus — source + build/CI + tracked docs
FILES=$(CORPUS)

# EXPLICIT commit (rule 2), parentless-safe (root commit OK):
# FILES=$(git show --name-only --pretty="" "${TOK}" -- "${PATHSPEC[@]}")
# EXPLICIT branch (rule 2) — diff the NAMED branch $TOK, never HEAD; prefer the remote ref:
# FILES=$(git diff --name-only "origin/${MAIN}...${TOK}" -- "${PATHSPEC[@]}" 2>/dev/null \
#         || git diff --name-only "${MAIN}...${TOK}" -- "${PATHSPEC[@]}")
# EXPLICIT folder / single file (rule 2): the corpus, filtered — never raw find (build-output junk).
# FILES=$(CORPUS | grep "^${TOK%/}/")                                   # single file: FILES="$TOK"

# UNCOMMITTED (rule 3a): staged + unstaged + UNTRACKED-not-ignored (porcelain counts ?? files, so the scope must too)
# FILES=$( { git diff --name-only HEAD -- "${PATHSPEC[@]}"; git ls-files --others --exclude-standard -- "${PATHSPEC[@]}"; } | sort -u )
# Dirty tree but FILES empty (only ignored/out-of-scope files) -> fall through to LAST_COMMITS (rule 3b).

# LAST_COMMITS (rule 3b): nested fallback so a single-commit repo never errors
# if HEAD~2 exists -> HEAD~2..HEAD ; elif HEAD~1 exists -> HEAD~1..HEAD ; else the single root commit
# if git rev-parse --verify -q HEAD~2 >/dev/null; then
#   FILES=$(git diff --name-only "HEAD~2..HEAD" -- "${PATHSPEC[@]}")
# elif git rev-parse --verify -q HEAD~1 >/dev/null; then
#   FILES=$(git diff --name-only "HEAD~1..HEAD" -- "${PATHSPEC[@]}")
# else
#   FILES=$(git show --name-only --pretty="" HEAD -- "${PATHSPEC[@]}")   # root commit
# fi

# RANGE = the commit range of the chosen mode. EXPORT it — references/scope.md step e reads commit intent from it
# and reports "not read" rather than guessing when it is unset. FULL_PROJECT / UNCOMMITTED have no range: leave empty.
# export RANGE="HEAD~2..HEAD" | "$TOK^!" | "origin/${MAIN}...${TOK}" | ""

# EXACT count for the mandatory announcement (count, never the displayed list, is truncated):
COUNT=$(printf '%s\n' "$FILES" | grep -c .)
echo "$FILES" | head -50   # DISPLAY only — truncating the shown list is fine; COUNT above stays exact
```

**MANDATORY announcement before reviewing — exact template in Phase 0 step 7 (single source).**

---

## Depth Detection (semantic — resolve IMMEDIATELY after `{MODE}`, then ANNOUNCE)

`{DEPTH}` has EXACTLY TWO values, `QUICK` and `EXTENDED`, and is inferred by YOU from the user's prompt and the
run's context — the same way `{MODE}` rule 1 reads intent from prose. **There is NO flag, NO `--fast`, NO CLI
token for depth; never invent one and never tell the user to pass one.** Depth is orthogonal to `{MODE}`: any
mode can run at either depth.

| # | Signal (checked in order) | `{DEPTH}` |
|---|---------------------------|-----------|
| 1 | The prompt asks for DEPTH, completeness or expertise, in any language: "deep", "go deep", "thorough", "properly", "full review", "full audit", "audit", "expert review", "review everything", "all the experts", "before release", "before merge", "pre-merge review", "serious review" — RU: "глубоко", "глубокий", "по полной", "тщательно", "полный ревью", "аудит", "перед релизом" | `EXTENDED` |
| 2 | The prompt asks for SPEED or a sanity check: "quick", "quick look", "fast", "just check", "sanity check", "did I do what was asked", "am I on track" — RU: "быстро", "по-быстрому", "просто проверь", "то ли я сделал" | `QUICK` |
| 3 | Anything else — a focus directive, a bare scope token, an empty prompt, genuine ambiguity | `QUICK` (DEFAULT) |

> **Depth words are CONSUMED here.** Strip them from the prompt before treating the remainder as a `{FOCUS}`
> directive — "deep review, focus on auth" resolves to `{DEPTH}=EXTENDED` + `{FOCUS}=auth`, never to a focus of
> "deep review, focus on auth". A prompt that is ONLY depth words leaves `{FOCUS}` = the default ordering.

## Focus (user directive, else default ordering)

If the user passed a focus directive in the prompt ("focus on X"), PRIORITIZE it. Otherwise use this DEFAULT focus
order (highest first). Bake this exact ordering into BOTH the shared agent prompt and the validator prompt.

| Rank | Focus | What it means in allure-server (CITE the rule file, never restate it) |
|------|-------|------------------------------------------------------------------------|
| 1 | **Correctness + SCOPE DISCIPLINE** | defects that change behaviour: broken control flow, unclosed streams / leaked temp dirs in the `ResultService` ZIP intake, path traversal on `allure/results/<uuid>` (`PathUtil` UUID guard), transaction + `@Cacheable`/`@CacheEvict` boundaries in `JpaReportService`, `SecurityFilterChain` matcher gaps, JTE/HTMX pages rendering stale or wrong state. PLUS blast radius vs the sanctioned baseline -> `references/scope.md` |
| 2 | **Architecture / layer boundaries** | layer leaks between `controller/` (REST), `web/` (server-rendered UI), `service/`, `helper/`; plugin SPI (`AllureServerPlugin`) contract breaks; config read outside `@ConfigurationProperties` (`System.getenv` in business code); entity changes taken as live schema changes because `ddl-auto: update`. Cite `.claude/convention/project-architecture.md` + `.claude/convention/reference-patterns.md` |
| 3 | **Reuse / duplication (SEARCH FIRST, then flag)** | before flagging, actually search the corpus. 90% overlap -> USE the existing thing; 70% -> EXTEND/abstract it; 50% -> note the overlap only. Existing helpers come first (`helper/Util`, `PathUtil`, `MoveFileVisitor`, `ServeRedirectHelper`), lib priority JDK > Apache Commons > Guava, JTE `partials/` + `input.css` `@layer components` before new markup/CSS. Etalon map: `.claude/convention/reference-patterns.md` |
| 4 | **Version pins** | every version literal belongs in `.claude/convention/versions.md` and must match the source file in lockstep; no `latest` / `+` / ranges in `build.gradle`, `gradle/dependencies.gradle`, `Dockerfile`, `docker-compose*.yml`, or the pinned Tailwind / HTMX / Alpine assets (`CLAUDE.md` §3 rule 4) |
| 5 | **Business requirements / delivery** | the matched board task's `## Acceptance` boxes actually delivered (not just claimed); docs (`README.md`, `.claude/**`) not silently rewritten to match the code; English-only artifacts (`CLAUDE.md` §3 rule 6) |

> **Project emphasis:** default ordering — the generation prompt asked for an autonomous run with recommended defaults, not a focus reorder. Two standing weights on top: (a) this is a published OSS artifact, so a break in the PUBLIC surfaces (REST `/api/result` + `/api/report`, the `AllureServerPlugin` SPI loaded from `/ext`, `application*.yaml` env-var names, Docker/Compose contract) outranks internal tidiness; (b) version-pin drift is cheap to check and expensive to miss. Scope discipline stays inside rank 1 and is never dropped.

> **SCOPE DISCIPLINE / minimal blast radius is part of focus rank 1 — never optional.** Measure every change
> against the SANCTIONED baseline (Phase 0 step 5: task + issue + recorded decisions). Beyond it = `scope-creep`,
> up to **P0**: shared contract / schema / migration / registry / CI edit, another owner's surface, past-acceptance
> feature, drive-by refactor, doc silently rewritten to match code. The INVERSE ranks equally: an undelivered
> acceptance criterion or an unrecorded scope reduction is **P0**. Correctness-driven overlap into a neighbour's
> task is legit if recorded. Taxonomy, delivery/closeout maps, exclusion list and the user gate: `references/scope.md`.

> **Security is explicitly NOT a priority.** Report a security finding ONLY when it is CRITICAL (P0) — e.g. logged
> secret, missing auth on a public path, injection. Do NOT spend agent effort on low/medium security.

> **SCALE CALIBRATION (every rank).** Judge harm against this project's REAL scale, not a hypothetical one. Harm
> reachable only via concurrency / load / contention the system does not have -> P3 or DROP. A race claim MUST
> state its traffic assumption ("same millisecond" is not one). Correctness invariants (constraints, unique keys,
> state machines) are untouched by this calibration.

---

## Domain experts — selected at RUNTIME from the live roster

The skill picks its own experts each run. The table below is the EXPECTED RESULT as of generation time, not a
frozen contract: **READ `references/agent-prompt.md` FIRST and EXECUTE its "Dynamic expert selection" procedure** —
it holds the roster command, the recon-agent exclusion list, the selection steps and the fallback chain. On any
disagreement between the table and the live roster, **the live roster wins**.

| Guard | Rule |
|-------|------|
| Domain expert first | every non-empty file group goes to the agent whose description claims that path/responsibility MOST specifically. A generic agent on a domain surface is a DEGRADED run — say so in the report |
| Recon agents excluded | read-only external-system agents (cloud/SaaS/DB consoles, ticket readers) never review source files — list in `references/agent-prompt.md` |
| No confident match | built-in `Explore` (read-only); note the fallback in the report |
| Auditability | record the DERIVED map in the report's `Agents run` line |
| Model override | do NOT pass a `model` override — project agents define their own |

| Agent | Domain owned (expected map as of generation time — live roster wins) |
|-------|----------------------------------------------------------------------|
| `rest-controller` | Domain owner — `src/main/java/**/controller/**`: REST endpoints, request validation, HTTP status mapping, `@ExceptionHandler`, controller-level caching |
| `dto-model` | Domain owner — `src/main/java/**/model/**` (+ `web/dto/**` if present): REST DTOs as records, bean validation, `@Schema` docs |
| `report-service` | Domain owner — `src/main/java/**/service/JpaReportService.java`, report lifecycle, `@Transactional` + cache boundaries, `@Scheduled` cleanup, redirect registration |
| `result-service` | Domain owner — `src/main/java/**/service/ResultService.java` + path utils: upload intake, ZIP extraction, UUID path validation, filesystem moves, temp-dir cleanup |
| `generation-pipeline` | Domain owner — `src/main/java/**/helper/AllureReportGenerator.java`, `helper/plugin/AllureServerPlugin.java` + bundled plugins: Allure core integration, plugin SPI lifecycle and dispatch resilience |
| `plugin-youtrack` | Domain owner — `src/main/java/**/helper/plugin/YouTrackPlugin.java`, `helper/plugin/youtrack/**`, `api/youtrack/**`: TMS integration, Feign client, OpenAPI codegen input |
| `config-security` | Domain owner — `src/main/java/**/properties/**`, `config/**`, `security/**`, `src/main/resources/application*.yaml`: `@ConfigurationProperties`, `SecurityFilterChain`, DB auth + API-token filter, OAuth2 profile |
| `persistence-jpa` | Domain owner — `src/main/java/**/entity/**`, `repo/**`, `migration.sql`: JPA schema, derived queries, H2/Postgres portability, `ddl-auto` implications |
| `web-ui` | Domain owner — `src/main/java/**/web/**` (the `/app/**` pages), `src/main/jte/**`, `src/main/frontend/input.css`, `tailwind.config.js`, `src/main/resources/static/**` (vendored htmx/Alpine, theme.js, icons, swagger theme): server-rendered UI (JTE + HTMX + Alpine.js + Tailwind standalone). Enforces `.claude/rules/frontend-design.md`. NOT the Tailwind Gradle tasks (`build-ci-qa`) and NOT the Allure-report branding assets (`generation-pipeline`) |
| `build-ci-qa` | Domain owner — `build.gradle`, `gradle/*.gradle`, `.github/workflows/**`, `Dockerfile`, `docker-compose*.yml`, `src/test/**` infra: build, pins, CI/release, test infrastructure |
| `task-tracker` | Owner of `.claude/features/**` — the BASELINE input, not a review target. Used as scope pass A only; never routed a source group |

PLUS up to two GENERAL cross-cutting agents — they are NOT auto-spawned every time; **the model DECIDES** whether
(and which) to include, by JUDGEMENT of the scope. Each, if included, runs ONCE over the full changed set:

| Agent | Role | Include WHEN |
|-------|------|--------------|
| `general-purpose` | Cross-cutting correctness/quality 2nd pass over the FULL changed set — logic the single-domain owner cannot see because it spans their boundary | the change carries non-trivial logic, spans >= 2 domain groups, or any group fell back to `Explore`. SKIP for a small, single-domain, low-risk diff |
| `general-purpose` (arbiter role — a SEPARATE spawn with the arbiter brief) | Boundary / architecture arbiter: layer placement (`controller` vs `service` vs `web` vs `helper`), etalon conformance vs `.claude/convention/reference-patterns.md`, plugin-SPI and public-REST contract stability. ALSO the Phase 3 validator | a class is added or moved between layers, the plugin SPI / public REST surface / `application*.yaml` keys change, or `.claude/convention/**` is edited. **DEGRADED axis:** this project has no dedicated architect agent, so a built-in carries it — say so in the report's `Agents run` line |

---

## The intent pass — `intent-guard`, spawned at BOTH depths, ALWAYS

`intent-guard` (`.claude/agents/intent-guard.md`) answers the one question no domain expert asks: **was the thing
that was DELIVERED the thing that was ASKED for?** Drift starts small at the first turn and is large by the last;
an agent can follow an approved plan faithfully for hours and still ship something nobody requested.

| Rule | Detail |
|------|--------|
| Always spawned | at `QUICK` and at `EXTENDED`, in the Phase 2 message. It is NOT chosen by the runtime expert-selection procedure and is NOT a domain group owner — it is unconditional |
| Not a domain expert | never assign it a file group, never count it as the group's owner, never use it as the Phase 3 validator |
| Self-contained | its procedure, evidence budget (<=15 tool calls), tier model and output shape live in ITS OWN file. The spawn brief passes context only and NEVER restates them |
| Read-only + cheap | file/dir NAMES, `git diff --stat`, manifest diffs, targeted 1-5 line peeks. It never reads a source file whole, never runs a build |
| Missing agent | `.claude/agents/intent-guard.md` absent -> the intent pass is `not run`, said so in the ANNOUNCE, the report AND the chat summary. At `QUICK` that leaves gates only — never report a clean run |

---

## allure-server rules — REFERENCE the canonical files, do NOT restate them

The project rules are authoritative in their own files; this skill does NOT duplicate them. Every agent READS the
files relevant to its area and CITES the rule number it enforces (`avoid#N`, `architecture#N`, `containers#N`, …).
One-line gist per pointer only — the file is the authority. Phase 0 PREFLIGHT-validates that these exist (see below).

| File | One-line gist (the file is the authority) | Cite as |
|------|------------------------------------------|---------|
| `.claude/rules/avoid.md` | numbered table of banned practices for `*.java` / `*.gradle` / `application*.yaml` (config read outside CP, `System.out`, etc.) — each row is a drift class someone already hit | `avoid#N` |
| `.claude/rules/best-practice.md` | numbered table of required practices: records for DTOs, `@Getter`+`final`+`@ConstructorBinding` for CP classes, constructor DI, Lombok set | `best-practice#N` |
| `.claude/rules/frontend-design.md` | binding UI rules: canonical color tokens (single source), logo mark, utility vs component classes (`@layer components` in `input.css`), theme, table/list pages, explicit "what NOT to do" | `frontend#<section>` |
| `.claude/rules/test-avoid.md` | test anti-patterns table (`src/test/**`): no `if` in tests, no bare `isNotNull`, no log noise | `test-avoid#N` |
| `.claude/rules/test-best-practice.md` | required test practices: GIVEN/WHEN/THEN, `@DisplayName` on methods, AssertJ `.as()` + concrete assertions, few real scenario tests | `test-best-practice#N` |
| `.claude/rules/tasks.md` | task-board rules for `.claude/features/**` — folder == status, board updated on every transition | `tasks#N` |
| `.claude/rules/semble-first.md` | search policy: semantic `semble_code` search for intent, `rg`/Grep for identifiers, literals, paths and any "every/all" enumeration | `semble#N` |
| `.claude/convention/project-architecture.md` | module map, generation pipeline, plugin SPI, security, persistence, CI — the architecture the project committed to | `architecture#<section>` |
| `.claude/convention/reference-patterns.md` | etalon map: the exact class to copy per layer (DTO, CP, config, SPI, Feign, repo, scheduler) | `patterns#<etalon>` |
| `.claude/convention/testing-conventions.md` | test slices, fixtures, live etalon test classes, and the legacy suites to bring to standard on touch | `testing#<section>` |
| `.claude/convention/versions.md` | SINGLE source of version truth — every pin lives here; no version literal anywhere else | `versions#<row>` |
| `CLAUDE.md` | §1 what the project is (Vaadin/Node REMOVED), §2 verified build commands, §3 the 9 hard rules, §4 team roster, §5 git identity | `claude#<section>` |

> `CLAUDE.md` (+ any per-module `CLAUDE.md`) auto-load and add project context.
> Breach of any cited rule = P0/P1 candidate (per the Focus ordering; security only as P0).

---

## Delegation (applies to EVERY Task this skill spawns)

A big task handed to one agent = an agent gone for an hour: you cannot observe it, cannot correct it, and it
usually drifts off-target. One subagent = ONE bounded unit — one deliverable (here: ONE file group's review),
~<=5 files, ~<=10 steps. Bigger MUST be split into N tasks, all spawned in ONE message — split an oversized
group into two groups rather than handing one agent half the repo.

Every spawn prompt MUST carry:

| Field | Content |
|-------|---------|
| GOAL | the overall task and why it exists — the point beyond the file edit |
| ROLE | what this agent owns; what it must NOT touch |
| SCOPE | exact paths/commands in bounds + explicit out-of-bounds |
| CONTEXT | what is already done, by whom, what runs in parallel — trimmed to what THIS agent needs |
| CONSUMER | who or what uses the result next, and the shape it must fit |
| DONE | acceptance criteria + the exact report shape you want back |

A bare one-line task is never enough. Phase 2 fills these from `references/agent-prompt.md`; Phase 3 fills them
for the validator.

---

## Execution

> **VALIDATION INVARIANT (binding on every phase below).** No finding is reported without a verdict. The verdict is
> the Phase 3 adversarial validation (`CONFIRM`) or one of exactly TWO non-adversarial verdicts, each tied to one
> source: mechanical gate output carries `CONFIRMED-BY-EXECUTION` (citing the command and its output), and an
> `intent-guard` row carries `CONFIRMED-BY-EVIDENCE` (citing the verbatim ASKED quote, its source tier, and the
> path/count that evidences delivery). No other source may use either.
> A finding that could not be validated carries `verdict: UNVALIDATED` — claiming nothing — and
> forces an `INCOMPLETE` run. Every report table is a VIEW over the merged set `{MERGED}`, never a paste of raw
> Phase 2 candidates, and the unvalidated count appears in the report AND the chat summary, so a degraded run can
> never look clean.

### Phase 0 — Preflight, MODE, MECHANICAL GATES, SCOPE BASELINE, ANNOUNCE

1. **PREFLIGHT — validate the files this run DEPENDS ON.** Two classes: the assets this skill EXECUTES against (a
   missing one silently guts a whole phase) and the rule files reviewers cite. WARN per missing file; degrade
   gracefully, do NOT hard-fail:

```bash
S=".claude/skills/superreview"
for f in "$S/references/agent-prompt.md" "$S/references/scope.md" "$S/references/java-kotlin.md" \
         "$S/references/report-template.md"; do
  [ -f "$f" ] || echo "WARN missing EXECUTED asset: $f (the phase that uses it degrades — say so in the report)"
done
# intent-guard runs at BOTH depths; at QUICK it is the ENTIRE review, so its absence is not a soft warning.
# An empty / frontmatter-less file is as broken as a missing one — check CONTENT, not just existence.
{ [ -s .claude/agents/intent-guard.md ] && grep -q '^name:[[:space:]]*intent-guard[[:space:]]*$' .claude/agents/intent-guard.md; } \
  || echo "WARN missing or unusable intent-guard agent: .claude/agents/intent-guard.md (intent pass = 'not run'; at QUICK depth this run reports GATES ONLY — regenerate with /brewcode:superreview-setup)"

for f in .claude/rules/avoid.md .claude/rules/best-practice.md .claude/rules/frontend-design.md \
         .claude/rules/test-avoid.md .claude/rules/test-best-practice.md .claude/rules/tasks.md \
         .claude/rules/semble-first.md .claude/convention/project-architecture.md \
         .claude/convention/reference-patterns.md .claude/convention/testing-conventions.md \
         .claude/convention/versions.md CLAUDE.md; do
  [ -f "$f" ] || echo "WARN missing rule: $f (reviewers cannot cite it — note the gap in the report)"
done
```

2. **Resolve `{MODE}`, then `{DEPTH}`, then `{FOCUS}`** per the **Mode Detection**, **Depth Detection** and
   **Focus** sections above (deterministic / semantic; never interactive). Run the mode-resolution commands to get
   `{BRANCH}`, `{SCOPE}`, `FILES`, EXACT `COUNT`, and export `RANGE` (the resolved commit range) for
   `references/scope.md` step `e`. Keep the user's prompt VERBATIM as `{USER_REQUEST}` — it is `intent-guard`'s
   tier-1/tier-5 source and must reach it unparaphrased.
   Capture `{DEPTH}` before anything else branches on it; every "skipped at QUICK" below reads it.
3. **Derive the expert map** — `EXTENDED` ONLY (`QUICK` spawns no domain expert, so there is nothing to map; record
   `Experts: none (QUICK)`). READ `references/agent-prompt.md`, run its roster command, build the group -> agent
   map from the LIVE roster. Mark any group that fell back to a generic agent as DEGRADED. `intent-guard` is never
   part of this map — it is spawned unconditionally, outside the group routing.
4. **MECHANICAL GATES (ground truth).** Run the project's real gates BEFORE the fan-out; their output is FACT, not
   opinion. The block is HARD-GUARDED with captured exit codes (a pipe into `tail` would report `tail`'s status)
   and runs in a SUBSHELL (the Bash tool keeps cwd between calls, so a bare `cd` would re-root every later command):

```bash
# allure-server gates: Gradle wrapper + Java 25. `compileJava` pulls in `openApiGenerate` (YouTrack Feign
# codegen) automatically; `test` pulls in processResources -> tailwindBuild -> tailwindDownload, which needs
# NETWORK on a cold cache. A tailwindDownload/dependency-resolution failure is an ENVIRONMENT skip, not a
# code finding — say which one it was. Subshell + captured exit codes: never `cmd | tail && echo OK`.
GLOG="$(mktemp -d)"
if [ ! -x ./gradlew ] || ! command -v java >/dev/null 2>&1; then
  echo "GATE compile SKIP (missing toolchain: ./gradlew executable + java on PATH required)"
  echo "GATE test SKIP (missing toolchain)"
  echo "Gates: not run (toolchain absent) — NEVER invent gate results"
else
  ( ./gradlew --console=plain compileJava compileTestJava ) >"$GLOG/compile.log" 2>&1; RC=$?
  if [ "$RC" -eq 0 ]; then
    echo "GATE compile OK (exit 0)"
  else
    echo "GATE compile FAIL (exit $RC)"
    grep -nE "error:|warning:|FAILURE:|What went wrong|Could not resolve|Caused by" "$GLOG/compile.log" | head -30
    tail -20 "$GLOG/compile.log"
  fi
  ( ./gradlew --console=plain test ) >"$GLOG/test.log" 2>&1; RC=$?
  if [ "$RC" -eq 0 ]; then
    echo "GATE test OK (exit 0)"
    grep -nE "tests? completed" "$GLOG/test.log" | tail -3
  else
    echo "GATE test FAIL (exit $RC)"
    grep -nE "FAILED|tests? completed|What went wrong|Could not resolve|tailwindDownload" "$GLOG/test.log" | head -30
    tail -20 "$GLOG/test.log"
  fi
fi
echo "gate logs: $GLOG (compile.log, test.log)"
```

   | Rule | Detail |
   |------|--------|
   | Ground truth | a real gate error carries verdict **`CONFIRMED-BY-EXECUTION`**: the tool ran it and the output IS the proof. That verdict IS its validation — not an exemption from the invariant. Every such row MUST cite the command AND the output line |
   | Priority | a hard-rule / boundary error from the gate = **P0**; other build/lint/type/test failures = **P1** |
   | Missing deps | toolchain or dependencies absent -> the guard SKIPS the gates; record `Gates: not run (<reason>)`. **Never invent gate results** |
   | Exit code only | report a gate OK only on an explicit `GATE <g> OK`. Never infer success from quiet output |
   | Scope | gates run repo-wide; attribute a gate finding to a reviewed file only when the file appears in `FILES` — otherwise list it under "Pre-existing gate failures" and still report it (noticed -> owned) |
   | No re-run | pass `{GATE_RESULTS}` to every agent so nobody re-runs or re-litigates them |

5. **SCOPE BASELINE (what this change was SANCTIONED to touch) — `EXTENDED` ONLY.** At `QUICK`, SKIP this step
   entirely: set `{SCOPE_BASELINE}` = `not resolved (QUICK)`, `{OWNERSHIP}` = `not resolved (QUICK)`,
   `{PR_ISSUE_JSON}` = `not fetched (QUICK)`, and go to step 6 — `intent-guard` resolves its own tier-1..tier-5
   sources from its own file, so nothing here is needed to run the intent pass.
   READ `references/scope.md` and run its section 1
   resolution block. Output is `{SCOPE_BASELINE}` — task id + file, issue id + acceptance criteria, recorded
   decisions, the sanctioned file surface — plus `{OWNERSHIP}` (section 2) for every file in `FILES` outside it,
   and `{PR_ISSUE_JSON}` — the RAW issue/PR data fetched once here (or `none` / `not reached`). `{SCOPE_BASELINE}` +
   `{OWNERSHIP}` go to Phase 2 and to the validator; `{PR_ISSUE_JSON}` goes to scope pass B so nothing re-fetches it.

   | Rule | Detail |
   |------|--------|
   | Board = INPUT, never a subject | task-board / planning files are read here as the BASELINE: never in `FILES`, never routed to an owner, never a finding target |
   | Scope ids, when the task file has them | a `## Scope` table (`id \| block \| in/out \| status`) enters `{SCOPE_BASELINE}` as the delivery checklist pass B walks. **No such table, no task file, no `.claude/features/` board -> record nothing, silently.** No WARN, no cap, no report line — see `references/scope.md` section 1 |
   | Read-only on the tracker | view issues/PRs only. Never create, edit, comment on or close anything |
   | Never invent | no task + no issue -> `{SCOPE_BASELINE} = UNKNOWN`; scope findings cap at P2 **permanently** — the Phase 3b gate may lower further but never restores a pre-cap priority |
   | Precedence | `references/scope.md` section 1 table. A PR body / commit message sanctions NOTHING — it is the artefact under review |
   | FULL_PROJECT | no single task exists -> scope axis is INFORMATIONAL: scope pass A only (shapes 3/4); pass B is SKIPPED. Say so in the report |

6. Compute a single `TIMESTAMP` for the report dir:

```bash
TIMESTAMP=$(date +"%Y%m%d-%H%M%S")
```

7. **ANNOUNCE to the user** (MANDATORY, before any review). `{COUNT}` is the EXACT count from Mode Detection (never
   truncated); include any `WARN missing` lines:

```
PLAN — allure-server superreview
INPUT:  {USER_REQUEST, or "(empty)"}
MODE:   {MODE} — {which rule resolved it: whole-project request | explicit token | dirty tree | clean tree}
        Depth: {DEPTH} ({which signal resolved it: depth request in the prompt | speed request | default})
        {if QUICK: "intent + gates only, 1 agent — say 'deep review' to run the full expert fan-out"}
        {if EXTENDED: "full fan-out: domain experts + scope passes + validation, plus the intent pass"}
SCOPE:  Branch: {BRANCH}
        Scope: {commit range | branch-vs-main | folder | working-tree diff vs HEAD | full project}
        Files: {COUNT} ({displayed list — may be truncated; COUNT stays exact})
        Focus: {resolved focus — user directive with depth words stripped, else default ordering | n/a (QUICK)}
DO:     Gates: {gate name} {OK|FAIL|not run} / ...
        Intent pass: intent-guard {will run | NOT RUN — agent file missing}
        Scope baseline: task {T-ID} | none  /  issue {id} ({title}) | not reached | none  /  {K} of {COUNT} files outside the sanctioned surface  |  not resolved (QUICK)
        Experts (derived from live roster): {group -> agent, ...}{, DEGRADED: <group> -> generic} | none (QUICK)
        Preflight: {OK | "WARN missing: <files>"}
RESULT: one merged, validated report + chat summary
```

8. **READ** `references/java-kotlin.md` — Java 25 / Spring Boot (Gradle) stack guidelines (`EXTENDED` only; `QUICK` spawns no agent
   that uses it). Agents get its PATH via the prompt contract and read it themselves; never paste its content into
   a prompt. Same for `references/agent-prompt.md` and `references/scope.md`.
9. If `FILES` is empty (`COUNT == 0`) -> **exit** ("Nothing to deep-review for {MODE} / {SCOPE}.") — but still
   report any gate failure from step 4. This applies at BOTH depths: an empty change set has no delivery to
   compare against the request either.
10. If `COUNT > 50` (and `{MODE}` != `FULL_PROJECT`) -> AskUserQuestion: narrow the scope (per-folder / per-commit)
    or proceed anyway. **`EXTENDED` only** — `QUICK` costs one agent that reads NAMES and counts, so file volume
    does not scale it; never ask at `QUICK`. For `FULL_PROJECT`, proceed but warn it is a large, slower pass.
11. Optionally study context with read-only `Explore` agents — `EXTENDED` only, at MOST 2, and only for
    `FULL_PROJECT` or an unfamiliar area. Context only, NOT findings.

### Phase 1 — Agent selection (route changed files to domain owners)

> **`{DEPTH}` = `QUICK` -> SKIP this entire phase.** No domain expert, no scope pass, no general agent is selected
> or spawned. The selected set is exactly `intent-guard`, and Phase 2 spawns that ONE agent. Record
> `Experts: none (QUICK)`. Do NOT "just add one expert because the diff looks risky" — a risky diff is the reason
> to tell the user to re-run deep, not to silently upgrade the depth they chose.

**`EXTENDED` from here to the end of Phase 1.**

Group the `{FILE_LIST}` by path. **Enable ONLY the groups whose files actually changed** — this is the key
"fewer agents" constraint: ONE targeted spawn per relevant group, NOT a quorum × N + a separate full standards pass.

| Group | Path pattern | `subagent_type` |
|-------|--------------|-----------------|
| rest-api | `src/main/java/**/controller/**` | `rest-controller` |
| dto | `src/main/java/**/model/**`, `src/main/java/**/web/dto/**` | `dto-model` |
| report-lifecycle | `src/main/java/**/service/JpaReportService.java`, `src/main/java/**/service/*Report*.java` | `report-service` |
| result-intake | `src/main/java/**/service/ResultService.java`, `src/main/java/**/helper/MoveFileVisitor.java`, `**/PathUtil*.java` | `result-service` |
| generation | `src/main/java/**/helper/AllureReportGenerator.java`, `src/main/java/**/helper/plugin/AllureServerPlugin.java`, `**/helper/plugin/{Branding,CustomReportMeta,ExecutorCi}*.java`, `src/main/java/**/helper/{Util,ServeRedirectHelper}.java` | `generation-pipeline` |
| youtrack | `src/main/java/**/helper/plugin/YouTrackPlugin.java`, `src/main/java/**/helper/plugin/youtrack/**`, `src/main/java/**/api/youtrack/**`, `src/main/resources/static/openapi-youtrack.json` | `plugin-youtrack` |
| config-security | `src/main/java/**/properties/**`, `src/main/java/**/config/**`, `src/main/java/**/security/**`, `src/main/resources/application*.yaml` | `config-security` |
| persistence | `src/main/java/**/entity/**`, `src/main/java/**/repo/**`, `migration.sql` | `persistence-jpa` |
| web-ui | `src/main/java/**/web/**`, `src/main/jte/**`, `src/main/frontend/input.css`, `tailwind.config.js`, `src/main/resources/static/**` (except `openapi-youtrack.json`; `static/css/app.css` is GENERATED — never a hand-edit finding) | `web-ui` |
| report-branding | `src/main/resources/brew-brand/**`, `src/main/resources/plugins/**` (assets injected INTO generated Allure reports, not the server UI) | `generation-pipeline` (colors move in lockstep with `.claude/rules/frontend-design.md`) |
| build-ci | `build.gradle`, `gradle/*.gradle`, `gradle.properties`, `settings.gradle`, `Dockerfile`, `docker-compose*.yml`, `.github/**` | `build-ci-qa` |
| tests | `src/test/**` | `build-ci-qa` (adds the TEST-BLOAT audit below; cite `test-avoid` / `test-best-practice` / `testing`) |
| docs | `*.md`, `README*`, `.claude/convention/**`, `.claude/rules/**` (only when git-TRACKED and in `FILES`) | `general-purpose` (docs-vs-code truthfulness only) |

> **Splitting rule.** A group whose changed files exceed ~5 files or clearly split into two deliverables MUST be
> spawned as TWO tasks to the SAME agent with disjoint file lists (e.g. `web-ui: Java controllers` +
> `web-ui: JTE templates + CSS`) — never one agent owning half the diff.
> `Application.java` and anything not matched above -> the group whose owner is nearest by package; no nearest
> owner -> `Explore` + DEGRADED marker. `.claude/features/**` is baseline INPUT, never a review group.

> **Total spawns, by depth:**
> `QUICK` = **1** — `intent-guard`, and nothing else.
> `EXTENDED` = `intent-guard` (always) + (NON-EMPTY domain groups) + the TWO scope passes (A diff-side,
> B baseline-side; A alone in `FULL_PROJECT`) + **{0, 1, or 2} general agents (model's call)**.
> `intent-guard` is NOT one of the {0,1,2} general agents and is never traded against them.
> Examples: a tiny single-file tweak = its domain owner + the scope passes; a change with non-trivial logic =
> domain owner + `general-purpose`; a change spanning multiple domains = both domain owners + `general-purpose`.
> A SMALL, single-domain, low-risk change MAY skip the general passes — **never a scope pass**: a small change is
> exactly where an unnoticed foreign-surface edit hides, and a one-file diff is exactly where an undelivered
> criterion hides. If a mapped domain agent is unavailable, fall back to built-in `Explore`; note it in the report.

> **Test-bloat audit:** when the `tests` group is non-empty, its prompt MUST also audit for TEST OVER-PROLIFERATION
> (LLMs over-write tests) — cite the project `testing` rule, do NOT restate it. Flag, as `category: test-quality`:
> redundant tests to DELETE (duplicate coverage, trivial getters, internal-mock-only tests); tests to COLLAPSE/MERGE
> or PARAMETRIZE via HELPER FUNCTIONS; over-granular micro-tests violating "FEW targeted scenario tests over BIG user
> journeys". **NON-NEGOTIABLE:** reducing test COUNT must NOT cost quality — every remaining/merged test stays
> ISOLATED + FAST + REAL (fakes-over-mocks). Also flag any slow or non-isolated test as its own finding; never
> recommend a merge that makes a test slow or non-isolated. Full prompt text in `references/agent-prompt.md`.

### Phase 2 — ONE parallel fan-out (find candidates)

Everything selected is spawned in **ONE message**. At `QUICK` that message contains exactly ONE `Task` — the
INTENT pass below — and nothing else in this phase applies. At `EXTENDED` the intent pass rides in the SAME
message as the domain owners, the two scope passes and any general agent.

#### The INTENT pass — `intent-guard` (BOTH depths, unconditional)

Its own file holds the procedure, the evidence budget, the tier model, the drift classes and the output shape —
the brief below passes CONTEXT ONLY and deliberately restates none of it. Skip this spawn only when
`.claude/agents/intent-guard.md` is missing (Phase 0 preflight said so); then record the intent pass as
`not run` everywhere and never let the run read as clean.

```
Task(subagent_type="intent-guard", prompt="
## superreview — INTENT pass: asked vs delivered (allure-server)

GOAL: one anti-drift check over this change set — is the DELIVERED work the work that was ASKED for?
ROLE: exactly the role your own agent file defines. Follow it verbatim: tier collection, the <=15 tool-call
evidence budget, the drift classes, the <=10-finding cap, the verdict line. Nothing is restated here, so READ
YOUR FILE and do not improvise a different job. Do NOT review code quality, correctness, framework usage,
security or style — other passes own those (at QUICK depth they simply do not run, and that is deliberate:
you are not their stand-in).
SCOPE: this run only — {MODE} / {SCOPE}, depth {DEPTH}. Read-only; edit nothing.

**The request, VERBATIM (tier 5, and tier 1 when no external source exists):**
{USER_REQUEST}
**Files changed ({COUNT}):** {FILE_LIST}
**Diff range:** {SCOPE}  (empty for FULL_PROJECT / UNCOMMITTED)
**Mechanical gate results (already run — never re-run a build or a test):** {GATE_RESULTS}
**Sanctioned baseline resolved by this skill:** {SCOPE_BASELINE}
  (`not resolved (QUICK)` means nobody pre-resolved it — resolve your own tiers from the locations your file
   names, and record any tier you cannot reach as absent. Never invent one.)
**Review corpus rule:** git-IGNORED is OUT. The instruction tree (`.claude/**`, `CLAUDE.md`) is AUTHORITY you
CITE, never a drift target; the task board is a BASELINE input, never a subject.

CONTEXT: Phase 0 resolved the mode, the depth and the gates and announced them. At QUICK you are the ONLY agent
in this run. At EXTENDED domain owners and two scope passes run beside you right now — they own code quality and
sanctioned-surface accounting; do not duplicate them, and do not soften your verdict because they exist.
CONSUMER: the coordinator puts your verdict line at the TOP of the chat summary and of the report's Intent
section, and merges your JSON rows into the finding table. At EXTENDED a validator RANKS your rows but does not
re-litigate them, so your evidence has to stand on its own.
DONE: the verdict line first, then the numbered findings, then the merge-contract JSON object and nothing after
it. ONE override on the JSON in your file, for this project: add \"verdict\": \"CONFIRMED-BY-EVIDENCE\" to every
row. The `intent` category and the `intent#<class>` rule come from your own file and are RESERVED for you —
keep them exactly as written there.
A row without a quoted ASKED, a named SOURCE tier and a concrete DELIVERED path/count/command-output is NOT
self-evidenced: DROP it rather than ship it. Empty findings + `VERDICT: ALIGNED` is a good result.
")
```

Its rows enter `{CANDIDATES}` with `source: intent-guard` and verdict `CONFIRMED-BY-EVIDENCE`.

#### The rest of the fan-out — `EXTENDED` ONLY

Spawn ALL selected agents (the non-empty domain owners + the two scope passes + whichever general agents the model
chose in Phase 1) in the SAME message as the intent pass. Use the prompt contract in `references/agent-prompt.md`
VERBATIM — it already carries the focus ordering, the security-only-if-P0 rule, the over-complexity dimension, the
rule-citation requirement and the output JSON, so restate NONE of them here. Substitute EVERY placeholder it
contains, or the literal brace text reaches the agent: `{FILE_LIST}` (scoped to that group for domain owners; full
set for any general agent), the resolved `{FOCUS}`, `{GATE_RESULTS}`, `{SCOPE_BASELINE}` and `{OWNERSHIP}` (from
Phase 0 step 5; `UNKNOWN` / `none` when unresolved), and the `references/java-kotlin.md` guidelines.

> The finding JSON ("Output JSON ONLY"), the severity guide and the RESERVED rule namespaces (`intent#<class>`
> for `intent-guard`, `scope#S<n>` for nobody) live ONCE in `references/agent-prompt.md` — every agent, including
> both scope passes, returns exactly that shape. Never re-type it here.

**PLUS TWO DEDICATED SCOPE passes** (spawn BOTH in the SAME message as the rest of the fan-out). They are split by
EVIDENCE SOURCE, not by axis: **A walks the DIFF inward** against the sanctioned surface, **B reasons from the
BASELINE outward** and must SEARCH the corpus to settle an absence. Their rule-id spaces are disjoint — A emits
`scope#<shape>` only, B emits `scope#D*` / `scope#C*` only — so neither can produce the other's finding. Neither is
one of the {0,1,2} general passes. `{MODE}` = `FULL_PROJECT` -> run **pass A only**, restricted to shapes 3/4 and
reported as INFORMATIONAL; **skip pass B** (no single task, so no criteria and no PR to close out).

**Pass A — diff side.** Owner: `task-tracker` (the agent that owns the task board / tracker read path; fall back
to `Explore`):

```
Task(subagent_type="task-tracker", prompt="
## superreview — SCOPE pass A: DIFF SIDE (shapes 1-6, single axis)

READ-ONLY run: audit ONLY the scope + blast radius OF THE CHANGED FILES. Do NOT edit the board, do NOT touch any
file, do NOT create, edit or close an issue. Other agents own correctness, architecture and style; scope pass B
owns delivery (scope#D*) and closeout (scope#C*) — do NOT score those and do NOT judge whether a criterion was
delivered.

READ (path only): .claude/skills/superreview/references/scope.md — section 2 (ownership map), section 3 (the
6-shape taxonomy + severity map), section 4 (the binding NOT-creep exclusion list). Apply them verbatim.

**Sanctioned baseline (already resolved in Phase 0 — re-verify anything you doubt, never widen it):**
{SCOPE_BASELINE}
**Ownership signals:** {OWNERSHIP}
**Files changed:** {FILE_LIST}
**Diff range:** {SCOPE}

Per file: (1) is it inside the sanctioned surface implied by the acceptance criteria? (2) if not, is there a
RECORDED decision sanctioning it (task notes / issue comment / docs decision log)? cite it; (3) if not, classify
by shape 1-6 and score per the severity map; (4) name WHO ELSE is hit — other owner or shared surface.
OVERLAP: a file owned by a neighbouring task is legitimate when the sanctioned deliverable cannot be correct
without it AND the boundary is recorded (section 4) — unrecorded but correctness-driven overlap is shape 6, NEVER
a shape-1 blocker.

An unsanctioned edit to a shared surface is a P0, not a nitpick. But do not manufacture findings: the exclusion
list in section 4 is binding, and a missing baseline caps you at P2.

Report in the standard finding JSON, category \"scope-creep\", rule \"scope#<shape-number>\" ONLY. In every
\"description\" state: the baseline source you checked against (task id / issue id / decision id / UNKNOWN), the
shape id, and the owner or shared surface impacted. \"suggestion\" = split it out / revert it / record the decision.
")
```

**Pass B — baseline side.** Owner: `Explore`. Its core job is proving or disproving an ABSENCE across the
whole corpus — a search problem, so a read-only searcher (built-in `Explore`) is a legitimate owner here. Delivery
and closeout ride together: both reason from the baseline outward and share ONE tracker context, `{PR_ISSUE_JSON}`:

```
Task(subagent_type="Explore", prompt="
## superreview — SCOPE pass B: BASELINE SIDE (delivery scope#D*, closeout scope#C*)

READ-ONLY run: audit ONLY whether the sanctioned scope was fully DELIVERED and correctly CLOSED OUT. Do not edit
any file, do not touch the board, do not create, edit or close an issue, and do NOT re-fetch tracker data — the
issue + PR data you need is pasted below. Scope pass A owns the changed-file shapes 1-6 — do NOT score those.

You SEARCH the codebase; you do not read a diff. A criterion may be satisfied by code this change never touched,
and a criterion delivered under a DIFFERENT NAME reads exactly like absence. Use `rg` / `grep` / `git ls-files`
across the source tree AND the tests, by CONCEPT and by SYNONYM, before claiming anything is missing. Proving or
disproving an absence IS the job, not a side task.

READ (path only): .claude/skills/superreview/references/scope.md — section 3b (the DELIVERY map D1-D5 plus the
mandatory PROOF OF ABSENCE rules) and section 4b (the CLOSEOUT map C1-C4). Apply them verbatim.

**Sanctioned baseline (already resolved in Phase 0 — re-verify anything you doubt, never widen it):**
{SCOPE_BASELINE}
**Issue + PR data (fetched in Phase 0 — this is your whole tracker context; never re-fetch it):**
{PR_ISSUE_JSON}
**Files changed:** {FILE_LIST}
**Diff range:** {SCOPE}

DELIVERY (section 3b): score every acceptance criterion / issue done-when D1-D5 — full scope must be DELIVERED,
and a reduction is clean ONLY when its blocker is recorded and named (then report it as an accepted reduction,
not a finding).
SCOPE IDS — ONLY if the baseline above carries a `## Scope` table (`id | block | in/out | status`): walk EVERY `in`
id and score the claimed `status` against the corpus, per the status table in section 3b — `done` with nothing
implementing it is D1/D2 (the proof rules below still bind: a `done` claim is not evidence), an implemented block
still marked `not-started`/`in-progress` is D5 (stale record, P3), an honestly unfinished id is NOT a finding, and
an implemented `out` id belongs to pass A as shape 2 — mention it, do not score it. Cite each as `<TASK-ID>#S<n>`;
`scope#S*` is RESERVED, never a rule id. ANCHOR a D5 on the IMPLEMENTING code — the hunk that proves the block
landed; `file`/`lineStart` may NEVER be the board file (INPUT only, never a finding location), and the stale row
lives in the finding TEXT. A `done` id with nothing implementing it has no such anchor: that is D1/D2 under PROOF
OF ABSENCE, not D5. **No `## Scope` table in the baseline -> skip this paragraph silently: no tally, no
note, no finding.** Report the id tally (done-as-claimed / total `in`, plus how many `in` ids are legitimately
unfinished — those are NOT findings) for the report's scope section.
D1 and D4 are ABSENCE claims. Every scope#D* finding MUST carry, in \"description\": (a) the criterion QUOTED
VERBATIM from the task/issue, and (b) the exact search command you ran and its empty result. No quote or no cited
search = you may not claim P0: report it at P2 with \"deliveryProofMissing\": true, for the Phase 3b gate.
CLOSEOUT (section 4b) — ONLY if the data above contains a PR, else record \"PR: none\" and skip it: PR body
(shipped / deliberately NOT built / what consumers may rely on), the Closes-vs-Refs split, a closeout comment on
every issue touched, AI attribution anywhere, and the short-and-clear bar in BOTH directions.

Do not manufacture findings: a missing baseline caps you at P2, and an unquoted, unsearched absence is never P0.

Report in the standard finding JSON, category \"scope-creep\", rule \"scope#D<n>\" | \"scope#C<n>\" ONLY.
\"suggestion\" = deliver the criterion / record the blocker / fix the PR body or the Closes-Refs split / add the
closeout comment.
")
```

Each agent MUST search-first (Bash `grep`/`rg` + verify imports) before flagging any
reuse/duplicate, and read the ACTUAL code at every cited line. Collect every agent's findings into one pool
`{CANDIDATES}` (tag each finding with its producing agent as `source`). Gate failures from Phase 0 step 4 enter the
pool with `source: gate` and verdict `CONFIRMED-BY-EXECUTION`, citing the command + output line: they carry their
verdict already, so Phase 3 only RANKS them. **Intent rows enter the same way** — `source: intent-guard`, verdict
`CONFIRMED-BY-EVIDENCE`, carrying their own quoted ASKED + tier + DELIVERED evidence, so Phase 3 only RANKS them
too and MAY NOT re-litigate or reject them. Every OTHER candidate enters verdictless and may reach the report only
via Phase 3.

### Phase 3 — VALIDATION (per-finding adversarial reverse-check, the GATE)

> **`{DEPTH}` = `QUICK` -> SKIP this phase entirely.** The pool contains only rows that already carry their own
> verdict — `CONFIRMED-BY-EXECUTION` (gates) and `CONFIRMED-BY-EVIDENCE` (intent) — so there is nothing to
> adversarially validate and NO validator is spawned. Do the mechanical part yourself instead, in-session, over
> the pool: step 2 (merge + de-duplicate), step 3 (map severity -> P0-P3 by the SAME priority map below) and
> step 4 (consistency check). Record the result as `{MERGED}` and the run as validated — a QUICK run is NOT
> `INCOMPLETE` on validation grounds, because nothing in it was left unvalidated. The one exception is the
> intent pass itself failing to run (missing agent, failure, timeout): its only producer is gone, so that run
> IS `INCOMPLETE` — see Error Handling.

**`EXTENDED` from here to the end of Phase 3b.**

If `{CANDIDATES}` is EMPTY, skip Phase 3 — nothing needs a verdict (a pool of gate + intent rows alone already
carries its verdicts, so it skips straight to ranking); verdict APPROVED only under the Error-Handling
conditions below. Otherwise spawn ONE validator that **independently RE-VERIFIES EVERY candidate finding in
reverse** against the real code BEFORE anything reaches the user report, AND merges the survivors into one
consistent, de-duplicated, prioritized list. Per-finding gate, NOT a sample.

**Validator selection — walk this chain and take the FIRST agent that does NOT own a group the pool came from:**
`general-purpose` -> `general-purpose` -> another domain expert with no findings in the pool -> built-in
`general-purpose` -> built-in `Explore`. **`intent-guard` is never in this chain** — it owns rows in the pool and
its job is not code-reading validation.

| Rule | Detail |
|------|--------|
| Disqualifier | ownership ONLY — an agent may not validate findings from the group IT reviewed in Phase 2. Merely having produced SOME candidate disqualifies nothing: on a multi-domain branch every expert has, which would drop every run to a generic agent exactly when the review is biggest |
| Mixed pool | one validator whose own group is a MINORITY of the pool is fine — tell it, in the prompt, which findings are its own and to hold those to a stricter bar. Prefer a validator with zero findings in the pool when one exists |
| Generic fallback | genuine LAST resort: a generic agent weakens the adversarial gate — use it only when every expert owns part of the pool, and note the downgrade in the report |

**Batching (>~40 candidates):** split into batches of **<=40 candidates, max 4 spawns** — NOT per-group (a full
sweep has many groups, and step 2's "merge and de-duplicate ACROSS agents" is impossible inside a single-group
batch). Batch by descending severity so the worst findings are validated first. Each batch runs steps 1 and 3-4 on
its own slice; **run step 2 (merge + de-duplicate) ONCE, in a final pass over the union of all batch outputs**.
Sum `stats` from the final pass, never from the batches.

```
Task(subagent_type="general-purpose", prompt="
## superreview — per-finding VALIDATION + Merge (allure-server)

GOAL: produce ONE merged, de-duplicated, priority-sorted report for {MODE} / {SCOPE} that a human can act on
without re-checking it. Candidates come from parallel domain owners who each saw only their own file group, so
some are already fixed, misread, out of scope, or the same issue reported twice.
ROLE: you are the adversarial validator + final arbiter. For EVERY candidate finding (no sampling, no skipping),
READ the ACTUAL code at the cited file:line and try to DISPROVE it. Decide CONFIRM or REJECT per finding. Only
CONFIRMed findings may appear in the final report. Then merge the survivors. Do NOT edit code, do NOT review
files outside the set below, do NOT invent findings — the one exception is a miss you deliberately RESTORE.
SCOPE: in — the candidate pool + the cited code + the files under review, listed below. Out — applying fixes,
low/medium security, prose outside the JSON.

**READ (paths only — read them yourself, nothing is pasted here):**
  .claude/skills/superreview/references/java-kotlin.md      — stack facts + per-stack checks.
  .claude/skills/superreview/references/agent-prompt.md  — the SAME focus ordering the Phase 2 agents were given;
    apply it verbatim as your effort ordering and tie-break.
  .claude/skills/superreview/references/scope.md         — baseline precedence, the 6-shape taxonomy + severity map,
    the delivery/closeout maps and the NOT-creep exclusion list, for validating every 'scope-creep' candidate.

**Your own Phase 2 findings, if any:** {OWN_FINDING_IDS | none}. Hold those to a STRICTER bar than the rest —
argue against them twice, and REJECT on any doubt. You may not skip them, and you may not wave them through.

**Sanctioned scope baseline (resolved in Phase 0 — the yardstick for every scope-creep candidate):**
{SCOPE_BASELINE}
**Ownership signals:** {OWNERSHIP}
**Mechanical gate results (verdict CONFIRMED-BY-EXECUTION — never re-validate, never reject):** {GATE_RESULTS}
Findings with source 'gate' already carry their verdict, issued by the run itself; pass them through untouched and
only RANK them. No other source may use that verdict — everything else you output is \"CONFIRM\" or it does not ship.

**Intent findings (source 'intent-guard', category 'intent', verdict CONFIRMED-BY-EVIDENCE):** same treatment as
gate rows and for the same reason — they carry their own evidence (a verbatim ASKED quote + its source tier + the
delivered path/count). PASS THEM THROUGH UNTOUCHED: do not re-verify them, do not reject them, do not rewrite their
description, do not count them against your batch budget. You RANK them and you may de-duplicate a scope-creep row
that says the same thing (keep the intent row, union the sources). You may NEVER promote another finding to
CONFIRMED-BY-EVIDENCE, and you may never author an intent row yourself.

**Candidate findings (pool from the domain owners + the scope passes + any included general agents):**
{CANDIDATES}
**Files under review:** {FILE_LIST}
**Focus:** {FOCUS}

CONTEXT: Phase 0 resolved the mode/scope and announced it; Phase 2 already ran the targeted fan-out and each
candidate is tagged with its producing agent as `source`. You are the GATE — nothing has been shown to the user
yet, and no other agent runs after you except the report writer.
CONSUMER: Phase 4 writes your JSON straight into `.claude/reports/{TIMESTAMP}_superreview/REPORT.md` using
`references/report-template.md`, and Phase 5 prints your `stats` + `verdict` in the chat summary. Any text
outside the JSON object breaks both; a finding without file + lineStart/lineEnd cannot be rendered.
DONE: JSON only, in the schema below — findings ORDERED P0 -> P3, every row with file:line + an actionable
suggestion, plus `dropped`, `verdict` and `stats` filled.

**Focus ordering (effort + tie-breaks, highest first):** {FOCUS}
  Security = report ONLY if CRITICAL (P0); ignore low/medium.
  Also validate OVER-COMPLEXITY findings (category over-complexity): speculative abstraction, gold-plating,
  premature generalization, KISS/YAGNI-removable indirection, collapsible duplication. Keep them (with the simpler
  shape) when real; drop if the complexity is justified.

### 1. Reverse-validate EACH candidate (per finding — drop false positives)
  a. Existence — does the cited code/line actually exist and exhibit the issue NOW? (REJECT if not / already fixed)
  b. Accuracy — is the claim a correct reading of the code? (REJECT if it misreads the code)
  c. Actionability — is there a concrete fix path? (REJECT if vague / not actionable)
  d. Severity — right for the focus ordering? SCALE CALIBRATION: harm reachable only under concurrency/load this
     system does not have -> P3 or REJECT; a race claim with no stated traffic assumption -> REJECT.
  e. Rule truth — does the cited rule actually say that, and does its scope cover the cited file? REJECT on scope
     mismatch or an unverified framework-shape claim (verify against the installed dependency, not memory).
  Adversarial: actively argue AGAINST each finding first. It survives ONLY if it withstands a-e. Do this for every
  single candidate; none is reported unverified.
  Over-complexity: keep with the simpler-shape suggestion when real, drop if the complexity is justified.
  Scope-creep: REJECT when the file is implied by the acceptance criteria, required to make the sanctioned change
  compile/pass the gates, covered by a RECORDED decision (cited or found), board bookkeeping for THIS task, or on
  the scope.md section-4 exclusion list. CONFIRM only with the baseline source named. Baseline UNKNOWN -> cap at P2
  and set \"sanctionUnknown\": true — PERMANENT for this run; Phase 3b may lower further but never restores a
  pre-cap P0/P1 while UNKNOWN. REJECT a shape-1 claim on a neighbour's file when the overlap was correctness-driven
  AND recorded; demote to shape 6 when only the recording is missing.
  DELIVERY (scope#D*): D1/D4 are ABSENCE claims, the easiest false P0 here. CONFIRM at P0 only when the finding
  QUOTES the criterion verbatim AND cites the search that proved absence — then RE-RUN that search yourself plus
  one synonym/concept variant across source AND tests; any hit -> REJECT. No quote or no cited search -> cap at P2,
  set \"deliveryProofMissing\": true, route to Phase 3b. D5 is the opposite claim — a PRESENCE: CONFIRM only after
  reading the code the row cites, REJECT if that code does not implement the id, and never rank it above P2.
  CLOSEOUT (scope#C*) — void when no PR exists.
  You MAY ADD a finding of your own ONLY for a P0-grade issue you directly observe (source: \"validator\") — never
  lower-priority. Run the SAME a-e pass on your own addition, quote the proof, set \"selfValidated\": true.

### 2. Merge + de-duplicate ACROSS agents
  Same file +/-5 lines + same category = ONE finding. Keep the most detailed description, highest severity,
  union of sources (comma-join). A 'duplicate/reuse' miss + an 'architecture' flag on the same code = one row.

### 3. Prioritize (MANDATORY P0 -> P3)
  - P0 = architecture/boundary BLOCKERS + hard-rule bypasses + CRITICAL security (logged secret, missing auth,
         injection) + any miss you RESTORE + UNSANCTIONED scope shape 1 (foreign surface / shared contract /
         another owner's files) + an undelivered acceptance criterion (scope#D1) + an unrecorded scope reduction
         (scope#D4). D1/D4 reach P0 ONLY with the section-3b proof; `deliveryProofMissing` or `sanctionUnknown`
         caps the row at P2 per step 1, and that cap wins over this map.
  - P1 = confirmed functional-correctness + architecture/boundary issues + other gate failures + scope shapes 2
         and 5 + a partially delivered criterion (scope#D2) + a misleading closeout artefact (scope#C2).
  - P2 = reuse misses/duplication + over-complexity + version-pin violations + scope shapes 3, 4, 6, scope#D3, the
         remaining scope#C* rows, and any scope finding with baseline UNKNOWN + test-quality issues rated major+.
  - P3 = business-requirements nits + minor over-complexity + warnings + style + minor/info + a stale scope-id
         status (scope#D5) — P2 only when that row is the task's only delivery record.
  - INTENT rows (category 'intent') rank by the severity intent-guard already assigned — blocker -> P0,
    critical -> P1, major -> P2, minor -> P3 — and no baseline cap applies to them: intent-guard resolved and
    named its own source tier, so its evidence IS the yardstick. Never re-score an intent row downward because
    the scope baseline came back UNKNOWN; those are two different yardsticks.

### 4. Consistency check on the merged list
  No duplicate rows, severities monotonic with priority, every row has file:line + actionable suggestion,
  findings ORDERED P0 -> P3.

**Output JSON ONLY:**
{
  \"findings\": [{
    \"id\": \"P0-1\", \"priority\": \"P0|P1|P2|P3\", \"source\": \"agent(s)|gate|intent-guard|validator\",
    \"file\": \"path\", \"lineStart\": 42, \"lineEnd\": 45,
    \"category\": \"boundary|architecture|scope-creep|intent|reuse|over-complexity|security|logic|persistence|test-quality|pins|style\",
    \"severity\": \"blocker|critical|major|minor\",
    \"rule\": \"avoid#N|architecture#N|scope#1|scope#D1|scope#C2|intent#scope|intent#tests|... or null\",
    \"title\": \"...\", \"description\": \"...\", \"suggestion\": \"...\",
    \"existing\": \"path|null\", \"reuse\": \"REUSE|EXTEND|CONSIDER|KEEP_NEW|null\",
    \"scopeShape\": 1, \"sanctionUnknown\": false, \"deliveryProofMissing\": false,
    \"impactedOwner\": \"task/person/shared surface|null\",
    \"verdict\": \"CONFIRM|CONFIRMED-BY-EXECUTION|CONFIRMED-BY-EVIDENCE|UNVALIDATED\", \"selfValidated\": false, \"confidence\": 0.9
  }],
  \"dropped\": [{\"title\": \"...\", \"reason\": \"already-fixed|false-positive|not-actionable|unverified-rule|in-sanctioned-scope|duplicate-of:P0-1\"}],
  \"verdict\": \"APPROVED|CONDITIONAL|REWORK\",
  \"intentVerdict\": \"ALIGNED|MINOR DRIFT|MAJOR DRIFT|not run — <one clause of why, copied VERBATIM from intent-guard>\",
  \"stats\": {\"p0\": 0, \"p1\": 0, \"p2\": 0, \"p3\": 0, \"scopeCreep\": 0, \"intent\": 0, \"overComplexity\": 0, \"candidates\": 0, \"confirmed\": 0, \"confirmedByExecution\": 0, \"confirmedByEvidence\": 0, \"unvalidated\": 0, \"dropped\": 0}
}
(scopeShape / sanctionUnknown / deliveryProofMissing / impactedOwner: only on 'scope-creep', else null/false.)
Every row MUST carry a \"verdict\". \"candidates\" = confirmed + confirmedByExecution + confirmedByEvidence +
unvalidated + dropped; if that does not balance, a finding went missing — fix it before returning.
\"intentVerdict\" is COPIED from intent-guard's verdict line, never re-derived by you and never softened.

### Verdict rule
- REWORK if any P0; CONDITIONAL if any P1/P2 (no P0); APPROVED if only P3 / none.
- Any row with verdict UNVALIDATED -> append `- INCOMPLETE` to the verdict, whatever it is. An INCOMPLETE run may
  never read as APPROVED without qualification.
")
```

Record output as `{MERGED}`. If the validator fails or is unavailable: retry once, then run the SAME prompt on the
NEXT agent in the selection chain; if the whole chain is exhausted, ship the report with candidates explicitly
marked `"verdict": "UNVALIDATED"` per row (never `CONFIRM`, never quietly dropped), the run marked **INCOMPLETE**,
and the unvalidated COUNT carried into the report header, the Stats table and the chat summary. Gate rows keep
`CONFIRMED-BY-EXECUTION` regardless — execution still happened.

### Phase 3b — SCOPE GATE (AskUserQuestion; after validation, before the report)

> **`{DEPTH}` = `QUICK` -> SKIP.** The gate only ever fires on `scope-creep` rows, and QUICK runs no scope pass,
> so the pool contains none. Intent rows NEVER enter this gate at either depth: they are not scope-sanction
> questions, they carry their own named source tier, and asking the user to sanction a drift they just reported
> would be asking them to overrule their own request.

Scan `{MERGED}` for CONFIRMED `scope-creep` findings of shape **1, 2 or 5** carrying `sanctionUnknown: true` or no
cited decision, PLUS any `scope#D1` / `scope#D4` row carrying `deliveryProofMissing: true`. None -> skip silently.

Otherwise ask the user — the full rule (batching, option wording, non-interactive fallback, recording duty) is
`references/scope.md` section 5; do not restate it, apply it. In short: ONE `AskUserQuestion` call, <=4 questions,
each naming the files + the surface + who else is impacted + the baseline checked, recommended option first. The
gate REWRITES existing validated rows only — it may never ADD a finding; an answer that reveals a new issue is
material for the NEXT run. Then rewrite the affected rows in `{MERGED}`:

| Answer | Effect on the finding |
|--------|------------------------|
| Sanctioned — decision exists | demote to **P3**, retitle `record the decision`, keep the row |
| Not sanctioned | restore the mapped priority (P0/P1) **only if the baseline was KNOWN**; an UNKNOWN baseline keeps the P2 cap, with the answer recorded |
| Intentional — accept for now | **P2**, tagged `accepted-scope` |
| Gate not available (non-interactive) | keep the priority the finding ENTERED the gate with, tag `unconfirmed-sanction`. Never silently downgrade, never silently upgrade past a cap |

> A `deliveryProofMissing` D1/D4 answered `Not sanctioned` (the criterion really is undelivered) returns to **P0** —
> the user's answer IS the missing proof; unanswered, it stays P2.

Re-derive `stats` and the VERDICT after the rewrite (a demotion can flip REWORK -> CONDITIONAL; an `UNVALIDATED`
row keeps its `- INCOMPLETE` suffix through any rewrite). Verdicts are NOT rewritten here — this gate moves
priorities, never validation status. Record every question + answer VERBATIM in the report's Scope section.

### Phase 4 — Write the merged report

Shell variables do NOT survive between Bash tool calls, so anchor the report at the repo root and echo the literal
values you then substitute (never re-run `date` — two calls would disagree):

```bash
ROOT=$(git rev-parse --show-toplevel) || exit 1
REPORT_DIR="${ROOT}/.claude/reports/${TIMESTAMP}_superreview"
if mkdir -p "${REPORT_DIR}"; then echo "REPORT_DIR=${REPORT_DIR}"; else echo "MKDIR FAIL"; fi
```

Write ONE consolidated report to `${REPORT_DIR}/REPORT.md` using the layout in `references/report-template.md`.
Every table is a VIEW over `{MERGED}` — never a paste of raw Phase 2 candidates. Findings are MANDATORY-sorted by
priority P0 -> P3, every row carries its verdict, and the Scope section records the Phase 3b questions + answers.

The header carries `{DEPTH}`, and the **Intent / Drift** section carries `{INTENT_VERDICT}` VERBATIM as
intent-guard wrote it (`ALIGNED` / `MINOR DRIFT` / `MAJOR DRIFT` + its one clause, or `not run` + why). At `QUICK`
the sections that had no producer — Boundary & Architecture, Reuse, Over-Complexity, Scope Discipline, Dropped in
Validation — are written as `not run (QUICK depth)` rather than as empty tables: an empty table reads as "checked,
nothing found", which would be a lie.

### Phase 4b — SELF-SYNC (`EXTENDED` only, COORDINATOR only, BEFORE the Phase 5 summary)

This skill was generated against a snapshot; this run already computed what that snapshot got wrong. Correct THIS
file and `references/scope.md` from data ALREADY in context — no new spawn, no new read except the `wc -l` below.
It runs AFTER Phase 4's report is written and BEFORE Phase 5 prints, so the summary's `Self-sync:` line reports a
result that already happened.

**SKIP entirely at `QUICK`** — one agent, no live roster, no baseline: rewriting those tables from absence would
replace real data with nothing. **Never inside a spawned agent** — parallel agents editing one SKILL.md collide.
Code, tests, docs and the report stay READ-ONLY here as everywhere: the ONLY writable paths are these two files.

| Step | Do it when | Edit |
|------|-----------|------|
| Roster refresh | the Phase 1 LIVE roster differs from the emitted tables | rewrite the group -> agent rows: add the new agent, mark a vanished one `MISSING -> Explore` |
| **Gate repair** | a Phase 0 step 4 gate reported `not run` **AND** a `command -v <bin>` re-test proves the binary is ABSENT. `not run` with the binary present is a toolchain/deps failure, NOT a dead gate — leave that row untouched | fix that row of the gate block to the real command, else annotate it `not available in this project`. Nothing else ever clears a dead gate — it reports `not run` forever |
| Scope baseline | step 5 resolved `UNKNOWN`, or the tracker actually found disagrees with `.claude/features board (canonical, file-based Kanban) + GitHub issues (read-only)` | correct `references/scope.md` section 1 to what was really resolvable |
| Shared surface | a scope pass A finding named a surface absent from `references/scope.md` section 2 | append that row — the finding IS the evidence |

> **Non-growth — MEASURED, never asserted.** Run `wc -l` on BOTH files before the first Edit and again after the
> last one, and print `before -> after (delta)` in the Phase 5 `Self-sync:` line. The total delta MUST be `<= 0`:
> delete the stale line before adding the true one, correct in place, never append a second version. A measured
> positive delta means REVERT the additions you cannot pay for and print them as proposals instead — an unmeasured
> "delta <= 0" claim is not a measurement.

> **CARVE-OUT.** FACTS may be corrected. DECISIONS may NOT be rewritten without explicit user instruction: depth
> semantics, the corpus rule, the permanent UNKNOWN-baseline P2 cap, focus ordering, the read-only invariant.

**Never auto-written — PROPOSE in the Phase 5 summary instead:**
- **DEGRADED groups / missing experts** -> print `DEGRADED: {groups}` + "re-run `/brewcode:superreview-setup`". Creating
  an expert is an `agent-creator` spawn that would change this review's own tooling mid-run.
- **New drift examples for `.claude/agents/intent-guard.md`** -> print the proposed row only. That file is
  byte-untouchable by contract, and an append per run grows it monotonically at every future review's expense.

### Phase 5 — Chat summary

```
Super Review complete — MODE={MODE} / DEPTH={DEPTH} (branch {BRANCH}), {COUNT} files, {N} agents.

DRIFT: {ALIGNED | MINOR DRIFT | MAJOR DRIFT} — {intent-guard's clause, verbatim} | intent pass NOT RUN ({reason})
       Sources: T1 {label|none} / T2 {…} / T3 {…} / T4 {…} / T5 transcript
       {N} intent finding(s): {intent#class - one-line title, highest drift first, max 3 shown}

VERDICT: {APPROVED | CONDITIONAL | REWORK}{ - INCOMPLETE if anything went unvalidated}
Gates: {gate} {OK|FAIL|not run} / ...
Experts (live roster): {group -> agent, ...}{, DEGRADED: ...} | none (QUICK)
Validation: {all {N} findings validated | {U} of {N} UNVALIDATED ({reason}) — this run is INCOMPLETE}
            | not needed (QUICK — every row carries CONFIRMED-BY-EXECUTION or CONFIRMED-BY-EVIDENCE)

Scope: task {T-ID|none} / issue {id|none|not reached}; {K}/{COUNT} files outside the sanctioned surface;
       delivery {D} undelivered/partial; gate {not triggered|answered|unavailable}  | not run (QUICK)

Priority breakdown (sorted P0 -> P3){ at QUICK: only intent + gate rows can appear here}:
- P0 (architecture blockers + CRITICAL security + unsanctioned foreign-surface scope + undelivered criterion): {N}
- P1 (confirmed correctness + architecture/boundary + unsanctioned feature/doc scope + gate failures): {N}
- P2 (reuse misses + over-complexity + drive-by scope + version-pin errors): {N}
- P3 (business-requirements nits + warnings + style): {N}
Scope-creep findings: {SC} | intent findings: {N} | over-complexity findings: {OC}

Per-finding reverse-validation dropped {N} candidates (false-positive/already-fixed/duplicate). | not run (QUICK)
Gate findings carry verdict CONFIRMED-BY-EXECUTION — validated by the run itself, command + output cited.
Intent findings carry verdict CONFIRMED-BY-EVIDENCE — each quotes the requirement, its source tier and the
delivered path/count.

Self-sync: {N row(s) corrected in SKILL.md / references/scope.md; wc -l before -> after, delta {D} | no change | not run (QUICK)}
       {DEGRADED: <groups> — re-run /brewcode:superreview-setup to re-tailor the expert roster}
       {intent-guard drift row PROPOSED (never auto-written): <row>}

Report: .claude/reports/{TIMESTAMP}_superreview/REPORT.md

Recommendations / next steps (superreview is READ-ONLY — it does not apply fixes):
{if DEPTH == QUICK: - This was a QUICK run: intent + gates, no domain experts, no scope passes, no adversarial
  validation. For the full pass say "deep review" (e.g. /superreview "deep review of this branch").}
- To fix: new session (English) in Manager mode (++m); delegate to the domain-owner agents (rest-controller, dto-model, report-service, result-service, generation-pipeline, plugin-youtrack, config-security, persistence-jpa, web-ui, build-ci-qa);
  P0/P1 first, then P2/P3.
- To reduce over-complexity: {OC} finding(s).{if >0: run the built-in /simplify skill (reviews + APPLIES
  reuse/simplification/efficiency cleanups), then re-run superreview.}{if 0: /simplify optional.}
  /simplify is a BUILT-IN skill (not this skill, not a plugin); skip if unavailable.
- Optional: /code-review (built-in) for a focused correctness diff pass.
superreview does NOT run /simplify or any skill and does NOT edit code — these are recommendations only.
```

---

## Configuration

| Setting | Default | Description |
|---------|---------|-------------|
| Mode | deterministic | `FULL_PROJECT` \| `EXPLICIT` \| `UNCOMMITTED` \| `LAST_COMMITS`; computed + announced |
| Depth | **`QUICK`** | `QUICK` \| `EXTENDED`, inferred SEMANTICALLY from the prompt (no flag, no CLI token); orthogonal to Mode; announced. `QUICK` = intent pass + gates, 1 spawn. `EXTENDED` = full fan-out + validation + scope gate, plus the intent pass |
| Intent pass | ALWAYS, both depths | `intent-guard` (`.claude/agents/intent-guard.md`), 1 spawn, <=15 tool calls; rows carry `CONFIRMED-BY-EVIDENCE` and skip the adversarial validator; missing agent -> `not run`, said everywhere |
| Focus | ordered | user directive (depth words stripped) wins; default table above; scope discipline is part of rank 1; security only if P0; unused at `QUICK` |
| Review corpus | IGNORED = OUT, else IN | see "What the review corpus IS" |
| Expert selection | RUNTIME from `.claude/agents/*.md` | live roster each run; the emitted table = expected result at generation time |
| Mechanical gates | `./gradlew compileJava compileTestJava` + `./gradlew test` (Phase 0 step 4) | verdict `CONFIRMED-BY-EXECUTION`, the only non-adversarial verdict |
| Scope baseline | .claude/features board (canonical, file-based Kanban) + GitHub issues (both read-only) | Phase 0 step 5 / `references/scope.md`; never invented — none -> `UNKNOWN`, findings cap P2 (permanent) |
| Scope pass A / B | `task-tracker` diff-side / `Explore` baseline-side | always at `EXTENDED`, except B in `FULL_PROJECT`; not spawned at `QUICK`; A = shapes 1-6, B = delivery + closeout |
| Scope gate | `AskUserQuestion`, Phase 3b | fires per the Phase 3b trigger; never lifts the UNKNOWN-baseline P2 cap |
| Fan-out | targeted | `QUICK`: `intent-guard` only. `EXTENDED`: `intent-guard` + non-empty domain groups + 2 scope passes + {0,1,2} general agents, model's call |
| Validation | `EXTENDED`, per-finding | Phase 3 reverse-checks EVERY verdictless candidate; unvalidatable -> `UNVALIDATED`, run INCOMPLETE, counted everywhere. Skipped at `QUICK` (nothing verdictless in the pool) — a QUICK run is not INCOMPLETE unless the intent pass itself failed to run |
| Validator agent | first non-owning in the Phase 3 chain (`general-purpose` -> `general-purpose` -> generic) | batches <=40, max 4 spawns, merge/de-dup ONCE over all batches |
| Report dir | `<repo-root>/.claude/reports/{TIMESTAMP}_superreview/` | Merged report, findings sorted P0 -> P3 |
| Max files | 50 (except `FULL_PROJECT`) | `EXTENDED` only — AskUserQuestion: narrow or proceed. Never asked at `QUICK` |
| Search tool | Bash `rg`/`grep`/`git ls-files` | reuse-first search; note which in report |
| Self-sync | Phase 4b, `EXTENDED` only, coordinator only | corrects THIS file + `references/scope.md` in place from in-context data; line delta `<= 0`, PROVEN by a before/after `wc -l`; facts only — decisions, missing experts and `intent-guard.md` are PROPOSALS |

---

## Error Handling

| Condition | Action |
|-----------|--------|
| Depth signal ambiguous / absent | Resolve `QUICK` (the default). Never ask, never invent a flag; the ANNOUNCE tells the user how to escalate |
| Prompt mixes depth signals ("quick deep check") | Rule 1 wins — an explicit depth request beats a speed word; announce which signal decided it |
| `intent-guard` agent file missing | Intent pass `not run` + reason, in ANNOUNCE + report + chat summary. At `QUICK` the run reports GATES ONLY and MUST NOT read as clean; recommend re-generating with `/brewcode:superreview-setup` |
| `intent-guard` fails or times out | Retry ONCE, then record `intent: not run (agent failed)`. At `QUICK` that makes the run `INCOMPLETE` — its only producer is gone; at `EXTENDED` the rest of the fan-out still stands, flagged |
| Intent finding without a quote / tier / evidence | It should never arrive (intent-guard drops it). If one does, DROP it with reason `not-actionable` — never upgrade it to `CONFIRM` and never ship it unevidenced |
| No changed files for scope | **Exit** ("Nothing to deep-review for {SCOPE}") — still report any gate failure |
| Gates cannot run (toolchain/deps missing) | Record `Gates: not run (<reason>)`; NEVER invent results; continue the fan-out |
| Gate command fails to start | Non-zero exit -> `GATE <g> FAIL`; mark `not run` + reason if the script is absent; continue |
| >50 files (non-`FULL_PROJECT`) | `EXTENDED` only — AskUserQuestion: narrow (per-folder / per-commit) or proceed anyway. At `QUICK` proceed without asking (one agent reads names + counts, so volume does not scale it) |
| Live roster empty/unreadable | Fall back to the emitted group->agent table; warn in the report |
| A domain agent unavailable | Fall back to built-in `Explore` with the same prompt; mark the group DEGRADED in the report |
| Only recon agents match a group | Do NOT use them — route to `Explore`, note it |
| Empty candidate pool, gates green, >=1 agent RETURNED | Skip Phase 3 — verdict APPROVED |
| Empty pool because agents FAILED | **Never APPROVED** — absence of evidence, not evidence of absence. Some returned -> verdict tagged `INCOMPLETE ({n}/{N} agents returned)`; NONE returned -> `INCOMPLETE — review did not run` |
| Every validator-chain candidate owns part of the pool | Prefer the smallest-share owner, tell it which findings are its own; generic agent only if impossible, note the downgrade |
| Validator unavailable / fails | Retry once, then the next agent in the chain; chain exhausted -> ship remaining as `UNVALIDATED`, verdict suffixed `- INCOMPLETE`, counted in header + Stats + chat |
| Candidate pool exceeds the batch budget | Raise batch size + de-dup first to fit 4 spawns; still too big -> validate the highest-severity batches, mark the remainder `UNVALIDATED` (named + counted). Never truncate silently |
| No task + no issue resolvable | `{SCOPE_BASELINE} = UNKNOWN`; run BOTH scope passes anyway, cap findings at P2, raise the Phase 3b gate |
| Tracker missing / unauthenticated | Local task + docs baseline only; report `issue: not reached`; never assume an issue sanctions anything |
| `task-tracker` unavailable | Run scope pass A's prompt on `Explore`, note the downgrade |
| `{PR_ISSUE_JSON}` empty | Pass B still runs DELIVERY vs the local task; records `PR: none`, skips closeout `scope#C*` |
| Scope gate cannot be asked (non-interactive) | Report at the priority the finding ENTERED with, tagged `unconfirmed-sanction`; never silently downgrade or upgrade past a cap |
| Agent timeout | Retry once, then mark that source unavailable + warn; the verdict inherits INCOMPLETE — a timed-out group was NOT reviewed |
| Validation rejects everything, gates green | Report "No issues survived validation" — verdict APPROVED |
| All sources clean | Report "No issues found across standards, architecture, scope and correctness" — verdict APPROVED |
| Phase 4b self-sync would GROW the file (measured `wc -l`) | Do NOT write. Report `Self-sync: skipped (would grow +{N})` and name what it wanted to add — a correction that cannot be paid for is a proposal |
| Phase 4b correction touches a DECISION | Do NOT write it. Print it as a proposal line; only the user changes decisions |
| Arguments are prose, not a scope token | Extract the commit/branch/folder/focus from the prose; never treat the first word as the scope |
| PLAN block missing, or printed after Phase 0 started | Defect — reprint it before continuing |

---

## References

- `references/java-kotlin.md` — Java 25 / Spring Boot (Gradle) stack guidelines (path passed to every agent).
- `references/agent-prompt.md` — runtime expert-selection procedure + recon-exclusion list + the domain-owner
  prompt contract (Phase 2) with the detailed focus ordering and the test-bloat block.
- `references/scope.md` — sanctioned-scope baseline resolution + precedence, ownership map, 6-shape taxonomy +
  severity map, delivery (D1-D5) + closeout (C1-C4) maps, NOT-creep exclusion list, Phase 3b gate.
- `references/report-template.md` — merged-report layout (Phase 4).
- `.claude/agents/intent-guard.md` — the intent pass's whole procedure, budget, drift classes and output shape
  (spawned at BOTH depths; this skill passes it context only and restates nothing from it).

<!--
SKILL NOTES — provenance lives in the frontmatter above (`version` / `generated_by` / `last_updated`).

Self-contained project-local deep-review skill for allure-server. NO sibling-skill orchestration, never invokes
another skill, NO plugin dependency. Two axes (MODE = scope, DEPTH = effort) and what each costs: see Configuration.

Corpus rule: git-IGNORED is OUT, everything tracked-or-will-be-tracked is IN (commit status irrelevant). The
instruction tree is AUTHORITY, never a subject; task-board files are the scope-baseline INPUT only.

Re-adopt: Phase 4b SELF-SYNC corrects the routing table, dead gates, the scope baseline and shared surfaces in
place on every EXTENDED run (facts only; decisions need you). Regenerate with /brewcode:superreview-setup on a
structural change — new stack, new service group / PATHSPEC, rules or CLAUDE.md invariants rewritten, DEGRADED
groups needing new experts, and a NEW or RENAMED agent in .claude/agents/ (the routing table refreshes itself only
on an EXTENDED run — a QUICK-only user never gets it). .claude/agents/intent-guard.md sections 3-4 stay yours: the generator REUSES an
existing one and never overwrites it. `generate.sh upgrade` refreshes an installation without losing these edits.
-->
