---
paths:
  - "**/*.java"
  - "**/*.gradle"
  - "**/*.gradle.kts"
  - "**/application*.yaml"
  - "**/application*.yml"
---

# Best Practices

| # | Practice | Context | Source |
|---|----------|---------|--------|
| 1 | Java `record` for REST DTOs/value objects; `@Getter` + `final` + `@ConstructorBinding` for `@ConfigurationProperties` | `model/`, `web/dto/`, `properties/`. Etalons: `MarkdownStatisticModel`, `BasicProperties` | convention |
| 2 | `@ToString(exclude = "password")` on classes with credentials/tokens | `BasicProperties.password`, `TmsProperties.token` | convention |
| 3 | SPI: interface + `Collection<T>` injection + `default` opt-in methods | Plugins/strategies. Etalon: `AllureServerPlugin` → `Collection<AllureServerPlugin>` | convention |
| 4 | `org.springframework.transaction.annotation.Transactional` on `@Service`; class-level default, `readOnly=true` on reads. Never jakarta variant | All JPA services | convention |
| 5 | `@FeignClient` extends OpenAPI-generated interface; URL via `${prefix.api-base-url}` | `api/`. Etalon: `IssuesClient extends IssuesApi` | convention |
| 6 | Resilience4j `@CircuitBreaker` + `@Retry` (idempotent only) + connect/read timeouts on every Feign client | Configure per-instance under `resilience4j.*` in `application.yaml` | convention |
| 7 | `application-{profile}.yaml`; `${ENV:default}` for optional, `${ENV}` for required secrets | Profiles: `dev`, `prod`, `test`. Etalon: `application-oauth.yaml` | convention |
| 8 | Naming suffixes: `*Controller`, `*Configuration`, `*Properties`, `*Service`, `*Repository`, `*Utils`, `*Plugin`, `*Entity`, `*Request`/`*Response`/`*Spec`, `*Dto`, `*Model` | Project-wide | convention |
| 9 | `@UtilityClass` (Lombok) for stateless helpers — `final` + private constructor | Etalon: `service/PathUtil.java` (target `util/` split is documented DEBT) | reference-patterns |
| 10 | `@RestControllerAdvice` + `ProblemDetail` (RFC 7807) — single central exception handler | Handles `ConstraintViolationException`, `ResponseStatusException`, domain exceptions | reference-patterns |
