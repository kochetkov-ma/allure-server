---
paths:
  - "**/*.java"
  - "**/*.gradle"
  - "**/*.gradle.kts"
  - "**/application*.yaml"
  - "**/application*.yml"
---

[DICT: AY=application.yaml, CP=@ConfigurationProperties, SB=Spring Boot, SC=@Component]

# Avoid

| # | Avoid | Instead | Why |
|---|-------|---------|-----|
| 1 | `@Data` on `@Entity` | `@Getter @Setter @EqualsAndHashCode(of="id")` + `@Version` | `@Data` hashCode over all fields breaks JPA identity |
| 2 | `@Data` on REST DTOs / CP | Java record for DTOs; `@Getter` + `final` + `@ConstructorBinding` for CP | Immutable contracts; mutable DTOs leak state |
| 3 | `@SneakyThrows` on public API / REST endpoints | Declare `throws IOException` or wrap in domain `RuntimeException` | Swallows checked-exception contracts; `IOException` → 500 with no meaning |
| 4 | `beanFactory.getBean(X.class)` in business logic | Constructor-inject via `@RequiredArgsConstructor` | Hides deps, breaks DIP, fails at runtime not startup |
| 5 | Zip Slip: `new File(root, entryName)` without path check | `destinationFile.normalize().startsWith(unzipTo.normalize())` before write | CVE path traversal — `../../etc/passwd` escapes destination |
| 6 | `@Cacheable` on `this.method()` self-invocation | Extract to separate `@Service` bean or `AopContext.currentProxy()` | AOP proxy skipped on self-calls; `@Cacheable` silently ignored |
| 7 | `Jpa*` prefix on repositories (e.g. `JpaReportRepository`) | `*Repository` only (e.g. `ReportRepository`) | Leaks impl detail; forces rename on switch |
| 8 | `*Util` (singular) or `*Config` class naming | `*Utils` for helpers; `*Configuration` for `@Configuration` | Consistent: `PathUtils`, `SecurityConfiguration` |
| 9 | `new ObjectMapper()` static in util | Inject SB-managed `ObjectMapper`; `Jackson2ObjectMapperBuilder` if custom | Bypasses SB auto-config; serialization drift across layers |
| 10 | Hardcoded versions for SB BOM libs (`h2`, `hibernate-validator`) | Drop version; BOM (`io.spring.dependency-management`) owns it | Drift from managed transitives → classpath conflicts |
| 11 | `hibernate.ddl-auto: update` in prod AY | Flyway `V{N}__desc.sql` + `ddl-auto: validate` | Non-reproducible; silently drops columns |
| 12 | Hardcoded secret defaults in AY (`admin/admin`, `my-token`) | `${ENV}` for secrets (fail-fast); `${ENV:default}` for non-sensitive | Default creds reach prod by accident |
| 13 | `catch(Throwable)` in app code | `catch(Exception)` or specific types; let `Error` propagate | Catches `OOMError`/`SOError` → hides JVM-fatal state |
| 14 | `@ExceptionHandler` duplicated in every `@RestController` | Single `@RestControllerAdvice` returning `ProblemDetail` (RFC 7807) | DRY; consistent error envelope across API |
| 15 | `Preconditions.checkArgument` for HTTP 400 | `@Valid` + Bean Validation on DTO; `ResponseStatusException(BAD_REQUEST)` for biz violations | `IllegalArgumentException` unmapped → 500 |
| 16 | `Stream.peek()` with side-effects (log/delete/save) | `forEach` for terminal effects or explicit loop | Debugging-only per JDK docs; parallel/lazy streams skip or duplicate |
| 17 | `parallelStream()` on IO-bound tasks | `ExecutorService` + `CompletableFuture.allOf`; Reactor for high-concurrency | Uses common `ForkJoinPool`; IO latency starves CPU work |
| 18 | `ReflectionUtil.createImplementations` bypassing Spring DI | Implement as SC, inject `Collection<PluginInterface>` | Loses lifecycle, DI, conditional beans, proxy support |
| 19 | Mixed camelCase + kebab-case in AY | Kebab-case only: `results-dir`, `dry-run`, `age-days` | Breaks `@ConfigurationPropertiesScan` metadata + IDE hints |
| 20 | `new SomeService(...)` inside SC (e.g. `new ResultService(dir)` in `JpaReportService`) | Register as SC, constructor-inject | No `@Transactional` proxy, no caching, duplicate state |
