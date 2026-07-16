# Project Architecture — allure-server

[DICT: AP=anti-pattern, BOM=bill of materials, CFG=configuration, GEN=OpenAPI generator, SB=Spring Boot, SC=Spring Cloud]

> Versions: ALL pins in `versions.md` (same dir) — != version literals here.
> Stack: Java 25 + SB 3.4 + Gradle 9 + server-rendered UI (JTE + HTMX + Alpine.js + Tailwind standalone; Vaadin REMOVED) + Lombok + JPA (H2/PostgreSQL) + GEN Feign client (YouTrack). Open-source -> strict standards.

## 1. Build (L1)

| Item | Value | Source |
|------|-------|--------|
| Main class | `ru.iopump.qa.allure.Application` | `build.gradle` `springBoot{}` |
| Boot packaging | `PropertiesLauncher`; `/ext` plugin loader via `-Dloader.path=/ext` | `build.gradle` `bootJar`, `Dockerfile` ENTRYPOINT |
| Group / artifact | `theGroup=ru.iopump.qa` / `theArchivesBaseName=allure-server` | `gradle.properties` |
| Build flags | daemon, parallel, configure-on-demand, `-Xmx2024m` | `gradle.properties` |
| Version resolution | `-Pversion` -> `RELEASE_VERSION` env -> `git describe --tags` -> `dev` | `build.gradle` `resolveProjectVersion` |
| Docker | 2-stage: JDK alpine builds bootJar in-image (self-contained CI) -> JRE alpine runtime, non-root `app` user, `EXPOSE 8080` | `Dockerfile` |

Plugins (boot, dependency-management, freefair lombok, GEN, jte, ben-manes versions): `build.gradle:3-16`; versions -> `versions.md`.

## 2. Dependency Management (L2)

| Rule | Status | Detail |
|------|--------|--------|
| SB BOM manages versions | OK | via `io.spring.dependency-management` |
| SC BOM imported | OK | `ext.springCloudVersion` in `build.gradle`; openfeign starter version-less |
| Byte Buddy override | OK (deliberate) | SB 3.4 BOM pin supports Java 24 max -> overridden for Java 25; rationale in `versions.md` |
| `ext{}` version catalog | OK | `gradle/dependencies.gradle` + `build.gradle` |
| Duplicate `spring-boot-starter-security` | DEBT | declared twice (`dependencies.gradle:28,37`) — remove one |
| Unused `openApiVersion` ext | DEBT | declared, never referenced — remove |
| Logback wiring | OK | excluded group-wide from `spring-boot-starter-web`, re-added as `runtimeOnly logback-classic` — keep single source |

## 3. Code Generation (L3)

GEN runs before `compileJava`/`compileTestJava`; output `build/generated/src/main/java` added to `sourceSets.main`.

| Option | Value |
|--------|-------|
| `generatorName` / `library` | `spring` / `spring-boot` |
| `inputSpec` | `src/test/resources/tms/openapi-youtrack.json` |
| `apiPackage` / `modelPackage` | `org.brewcode.api.youtrack` / `.model`, suffix `Dto` |
| `useJakartaEe` / `useSpringBoot3` | `true` |
| `interfaceOnly` / `skipDefaultInterface` | `true` |
| `useResponseEntity` | `false` |

AP (`build.gradle` `openApiGenerate.doLast`): regex post-processing rewrites generated sources — incl. heavy `BaseBundleDto.java` surgery (strips orphan fluent setters + the `values` family GEN 7.11 emits broken on the polymorphic parent). Prefer Mustache template overrides / `typeMappings`. Mitigation present: fail-loud `GradleException` if generator output shape changes (post-processing no-op -> build break).

## 4. Frontend Pipeline (L3b — no Node/npm/pnpm)

| Step | Detail |
|------|--------|
| `tailwindDownload` task | fetches Tailwind standalone binary per OS/arch into `build/tailwind/` |
| `tailwindBuild` task | `src/main/frontend/input.css` + `tailwind.config.js` + scan of `src/main/jte/**/*.jte` -> minified `src/main/resources/static/css/app.css`; wired via `processResources.dependsOn` |
| JTE precompile | `jte { generate() }` -> templates compiled into bootJar; runtime loads from classpath (`gg.jte.development-mode: false`, `use-precompiled-templates: true` in `application.yaml`) |
| Gradle 9 note | `Project#exec` in `doLast` removed -> injected `ExecOperations` used |

`static/css/app.css` is GENERATED — != hand-edit.

## 5. Migrations (L12) — CRITICAL GAP

| Concern | Status |
|---------|--------|
| Flyway / Liquibase | MISSING |
| `spring.jpa.hibernate.ddl-auto` | `update` (`application.yaml`) — propagated to `docker-compose.yml` env + Helm chart |
| Migration baseline | none |

Fix: introduce Flyway, `V1__init.sql` from current schema, switch `ddl-auto` to `validate`. `update` != acceptable in a release artifact.

## 6. Resources & CFG (L13)

| File | Role |
|------|------|
| `application.yaml` | base profile: H2 file datasource, JPA, JTE, security bootstrap, springdoc theming, allure props, logging, TMS |
| `application-oauth.yaml` | Google OAuth2, opt-in via `spring.profiles.active=oauth` |
| `src/main/resources/config/allure.yml` (+ `allure-cucumber.yml`, `allure-junit.yml`) | Allure generator plugin list |

Security CFG model (always-on auth): `basic.auth.username/password` seed the bootstrap main admin on FIRST startup only -> DB authoritative afterwards (`/app/admin/users`). `basic.auth.enable` = deprecated but HONORED (logs a startup warning): `true` restores legacy lock-everything — every request except public static assets requires auth, incl. `/api/**` and `/allure/**`, ignoring the `require-api-auth` toggle — NOT a no-op. `app.security.require-api-auth` = bootstrap default for `/api/**` + `/allure/**` protection; runtime value lives in system-settings DB row, flipped via `/app/admin/settings`.

### Env var pattern — etalon

```yaml
# application-oauth.yaml — required secret, no default
client-id: ${OAUTH2_GOOGLE_ALLURE_CLIENT_ID}
client-secret: ${OAUTH2_GOOGLE_ALLURE_CLIENT_SECRET}
```

```yaml
# application.yaml — overridable with default
server.port: ${PORT:8080}
```

### APs

| Issue | Location | Fix |
|-------|----------|-----|
| `admin/admin` committed as bootstrap seed | `application.yaml` `basic.auth` | override via `${BASIC_AUTH_USERNAME}`/`${BASIC_AUTH_PASSWORD}` in any deployment |
| Committed `token: "my-token"` | `application.yaml` `tms.token` | `${TMS_TOKEN}` |
| Mixed key casing (`dryRun`, `ageDays` vs `history-level`, `support-old-format`) | `application.yaml` `allure.clean` | kebab-case everywhere |
| No `logback-spring.xml` | `src/main/resources/` | add JSON + rolling-file config |
| No `application-dev/prod/test.yaml` | `src/main/resources/` | add per-environment profiles (only `oauth` exists) |

## 7. Directory Structure

```
src/main/java/ru/iopump/qa/allure/
  Application.java          # @SpringBootApplication entry point
  api/                      # Feign clients + FeignConfiguration (youtrack/ per-vendor)
  config/                   # Spring @Configuration classes
  controller/               # @RestController /api/** endpoints
  entity/                   # @Entity JPA classes
  helper/                   # MIXED: utils, generator, FileVisitor, plugin SPI + impls
    plugin/                 # AllureServerPlugin SPI + impls
  model/                    # REST DTOs (Request / Response / Spec)
  properties/               # @ConfigurationProperties beans
  repo/                     # Spring Data JPA repositories
  security/                 # SecurityConfiguration, filters, DbUserDetailsService
  service/                  # @Component services (mixed with CleanUpServiceConfiguration)
  web/                      # server-rendered JTE controllers /app/** (+ dto/)
src/main/jte/               # JTE templates: about, admin, layout, partials, profile, reports, results
src/main/frontend/input.css # Tailwind input
src/main/resources/static/css/app.css  # GENERATED by tailwindBuild
```

AP `helper/` cohesion: `@Component` beans + static utils + `FileVisitor` impls + plugin SPI in one package. Split: `util/` (pure static), `fs/` (FileVisitor), `plugin/` (SPI only).

## 8. Naming Conventions

| Entity type | Pattern | Example | Status |
|-------------|---------|---------|--------|
| `@RestController` | `*Controller` | `AllureReportController` | OK |
| Web (JTE) controller | `*WebController` / `*Controller` in `web/` | `ReportsWebController` | OK |
| `@Configuration` | `*Configuration` (!= `*Config`) | `SpringConfiguration` | OK |
| `@ConfigurationProperties` | `*Properties` | `AllureProperties` | OK |
| `@Service` / `@Component` | `*Service` | `ReportService` | OK |
| Spring `Converter` | `*Converter` | `PathConverter` | OK |
| Spring Data repository | `*Repository` | `ReportRepository` | DEBT: `JpaReportRepository` — drop `Jpa` prefix |
| `@Entity` | `*Entity` | `ReportEntity` | OK |
| Record value object | `*Model` | `MarkdownStatisticModel` | OK |
| REST DTO | `*Request` / `*Response` / `*Spec` | `ReportSpec`, `ReportResponse` | OK |
| Utility class | `*Util` (singular, etalon `PathUtil`) | `PathUtil` | DEBT: generic `Util.java` in `helper/` — split per concern |
| Plugin SPI impl | `*Plugin` | `CustomReportMetaPlugin` | OK |
| YAML keys | kebab-case | `history-level` | DEBT: mixed in `application.yaml` |

## 9. Constraints (open-source-grade)

| # | Rule |
|---|------|
| 1 | Java 25 only — `java { sourceCompatibility = VERSION_25 }` |
| 2 | SB 3 Jakarta EE — GEN must keep `useJakartaEe=true` |
| 3 | != `@Data` on `@Entity` — `@Getter` + `@Setter` + explicit `equals/hashCode` |
| 4 | != `jakarta.transaction.Transactional` in Spring services — use `org.springframework.transaction.annotation.Transactional` |
| 5 | != `@SneakyThrows` on public API (controllers, services, SPI) |
| 6 | != reflective plugin loading — inject `List<AllureServerPlugin>` via Spring |
| 7 | All secrets via env vars: `${NAME:default}` for overrides, `${NAME}` for required |
| 8 | All CFG keys kebab-case (`spring.jpa.hibernate.ddl-auto`, `allure.history-level`) |
| 9 | != hardcoded versions for BOM-managed artifacts (SB, SC); deliberate overrides documented in `versions.md` only |
| 10 | != regex rewrite of generated sources — prefer Mustache templates / `typeMappings` |
| 11 | DB schema must be migration-managed (Flyway); `ddl-auto` != `update` in release |
| 12 | != hand-edit generated `static/css/app.css`; edit `input.css` / templates -> `tailwindBuild` |
