---
name: brewcode:review
description: Project-adapted code review with quorum consensus. Triggers "review code", "code review", "/brewcode:review".
user-invocable: true
argument-hint: "<prompt-or-file-path> [-q|--quorum [G-]N-M] [-c|--critic]"
allowed-tools: Read, Glob, Grep, Task, Bash, Write
context: session
model: opus
---

# Code Review

**ROLE:** Code Review Coordinator | **OUTPUT:** Prioritized findings report

## Input Handling

| Input | Action |
|-------|--------|
| Text prompt | Review focus description |
| File path (`.md`, `.txt`) | Read as review instructions |
| `-q N-M` / `--quorum N-M` | N agents per group, M quorum threshold |
| `-q G-N-M` / `--quorum G-N-M` | G groups, N agents/group, M quorum |
| `-c` / `--critic` | Enable Critic phase (after DoubleCheck) |
| Text contains `критик`, `с критиком`, `critic` | Auto-enable Critic |
| Default | `-q 3-2` (3 agents, quorum 2), no critic |

---

## Project Agents

> Adapted from `.claude/agents/` — specialized reviews

| Agent | Scope | Use For |
|-------|-------|---------|
| rest-controller | `controller/` | REST endpoints, validation, caching, exception handling |
| dto-model | `model/` | REST DTOs (records), bean validation, `@Schema` |
| report-service | `service/JpaReportService`, `entity/`, `repo/` | report lifecycle, caching, cleanup |
| result-service | `service/ResultService`, path utils | upload intake, ZIP extraction, filesystem ops |
| generation-pipeline | `helper/AllureReportGenerator` + plugin SPI | Allure core, plugin lifecycle |
| plugin-youtrack | `helper/plugin/YouTrackPlugin`, `api/youtrack/` | TMS, Feign, OpenAPI codegen |
| config-security | `properties/`, `config/`, `security/` | `@ConfigurationProperties`, `SecurityFilterChain`, OAuth2/DB auth |
| persistence-jpa | `entity/`, `repo/`, `migration.sql` | JPA schema, derived queries, datasource |
| build-ci-qa | `build.gradle`, `.github/workflows/`, `Dockerfile` | build, CI, test infra |

<!--
Example:
| Agent | Expertise | Use For |
|-------|-----------|---------|
| db_expert | PostgreSQL, JOOQ | Database layer review |
| api_reviewer | REST, validation | Controller review |
-->

## Core Agents (Fallback)

| Agent | Expertise |
|-------|-----------|
| `reviewer` | Code quality, patterns, architecture |
| `tester` | Test coverage, assertions, mocking |
| `sql_expert` | SQL queries, transactions, N+1 |

---

## Tech-Specific Checks

> Adapted based on detected tech stack

| Category | Checks |
|----------|--------|
| DI | Constructor injection only; `@RequiredArgsConstructor` + `final` fields; no field injection; no `beanFactory.getBean` / `new Service(...)` in business logic |
| Transactions | `org.springframework` `@Transactional` on `@Service` (never `jakarta`); class-level default; `readOnly=true` on reads; no `@Cacheable` self-invocation |
| Null-safety | `Optional` over raw `null` returns; defensive checks at boundaries |
| N+1 / JPA | Lazy vs eager fetch; batch fetching; no `@Data` on `@Entity` (`@Getter @Setter @EqualsAndHashCode(of="id")` + `@Version`); `*Repository` naming (no `Jpa*` prefix) |
| Security | `SecurityFilterChain` matchers; Zip Slip path normalization (`normalize().startsWith(root)`); no hardcoded secret defaults in `application.yaml` (`${ENV}` for required secrets) |
| Lombok / DTOs | `record` or `@Value` for DTOs; `@Getter` + `final` + `@ConstructorBinding` for `@ConfigurationProperties`; `@ToString(exclude=...)` on credentials/tokens |
| Exceptions | Single `@RestControllerAdvice` + `ProblemDetail` (RFC 7807); `@Valid` for HTTP 400; no `catch(Throwable)`; no `@SneakyThrows` on public API |
| Naming | `*Controller` / `*Configuration` / `*Properties` / `*Service` / `*Repository` / `*Utils` / `*Plugin` / `*Entity` / `*Request` / `*Response` / `*Spec` / `*Dto` / `*Model` |
| Versions | Pin exact (no `latest`/ranges); drop version on SB-BOM-managed deps; single source `.claude/convention/versions.md` |

<!--
Examples by tech stack:

### Java/Spring
| Category | Checks |
|----------|--------|
| DI | Constructor injection, no field injection, @RequiredArgsConstructor |
| Transactions | @Transactional scope, rollback rules, isolation levels |
| Null-safety | Optional usage, @NonNull/@Nullable, null checks |
| N+1 | Eager vs lazy loading, batch fetching, entity graphs |
| Security | @PreAuthorize, input validation, SQL injection |

### Node.js/TypeScript
| Category | Checks |
|----------|--------|
| Async | Promise handling, unhandled rejections, async/await |
| Types | Strict null checks, type guards, generics |
| Validation | Input sanitization, schema validation (Zod/Joi) |
| Security | XSS prevention, CSRF tokens, helmet.js |

### Python
| Category | Checks |
|----------|--------|
| Type hints | Function signatures, return types, generics |
| Exceptions | Specific exception types, context managers |
| Async | asyncio patterns, event loop handling |
| Security | SQL parameterization, input validation |

### Go
| Category | Checks |
|----------|--------|
| Error handling | Error wrapping, sentinel errors, error types |
| Concurrency | Goroutine leaks, channel patterns, sync primitives |
| Memory | Slice capacity, pointer semantics, defer usage |
| Security | SQL injection, input validation |
-->

---

## Project Rules

> Extracted from CLAUDE.md patterns

AssertJ exclusively (no JUnit assertEquals/assertTrue); concrete assertions isEqualTo/hasSize/containsExactly, never isNotNull alone; .as(desc) on every assertion; GIVEN/WHEN/THEN comments; @DisplayName('should X when Y') on every test; no if in tests; slice tests (@WebMvcTest/@DataJpaTest) over @SpringBootTest; one fixture per scenario; English only in artifacts; no AI attribution in commits/PRs.

<!--
Examples:
- AssertJ: Use .as() on EVERY assertion
- Lombok: @RequiredArgsConstructor for DI
- Logging: @Slf4j, never System.out
- Imports: No FQN in code
-->

---

## Review Groups

| Group | Focus | Agent | Files |
|-------|-------|-------|-------|
| main-code | Logic, architecture, Spring correctness | config-security | `src/main/java/**` |
| tests | AssertJ quality, slice usage, coverage | build-ci-qa | `src/test/java/**` |
| security-config | SecurityFilterChain, OAuth2/DB auth, @ConfigurationProperties, Zip Slip, secret handling | config-security | `security/**`, `config/**`, `properties/**` |
| persistence | JPA entities, derived queries, migration.sql, H2/Postgres dialect parity | persistence-jpa | `entity/**`, `repo/**`, `**/migration.sql` |

<!--
Add project-specific groups:
| security | Auth, validation | security_reviewer | **/auth/** |
-->

---

## Execution

### Phase 1: Codebase Study (Parallel Explore)

```
ONE message with 5-10 Explore agents scanning:
src/main/java/**, src/test/java/**, src/main/jte/**, src/main/resources/**, build.gradle, gradle/**
```

### Phase 2: Group Formation

Detected files → enabled groups:
- DB detected → db-layer group
- Tests detected → tests group
- Auth files → security focus

### Phase 3: Parallel Review

```
N agents per group × 3 groups = total agents

Each agent reviews with tech checks, project rules, focus: Review for Spring Boot correctness, security (auth/Zip Slip/secrets), JPA transaction scope, and AssertJ test quality
```

### Phase 4: Quorum Collection

```python
confirmed = []
exceptions = []
discarded = []

for finding in all_findings:
    cluster = find_similar_findings(finding, all_findings)
    unique_agents = count_unique_agents(cluster)

    if unique_agents >= M:  # Quorum threshold
        merged = merge_cluster(cluster)
        confirmed.append(merged)
    elif finding.severity in ['blocker', 'critical']:
        exceptions.append(finding)  # Priority 3
    else:
        discarded.append(finding)
```

**Matching rules:**

| Criterion | Tolerance | Weight |
|-----------|-----------|--------|
| Same file | Exact | Required |
| Line range | ±5 lines | Required |
| Category | Same or equivalent | Required |
| Description | Semantic similarity ≥ 0.6 | Optional |

**Merge rules:**

| Field | Strategy |
|-------|----------|
| description | Longest/most detailed |
| severity | Highest in cluster |
| suggestion | First non-null |
| confidence | Average of cluster |
| lineStart/End | Min/Max of cluster |
| agents | List all contributing |

### Phase 5: DoubleCheck

Single `reviewer` (Opus) verifies ALL confirmed findings:

```markdown
Task(subagent_type="reviewer", model="opus", prompt="
  ## DoubleCheck Verification

  For EACH finding below:
  1. **Verify existence:** Issue exists in code?
  2. **Verify accuracy:** Description correct?
  3. **Verify actionability:** Clear fix path?
  4. **Verify severity:** Severity appropriate?

  **Findings to verify:**
  {CONFIRMED_FINDINGS_JSON}

  **Output format (JSON array):**
  [{
    \"findingId\": \"1\",
    \"verdict\": \"CONFIRM|REJECT\",
    \"reason\": \"Explanation if REJECT\",
    \"severityAdjustment\": \"null|blocker|critical|major|minor\"
  }]

  **Verdict rules:**
  - CONFIRM: Issue exists, description accurate, actionable
  - REJECT: False positive, already fixed, or not actionable
")
```

| Verdict | Action |
|---------|--------|
| CONFIRM | Priority 1 (highest confidence) |
| REJECT | Remove from report |
| Severity adjusted | Update severity in report |

### Phase 5.5: Critic (Optional — Devil's Advocate)

> Enabled by `-c`/`--critic` flag or keywords `критик`/`с критиком`/`critic` in prompt.

Single `reviewer` (Opus) challenges entire review. Input: all findings (confirmed, rejected, discarded) + source code.

**Task:** Find missed issues, challenge DoubleCheck verdicts, identify blind spots.

```markdown
Task(subagent_type="reviewer", model="opus", prompt="
  ## Critic Review (Devil's Advocate)

  Find what ALL reviewers MISSED.

  **Confirmed findings (DoubleCheck passed):**
  {CONFIRMED_FINDINGS_JSON}

  **Rejected findings (DoubleCheck rejected):**
  {REJECTED_FINDINGS_JSON}

  **Discarded findings (no quorum):**
  {DISCARDED_FINDINGS_JSON}

  **Files under review:**
  {FILE_LIST}

  **Output format (JSON):**
  {
    \"missedFindings\": [{
      \"file\": \"path/to/file.java\",
      \"lineStart\": 42,
      \"lineEnd\": 45,
      \"category\": \"null-safety|security|performance|logic|style|test-quality\",
      \"severity\": \"blocker|critical|major|minor\",
      \"title\": \"Short summary\",
      \"description\": \"Why ALL reviewers missed this\",
      \"suggestion\": \"Fix approach\",
      \"confidence\": 0.85
    }],
    \"challenges\": [{
      \"findingId\": \"P1-3\",
      \"type\": \"WRONG_CONFIRM|WRONG_REJECT|SEVERITY_WRONG\",
      \"reason\": \"Why the verdict is wrong\",
      \"suggestedSeverity\": \"blocker|critical|major|minor|null\"
    }],
    \"blindSpots\": [\"category with 0 findings that warrants attention\"]
  }

  **Rules:**
  - Focus on what was MISSED, not what was found
  - Challenge at least 1 confirmed finding (stress-test)
  - Report blind spots even if no missed issues found
  - Confidence reflects how certain you are the issue was missed
")
```

**Critic output → Phase 5.75 input** (all findings require verification)

### Phase 5.75: DoubleCheck Critic

Single `reviewer` (Opus) verifies ALL Critic output — same role as Phase 5 DoubleCheck.

```markdown
Task(subagent_type="reviewer", model="opus", prompt="
  ## DoubleCheck: Critic Verification

  Verify ALL findings from the Critic (Devil's Advocate).
  Apply the SAME standards as Phase 5 DoubleCheck.

  **Critic missed findings:**
  {CRITIC_MISSED_FINDINGS_JSON}

  **Critic challenges:**
  {CRITIC_CHALLENGES_JSON}

  **Critic blind spots:**
  {CRITIC_BLIND_SPOTS}

  **Source code files:**
  {FILE_LIST}

  **For EACH missed finding:**
  1. Does the issue actually exist in code?
  2. Is the description accurate?
  3. Is there a clear fix path?
  4. Is the severity appropriate?

  **For EACH challenge:**
  1. Is the challenge valid? Re-read the original finding and code.
  2. Was the original DoubleCheck verdict correct or wrong?

  **For EACH blind spot:**
  1. Is the category relevant to the reviewed code?
  2. Are there actual issues in that category?

  **Output format (JSON):**
  {
    \"missedFindings\": [{
      \"findingId\": \"CF-1\",
      \"verdict\": \"CONFIRM|REJECT\",
      \"reason\": \"Explanation\",
      \"severityAdjustment\": \"null|blocker|critical|major|minor\"
    }],
    \"challenges\": [{
      \"challengeId\": \"CC-1\",
      \"verdict\": \"CONFIRM|REJECT\",
      \"reason\": \"Explanation\"
    }],
    \"blindSpots\": [{
      \"spotId\": \"BS-1\",
      \"verdict\": \"CONFIRM|REJECT\",
      \"reason\": \"Explanation\"
    }]
  }

  **Verdict rules:**
  - CONFIRM: Issue exists / challenge valid / blind spot real
  - REJECT: False positive, already covered, or not actionable
")
```

**Result processing:**

| Verdict | Source | Action |
|---------|--------|--------|
| CONFIRM `missedFinding` | Critic | → Priority 0 (verified) |
| REJECT `missedFinding` | Critic | Discard |
| CONFIRM `challenge[WRONG_REJECT]` | Critic | Restore finding → P0 |
| CONFIRM `challenge[WRONG_CONFIRM]` | Critic | Add note to existing finding |
| CONFIRM `challenge[SEVERITY_WRONG]` | Critic | Adjust severity |
| REJECT `challenge` | Critic | Keep original verdict |
| CONFIRM `blindSpot` | Critic | → Report Statistics |
| REJECT `blindSpot` | Critic | Discard |

### Phase 6: Report

Output: `.claude/tasks/reviews/{TIMESTAMP}_{NAME}_report.md`

Priority order:
0. Critic findings — verified by DoubleCheck (when critic enabled)
1. Quorum + DoubleCheck confirmed
2. Quorum only (not DoubleCheck confirmed)
3. Blocker/Critical without quorum (exceptions)

---

## Output Format

**Without critic:**
```
Review complete. Found {TOTAL} issues ({BLOCKERS} blocker, {CRITICAL} critical, {MAJOR} major).

Priority breakdown:
- P1 (Confirmed): {N} issues
- P2 (Quorum only): {N} issues
- P3 (Exceptions): {N} issues

Full report: .claude/tasks/reviews/{TIMESTAMP}_{NAME}_report.md
```

**With critic (`-c`):**
```
Review complete. Found {TOTAL} issues including {P0_COUNT} critic ({BLOCKERS} blocker, {CRITICAL} critical, {MAJOR} major).

Priority breakdown:
- P0 (Critic): {N} issues — verified by DoubleCheck
- P1 (Confirmed): {N} issues
- P2 (Quorum only): {N} issues
- P3 (Exceptions): {N} issues

Critic challenges: {COUNT} verdicts challenged ({ACCEPTED} accepted)
Blind spots: {N} categories flagged

Full report: .claude/tasks/reviews/{TIMESTAMP}_{NAME}_report.md
```

---

## Configuration

| Setting | Default | Description |
|---------|---------|-------------|
| `-q` / `--quorum` | `3-2` | `N-M` (agents-threshold) or `G-N-M` (groups-agents-threshold) |
| Report dir | `.claude/tasks/reviews/` | Output directory |
| Max parallel | 25 | Maximum agents in one message (G×N) |
| Line tolerance | ±5 | Lines overlap for matching |
| Similarity | 0.6 | Semantic similarity for matching |
| `-c` / `--critic` | Off | Enable Critic (Phase 5.5) + DoubleCheck Critic (Phase 5.75) |

---

## Error Handling

| Condition | Action |
|-----------|--------|
| No files found for block | Skip block, warn in report |
| Agent timeout | Retry once, then mark unavailable |
| No findings | Report "No issues found" with confidence |
| Invalid quorum args | Error: "Invalid -q/--quorum. Format: G-N-M or N-M" |
| No CLAUDE.md | Use default rules only |
| Critic finds 0 issues | Report confidence boost: "Critic found no missed issues" |
| DoubleCheck rejects >50% of Critic findings | Warning in report: "Most critic findings unconfirmed" |
| Critic challenges >50% of P1 | Warning: "High challenge rate — consider re-review" |
| `-c` with `-q 1-1` | Warning: "Critic most valuable with quorum >= 2" |

---

## References

- `references/agent-prompt.md` — Agent prompt templates (standard + DB-layer + Critic)
- `references/report-template.md` — Report format and naming conventions

---

<!--
ADAPTATION METADATA

Template: brewcode:review
Adapted: 2026-06-12T08:15:20Z
Tech stack: Java 25, Spring Boot 3.4.13, Gradle 9.4.1, JUnit 5 + AssertJ + Mockito, JPA/Hibernate (H2/PostgreSQL), JTE+HTMX+Alpine+Tailwind
Project agents: 11

Readopt triggers:
- New agent in .claude/agents/
- CLAUDE.md patterns updated
- Tech stack changes

Run: /brewcode:setup to refresh
-->
