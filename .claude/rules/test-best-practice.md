---
paths:
  - "src/test/**/*.java"
---

# Test Best Practices

| # | Practice | Context | Source |
|---|----------|---------|--------|
| 1 | `@WebMvcTest` per controller group; `@DataJpaTest` for repo; `@SpringBootTest(webEnvironment=RANDOM_PORT)` for full IT | Base: `AbstractWebMvcSliceTest`, `AbstractDataJpaSliceTest`, `AbstractSpringIT` | testing-conventions |
| 2 | `@DisplayName("should {behavior} when {condition}")` on every `@Test` / `@ParameterizedTest` | All test methods | convention |
| 3 | `// GIVEN / WHEN / THEN` block comments + `.as("desc")` on every assertion | All test methods | convention |
| 4 | `@ParameterizedTest` + `@MethodSource` for same behavior with multiple inputs | Replaces N identical `@Test` methods | convention |
| 5 | One fixture per scenario: `empty-folder.zip`, `missing-manifest.zip`, `valid-two-suites.zip` | `src/test/resources/` | testing-conventions |
| 6 | `private static final` constants for magic values in assertions (`EXPECTED_SUITES = 2`) | All assertion sites | convention |
| 7 | AssertJ `assertThat(...)` exclusively — no JUnit `assertEquals` / `assertTrue` | All assertion sites | convention |
