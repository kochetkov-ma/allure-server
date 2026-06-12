# Testing Conventions — allure-server

[DICT: AP=anti-pattern, GWT=GIVEN/WHEN/THEN, PT=@ParameterizedTest]

> Stack: JUnit 5 + AssertJ + Spring Boot Test (versions -> `versions.md`). Strict quality bar.
> Live etalon: `web/` + `security/` test packages (e.g. `web/ReportsWebControllerTest.java` — `@DisplayName` + GWT + `.as()` asserts). Legacy suite (`helper/`, `service/ResultServiceTest`, `UtilTest`, `MarkdownStatisticModelTest`) = AP material below — bring to standard on touch.

## 1. Quality Standards

| # | Rule | |
|---|------|---|
| 1 | `@DisplayName` on every `@Test` — `"should {behavior} when {condition}"` | REQUIRED |
| 2 | `// GIVEN` / `// WHEN` / `// THEN` / `// AND` structure | REQUIRED |
| 3 | AssertJ `assertThat(...).as("description")` on every assertion | REQUIRED |
| 4 | JUnit `Assertions.assertEquals/assertTrue/assertNotNull/assertFalse` | BANNED — AssertJ only |
| 5 | `if` in tests — assert precondition first, then unconditional assertion | BANNED |
| 6 | `System.out.println` / `log.info` as evidence — every branch must assert | BANNED |
| 7 | Full object comparison `isEqualTo(expected)` over field-by-field | PREFERRED |
| 8 | Concrete assertions: `isEqualTo(value)`, `hasSize(N)`, `containsExactly(...)` | REQUIRED |
| 9 | Weak assertions: `isNotNull()`, `isNotEmpty()`, `isGreaterThanOrEqualTo(0)` | BANNED |
| 10 | One logical behavior per `@Test` — != 4 unrelated inputs sequentially | REQUIRED |

## 2. T1 — Test Data

| Pattern | Location |
|---------|----------|
| Binary fixtures (zip, json archives) | `src/test/resources/{feature}/` |
| Text fixtures (markdown, yaml) | `src/test/resources/{feature}/{scenario}.{ext}` |
| Factory for allure-result trees | `src/test/java/ru/iopump/qa/allure/support/AllureResultsFactory.java` (prescriptive — create on first need) |

- One fixture per scenario — != shared catch-all `allure-results.zip`. Name after behavior: `empty-folder.zip`, `valid-two-suites.zip`.
- Factories return immutable POJOs — no hidden mutation between tests.

## 3. T2 — Base Classes (prescriptive — none exist yet; target `support/`)

| Base class | Annotations | Use for |
|------------|-------------|---------|
| `AbstractSpringIT` | `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@ActiveProfiles("test")` | full context, feign, services |
| `AbstractWebMvcSliceTest` | `@WebMvcTest(controllers = X.class)` + `@Import(SecurityTestConfig.class)` | controller slice per endpoint group |
| `AbstractDataJpaSliceTest` | `@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)` + H2 | repository layer |

Compose via meta-annotation — != repeat `@EnableConfigurationProperties` per test (see `YouTrackPluginTest` AP). Keep slices narrow.

## 4. T3 — Helpers

| Helper | Purpose |
|--------|---------|
| `TestResourceLoader` (`@UtilityClass`) | wrap `ClassPathResource` — `TestResourceLoader.stream("unzip/valid.zip")` |
| `MockMvcRestClient` | fluent `MockMvc` wrapper for controller ITs |
| `AllureResultsFactory` | build `LaunchResults` / `TestResult` graphs for plugin tests |

Helpers `final` + `@UtilityClass` or package-private constructors. Composition only — no helper inheritance.

## 5. T4 — Data Preparation (triad)

| Class | Responsibility |
|-------|----------------|
| `{Feature}Test` | `@Test` methods only — structure + assertions |
| `{Feature}ExpectedData` | `public static final` expected POJOs; inner class per scenario (`ExpectedData.EmptyZip`) |
| `{Feature}Requests` | input builders / request fixtures |

Named constants over magic values (`private static final int EXPECTED_SUITES = 2;`). Expected data immutable — `record`, `List.of(...)`.

## 6. T5 — Etalon Test Class

```java
@DisplayName("PathUtil — unzip behavior")
class PathUtilTest {

    private static final String FIXTURE_VALID_ZIP = "unzip/valid-two-suites.zip";
    private static final int EXPECTED_FILE_COUNT = 2;

    @TempDir Path targetDir;

    @Test
    @DisplayName("should extract all entries when zip is valid")
    void shouldExtractAllEntriesWhenZipIsValid() throws IOException {
        // GIVEN a valid zip with two allure-result files
        var zipStream = TestResourceLoader.stream(FIXTURE_VALID_ZIP);

        // WHEN unzip is executed into a temp dir
        Path result = PathUtil.unzip(zipStream, targetDir);

        // THEN target dir contains exactly the expected files
        assertThat(result)
            .as("unzip target path")
            .isEqualTo(targetDir);
        assertThat(Files.list(result))
            .as("extracted files in %s", result)
            .hasSize(EXPECTED_FILE_COUNT);
    }
}
```

## 7. T6 — Parameterized Tests

| Rule | Example |
|------|---------|
| Prefer PT over repeated `@Test` | `unzipAndStoreNegative` (4 inputs) -> `@MethodSource` |
| Name pattern `"{index} — should reject when input is {0}"` | `@ParameterizedTest(name = "...")` |
| Factories for invalid payloads | `interface InvalidPayloadTestFactory { static Stream<Arguments> invalidZips() {...} }` |
| One assertion style per PT | != mix thrown/returned assertions |

## 8. APs (live in current legacy suite — verified 2026-06-11)

| Location | Problem | Fix |
|----------|---------|-----|
| `helper/UtilTest.java` | JUnit `assertEquals` with reversed args, no `@DisplayName` | `assertThat(Util.lastSegment(url)).as("uuid segment").isEqualTo("e6b22402-...")` |
| `helper/plugin/YouTrackPluginTest.java` | plugin invoked, side-effects not asserted | `assertThat(capturedReportFile).as("markdown").isEqualTo(expected)` + verify feign interactions |
| `helper/plugin/youtrack/MarkdownStatisticModelTest.java:50,56,95` | `System.out.println` as evidence | `assertThat(model.toMarkdown()).as("markdown").isEqualToNormalizingWhitespace(EXPECTED)` |
| `service/ResultServiceTest.java` (positive block) | 3 fixtures in one `@Test`, `log.info` only, zero assertions | split into PT cases, assert returned `Path` contents |
| `service/ResultServiceTest.java` (negative block) | 4 negative inputs in one `@Test` | PT + `@MethodSource("invalidInputs")` |

## 9. Quick Reference — When Writing...

| When writing... | Target pattern |
|-----------------|----------------|
| Pure util test | §6 etalon — single GWT, `@DisplayName`, `.as()` |
| Service with filesystem IO | §6 etalon + `@TempDir` + PT for inputs |
| Web/controller slice | existing `web/*Test` etalons; or new `AbstractWebMvcSliceTest` + `MockMvcRestClient` (§3-4) |
| Full Spring IT (feign, plugins) | new `AbstractSpringIT` — replaces ad-hoc `@SpringBootTest` in `YouTrackPluginTest` |
| Markdown / serialization | expected from `src/test/resources/youtrack/*.md`, `isEqualToNormalizingWhitespace` |
| Multiple invalid inputs | PT + `InvalidPayloadTestFactory` (§7) |
| Plugin behavior with results graph | `AllureResultsFactory` (§4) + capture side-effects + assert |

## Forbidden Imports

```
org.junit.jupiter.api.Assertions.assertEquals
org.junit.jupiter.api.Assertions.assertTrue
org.junit.jupiter.api.Assertions.assertNotNull
org.junit.jupiter.api.Assertions.assertFalse
```

`org.assertj.core.api.Assertions.assertThat` exclusively.
