# Pinned Versions — allure-server

> SINGLE source of truth for every version number in this repo. != inline version literals in any other `.claude/**` doc — point here. != floating tags (`latest`, `+`, ranges). Verified against code 2026-06-11.

## Toolchain

| Component | Version | Source file |
|-----------|---------|-------------|
| Java (source + target + daemon toolchain) | 25 | `build.gradle` (`JavaVersion.VERSION_25`), `gradle/gradle-daemon-jvm.properties` |
| Gradle wrapper (ALL distribution) | 9.4.1 | `gradle/wrapper/gradle-wrapper.properties`, `build.gradle` `wrapper{}` |
| Docker build image | `eclipse-temurin:25-jdk-alpine` | `Dockerfile` stage 1 |
| Docker runtime image | `eclipse-temurin:25-jre-alpine` | `Dockerfile` stage 2 |

## Gradle plugins (`build.gradle`)

| Plugin | Version |
|--------|---------|
| `org.springframework.boot` | 3.4.13 |
| `io.spring.dependency-management` | 1.1.7 |
| `io.freefair.lombok` | 9.2.0 |
| `com.github.ben-manes.versions` | 0.54.0 |
| `org.openapi.generator` | 7.11.0 |
| `gg.jte.gradle` | 3.2.3 |

## BOMs + deliberate overrides (`build.gradle`)

| Item | Version | Why |
|------|---------|-----|
| Spring Cloud BOM (`ext.springCloudVersion`) | 2024.0.3 | Release train for Boot 3.4.x; Spring Cloud starters MUST be declared version-less |
| Byte Buddy + agent (`ext.byteBuddyVersion`) | 1.17.5 | Boot 3.4 BOM pins 1.15.11 (Java 24 max); 1.17.5+ required for Java 25 class-file version 69 |

## Libraries (`gradle/dependencies.gradle`)

| Library | Version | ext key |
|---------|---------|---------|
| `io.qameta.allure:allure-plugin-api` / `allure-generator` | 2.39.0 | `allureVersion` |
| `ru.iopump.qa:qa-tools` (transitive=false) | 1.2.0 | `qaLibVersion` |
| `io.github.classgraph:classgraph` | 4.8.184 | inline |
| `com.google.guava:guava` | 33.6.0-jre | `guavaVersion` |
| `commons-io:commons-io` | 2.21.0 | `apacheIoVersion` |
| `org.springdoc:springdoc-openapi-starter-webmvc-ui` | 2.8.17 | inline |
| `gg.jte:jte` + `gg.jte:jte-spring-boot-starter-3` | 3.2.3 | `jteVersion` |
| `org.jooq:joor` | 0.9.15 | inline |
| `openApiVersion` ext | 1.8.0 | DEBT: declared, never referenced — remove |

All other deps (Spring starters, security, JPA, hibernate-validator, h2, postgresql, logback, slf4j, assertj, spring-security-test) = BOM-managed, NO explicit version. Keep version-less.

## Frontend assets (`build.gradle` ext — no Node/npm anywhere)

| Asset | Version | ext key |
|-------|---------|---------|
| Tailwind CSS standalone binary | 3.4.19 | `tailwindVersion` (downloaded by `tailwindDownload` task per OS/arch) |
| HTMX | 2.0.9 | `htmxVersion` |
| Alpine.js | 3.15.11 | `alpineVersion` |

## Deploy example pins (`docker-compose.yml`, `docker-compose-h2.yml`, `.helm/allure-server/values.yaml`)

| Image | Tag |
|-------|-----|
| `kochetkovma/allure-server` | 2.13.9 (example pin — bump BOTH compose files + helm `image.tag` in lockstep on release) |
| `postgres` | 16.3-alpine |

## Update procedure

1. Fetch latest stable from the canonical registry (Maven Central / Docker Hub / GitHub releases).
2. Bump this table + the source file in the SAME commit (lockstep).
3. Java bump -> re-check Byte Buddy override requirement; Boot bump -> re-check Spring Cloud train compatibility.
