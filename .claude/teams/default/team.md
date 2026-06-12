# Team: default

| Field | Value |
|-------|-------|
| Created | 2026-04-19 |
| Last update | 2026-04-19 |
| Agents | 10 |
| Project | /Users/maximus/IdeaProjects/allure-server |

## Agents

| Agent | Domain | Mission | Status | Updated |
|-------|--------|---------|--------|---------|
| rest-controller | controller/*.java | REST endpoints, validation, caching, @ExceptionHandler | active | 2026-04-19 |
| dto-model | model/*.java | REST DTOs (records preferred), bean validation, Swagger @Schema | active | 2026-04-19 |
| report-service | service/JpaReportService, entity/, repo/ | Report lifecycle, caching, cleanup scheduler, redirect registration | active | 2026-04-19 |
| result-service | service/ResultService, PathUtil, MoveFileVisitor | Upload intake, ZIP extraction, filesystem ops | active | 2026-04-19 |
| generation-pipeline | helper/AllureReportGenerator + plugin SPI | Allure core integration, plugin lifecycle dispatch | active | 2026-04-19 |
| plugin-youtrack | helper/plugin/YouTrackPlugin + api/youtrack/* | TMS integration, Feign client, OpenAPI codegen | active | 2026-04-19 |
| vaadin-gui | gui/view, gui/component, gui/dto | Vaadin 24 UI, dialogs, grids, /ui routing | active | 2026-04-19 |
| config-security | properties/, config/, security/ | @ConfigurationProperties, SecurityFilterChain, OAuth2/Basic | active | 2026-04-19 |
| persistence-jpa | entity/, repo/, migration.sql | JPA schema shape, derived queries, datasource config | active | 2026-04-19 |
| build-ci-qa | build.gradle, .github/workflows, Dockerfile, tests | Gradle, CI/CD, Docker, JUnit 5/AssertJ test infra | active | 2026-04-19 |
