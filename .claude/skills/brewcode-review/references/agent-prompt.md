# Agent Prompt Template

Template for parallel review agent invocation.

```markdown
Task(subagent_type="{AGENT_TYPE}", model="opus", prompt="
  ## Code Review Task

  **Group:** {GROUP_NAME}
  **Focus:** {REVIEW_PROMPT}
  **Instance:** {INSTANCE_NUMBER} of {TOTAL_INSTANCES}

  **Files to review:**
  {FILE_LIST}

  **Project rules (from CLAUDE.md):**
  {RELEVANT_RULES}

  **Output format (JSON array):**
  ```json
  [{
    \"file\": \"path/to/file.java\",
    \"lineStart\": 42,
    \"lineEnd\": 45,
    \"category\": \"null-safety|security|performance|logic|style|test-quality\",
    \"severity\": \"blocker|critical|major|minor\",
    \"title\": \"Short summary (max 80 chars)\",
    \"description\": \"Detailed explanation of the issue\",
    \"suggestion\": \"Recommended fix or approach\",
    \"confidence\": 0.85
  }]
  ```

  **Categories:**
  - null-safety: Potential NPE, missing null checks
  - security: Auth bypass, injection, secrets, OWASP top 10
  - performance: N+1 queries, memory leaks, inefficient algorithms
  - logic: Business logic errors, race conditions, edge cases
  - style: Code style violations, naming, patterns
  - test-quality: Missing tests, weak assertions, flaky tests

  **Severity guide:**
  - blocker: Production outage, security breach, data loss
  - critical: Significant bug, performance degradation
  - major: Important issue, maintainability concern
  - minor: Style, minor improvement

  **Rules:**
  - Report ONLY issues, not positives
  - Include confidence score (0.0-1.0)
  - Provide actionable suggestions
  - Reference specific lines
")
```

## DB-Layer Specific Prompt

For `db-layer` group, use `reviewer` agent with database-focused prompt additions:

```markdown
Task(subagent_type="reviewer", model="opus", prompt="
  ## Code Review Task - Database Layer

  **Group:** db-layer
  **Focus:** {REVIEW_PROMPT}
  **Instance:** {INSTANCE_NUMBER} of {TOTAL_INSTANCES}

  **Specialized focus areas:**
  - N+1 query detection
  - Transaction boundary issues
  - Connection pool exhaustion
  - SQL injection vulnerabilities
  - Missing indexes (based on WHERE/JOIN clauses)
  - Improper eager/lazy loading
  - Batch operation opportunities

  **Files to review:**
  {FILE_LIST}

  ... (rest of standard template)
")
```

## Critic Prompt

For optional Phase 5.5 — Devil's Advocate review. Single `reviewer` (Opus).

```markdown
Task(subagent_type="reviewer", model="opus", prompt="
  ## Critic Review (Devil's Advocate)

  You are the CRITIC. Your job is to find what ALL other reviewers MISSED.

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

## DoubleCheck Critic Prompt

For Phase 5.75 — verifies ALL Critic output. Same `reviewer` (Opus) role as Phase 5 DoubleCheck.

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

## Critic vs DoubleCheck

| Aspect | DoubleCheck (Phase 5) | Critic (Phase 5.5) | DoubleCheck Critic (Phase 5.75) |
|--------|----------------------|--------------------|---------------------------------|
| **Goal** | Verify existing findings | Find missed issues | Verify Critic output |
| **Input** | Confirmed findings only | All findings + code + rejected | Critic output + code |
| **Output** | CONFIRM/REJECT per finding | New findings + challenges + blind spots | CONFIRM/REJECT per Critic item |
| **When** | Always (Phase 5) | Optional `-c` | Optional `-c` (after Critic) |
| **Adds findings** | No | Yes (P0 candidates) | No (filters P0) |
| **Agent** | `reviewer` (Opus) | `reviewer` (Opus) | `reviewer` (Opus) |
| **Perspective** | Validator — "Is this real?" | Adversary — "What did you miss?" | Validator — "Did Critic get it right?" |