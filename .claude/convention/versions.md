# Pinned Versions — allure-server

> SINGLE source of truth for every version number in this repo. != inline version literals in any other `.claude/**` doc — point here. != floating tags (`latest`, `+`, ranges). Verified against code 2026-06-11.

## Toolchain

| Component | Version | Source file |
|-----------|---------|-------------|
| Java (toolchain + daemon toolchain) | 25 | `build.gradle` (`java { toolchain { languageVersion = 25 } }`), `gradle/gradle-daemon-jvm.properties` |
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
| `org.cyclonedx.bom` | 3.4.1 |

`org.cyclonedx.bom` emits the CycloneDX 1.6 SBOM. `build` dependsOn `cyclonedxBom`, and `xmlOutput`/`jsonOutput` are pinned to `build/reports/bom.xml` + `bom.json` — the exact task name and paths `.github/workflows/release.yml` attaches as release assets. Bumping the plugin -> re-verify both.

## Gradle settings plugins (`settings.gradle`)

| Plugin | Version | Why |
|--------|---------|-----|
| `org.gradle.toolchains.foojay-resolver-convention` | 1.0.0 | Auto-provisions the Java 25 toolchain when the host JDK differs |

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
| `kochetkovma/allure-server` | 3.0.0 — pinned in `docker-compose.yml` / `docker-compose-h2.yml` (`image:` + `build.args.APP_VERSION`, both files carry an active `build: .`, so the tag names the locally built image) and in `.helm/allure-server/Chart.yaml` `appVersion` (helm `values.yaml` `image.tag` is intentionally empty and falls back to `.Chart.AppVersion`). Chart `version` tracks `appVersion` 1:1 |
| `postgres` | 16.15-alpine (`docker-compose.yml`) |

## GitHub Actions (`.github/workflows/*.yml` — SHA-pinned, comment carries the tag)

| Action | Version | Commit SHA | Used by |
|--------|---------|------------|---------|
| `actions/checkout` | v4.4.0 | `11d5960a326750d5838078e36cf38b85af677262` | release, codeql, security-scan, check, branch-image |
| `actions/setup-java` | v4.9.1 | `cf277c60eb25467037889841efdb72551f06f6c3` | release, codeql, check |
| `gradle/actions/setup-gradle` | v4.4.4 | `748248ddd2a24f49513d8f472f81c3a07d4d50e1` | release, codeql, check |
| `docker/setup-qemu-action` | v3.7.0 | `c7c53464625b32c7a7e944ae62b3e17d2b600130` | release |
| `docker/setup-buildx-action` | v3.12.0 | `8d2750c68a42422c14e847fe6c8ac0403b4cbd6f` | release, security-scan, branch-image |
| `docker/login-action` | v3.7.0 | `c94ce9fb468520275223c153574b00df6fe4bcc9` | release, branch-image |
| `docker/build-push-action` | v6.19.2 | `10e90e3645eae34f1e60eeb005ba3a3d33f178e8` | release, security-scan, branch-image |
| `sigstore/cosign-installer` | v4.1.2 | `6f9f17788090df1f26f669e9d70d6ae9567deba6` | release |
| `peter-evans/dockerhub-description` | v5.0.0 | `1b9a80c056b620d92cedb9d9b5a223409c68ddfa` | release |
| `softprops/action-gh-release` | v2.6.2 | `3bb12739c298aeb8a4eeaf626c5b8d85266b0e65` | release |
| `github/codeql-action` (`init`, `analyze`, `upload-sarif`) | v4.37.7 | `ff2f1c621b7f889edc0d3c761ac2e6a3f8cdb0dd` | codeql, security-scan |
| `aquasecurity/trivy-action` | v0.36.0 | `ed142fd0673e97e23eac54620cfb913e5ce36c25` | security-scan |
| `actions/upload-artifact` | v7.0.1 | `043fb46d1a93c77aae656e7c1c64a875d1fc6a0a` | security-scan |

All five workflows are fully SHA-pinned — no floating major refs remain.

## Dependabot ecosystems (`.github/dependabot.yml`)

`gradle` + `github-actions` only. NO npm/yarn ecosystem: there is no Node and no `package.json` in this repo — Tailwind is a standalone binary downloaded by the `tailwindDownload` Gradle task, HTMX and Alpine.js are version-pinned `ext` properties in `build.gradle`, so every frontend asset is already covered by the `gradle` ecosystem.

SHAs resolved from the GitHub API for the exact tag (annotated tags dereferenced to the commit). Bump = re-resolve the SHA and update BOTH this table and the `uses:` comment. `actionlint` 1.7.12 validates the workflows.

## Update procedure

1. Fetch latest stable from the canonical registry (Maven Central / Docker Hub / GitHub releases).
2. Bump this table + the source file in the SAME commit (lockstep).
3. Java bump -> re-check Byte Buddy override requirement; Boot bump -> re-check Spring Cloud train compatibility.
