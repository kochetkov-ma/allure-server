# Compatibility: 2.13.9 -> 3.0.0

Every row is cited to a file and line on both refs. The 2.13.9 side is reachable with
`git show v2.13.9:<path>`; the 3.0.0 side is the working tree.

Legend: KEPT (contract unchanged), CHANGED (same name, different behaviour), ADDED, REMOVED.

## REST endpoints

| Endpoint | Status | v2.13.9 | 3.0.0 |
|---|---|---|---|
| `GET /api/report` | KEPT | `controller/AllureReportController.java:64-66` | `controller/AllureReportController.java:69-71` |
| `POST /api/report` | KEPT | `controller/AllureReportController.java:84-88` | `controller/AllureReportController.java:89-93` |
| `POST /api/report/{reportPath}` | KEPT | `controller/AllureReportController.java:107-112` | `controller/AllureReportController.java:112-117` |
| `DELETE /api/report` | KEPT | `controller/AllureReportController.java:163-166` | `controller/AllureReportController.java:171-174` |
| `DELETE /api/report/history` | KEPT | `controller/AllureReportController.java:149-152` | `controller/AllureReportController.java:142-145` |
| `DELETE /api/report/{uuid}` | ADDED | not present (class ends `controller/AllureReportController.java:188`) | `controller/AllureReportController.java:156-169` |
| `GET /api/result` | KEPT | `controller/AllureResultController.java:82-85` | `controller/AllureResultController.java:81-84` |
| `GET /api/result/{uuid}` | KEPT | `controller/AllureResultController.java:73-75` | `controller/AllureResultController.java:72-74` |
| `POST /api/result` | KEPT | `controller/AllureResultController.java:103-110` | `controller/AllureResultController.java:102-109` |
| `DELETE /api/result` | KEPT | `controller/AllureResultController.java:55-58` | `controller/AllureResultController.java:54-57` |
| `DELETE /api/result/{uuid}` | KEPT | `controller/AllureResultController.java:64-67` | `controller/AllureResultController.java:63-66` |
| `GET /swagger-ui.html` | KEPT | `resources/application.yaml:38-39` | `resources/application.yaml:71-72` |
| `GET /swagger`, `GET /api` (redirect to Swagger UI) | KEPT | `config/RedirectConfiguration.java:41-42` | `config/RedirectConfiguration.java:41-42` |
| `GET /` | CHANGED | redirects to `ui`, `config/RedirectConfiguration.java:43` | redirects to `/app/reports`, `config/RedirectConfiguration.java:43` |
| `GET /ui`, `/ui/`, `/ui/results`, `/ui/about`, `/ui/swagger` (Vaadin UI) | REMOVED | served by Vaadin, `resources/application.yaml:26` (`vaadin.urlMapping: "/ui/*"`) | these five literal paths redirect to `/app/reports`, `/app/reports`, `/app/results`, `/app/about` and the Swagger UI, `config/RedirectConfiguration.java:45-49`; there is no wildcard handler, so any other path under `/ui/` answers 404 |
| `GET /actuator/health` | ADDED | no actuator dependency (`build.gradle`, no `spring-boot-starter-actuator`) | `gradle/dependencies.gradle:36`, permitted pre-auth at `security/SecurityConfiguration.java:172` |

No endpoint under `/api` was removed or renamed.

## Request shapes

| Shape | Status | v2.13.9 | 3.0.0 |
|---|---|---|---|
| `ReportGenerateRequest.reportSpec` / `.results` / `.deleteResults` | KEPT (names, types, JSON) | `model/ReportGenerateRequest.java:23,28-29,33` | `model/ReportGenerateRequest.java:25-27,31-32,36` |
| `ReportGenerateRequest` validation | CHANGED (stricter, rejects payloads previously accepted) | `@NotNull` on `reportSpec` without `@Valid`, so `ReportSpec` constraints never ran; `@NotEmpty` plus per-element UUID `@Pattern` on `results`, `model/ReportGenerateRequest.java:23-24,28-29` | adds the `@Valid` cascade into `ReportSpec` and a per-element `@NotBlank` on `results`, `model/ReportGenerateRequest.java:25-26,31-32` |
| `ReportSpec.path` / `.executorInfo` | KEPT (names, types, JSON) | `model/ReportSpec.java:13-15` | `model/ReportSpec.java:18-22` |
| `ReportSpec` validation | CHANGED (stricter) | `@NotEmpty` declared but never enforced, no `@Valid` on the owning field, `model/ReportSpec.java:13` + `model/ReportGenerateRequest.java:23-24` | `@NotEmpty` now enforced through the cascade, plus `@Size(max = 32)` and a non-blank segment check, `model/ReportSpec.java:19,36-40` |
| `POST /api/result` multipart param `allureResults` | KEPT | `controller/AllureResultController.java:110-117` | `controller/AllureResultController.java:109-116` |
| `POST /api/report/{reportPath}` multipart param `allureReportArchive` | KEPT (param name); OpenAPI `@Parameter(name=...)` corrected from `allureResults` to the real name | `controller/AllureReportController.java:114-120` | `controller/AllureReportController.java:119-125` |
| `path` query param on `GET /api/report` | KEPT | `controller/AllureReportController.java:66` | `controller/AllureReportController.java:71` |
| `seconds` query param on `DELETE /api/report` | KEPT | `controller/AllureReportController.java:166` | `controller/AllureReportController.java:174` |

## Response shapes

`ReportResponse`, `ResultResponse` and `UploadResponse` are byte-identical on both refs
(verified with `shasum` of `git show v2.13.9:<path>` against the working tree).

| Shape | Status | v2.13.9 | 3.0.0 |
|---|---|---|---|
| `ReportResponse{uuid,path,url,latest}` | KEPT | `model/ReportResponse.java:12-17` | `model/ReportResponse.java:12-17` |
| `ResultResponse{uuid,size,created}` | KEPT | `model/ResultResponse.java:1-24` | `model/ResultResponse.java:1-24` |
| `UploadResponse{uuid,fileName}` | KEPT | `model/UploadResponse.java:1-11` | `model/UploadResponse.java:1-11` |
| `ResultResponse.created` time zone | KEPT (UTC, source moved off the Vaadin helper) | `gui/DateTimeResolver.zeroZone()`, `controller/AllureResultController.java:44,91` | `ZoneOffset.UTC`, `controller/AllureResultController.java:38,90` |
| Error body | CHANGED | Spring Boot default error JSON; the only handler is `response.sendError(400)`, `controller/AllureResultController.java:140-143` and `controller/AllureReportController.java:184-187` | RFC 7807 `application/problem+json`, `controller/GlobalExceptionHandler.java:25-33,137-142` |

## Status codes

| Case | Status | v2.13.9 | 3.0.0 |
|---|---|---|---|
| `POST /api/result` success -> 201 | KEPT | `controller/AllureResultController.java:108` | `controller/AllureResultController.java:107` |
| `POST /api/report` success -> 201 | KEPT | `controller/AllureReportController.java:86` | `controller/AllureReportController.java:91` |
| `POST /api/report/{reportPath}` success -> 201 | KEPT | `controller/AllureReportController.java:110` | `controller/AllureReportController.java:115` |
| `GET`/`DELETE` success -> 200 | KEPT | no `@ResponseStatus`, `controller/AllureResultController.java:56,65,74,83` | no `@ResponseStatus`, `controller/AllureResultController.java:55,64,73,82` |
| `DELETE /api/report/{uuid}` -> 204, 404 when absent | ADDED | not present | `controller/AllureReportController.java:157-162`, 404 raised at `service/JpaReportService.java:121-122` |
| `DELETE /api/result/{uuid}` on an unknown uuid | CHANGED 500 -> 404 | no existence check, `FileUtils.sizeOfDirectory` on a missing directory throws `UncheckedIOException`, `service/ResultService.java:48-50`; unhandled, only `ConstraintViolationException` is mapped, `controller/AllureResultController.java:140-143` | `isDirectory` guard raises `ResponseStatusException(NOT_FOUND)`, `service/ResultService.java:68-73`, rendered as problem+json at `controller/GlobalExceptionHandler.java:98-110`; same shape as the report side, `service/JpaReportService.java:121-122` |
| `DELETE /api/result/{uuid}` with a malformed uuid -> 400 | KEPT | `@Pattern(regexp = PathUtil.UUID_PATTERN)`, `controller/AllureResultController.java:68` | `@Pattern(regexp = PathUtil.UUID_PATTERN)`, `controller/AllureResultController.java:67` |
| `GET /api/result/{uuid}` on an unknown uuid -> 200 with `{"size":0}` | KEPT (deliberate) | `.orElse(ResultResponse.builder().build())`, `controller/AllureResultController.java:75-79` | `.orElse(ResultResponse.builder().build())`, `controller/AllureResultController.java:74-78` |
| Path-variable constraint violation -> 400 | KEPT | `controller/AllureResultController.java:140-143` | `controller/GlobalExceptionHandler.java:54-72` |
| `@Valid` body rejection -> 400 | KEPT | Spring default `MethodArgumentNotValidException` handling | `controller/GlobalExceptionHandler.java:39-52` |
| Bad upload Content-Type or non-`.zip` filename on `POST /api/result` | CHANGED 500 -> 400 | `Preconditions.checkArgument` throws `IllegalArgumentException`, unhandled (only `ConstraintViolationException` is mapped), `controller/AllureResultController.java:124-131` + `:140-143` | `ResponseStatusException(BAD_REQUEST)`, `controller/AllureResultController.java:119,132-148`, mapped at `controller/GlobalExceptionHandler.java:98-110` |
| Bad upload Content-Type or non-`.zip` filename on `POST /api/report/{reportPath}` | CHANGED 500 -> 400 | `Preconditions.checkArgument` throws `IllegalArgumentException`, unhandled, `controller/AllureReportController.java:125-134` + `:184-187` | `ResponseStatusException(BAD_REQUEST)`, `controller/AllureReportController.java:127,197-213`, mapped at `controller/GlobalExceptionHandler.java:98-110` |
| Empty multipart file -> 400 | ADDED | not checked | `controller/AllureResultController.java:133-136`, `controller/AllureReportController.java:198-201` |
| Blank `{reportPath}` -> 400 | ADDED | not checked, `controller/AllureReportController.java:113` | `@NotBlank`, `controller/AllureReportController.java:118` |
| Upload over the multipart limit -> 413 | ADDED | not mapped | `controller/GlobalExceptionHandler.java:82-88` |
| Unhandled exception -> 500 | KEPT | Spring default | `controller/GlobalExceptionHandler.java:126-135` |

`GET /api/result/{uuid}` on a uuid that does not exist answers 200 with an empty
`ResultResponse` (`size` 0, no `uuid`, no `created`) on both refs, because both call
`.orElse(ResultResponse.builder().build())` (`controller/AllureResultController.java:75-79`
in 2.13.9, `controller/AllureResultController.java:74-78` in 3.0.0). That is 2.x observable
behaviour, so it was left alone on purpose in a compatibility release. Do not change it to
404 here. If it should become 404, that is a separate breaking change with its own
deprecation note, not a fix folded into 3.0.0.

The `DELETE /api/result/{uuid}` row above is the reverse case: 2.13.9 answered 500 there
because of an unguarded `FileUtils.sizeOfDirectory` on a missing directory
(`service/ResultService.java:48-50`), never a documented contract. Turning that into 404
breaks nothing a client could have relied on.

## Auth modes

| Mode | Status | v2.13.9 | 3.0.0 |
|---|---|---|---|
| Anonymous `/api/**` by default, request sends no credentials | KEPT (200) | filter chain only gates when `enableAnyAuth`, `security/SecurityConfiguration.java:36,48-52` | `/api/**` gated by the runtime toggle, default `false`, `security/SecurityConfiguration.java:189-190,209`, `resources/application.yaml:66` |
| Anonymous `/api/**` by default, request sends HTTP Basic credentials | CHANGED, BREAKING (200 -> 403 or 401) | Basic filter not registered unless `basic.auth.enable=true`, `security/SecurityConfiguration.java:58-60`, default `false`, `resources/application.yaml:34`, so the header was ignored and the request was served anonymously | Basic is always registered and the header is always evaluated, `security/SecurityConfiguration.java:232`; the default `admin`/`admin` gives 403 (`config/UserSeeder.java:106,133,151-153`, `security/SecurityConfiguration.java:165`, `security/ApiTempPasswordGuardFilter.java:87-91`), any other password for a non-matching credential gives 401; proven by `src/test/java/ru/iopump/qa/allure/security/AlwaysOnAuthIntegrationTest.java:142-166` |
| HTTP Basic credential source | CHANGED | in-memory user built from `basic.auth.*`, `security/SecurityConfiguration.java:65-73` | database users, `security/DbUserDetailsService.java`, seeded once at `config/UserSeeder.java:89-144` |
| HTTP Basic availability | CHANGED | only when `basic.auth.enable=true`, `security/SecurityConfiguration.java:58-60` | always registered, `security/SecurityConfiguration.java:232`, which is what makes the row above breaking |
| `basic.auth.enable=true` locks the whole surface | KEPT (explicit compatibility shim) | `security/SecurityConfiguration.java:48-52` | `security/SecurityConfiguration.java:173-181` |
| OAuth2 login (`oauth` profile) | KEPT | `security/SecurityConfiguration.java:54-56` | `security/SecurityConfiguration.java:219-221` |
| `X-API-Token` header auth | ADDED | not present | `security/ApiTokenAuthenticationFilter.java:49`, wired at `security/SecurityConfiguration.java:142` |
| CSRF on `/api/**` | KEPT (exempt) | CSRF disabled globally, `security/SecurityConfiguration.java:45` | CSRF on for `/app/**`, `/api/**` and `/allure/**` exempt, `security/SecurityConfiguration.java:137-139` |
| `/allure/**` report content readable anonymously | KEPT by default | `security/SecurityConfiguration.java:48-52` | `security/SecurityConfiguration.java:189` |

## Configuration properties

| Property | Status | v2.13.9 | 3.0.0 |
|---|---|---|---|
| `spring.servlet.multipart.max-file-size`, `max-request-size` | KEPT | `resources/application.yaml:7-8` | `resources/application.yaml:7-8` |
| `spring.datasource.*`, `spring.jpa.*` | KEPT | `resources/application.yaml:9-18` | `resources/application.yaml:16-25` |
| `spring.cloud.openfeign.*` | KEPT | `resources/application.yaml:19-24` | `resources/application.yaml:26-31` |
| `server.port` | KEPT | `resources/application.yaml:27` | `resources/application.yaml:33` |
| `springdoc.swagger-ui.path` | KEPT | `resources/application.yaml:38-39` | `resources/application.yaml:71-72` |
| `allure.title`, `logo`, `resultsDir`, `reports.*`, `support-old-format`, `date-format`, `clean.*`, `server-base-url` | KEPT | `resources/application.yaml:52-71` | `resources/application.yaml:89-113` |
| `tms.enabled`, `host`, `api-base-url`, `issue-key-pattern`, `dry-run` | KEPT | `resources/application.yaml:82-88` | `resources/application.yaml:123-129` |
| `basic.auth.enable` | KEPT, deprecated | `resources/application.yaml:34`, `properties/BasicProperties.java:23` | `resources/application.yaml:59`, deprecation note `resources/application.yaml:49-54` |
| `basic.auth.username` | CHANGED (seeds the admin row once; env override) | `resources/application.yaml:32` -> in-memory user `security/SecurityConfiguration.java:68` | `resources/application.yaml:57` -> `config/UserSeeder.java:97,128` |
| `basic.auth.password` | CHANGED (seeds the admin row once; env override; not authoritative afterwards) | `resources/application.yaml:33` -> in-memory user `security/SecurityConfiguration.java:69` | `resources/application.yaml:58` -> `config/UserSeeder.java:132`, skipped when an admin row exists `config/UserSeeder.java:89-93,123-125` |
| `tms.token` | CHANGED (no literal default) | `"my-token"`, `resources/application.yaml:86` | `${TMS_TOKEN:}`, `resources/application.yaml:127` |
| `app.security.enable-oauth2` | KEPT | `@Value("${app.security.enable-oauth2:false}")`, `security/SecurityConfiguration.java:32` | `resources/application.yaml:67`, `properties/AppSecurityProperties.java:24` |
| `app.security.require-api-auth` | ADDED (first-boot default only; DB wins afterwards) | not present | `resources/application.yaml:66`, `properties/AppSecurityProperties.java:23`, `security/SecurityConfiguration.java:292` |
| `allure.upload.max-uncompressed-bytes`, `allure.upload.max-entries` | ADDED | not present | `resources/application.yaml:97-99`, `properties/AllureProperties.java:91-114` |
| `spring.mvc.hiddenmethod.filter.enabled` | ADDED | not present | `resources/application.yaml:13-15` |
| `gg.jte.development-mode`, `gg.jte.use-precompiled-templates` | ADDED | not present | `resources/application.yaml:39-42` |
| `springdoc.swagger-ui.custom-css-url`, `custom-js-url` | ADDED | not present | `resources/application.yaml:73-76` |
| `vaadin.urlMapping` | REMOVED | `resources/application.yaml:26` | not present |
| `logging.level.org.atmosphere` | REMOVED | `resources/application.yaml:75` | not present |

## Upgrading from 2.x

`/api/report` and `/api/result` are unchanged. No endpoint was removed and no request or
response field was renamed. Six things behave differently, and one of them, HTTP Basic
credentials on `/api/**`, breaks a 2.x client that would otherwise need no edits.

**The Vaadin UI at `/ui/*` is gone.** It is replaced by a server-rendered UI at `/app/*`.
Old bookmarks are redirected: `/ui` and `/ui/` go to `/app/reports`, `/ui/results` to
`/app/results`, `/ui/about` to `/app/about`, `/ui/swagger` to the Swagger UI. `/` now goes
to `/app/reports` instead of `/ui`.

**Error responses are now RFC 7807 `application/problem+json`.** The body carries `type`,
`title`, `status`, `detail` and `instance`, plus an `errors` map for validation failures.
Two upload error paths that returned HTTP 500 in 2.13.9 now return 400: a wrong
`Content-Type` or a filename not ending in `.zip` on `POST /api/result`, and the same two
checks on `POST /api/report/{reportPath}`. In 2.13.9 both threw an unhandled
`IllegalArgumentException`; they are now explicit 400s. `DELETE /api/result/{uuid}` on a
uuid that does not exist moves the same way, from 500 to 404: 2.13.9 ran
`FileUtils.sizeOfDirectory` on a missing directory and let the resulting
`UncheckedIOException` escape (`service/ResultService.java:48-50`), 3.0.0 checks first and
raises 404 (`service/ResultService.java:68-73`). Clients that treated 500 as retryable
should stop retrying these. This is deliberate and there is no opt-out.

```
HTTP/1.1 400 Bad Request
Content-Type: application/problem+json

{"type":"about:blank","title":"Bad Request","status":400,
 "detail":"File must have '.zip' extension but was 'results.tar'",
 "instance":"/api/result"}
```

**`POST /api/report` now validates `reportSpec`, which 2.13.9 never did.** The field names,
types and JSON are unchanged. In 2.13.9 `reportSpec` carried `@NotNull` but no `@Valid`
(`v2.13.9:model/ReportGenerateRequest.java:23-24`), so bean validation never descended into
`ReportSpec`, whose only constraint was `@NotEmpty` on `path`
(`v2.13.9:model/ReportSpec.java:13-14`). 3.0.0 adds the `@Valid` cascade
(`model/ReportGenerateRequest.java:25-26`), so three payloads that 2.13.9 accepted now
return 400.

A blank `path` segment is the reachable one. `{"path":[""]}` was accepted by 2.13.9 and
generated a report; it now fails the `@AssertTrue isPathSegmentsNotBlank` check
(`model/ReportSpec.java:37`). That is exactly the shape an unresolved CI variable expands
to, because pipelines commonly build `path` from environment variables that can be empty,
so check that every segment you send is non-blank before upgrading. A `path` longer than 32
segments is also new, from `@Size(max = 32)` (`model/ReportSpec.java:19`), which was
unbounded in 2.13.9. An empty `path` array is the third, now that the declared `@NotEmpty`
actually runs.

The `results` list is not a concern: its per-element UUID `@Pattern` was already enforced
in 2.13.9 (`v2.13.9:model/ReportGenerateRequest.java:29`) and 3.0.0 only adds `@NotBlank` alongside
it (`model/ReportGenerateRequest.java:32`), which the pattern already rejected.

**HTTP Basic credentials sent to `/api/**` are now evaluated, and can be rejected.** This
is the one change that breaks a working 2.x call. In 2.13.9 the Basic filter was registered
only when `basic.auth.enable=true` (`v2.13.9:security/SecurityConfiguration.java:58-60`)
and that property defaulted to `false` (`v2.13.9:resources/application.yaml:34`), so an
`Authorization: Basic` header on `/api/**` was ignored and the request was served
anonymously with 200. In 3.0.0 Basic is always registered
(`security/SecurityConfiguration.java:232`), so the header is evaluated even on a route
that is otherwise open. Only callers that send Basic credentials are affected. A call that
sends no credentials behaves exactly as it did in 2.x and still returns 200.

Two outcomes replace that 200. `-u admin:admin` returns 403: the seeder flags the
administrator password as temporary while it is still the shipped default, whatever route
supplied it (`config/UserSeeder.java:106,133,151-153`), and the API guard filter blocks a
temporary-password principal on the stateless surface
(`security/SecurityConfiguration.java:165`,
`security/ApiTempPasswordGuardFilter.java:87-91`). `-u admin:` with any other value returns
401, because failed Basic authentication invokes the entry point before the permitted route
is reached. The 403 path is covered by
`src/test/java/ru/iopump/qa/allure/security/AlwaysOnAuthIntegrationTest.java:142-166`.

Worst affected is a 2.13.9 deployment that ran with `basic.auth.enable=true` and the
default password: that combination authenticated on 2.x and returns 403 on 3.0.0. There are
two recovery paths, and neither is config-only while the client keeps sending credentials.
Either drop the `Authorization` header from the call, which restores the anonymous 200, or
set a non-default `BASIC_AUTH_PASSWORD` before the first boot and update the client to send
the new value. Keeping `admin`/`admin` is not an option, because that value is flagged
temporary however it is supplied (`config/UserSeeder.java:151-153`). Setting
`BASIC_AUTH_PASSWORD` after the first boot has no effect on an existing installation; there
the password is rotated at `/app/profile/password`.

**`basic.auth.password` only seeds the administrator row on first boot.** Users now live in
the database. On the first startup an administrator is created from `basic.auth.username`
and `basic.auth.password`; after that the database is authoritative and changing the
property has no effect. Change the password at `/app/profile/password` or through
`/app/admin/users`. If the password is still the shipped default `admin`, the first login
is forced to rotate it. `basic.auth.enable=true` still locks the whole surface, including
`/api/**`, exactly as in 2.x, but it is deprecated; prefer the runtime "require API auth"
toggle at `/app/admin/settings`.

`tms.token` no longer defaults to the literal `my-token`. It now defaults to empty and
reads `TMS_TOKEN`, so a YouTrack integration that relied on the shipped placeholder must
supply a real token.

Uploaded archives are also capped at 100000 entries
(`properties/AllureProperties.java:99-100`, enforced at `service/ResultService.java:169-186`,
not present in 2.13.9), which is roughly 20000 to 30000 test cases at the usual Allure
file-per-test ratio and is raised with `ALLURE_UPLOAD_MAX_ENTRIES`
(`resources/application.yaml:97-99`).
