---
paths:
  - "src/test/**/*.java"
---

# Test Anti-Patterns

| # | Avoid | Instead | Why |
|---|-------|---------|-----|
| 1 | `org.junit.jupiter.api.Assertions.*` (assertEquals, assertTrue, assertNotNull, assertFalse) | `assertThat(...).as("desc")` — AssertJ exclusively | JUnit assertions lack fluent chaining, message context, collection matchers |
| 2 | `isNotNull()` / `isNotEmpty()` / `isGreaterThanOrEqualTo(0)` as final assertion | `isEqualTo(value)`, `hasSize(N)`, `containsExactly(...)` | Weak assertions pass for wrong results |
| 3 | `System.out.println` / `log.info` as sole evidence of behavior | Assert return value or side-effect — every branch needs `assertThat` | Logs pass even when output is wrong |
| 4 | `if (size > 1) { assertThat(...) }` in test body | `assertThat(list).as("list size").hasSizeGreaterThan(1)` first, then unconditional assertion | Conditional assertion silently skips on wrong input |
| 5 | Missing `.as("description")` on assertion | Every `assertThat(...)` chain: `.as("what is verified")` before the matcher | "expected false but was true" gives no context on failure |
| 6 | `@SpringBootTest` for unit/slice tests | `@ExtendWith(MockitoExtension.class)` for units; `@DataJpaTest` for repo; `@WebMvcTest` for controller | Full context is 10-30x slower and hides missing DI wiring |
| 7 | Multiple unrelated assertions in one `@Test` | One behavior per `@Test`; multiple inputs → `@ParameterizedTest` + `@MethodSource` | Failure hides which behavior broke |
| 8 | Missing `@DisplayName` on `@Test` / `@ParameterizedTest` | `@DisplayName("should {behavior} when {condition}")` on every method | Report shows method name only — unreadable in CI |
| 9 | Shared `allure-results.zip` across test classes | One fixture per scenario: `empty-folder.zip`, `missing-manifest.zip`, `valid-two-suites.zip` | Shared fixture couples unrelated tests |
| 10 | Magic values `2`, `"admin"`, `"uuid-string"` inline in assertions | `private static final int EXPECTED_SUITES = 2` | Unexplained numbers hide intent |
