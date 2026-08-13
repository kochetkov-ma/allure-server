---
doc_type: llm
version: "5.6.0"
generated_by: "brewcode:superreview-setup"
last_updated: "2026-08-13"
---

# Merged Report Layout (superreview Phase 4 — allure-server)

Output: `.claude/reports/{TIMESTAMP}_superreview/REPORT.md`. ONE consolidated, validated, P0->P3-sorted report.

```markdown
# Super Review Report — allure-server

**Generated:** {TIMESTAMP}
**Mode:** {MODE}  (branch: {BRANCH})
**Depth:** {DEPTH} — {QUICK: intent pass + mechanical gates, 1 agent, no domain experts | EXTENDED: full expert fan-out + scope passes + adversarial validation, plus the intent pass}
**Scope:** {concrete scope — commit range | branch-vs-main | folder | working-tree vs HEAD | full project}
**Focus:** {resolved focus — user directive, else default ordering; security only if P0 | n/a (QUICK)}
**Files Reviewed:** {COUNT}
**Sanctioned scope:** task {T-ID | none} / issue {id | none | not reached} / decisions {ids | none} — {K}/{COUNT} files outside it | not resolved (QUICK)
**Gates:** {gate} {OK|FAIL|not run} / ...
**Intent pass:** intent-guard — **{ALIGNED | MINOR DRIFT | MAJOR DRIFT}** | not run ({reason})
**Validation:** {all {N} findings validated | **{U} UNVALIDATED of {N} — run is INCOMPLETE ({reason})**} — every row below carries a verdict | not needed (QUICK — every row carries CONFIRMED-BY-EXECUTION or CONFIRMED-BY-EVIDENCE)
**Search tool used:** {Bash rg/grep/git ls-files}
**Agents run (derived from live roster):** {AGENT_LIST}{, DEGRADED: <group> -> generic} | intent-guard only (QUICK)

> Findings section below is MANDATORY-sorted by priority P0 -> P3 (highest severity first).
> A section whose producer did not run at this depth reads `not run (QUICK depth)` — never an empty table, which
> would falsely claim "checked, nothing found".

## Summary Severity Matrix

| Priority | Meaning | Count |
|----------|---------|-------|
| P0 | Architecture blockers + CRITICAL security + unsanctioned foreign-surface scope + undelivered criterion / unrecorded reduction + blocker-severity intent drift + validator-restored misses | {N} |
| P1 | Confirmed correctness + architecture/boundary + unsanctioned feature / silent doc mutation + partially delivered criteria + misleading closeout + other gate failures | {N} |
| P2 | Reuse misses + over-complexity + drive-by scope + version-pin errors + test quality | {N} |
| P3 | Business-requirements nits + minor over-complexity + style + warnings | {N} |

## Intent / Drift — asked vs delivered (category `intent`; agent `.claude/agents/intent-guard.md`)

> Runs at BOTH depths. At QUICK depth this section IS the review.

**VERDICT: {ALIGNED | MINOR DRIFT | MAJOR DRIFT} — {intent-guard's one clause, VERBATIM}**
{or: **INTENT PASS NOT RUN** — {reason}. This run cannot say whether the delivery matches the request.}

**Sources:** T1 {label | none — the chat request is the top source} / T2 {spec | none} / T3 {plan | none} /
T4 {policy | none} / T5 transcript
**The request, verbatim:** "{what the user actually asked for}"
**Findings:** {N} (cap 10) — an empty list with `ALIGNED` is a good result, not a gap

| # | ASKED (verbatim) | Source (tier) | DELIVERED (evidence) | Why drift | Rule | Severity | Correction |
|---|------------------|---------------|----------------------|-----------|------|----------|------------|
| 1 | "{quote}" | {ticket/spec/plan/policy/chat} (tier {N}) | {path \| count \| command output} | {one sentence} | intent#{scope\|scale\|indirection\|files\|tests\|deps\|arch\|policy\|skip\|artifacts\|naming\|conflict} | blocker \| critical \| major \| minor | {minimal fix, one line} |

Every row above is also a row in the merged findings table, verdict `CONFIRMED-BY-EVIDENCE` — self-evidenced by
the quote + tier + delivery evidence, so it does not pass through the adversarial validator at either depth.

## Scope Discipline / Blast Radius (category `scope-creep`; taxonomy in `references/scope.md`) {| not run (QUICK depth)}

**Baseline:** task {T-ID + file} | none — issue {id} "{title}" | not reached — decisions {ids} | none
**Acceptance criteria covered:** {c}/{total} ({unmet ones listed as findings})
**Scope ids done as claimed:** {c}/{total in} ({u} not started yet — honestly unfinished, NOT findings / {D1/D2: `done` with nothing built} / {D5: built but still marked not-started}) — OMIT this line entirely when the task file has no `## Scope` table
**Files outside the sanctioned surface:** {K}/{COUNT}
**Delivery (section 3b):** D1 {n} undelivered / D2 {n} partial-or-stubbed / D3 {n} unprovable{ / D5 {n} stale scope-id status} — reductions: {none \| accepted, blocker recorded in {where} \| UNRECORDED -> D4}

> The `/ D5 {n}` term belongs to the Delivery line ONLY when the task file HAS a `## Scope` table. No table (the
> usual case — most repos have no board) -> DROP that term, same silent no-op as the omitted line above. Never
> print `D5 0`: a repo with no scope ids gets no scope-id text anywhere in the report.
**Closeout (section 4b):** PR {id} — body {OK \| C1 gap \| too long \| too thin}; `Closes`/`Refs` {correct \| C2 {detail}}; issue comments {OK \| missing}; AI attribution {none \| C4 found in {artefact}} \| **PR: none — closeout skipped**

| File:Line | Shape / rule | Sanctioned? | Who else is hit | Issue | Fix |
|-----------|--------------|-------------|-----------------|-------|-----|
| ... | 1 foreign-surface \| 2 unsanctioned-feature \| 3 drive-by \| 4 opportunistic-dep \| 5 silent-doc-mutation \| 6 unrecorded \| D1-D5 delivery \| C1-C4 closeout | NO \| UNKNOWN \| yes ({decision id}) | {task/owner/shared surface} | ... | split out \| revert \| deliver the criterion \| fix the PR body \| record the decision |

**Scope gate (Phase 3b):** {not triggered | Q + user's answer, verbatim, per expansion | not available — findings kept at the priority they entered with}

## Mechanical Gate Results (verdict `CONFIRMED-BY-EXECUTION` — validated by the run itself, cite command + output)

| Gate | Result | Detail |
|------|--------|--------|
| {build/lint/type/test command} | {OK \| FAIL \| not run} | {first errors / reason not run} |

## Merged Prioritized Findings (sorted P0 -> P3, highest severity first)

Every row carries a Verdict. `CONFIRM` = adversarially validated; `CONFIRMED-BY-EXECUTION` = gate output, cite the
command; `CONFIRMED-BY-EVIDENCE` = intent-guard row, cite the ASKED quote + tier + delivered evidence;
`UNVALIDATED` = validation could not run, run is INCOMPLETE. No other value, no blank.

| ID | Priority | Verdict | Source | File:Line | Category | Severity | Rule | Title | Suggestion |
|----|----------|---------|--------|-----------|----------|----------|------|-------|------------|
| P0-1 | P0 | CONFIRM | {agent} | path:42-45 | boundary | blocker | architecture#3 | ... | ... |

## Boundary & Architecture {| not run (QUICK depth)}

| File:Line | Invariant | Issue | Fix |
|-----------|-----------|-------|-----|

## Reuse / Duplicates {| not run (QUICK depth)}

| New Code | Existing | Similarity | Action | Note |
|----------|----------|------------|--------|------|

## Over-Complexity / Over-Engineering {| not run (QUICK depth)}

| File:Line | What | Rule | Simpler shape |
|-----------|------|------|---------------|
| ... | speculative abstraction / gold-plating / premature generalization / collapsible dup | best-practices#N \| avoid#N | delete layer / inline one-caller / collapse dup / reuse existing |

## Dropped in Validation (false-positive / already-fixed / unverified-rule / in-sanctioned-scope / de-dup) {| not run (QUICK depth)}

| Title | Reason |
|-------|--------|

## VERDICT

**DRIFT: {ALIGNED | MINOR DRIFT | MAJOR DRIFT | intent pass not run} — {clause, verbatim}**

**{APPROVED | CONDITIONAL | REWORK}{ - INCOMPLETE ({U} unvalidated) if any row is UNVALIDATED}**

## Stats

| Metric | Value |
|--------|-------|
| Depth | {QUICK \| EXTENDED} |
| P0 / P1 / P2 / P3 | {a} / {b} / {c} / {d} |
| Intent (drift) findings | {N} — verdict {ALIGNED \| MINOR DRIFT \| MAJOR DRIFT \| not run} |
| Scope-creep findings (files outside the sanctioned surface) | {SC} ({K}/{COUNT} files) \| not run (QUICK) |
| Over-complexity findings | {OC} \| not run (QUICK) |
| Candidate findings (pre-validation) | {N} |
| Confirmed by validation | {N} \| not run (QUICK) |
| Confirmed by execution (gate output) | {N} |
| Confirmed by evidence (intent-guard) | {N} |
| **UNVALIDATED (forces INCOMPLETE)** | **{N}** |
| Dropped by validation | {N} |
| Agents spawned | {N} |
| Files reviewed | {COUNT} |

## Recommendations / Next steps

> **superreview is READ-ONLY — it does not apply fixes.** It only reports. Act on the findings as below.

- **Drift first:** {if MAJOR DRIFT: **the deliverable is not the thing that was asked for** — settle that before
  any other finding; fixing quality inside the wrong deliverable is wasted work. | if MINOR DRIFT: {N} small
  deltas from the request — wave through or correct, one sentence each. | if ALIGNED: the delivery matches the
  request; the findings below are quality only.}
- {if DEPTH == QUICK: **This was a QUICK run** — intent + gates only. No domain expert, scope pass or adversarial
  validation ran, so nothing below claims the code is good. For the full pass, re-run saying "deep review".}
- **To FIX the findings:** start a NEW session (English), turn on **Manager mode (`++m`)**, and DELEGATE the fixes
  to the domain-expert agents the routing map named. Address **P0/P1 first, then P2/P3**.
- **Scope:** {SC} scope finding(s). {if any P0/P1: split the unsanctioned work into its own task + PR, or get the
  decision recorded in the task notes and as a comment on the issue — superreview does NOT touch the board or the
  issue. | if none: blast radius stayed inside the sanctioned surface.}
- **Re-run the gates after fixing:** the same commands Phase 0 ran.
- **To reduce over-complexity:** {OC} over-complexity / missed-reuse / duplication findings.
  {if {OC} > 0: **run the built-in `/simplify` skill** — it reviews the changed code for reuse / simplification /
  efficiency and APPLIES the cleanups. Run it in a fix-session, then re-run superreview to confirm.}
  {if {OC} == 0: `/simplify` is OPTIONAL — no over-complexity was flagged.}
  `/simplify` is a BUILT-IN Claude Code skill (NOT this skill, NOT a plugin); if it is unavailable, skip it.
- **Optional:** `/code-review` (built-in) for a focused correctness diff pass.

> These are RECOMMENDATIONS only. superreview does NOT invoke `/simplify`, does NOT call any other skill, and does
> NOT edit code — acting on them is the user's next session.
```

## Severity / reuse legend

- **Priority:** P0 (blocker — fix first) -> P3 (nice-to-have).
- **Severity:** blocker (outage/breach/data-loss) > critical (significant bug/perf/boundary) > major (maintainability) > minor (style).
- **Reuse:** REUSE (import existing 90-100%) | EXTEND (add params to existing 70-89%) | CONSIDER (evaluate 50-69%) | KEEP_NEW (<50%, justified).
- **Run verdict:** REWORK if any P0; CONDITIONAL if any P1/P2 (no P0); APPROVED if only P3 / none; suffix
  `- INCOMPLETE` whenever any row is UNVALIDATED.
- **Row verdict:** CONFIRM (adversarially validated) | CONFIRMED-BY-EXECUTION (gate output, command + line cited) |
  CONFIRMED-BY-EVIDENCE (intent-guard row: ASKED quote + source tier + delivered evidence cited) |
  UNVALIDATED (validation could not run). No row ships without one.
- **Drift verdict:** ALIGNED (nothing beyond implied work) | MINOR DRIFT (extra/missing work a reviewer waves
  through after one sentence) | MAJOR DRIFT (the deliverable is not the thing that was asked for).
- **Intent classes:** intent#scope | scale | indirection | files | tests | deps | arch | policy | skip |
  artifacts | naming | conflict — full definitions in `.claude/agents/intent-guard.md`.
- **Depth:** QUICK (default — intent pass + gates, 1 agent) | EXTENDED (full fan-out + validation + scope gate,
  plus the intent pass). Resolved semantically from the prompt; there is no flag.
- **Scope shapes:** 1 foreign-surface (P0) | 2 unsanctioned-feature (P1) | 3 drive-by (P2) | 4 opportunistic-dep
  (P2) | 5 silent-doc-mutation (P1) | 6 sanctioned-but-unrecorded (P2); delivery D1/D4 (P0, proof required),
  D2 (P1), D3 (P2), D5 stale scope-id status (P3 — P2 only when that row is the task's only delivery record);
  closeout C2 (P1), C1/C3/C4 (P2).
