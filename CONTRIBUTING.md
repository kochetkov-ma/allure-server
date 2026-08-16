# Contributing

Thanks for considering it. This document covers building, testing, commit and branch naming, and
what a pull request needs to be merged.

## Prerequisites

Git and a JDK. Java 25 is required to compile, but you do not need Java 25 installed. `build.gradle`
declares a Java 25 toolchain and `settings.gradle` applies the
`org.gradle.toolchains.foojay-resolver-convention` plugin, so Gradle downloads a matching JDK on the
first build if your `JAVA_HOME` points at a lower one. Any recent JDK is enough to launch the
wrapper.

Always use the Gradle wrapper, `./gradlew`. Do not use a locally installed `gradle`.

There is no Node, no npm and no `package.json`. The web UI is JTE templates precompiled by the JTE
Gradle plugin plus CSS built by the Tailwind standalone binary, which `tailwindDownload` fetches
into `build/tailwind/` automatically. Never add a JS toolchain.

## Build

```bash
./gradlew build
```

That is the whole thing: YouTrack OpenAPI codegen, JTE precompile, Tailwind CSS, compile, tests,
bootJar, and a CycloneDX SBOM written to `build/reports/bom.json` and `build/reports/bom.xml`.

Useful subsets:

```bash
./gradlew bootRun          # run locally on http://localhost:8080
./gradlew bootJar          # runnable jar -> build/libs/allure-server-*.jar
./gradlew openApiGenerate  # regenerate the YouTrack Feign client
./gradlew tailwindBuild    # rebuild static/css/app.css only
```

`build/generated/` is produced by `openApiGenerate` and `src/main/resources/static/css/app.css` is
produced by `tailwindBuild`. Both are generated, both are gitignored. Never hand-edit them and
never commit them. If the generated YouTrack client is wrong, fix the spec at
`src/test/resources/tms/openapi-youtrack.json` or the post-processing in `build.gradle`, then
regenerate.

## Test

```bash
./gradlew test
```

The current state on `master` is 38 test classes, 255 tests, 0 failures, 0 skips. A pull request
that lowers any of the first two numbers or raises either of the last two will not be merged.

One class:

```bash
./gradlew test --tests ru.iopump.qa.allure.helper.UtilTest
```

One method:

```bash
./gradlew test --tests "*.UtilTest.methodName"
```

JUnit 5 and AssertJ. Test style is not negotiable:

- GIVEN, WHEN and THEN comments marking the three parts of every test.
- `@DisplayName` phrased as "should {behavior} when {condition}".
- Concrete assertions. `isEqualTo(v)`, `hasSize(n)`, `containsExactly(...)`. A test whose final
  assertion is `isNotNull()` or `isNotEmpty()` asserts nothing useful and will be rejected.
- A description on every assertion: `assertThat(x).as("report id").isEqualTo(expected)`.
- No `if` inside a test. Assert the precondition, then assert unconditionally.
- No logging in tests. Assert the value or the side effect.

Never disable a test to make the build pass, and never run with `-x test`.

## Commits

Conventional commits, one line, imperative:

```
type(scope): summary
```

Allowed types: `feat`, `fix`, `hotfix`, `docs`, `refactor`, `ci`. Scope is the area touched, for
example `api`, `web`, `build`, `security`. A body is only needed when the reason is not obvious
from the diff.

```
feat(api): add since filter to report listing
fix(web): keep sort order after htmx swap
ci(release): attach the SBOM to the GitHub release
```

Do not add AI attribution or co-author trailers.

## Branches

Same type prefix, then a kebab-case summary:

```
feat/slack-thread-bridge
fix/report-cleanup-race
docs/contributing-guide
```

Branch from `master`. One task, one branch, one pull request. Do not split part of an open task
onto a second branch.

## Pull Requests

Open an issue first for anything beyond a typo fix or a small bug. It costs less to be told an
approach is wrong before the code exists.

A pull request that gets merged quickly looks like this:

- One focused change. Unrelated cleanup belongs in its own PR.
- `./gradlew build` passes locally before pushing.
- Tests covering the new behavior, written to the rules above.
- No formatting churn in files the change does not touch.
- A description saying what changed and why, and how it was verified.
- A pinned exact version for any new dependency, plugin, Docker tag or frontend asset, recorded in
  `.claude/convention/versions.md` in the same commit. Never `latest`, never a range.
- Documentation updated when behavior visible to a user changes.

CI runs `./gradlew build` on every push and pull request. A red build will not be reviewed.

## What Will Not Be Accepted

- A change without tests, when the change is testable.
- A reformat, rename or restructure with no behavior change and no prior discussion.
- Anything reintroducing Node, npm, pnpm or a JS bundler.
- Hand-edited files under `build/generated/`, or a committed `static/css/app.css`.
- A breaking change to the public REST API (`/api/result`, `/api/report`, the DTOs, config property
  names) without a deprecation path and a release note.
- A change to the Docker launcher. `-Dloader.path=/ext` with the Spring Boot `PropertiesLauncher`
  is what makes external plugin JARs load, and changing it breaks every deployment using them.
- A new dependency where a few lines of code would do.

## Code of Conduct

Participation is covered by [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).

## License

Contributions are licensed under Apache-2.0, the same as the project. Opening a pull request means
you agree to that.
