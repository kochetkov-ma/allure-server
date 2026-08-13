---
task: T-UI-REPLACE-ME
kind: design
status: draft            # draft | agreed | superseded
open_questions: 0        # count of UNRESOLVED rows in this doc's "Open architectural questions", blocking or not
created: 2026-08-13
updated: 2026-08-13
---

# Design -- T-UI-REPLACE-ME

> System design + architecture. Product decisions live in `T-UI-REPLACE-ME-spec.md`,
> the ask in the task file.

## Context & constraints

*Where this lands in allure-server and what boxes it in: existing components touched, hard limits (latency, memory, deploy cadence), things that cannot change.*

| constraint | value / source |
|------------|----------------|
| EXAMPLE: added latency budget on the hot path | <= 2ms p99, from the existing API SLO |
| EXAMPLE: cannot change | public route paths and response envelope (external consumers) |

## Architecture

*Components and one-line responsibilities. New vs changed vs untouched-but-load-bearing. Keep it to the boxes that matter.*

| component | new/changed | responsibility |
|-----------|-------------|----------------|
| EXAMPLE: `RateLimitMiddleware` | new | reads the bucket, decides allow/deny, sets headers |
| EXAMPLE: `CacheClient` | untouched | key/value with TTL; load-bearing, single node |

## Data flow

*The path a request/event takes, step by step or as an arrow chain. Name the component at each hop and what crosses the boundary.*

EXAMPLE:

```
request -> RateLimitMiddleware -> BucketStore.take(user_id, cost=1)
             -> CacheClient.get/set(key)  [tokens, last_refill; TTL 2x window]
   allow -> next middleware -> handler -> response + X-RateLimit-* headers
   deny  -> 429 short-circuit, handler never runs
```

## Interfaces & contracts

*Every boundary this change creates or moves: function signatures, HTTP routes, events, config keys. Include the error/failure shape, not just the happy path.*

| interface | shape | notes |
|-----------|-------|-------|
| EXAMPLE: `BucketStore.take(key, cost)` | `-> {allowed: bool, remaining: int, retry_after_s: int}` | pure over the cache client; !=throws on cache miss, treats it as a full bucket |
| EXAMPLE: config key `ratelimit.default` | `{capacity: int, refill_per_s: float}` | hot-reloaded; invalid values -> keep previous, log warn |

## Data model

*Persistent or cached shapes this introduces: keys, tables, columns, TTLs, indexes, migrations. Omit this whole section if the change stores nothing.*

| store | key / table | shape | lifetime |
|-------|-------------|-------|----------|
| EXAMPLE: cache | `rl:user:<user_id>` | `{tokens: float, last_refill: epoch_ms}` | TTL 120s, refreshed on write |

## Failure modes & reliability

*What breaks, what the system does about it, and what the user sees. One row per mode. Include the dependency-down case.*

| failure | behaviour | blast radius |
|---------|-----------|--------------|
| EXAMPLE: cache node down | fail open, allow the request, log warn once per 10s | no limiting until recovery; !=user-visible |

## Complexity budget

*What we deliberately do NOT build, and why. This is the anti-over-engineering section: name the tempting generalisation, the cost of building it now, and the trigger that would make it worth revisiting. An empty budget is a smell -- there is always something you chose not to build.*

| !=building | why not now | revisit when |
|------------|-------------|--------------|
| EXAMPLE: pluggable limiter strategy interface with 3 implementations | one strategy is in use; the abstraction costs a factory, a config enum and 2 test suites to serve a hypothetical | a second real strategy is actually requested |

## Non-goals

*Architectural directions explicitly not taken here, so nobody reads them into the design. Distinct from the spec's `## Out of scope`, which is about product scope.*

- EXAMPLE: this !=a general-purpose quota/billing subsystem.

## Scope coverage

*Same scope ids as the task and the spec. `component / decision covering it` names a component from `## Architecture` or a spec decision id. Gate G1 applies here too, and reads `in` ids ONLY -- an `out` row's status never affects it.*

| scope id | component / decision covering it | status |
|----------|----------------------------------|--------|
| S1 | RateLimitMiddleware + BucketStore | covered |
| S2 | BucketStore (IP key variant), D1 | covered |
| S3 | -- no component yet, exemption list undesigned | uncovered |
| S4 | Complexity budget (deliberately not built) | covered |

Status values: `covered` | `partial` | `uncovered`. Nothing else. This is the SPEC-coverage axis, !=the task's execution `status` (`not-started` | `in-progress` | `done`) -- orthogonal, !=add an execution column here, !=merge the two.

## Open architectural questions

*Ids `AQ1..AQn`, never renumbered, a namespace of their own -- the spec's questions are `Q#` and are counted separately. Same blocking semantics: `blocking: yes` BLOCKS progress -> closed (gate G2, which scans BOTH documents); override only with a `SPEC WAIVER: <reason>` line in the task's `## Notes`.*

| id | question | blocking | owner |
|----|----------|----------|-------|
| AQ1 | EXAMPLE: fail-open or fail-closed when the cache is down? | yes | UI domain agent |
| AQ2 | EXAMPLE: does the admin exemption belong in config or in the route table? | no | api domain agent |

## Evidence

*Gate G4: the design !=authored by one generalist. One row per domain the task touches, naming the agent that covered it. A domain with no domain agent MUST still get a row saying so and naming the fallback -- silence is a defect. Fallback order: project domain agent -> project architecture-capable agent -> built-in `Plan`.*

| domain | agent consulted | verdict |
|--------|-----------------|---------|
| EXAMPLE: UI | `UI-expert` | approved; asked for the fail-open row in Failure modes |
| EXAMPLE: infra | none in `.claude/agents/` -- fell back to built-in `Plan` | no blocking objection; flagged the single-node cache as the real limit |
