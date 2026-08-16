---
doc_type: llm
version: "5.6.0"
generated_by: "brewcode:superreview-setup"
last_updated: "2026-08-13"
---

# Java/Kotlin Standards Reference

Standards for Java/Kotlin enterprise projects. The project's own rules in `.claude/rules/*` + `.claude/convention/*`
are authoritative — where this guidance conflicts, the **project rule WINS**. Cite the project rule # when enforcing.

## Tech-Specific Checks (priority dimensions)

| Category | Checks |
|----------|--------|
| DI | Constructor injection only, no field `@Autowired`, `@RequiredArgsConstructor` + final fields |
| Transactions | `@Transactional` scope (service not repository), rollback rules, isolation |
| Null-safety | `Optional` usage, `@NonNull`/`@Nullable`, `Objects.requireNonNull` |
| N+1 | Eager vs lazy loading, batch fetching, entity graphs |
| Reuse | JDK -> Apache Commons -> Guava before writing utility code |
| Security | `@PreAuthorize`, input validation, SQL injection (report only if CRITICAL/P0) |
| Lombok | `@Value`/`@Builder`/`@Slf4j`; `@Data` only on mutable entities |

## File Patterns

| Type | Patterns |
|------|----------|
| Source | `*.java`, `*.kt`, `*.kts` |
| Build | `pom.xml`, `build.gradle`, `build.gradle.kts` |
| Tests | `*Test.java`, `*Test.kt`, `*IT.java` |
| Config | `application.yml`, `application.properties` |

## Naming

| Type | Convention | Example |
|------|------------|---------|
| Entity | `*Entity` suffix | `UserEntity` |
| DTO Response | `*Response` | `UserResponse` |
| DTO Request | `*Request` | `CreateUserRequest` |
| Repository | `*Repository` | `UserRepository` |
| Service | `*Service` | `UserService` |
| Controller | `*Controller` | `UserController` |

## Dependency Injection

```java
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;   // final + constructor
    private final EmailService emailService;
}
```

Field injection (`@Autowired` on a field) = VIOLATION (harder to test, hidden deps).

## Stream API & Functional Style

| Rule | Verdict |
|------|---------|
| Prefer Stream API over imperative loops | REQ |
| Method references over lambdas (`User::getName`) | PREF |
| No side effects in streams | REQ |
| `toList()` over `collect(Collectors.toList())` (Java 16+) | PREF |

## Immutability

`final` fields by default; `List.of()`/`Set.of()`/`Map.of()` for immutable collections; Lombok `@Value` for
immutable DTOs; `@Builder` for complex construction.

## Library Usage Priority (reuse-first)

| Priority | Library | Common APIs |
|----------|---------|-------------|
| 1 | JDK | `Objects`, `Optional`, `String`, `Math`, `Collections`, `Files`, `Path` |
| 2 | Apache Commons | `StringUtils`, `CollectionUtils`, `Validate`, `IOUtils` |
| 3 | Guava | `Preconditions`, `Strings`, `Iterables`, `Lists`, `Maps` |

## Logging

`@Slf4j` + SLF4J facade; parameterized `log.info("User: {}", id)`; no `System.out.println`; no logs in tests; main
code at warn/error only. Never log secrets (security — P0).

## Test Patterns

| Rule | Verdict |
|------|---------|
| BDD comments `// GIVEN / // WHEN / // THEN` | REQ |
| `@DisplayName` on methods, not class | REQ |
| No Javadoc in tests | REQ |
| `.as("description")` on EVERY AssertJ assertion | REQ |
| Concrete: `isEqualTo(y)` / `hasSize(n)` over weak `isNotNull()` / `isNotEmpty()` / `>=` | REQ |
| No `if` in tests (assert precondition first, then unconditional assert) | VIOL |
| `allSatisfy()` over `forEach`; `extracting().contains(tuple())` for collections | REQ |

```java
// Weak — avoid
assertThat(result).isNotNull();
// Specific
assertThat(result).as("created user").isEqualTo(expected);
assertThat(list).as("result size").hasSize(3);
```

## Spring Boot Patterns

`@Transactional` on service; `ResponseEntity<T>` in controllers; `@Valid` on request bodies;
profile-specific `application-{profile}.yml`.

## Kotlin-Specific

`data class` for DTOs; extension functions for utilities; `?.let {}` over null checks; `when` over `if-else` chains;
`Duration` conversions explicit.

## Common Violations

| # | Violation | Fix |
|---|-----------|-----|
| 1 | Missing Entity suffix | Add `Entity` to JPA entities |
| 2 | Field injection | Constructor injection |
| 3 | Loop instead of Stream | Convert to Stream API |
| 4 | `System.out.println` | Use `@Slf4j` |
| 5 | Missing `.as()` in test | Add description |
| 6 | `isNotNull()` assertion | Specific value assertion |
| 7 | `if` in test | Assert precondition first |
| 8 | Writing utility that exists | Check JDK/Commons/Guava |
| 9 | Logs in tests | Remove all logging |
| 10 | Floating/`@latest` dependency | Pin exact `X.Y.Z` |

## Search Locations (reuse-first)

`**/util/`, `**/common/`, `**/shared/`, `**/core/`, `**/helper/`.

## Tools

Maven/Gradle, Spring Boot, JUnit 5, AssertJ, Mockito, Lombok, WireMock, Testcontainers.
