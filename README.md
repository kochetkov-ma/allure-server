Allure Portal (Allure Report Server)
=================================
![Build / Test / Check](https://github.com/kochetkov-ma/allure-server/workflows/Build%20/%20Test%20/%20Check/badge.svg?branch=master)
![License](https://img.shields.io/github/license/kochetkov-ma/allure-server)
![Latest release](https://img.shields.io/github/v/release/kochetkov-ma/allure-server)

![Static Badge](https://img.shields.io/badge/java-25-brightgreen)
![Static Badge](https://img.shields.io/badge/gradle-9.4.1-brightgreen)
![Static Badge](https://img.shields.io/badge/Spring%20Boot-3-green)

![Docker Image Version](https://img.shields.io/docker/v/kochetkovma/allure-server?label=DockerHub&link=https%3A%2F%2Fhub.docker.com%2Fr%2Fkochetkovma%2Fallure-server)
![Docker Pulls](https://img.shields.io/docker/pulls/kochetkovma/allure-server?link=https%3A%2F%2Fhub.docker.com%2Fr%2Fkochetkovma%2Fallure-server)

[Security policy](https://github.com/kochetkov-ma/allure-server/blob/master/SECURITY.md) | [Contributing](https://github.com/kochetkov-ma/allure-server/blob/master/CONTRIBUTING.md) | [Code of conduct](https://github.com/kochetkov-ma/allure-server/blob/master/CODE_OF_CONDUCT.md)

## About

https://allurereport.org/docs

Allure server for store / aggregate / manage Allure results and generate / manage Allure Reports.

The server ingests **Allure 2** input only. Upload `allure-results` produced by an Allure 2
adapter; Allure 1 (`allure-results` in the XML format) and Allure 3 input are not supported.

There is simple API with Swagger(OpenAPI) Description.

Just use Spring Boot Jar from Release Page.

A server-rendered Web UI is available since v2.0.0.

## Get Started

### Docker

There is a docker image on Docker Hub: [allure-server](https://hub.docker.com/r/kochetkovma/allure-server)

```shell
mkdir -p allure-server-store ext
chown -R 1000:1000 allure-server-store ext

docker run -d --name allure-server \
  -p 8080:8080 \
  -v "$PWD/allure-server-store:/allure:rw" \
  -v "$PWD/ext:/ext:rw" \
  kochetkovma/allure-server:3.0.0
```

The container runs as non-root uid/gid `1000` (`Dockerfile:35`, `Dockerfile:48`), so a host bind
mount must be owned by `1000:1000` before the first start, otherwise the container cannot write.
Named volumes need no `chown`.

`/allure` is the working directory and holds all application data: the H2 database file, the
unpacked results and the generated reports. `/ext` is optional and holds external plugin jars
(`-Dloader.path=/ext`).

Images are also published to GitHub Container Registry (GHCR) at
`ghcr.io/kochetkov-ma/allure-server`: release tags (e.g. `:latest` and the version
tag) are pushed by the release workflow, and a separate image is built and pushed
for every branch push.

### Kubernetes

Use the Helm chart in **[.helm/allure-server/](https://github.com/kochetkov-ma/allure-server/blob/master/.helm/allure-server/README.md)**:

```shell
helm upgrade --install allure-server .helm/allure-server -f my-values.yaml
```

`image.tag` in `values.yaml` is empty and falls back to the chart `appVersion`
(`.helm/allure-server/values.yaml:7`, `.helm/allure-server/Chart.yaml`). Set `image.tag` to pin an
explicit release. The startup, liveness and readiness probes hit `/actuator/health`
(`.helm/allure-server/templates/deployment.yaml:44-61`), which is permitted before authentication
in every auth mode.

The shipped `values.yaml` still carries the maintainer's own deployment as an example, so override
these before installing anywhere else:

- `ingress.hosts[].name` and `ingress.tls[].hosts` - placeholders `allure-server.example.com` and `example.com`.
- `ingress.tls[].secretName` and `ingress.annotations."cert-manager.io/cluster-issuer"` - `letsencrypt-prod`.
- `env.SPRING_DATASOURCE_URL` - a Yandex Cloud PostgreSQL JDBC URL.
- `pvc.spec.storageClassName` - `yc-network-hdd`.
- `databaseCrt.crt.sourcePath` - points at the sample `crt/CA-SAMPLE.pem`; set `databaseCrt.enabled: false` if you do not need a database CA certificate.

Database credentials come from a Kubernetes secret you create yourself in the target namespace.
`secret.name` is commented out in `values.yaml`, so the chart falls back to the hardcoded default
`allure-server-env-var-secret` and always renders `secretKeyRef` entries for the keys listed under
`secret.keys` (`SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`). Create a secret with
that exact name and those keys before installing, or the pod stays in `CreateContainerConfigError`.
Uncomment `secret.name` to point at a differently named secret.

### Jar

Get the latest release [Releases](https://github.com/kochetkov-ma/allure-server/releases)   
Download `allure-server.jar`  
Update your jre(jdk) up to [Java 25](https://adoptium.net/temurin/releases/?version=25)  
Execute command `java -jar allure-server.jar`

Data is written relative to the working directory (`./allure/db`, `./allure/results/`,
`./allure/reports/`), so run the jar from the directory you want to keep. External plugin jars are
picked up with `java -Dloader.path=/ext -jar allure-server.jar`.

Go to `http://localhost:8080` - the root path `/` redirects to the Web UI at `/app/reports`.
The OpenAPI (Swagger UI) is available at `http://localhost:8080/swagger-ui.html`.

### Authentication and Access Control

Authentication is **always on** and backed by the database. There is no switch to
run the server with security disabled.

#### Users and roles

Three roles exist:

- `GUEST` — read-only fallback used for anonymous visitors (no login).
- `USER` — can upload results and generate / delete reports.
- `ADMIN` — everything, plus user and settings management.

#### Default credentials and the first login

On first boot the server seeds one main administrator with the username `admin` and the password
`admin`, and marks that password as temporary. Login is HTTP Basic; there is no login form. The
browser prompts for credentials on any protected page, and `/app/signin` exists as an explicit
trigger: it is `authenticated()` in the security chain, so an unauthenticated request to it returns
`401` with `WWW-Authenticate: Basic realm="Allure Server"` and an authenticated one redirects to
`/app/reports`.

While the password is still temporary, two things happen that are worth knowing before you start:

Every `/app/**` request made with `admin`/`admin` returns `302` to `/app/profile/password?forced=true`.
That is the profile page, not `/app/admin/users`. `/app/admin/users` manages other accounts and is
itself redirected until the rotation is done. The rotation page itself returns `200`, so the redirect
terminates there rather than looping.

Requests to `/api/**` made with `admin`/`admin` return `403`, not `200`. `ApiTempPasswordGuardFilter`
rejects any Basic or session principal whose password is still temporary, with the message
`Password rotation required before API access`. So the seeded credential works for the web UI and is
refused by the REST API until it is changed. `X-API-Token` requests are exempt from that guard.

Rotate the password by posting the `/app/profile/password` form with the CSRF token and the
`currentPassword`, `newPassword` and `confirmPassword` fields. The new password must be at least 8
characters (`PasswordChangeService.MIN_PASSWORD_LENGTH`) and must match its confirmation. A
successful post redirects to `/app/reports`. After that the old password returns `401` everywhere and
the new one works on `/app/**`, `/app/admin/users` and `/api/**`. A wrong password is `401` at every
stage, so authentication failures and the temporary-password block are distinguishable by status code.

Manage every other account at `/app/admin/users`. The seed values come from `BASIC_AUTH_USERNAME` and
`BASIC_AUTH_PASSWORD` (`src/main/resources/application.yaml:57-58`); set them before the first start
to seed something else. Once the administrator row exists the database is authoritative and changing
those variables has no effect.

This is self-hosted software. Changing the default credentials is the operator's responsibility.

#### Read access, guest mode and the require-api-auth toggle

By default (`app.security.require-api-auth=false`) the server is open and
guest-readable: `/api/**` and the generated report content under `/allure/**` are
reachable anonymously through the read-only `GUEST` fallback. An admin can flip the
runtime **require API auth** toggle at `/app/admin/settings` to require an
authenticated, non-guest principal for those paths. This toggle is
runtime-authoritative — its value lives in the database, and
`app.security.require-api-auth` only seeds the initial value on first startup.

Mutations (upload, generate, delete, token minting, password change) always
require an authenticated, non-guest user, regardless of the toggle.

#### API tokens

REST clients authenticate with a personal API token instead of a password. Mint
and revoke tokens on the Profile page at `/app/profile`. The plaintext token value
is shown **exactly once** at creation — copy it immediately. The number of active
tokens per user is capped per role.

Send the token in the `X-API-Token` header (no `Bearer` prefix):

```shell
curl -H "X-API-Token: <token>" 'http://localhost:8080/api/report'
```

Add this header to any REST call below when `require-api-auth` is enabled;
otherwise the request returns `401 Unauthorized`.

#### OAuth2

An optional `oauth` Spring profile adds OAuth2 login (Google by default), gated by
`app.security.enable-oauth2` (default `false`). See the OAuth2 feature section below.

#### Legacy basic.auth.enable (deprecated)

`basic.auth.enable` is **deprecated but still honored**. When set to `true` it
restores the legacy "lock everything" behavior: every request except public static
assets requires authentication, including `/api/**` and `/allure/**`, regardless of
the require-api-auth toggle. Prefer the runtime toggle above and leave this flag
`false`.

### Upload results or use [GitHub Actions](#github-actions)

Only allure2 supported  
Make some allure results and create `zip` archive with these results, for example `allure-results.zip` in your root dir

```shell
curl -X POST 'http://localhost:8080/api/result' \
-H  "accept: */*" \
-H  "X-API-Token: <token>" \
-H  "Content-Type: multipart/form-data" \
-F "allureResults=@allure-results.zip;type=application/x-zip-compressed"
```

Response:

```json
{
    "fileName": "allure-results.zip",
    "uuid": "1037f8be-68fb-4756-98b6-779637aa4670"
}
```

Save `uuid`  
Don't forget specify form item Content type as `application/zip`. Server works with `zip` archives only!

### Generate report

For generate new report execute `POST` request with `json` body:

```shell
curl --location --request POST 'http://localhost:8080/api/report' \
--header 'X-API-Token: <token>' \
--header 'Content-Type: application/json' \
--data-raw '{
  "reportSpec": {
    "path": [
      "master",
      "666"
    ],
    "executorInfo": {
      "buildName": "#666"
    }
  },
  "results": [
    "1037f8be-68fb-4756-98b6-779637aa4670"
  ],
  "deleteResults": false
}'
```

Response:

```json
{
    "uuid": "c994654d-6d6a-433c-b8e3-90c77d0e8163",
    "path": "master/666",
    "url": "http://localhost:8080/allure/reports/c994654d-6d6a-433c-b8e3-90c77d0e8163/",
    "latest": "http://localhost:8080/reports/master/666"
}
```

Memorize `url`

> :warning: **Generated Reports, and their History are grouping by `path` key. This key means something like `project` or `job` or `branch`. The latest report with the same `path` will be active**: It is not a real path - it's a logical path. The same situation with `path` column in the Web UI!

### Access to generated reports

After generating you can access the latest report by `http://localhost:8080/allure/reports/master/666/index.html`

You may get all reports

```shell
curl --location --request GET 'http://localhost:8080/api/report'
```

Or by path as branch name `master`

```shell
curl --location --request GET 'http://localhost:8080/api/report?path=master'
```

You may get all uploaded results:

```shell
curl --location --request GET 'http://localhost:8080/api/result'
```

You can clear all results or reports:

```shell
curl --location --request DELETE 'http://localhost:8080/api/result'
curl --location --request DELETE 'http://localhost:8080/api/report'
```

Or clear reports older than date (in epoch seconds):

```shell
curl --location --request DELETE 'http://localhost:8080/api/report?seconds=1604693740'
```

### Cleanup features (since 1.10.0)

Once per day the scheduler started and remove old reports with age better then `allure.clean.ageDays`.

Besides, if specified `allure.clean.paths` items with fields `path` and `ageDays`
all reports with path = `allure.clean.paths[].path` will be removed based on separate max age
from `allure.clean.paths[].ageDays`

**_Example:_**

```yaml
allure:
  clean:
    dryRun: false
    time: "00:00"
    ageDays: 90
    paths:
      - path: "manual_uploaded"
        ageDays: 30
      - path: "service/production-job"
        ageDays: 10
```

- Report with path=`test` and age=`100d` will be removed at today MIDNIGHT
- Report with path=`test` and age=`99d` will **NOT** be removed at today MIDNIGHT
- Report with path=`manual_uploaded` and age=`30d` will be removed at today MIDNIGHT
- Report with path=`manual_uploaded` and age=`29d` will **NOT** be removed at today MIDNIGHT
- Report with path=`service/production-job` and age=`10d` will be removed at today MIDNIGHT
- Report with path=`service/production-job` and age=`9d` will **NOT** be removed at today MIDNIGHT

### OAuth2 feature (since 2.12.0)
Separate Spring profile has been added `oauth`

To enable `Oauth` add this profile to `SPRING_PROFILES_ACTIVE` . For example:
- in `values.yaml` (Helm): 
```yaml
env:
    SPRING_PROFILES_ACTIVE: oauth
``` 
- shell command `export SPRING_PROFILES_ACTIVE=oauth`
- docker compose
```yaml
    environment:
      SPRING_PROFILES_ACTIVE: oauth
```

Now [application-oauth.yaml](src/main/resources/application-oauth.yaml) is adjusted to use Google Auth Server:
```yaml
# Internal Spring Configuration
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${OAUTH2_GOOGLE_ALLURE_CLIENT_ID}
            client-secret: ${OAUTH2_GOOGLE_ALLURE_CLIENT_SECRET}
            scope: openid, profile, email
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
            client-name: Google
        provider:
          google:
            issuer-uri: https://accounts.google.com

# App OAuth2 Security Configuration Toggle
app:
  security:
    enable-oauth2: true
    # First-start seed only: with the oauth profile, /api/** and /allure/** are closed to
    # anonymous callers. The runtime value lives in the database (/app/admin/settings).
    require-api-auth: true
```
Pass your `OAUTH2_GOOGLE_ALLURE_CLIENT_ID` and `OAUTH2_GOOGLE_ALLURE_CLIENT_SECRET` or override configuration options to use other provider.

There is Oauth feature-toggle `app.security.enable-oauth2`

The `oauth` profile wires OAuth2 login with no user service, allowlist or provisioning. Any account
that completes login at the configured provider is treated as a signed-in non-guest user and gets
the full non-admin surface: `/api/**`, `/allure/**` and `/app/**` mutations. Admin pages stay
restricted to admin users. Restrict the provider or its app registration (for example a Google
Workspace-internal client) to the audience you intend to grant access to.

> Every spring boot setting can be passed through ENV variables with a little changes according to [spring boot cfg docs](https://docs.spring.io/spring-boot/reference/features/external-config.html)

**By default `oauth` profile is not used and disabled**

### Youtrack Integration (since 2.13.6) `new ⚡`

Enable in `application.yaml`
```yaml
tms:
  enabled: true # switched to true | Default: false
  host: youtrack.com # set youtrack HOST - NOT URL - Just hostname | Default: tms.localhost
  api-base-url: https://${tms.host}/api # optional - set youtrack API URL or use default | Default: https://${tms.host}/api
  token: "my-token" # set youtrack token generated in Profile | Default: "" (reads TMS_TOKEN)
  issue-key-pattern: "[A-Za-z]+-\\d+" # optional - set issue key pattern | Default: "[A-Za-z]+-\\d+"
  dry-run: false # optional - dry run mode. Set true for testing and watch AllureServer Logs | Default: false
```

OR in `docker-compose.yaml`
```yaml
    environment:
      TMS_ENABLED: 'true'
      TMS_HOST: youtrack.com
      TMS_TOKEN: '<token-here>'
      TMS_DRY_RUN: 'false'
```

- Add Link to TMS issue to yor scenario
```java
@Issue("KEY-666")
void test() {}
```

or
```java
@Link(value = "KEY-777", url = "https://youtrack.com/KEY-777")
void test() {}
```
- Generate Report
- Open Report in Browser
- Open scenario `test` go to link `KEY-666` and click on `KEY-666`
- In comments you will se statistics

  | **Scenario** | ❌ `Failed`                                 | ✅ `Passed`                                 |
  |--------------|--------------------------------------------|--------------------------------------------|
  | Scenario 1   | **2** times [`latest` on 01.01.2024](link) | **3** times [`latest` on 01.01.2024](link) |
  | Scenario 5    | **6** times [`latest` on 01.01.2023](link) | **7** times [`latest` on 01.01.2023](link) |

- this comment with statistics will be updated on every report generation
- your TOKEN should have permission: read issue comments, read/add/update issue comments  

### Custom Report Label/Logo and Title (since 2.13.6) `new ⚡`

Enable in `application.yaml`
```yaml
allure:
    title: "BrewCode | Allure Report"
    # FROM URL: https://avatars.githubusercontent.com/u/16944358?v=4
    # FROM FILE: file:/images/logo.png
    logo: "https://avatars.githubusercontent.com/u/16944358?v=4" # or file:/images/logo.png
```

OR in `docker-compose.yaml`
```yaml
    environment:
        ALLURE_LOGO: "https://avatars.githubusercontent.com/u/16944358?v=4"
        ALLURE_TITLE: "BrewCode | Allure Report"
```
> For using image from file you should put it into the container by volume
> 
> For using image from URL your should provide access to Company Network ot Internet from container

### Plugin System for Java Developers (since 2.13.6) `new ⚡` `beta`
Use `Java 25`
1. Create interface in your project in package `ru.iopump.qa.allure.helper.plugin`. It has to be exactly the same as in [AllureServerPlugin.java](src%2Fmain%2Fjava%2Fru%2Fiopump%2Fqa%2Fallure%2Fhelper%2Fplugin%2FAllureServerPlugin.java)
    ```java
    package ru.iopump.qa.allure.helper.plugin;
    
    import io.qameta.allure.core.LaunchResults;
    import org.springframework.beans.factory.BeanFactory;
    import ru.iopump.qa.allure.properties.AllureProperties;
    import ru.iopump.qa.allure.properties.TmsProperties;
    
    import java.nio.file.Path;
    import java.util.Collection;
    
    public interface AllureServerPlugin {
        void onGenerationStart(Collection<Path> resultsDirectories, Context context);
        void onGenerationFinish(Path reportDirectory, Collection<LaunchResults> launchResults, Context context);
        String getName();
        default boolean isEnabled(Context context) {
            return true;
        }
        interface Context {
            AllureProperties getAllureProperties();
            TmsProperties tmsProperties();
            BeanFactory beanFactory();
            String getReportUrl();
        }
    }
    ```
2. Crate your plugin like:
   - [CustomReportMetaPlugin.java](src%2Fmain%2Fjava%2Fru%2Fiopump%2Fqa%2Fallure%2Fhelper%2Fplugin%2FCustomReportMetaPlugin.java)
   - [YouTrackPlugin.java](src%2Fmain%2Fjava%2Fru%2Fiopump%2Fqa%2Fallure%2Fhelper%2Fplugin%2FYouTrackPlugin.java)
3. Create `FAT JAR` with all deps. Try to get rid of external deps if possible
4. Put your jar to container by volume to `/ext` folder
5. Run the server
6. Check logs. There is your plugin in message after plugins discovery and loading:
  ```
    [ALLURE SERVER CONFIGURATION] Allure server plugins loaded: [class ru.iopump.qa.allure.helper.plugin.CustomReportMetaPlugin:Logo Plugin, class ru.iopump.qa.allure.helper.plugin.YouTrackPlugin:YouTrack integration]
  ```

### Jira Integration (since 2.14.0) `coming soon`

### Plugin API in MavenCentral with proper Documentation (since 2.14.0) `coming soon`

### Custom HTTP Hooks (since 2.15.0) `coming soon`

### Settings reference

> Since version `1.2.0` all reports manage with Database and have unic uuids.

> Since version `1.10.0` there are new options for Cleanup,
> but also some old options have been renamed to integrate with the Spring Boot @ConfigurationProperties approach. And also the yaml format is used

Every setting below is a Spring property, so it can be given as an environment variable, a JVM
system property (`-Dallure.date-format=...`) or a key in `application.yaml`. The env var column is
the Spring relaxed-binding form: uppercase, dots and dashes become underscores
([external config docs](https://docs.spring.io/spring-boot/reference/features/external-config.html)).
Defaults are the ones shipped in `src/main/resources/application.yaml`.

#### Runtime

| Property | Env var | Type | Default | Description |
|---|---|---|---|---|
| `server.port` | `PORT` | int | `8080` | HTTP port the server listens on. Change the container HEALTHCHECK too if you change it in Docker |
| - | `JAVA_OPTS` | string | `-Xms256m -Xmx2048m` | JVM options used by the container entrypoint (`Dockerfile:51`). Not a Spring property |
| `spring.profiles.active` | `SPRING_PROFILES_ACTIVE` | string | none | Set to `oauth` to load `application-oauth.yaml` |

#### Storage and report generation

| Property | Env var | Type | Default | Description |
|---|---|---|---|---|
| `allure.results-dir` | `ALLURE_RESULTS_DIR` | string | `allure/results/` | Directory holding unpacked uploaded results |
| `allure.reports.dir` | `ALLURE_REPORTS_DIR` | string | `allure/reports/` | Directory holding generated reports |
| `allure.reports.path` | `ALLURE_REPORTS_PATH` | string | `reports/` | URL path segment reports are served under |
| `allure.reports.history-level` | `ALLURE_REPORTS_HISTORY_LEVEL` | int | `20` | Number of previous runs kept in report history |
| `allure.date-format` | `ALLURE_DATE_FORMAT` | string | `yy/MM/dd HH:mm:ss` | Timestamp format in listings and the Web UI |
| `allure.server-base-url` | `ALLURE_SERVER_BASE_URL` | string | empty | Absolute base URL used in generated links. Set it when the server sits behind a proxy. Keep the trailing `/`. Empty = infer from the request |
| `allure.title` | `ALLURE_TITLE` | string | `BrewCode \| Allure Report` | Page/browser title |
| `allure.logo` | `ALLURE_LOGO` | string | empty | Logo resource. Accepts an `https://` URL or `file:/images/logo.png` inside the container |

#### Upload limits

| Property | Env var | Type | Default | Description |
|---|---|---|---|---|
| `spring.servlet.multipart.max-file-size` | `SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE` | size | `100MB` | Max size of the compressed upload |
| `spring.servlet.multipart.max-request-size` | `SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE` | size | `100MB` | Max size of the whole compressed request |
| `allure.upload.max-uncompressed-bytes` | `ALLURE_UPLOAD_MAX_UNCOMPRESSED_BYTES` | long | `4294967296` (4 GiB) | Cumulative decompressed size allowed per archive. Zip-bomb guard: multipart limits cap only the compressed body |
| `allure.upload.max-entries` | `ALLURE_UPLOAD_MAX_ENTRIES` | long | `100000` | Max number of entries in one results archive |

#### Scheduled cleanup

| Property | Env var | Type | Default | Description |
|---|---|---|---|---|
| `allure.clean.dry-run` | `ALLURE_CLEAN_DRY_RUN` | boolean | `false` | `true` logs what would be deleted and deletes nothing |
| `allure.clean.time` | `ALLURE_CLEAN_TIME` | `HH[:mm][:ss]` | `00:00` | Daily run time, server local time |
| `allure.clean.age-days` | `ALLURE_CLEAN_AGE_DAYS` | int | `90` | Global retention in days, excluding paths listed below |
| `allure.clean.paths[0].path` | `ALLURE_CLEAN_PATHS_0_PATH` | string | `manual_uploaded` | Report path with its own retention |
| `allure.clean.paths[0].age-days` | `ALLURE_CLEAN_PATHS_0_AGE_DAYS` | int | `30` | Retention for that path |

#### Authentication

| Property | Env var | Type | Default | Description |
|---|---|---|---|---|
| `basic.auth.username` | `BASIC_AUTH_USERNAME` | string | `admin` | Username used to seed the main administrator on first startup only. Afterwards edit users at `/app/admin/users` |
| `basic.auth.password` | `BASIC_AUTH_PASSWORD` | string | `admin` | Password used to seed the main administrator on first startup only. Left at the default, the first login is forced to change it |
| `basic.auth.enable` | `BASIC_AUTH_ENABLE` | boolean | `false` | **DEPRECATED**, still honored. `true` = legacy lockdown: every request needs authentication, including `/api/**` and `/allure/**`, ignoring the require-api-auth toggle |
| `app.security.require-api-auth` | `APP_SECURITY_REQUIRE_API_AUTH` | boolean | `false` | First-start seed for the `/api/**` and `/allure/**` gate. The runtime value lives in the database, flip it at `/app/admin/settings`. `false` = guest-readable |
| `app.security.enable-oauth2` | `APP_SECURITY_ENABLE_OAUTH2` | boolean | `false` | Enable OAuth2 login. Set to `true` by the `oauth` profile |

API tokens are minted per user at `/app/profile` and sent in the `X-API-Token` header. There is no
environment variable for them.

#### OAuth2 (profile `oauth` only)

| Property | Env var | Type | Default | Description |
|---|---|---|---|---|
| `spring.security.oauth2.client.registration.google.client-id` | `OAUTH2_GOOGLE_ALLURE_CLIENT_ID` | string | none, required | Google OAuth2 client id. The context fails to start without it when the profile is active |
| `spring.security.oauth2.client.registration.google.client-secret` | `OAUTH2_GOOGLE_ALLURE_CLIENT_SECRET` | string | none, required | Google OAuth2 client secret |

`application-oauth.yaml` also sets `app.security.enable-oauth2: true` and seeds
`app.security.require-api-auth: true`.

#### Database

| Property | Env var | Type | Default | Description |
|---|---|---|---|---|
| `spring.datasource.url` | `SPRING_DATASOURCE_URL` | string | `jdbc:h2:file:./allure/db` | JDBC URL. The H2 file is created on first startup. PostgreSQL is supported |
| `spring.datasource.username` | `SPRING_DATASOURCE_USERNAME` | string | `sa` | Database user |
| `spring.datasource.password` | `SPRING_DATASOURCE_PASSWORD` | string | empty | Database password |
| `spring.jpa.database` | `SPRING_JPA_DATABASE` | string | `H2` | Hibernate dialect selector. Set to `postgresql` with a PostgreSQL URL |

#### YouTrack TMS integration

| Property | Env var | Type | Default | Description |
|---|---|---|---|---|
| `tms.enabled` | `TMS_ENABLED` | boolean | `false` | Master switch for the YouTrack plugin |
| `tms.host` | `TMS_HOST` | string | `tms.localhost` | YouTrack hostname only, no scheme |
| `tms.api-base-url` | `TMS_API_BASE_URL` | string | `https://${tms.host}/api` | YouTrack REST API base URL |
| `tms.project` | `TMS_PROJECT` | string | unset | YouTrack project short name |
| `tms.token` | `TMS_TOKEN` | string | empty | YouTrack permanent token. Keep it out of version control |
| `tms.issue-key-pattern` | `TMS_ISSUE_KEY_PATTERN` | regex | `[A-Za-z]+-\d+` | Pattern matching issue keys found in a report |
| `tms.dry-run` | `TMS_DRY_RUN` | boolean | `false` | `true` resolves issues but never writes back |

#### Logging

See the [Logging](#logging) section.

> You can mount external jars to `/ext` folder in the container, and they will be available in app classpath.  
> For example you may add new jdbc drivers

```shell
    volumes:
      - ./ext:/ext:rw
```

### Docker compose

Two compose files ship with the repository. Both declare a `build:` block, which compiles the bootJar
from this source tree and stamps it with the `APP_VERSION` build arg; keep that arg in lockstep with
the image tag, or delete the `build:` block to run the published image instead. Both mount `./allure-server-store`
at `/allure`, which must be owned by uid/gid `1000` on the host before the first start.

[docker-compose-h2.yml](https://github.com/kochetkov-ma/allure-server/blob/master/docker-compose-h2.yml) - single container, file-based H2 database:

```yaml
services:
  allure-server:
    build:
      context: .
      args:
        APP_VERSION: "3.0.0"
    image: kochetkovma/allure-server:3.0.0
    ports:
      - "8080:8080"
    volumes:
      - ./allure-server-store:/allure:rw
      - ./ext:/ext:rw
    restart: unless-stopped
    environment:
      JAVA_OPTS: "-Xms256m -Xmx2048m"
```

Run it with `docker compose -f docker-compose-h2.yml up --build`. The file also carries every other
setting as a commented-out line with its default value, so it doubles as a configuration checklist.

[docker-compose.yml](https://github.com/kochetkov-ma/allure-server/blob/master/docker-compose.yml) - application plus a PostgreSQL database. The database
wiring is the only difference from the H2 file:

```yaml
services:
  allure-server:
    build:
      context: .
      args:
        APP_VERSION: "3.0.0"
    image: kochetkovma/allure-server:3.0.0
    ports:
      - "8080:8080"
    volumes:
      - ./allure-server-store:/allure:rw
      - ./ext:/ext:rw
    restart: unless-stopped
    depends_on:
      - postgres
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/allure
      SPRING_DATASOURCE_USERNAME: postgres
      SPRING_DATASOURCE_PASSWORD: postgres
      SPRING_JPA_DATABASE: postgresql
      JAVA_OPTS: "-Xms256m -Xmx2048m"

  postgres:
    image: postgres:16.15-alpine
    environment:
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
      POSTGRES_DB: allure
    ports:
      - "5432:5432"
    volumes:
      - ./allure-server-store-db:/var/lib/postgresql/data:rw
    restart: unless-stopped
```

Run it with `docker compose up --build`. The `postgres`/`postgres` credentials are a local
development value; change them for any shared deployment, and drop the `5432:5432` port mapping,
which exists only for local inspection.

Use Helm Chart for Kubernetes from **[.helm/allure-server/README.md](https://github.com/kochetkov-ma/allure-server/blob/master/.helm/allure-server/README.md)**

### Upgrading from 2.x

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
`UncheckedIOException` escape, 3.0.0 checks first and raises 404. Clients that treated 500
as retryable should stop retrying these. This is deliberate and there is no opt-out.

```
HTTP/1.1 400 Bad Request
Content-Type: application/problem+json

{"type":"about:blank","title":"Bad Request","status":400,
 "detail":"File must have '.zip' extension but was 'results.tar'",
 "instance":"/api/result"}
```

**`POST /api/report` now validates `reportSpec`, which 2.13.9 never did.** The field names,
types and JSON are unchanged. In 2.13.9 `reportSpec` carried `@NotNull` but no `@Valid`, so
bean validation never descended into `ReportSpec` and its constraints never ran. 3.0.0 adds
the `@Valid` cascade, so three payloads that 2.13.9 accepted now return 400. A blank `path`
segment is the reachable one: `{"path":[""]}` was accepted by 2.13.9 and generated a report,
and it now fails the blank-segment check. That is exactly the shape an unresolved CI variable
expands to, because pipelines commonly build `path` from environment variables that can be
empty, so check that every segment you send is non-blank before upgrading. A `path` longer
than 32 segments is also new, from a `@Size(max = 32)` that was unbounded in 2.13.9. An empty
`path` array is the third, now that the declared `@NotEmpty` actually runs. The `results` list
is not a concern: its per-element UUID pattern was already enforced in 2.13.9.

**HTTP Basic credentials sent to `/api/**` are now evaluated, and can be rejected.** This is
the one change that breaks a working 2.x call. In 2.13.9 the Basic filter was registered only
when `basic.auth.enable=true`, and that property defaulted to `false`, so an `Authorization:
Basic` header on `/api/**` was ignored and the request was served anonymously with 200. In
3.0.0 Basic is always registered, so the header is evaluated even on a route that is
otherwise open. Only callers that send Basic credentials are affected. A call that sends no
credentials behaves exactly as it did in 2.x and still returns 200.

Two outcomes replace that 200. `-u admin:admin` returns 403: the administrator password is
flagged temporary while it is still the shipped default, whatever route supplied it, and a
temporary-password principal is blocked on the stateless API surface. `-u admin:` with any
other value returns 401, because failed Basic authentication invokes the entry point before
the permitted route is reached. Worst affected is a 2.13.9 deployment that ran with
`basic.auth.enable=true` and the default password: that combination authenticated on 2.x and
returns 403 on 3.0.0. Either drop the `Authorization` header from the call, which restores the
anonymous 200, or set a non-default `BASIC_AUTH_PASSWORD` before the first boot and update the
client to send the new value. Keeping `admin`/`admin` is not an option, because that value is
flagged temporary however it is supplied. Setting `BASIC_AUTH_PASSWORD` after the first boot
has no effect on an existing installation; there the password is rotated at
`/app/profile/password`.

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

The full endpoint-by-endpoint compatibility matrix is in [docs/COMPATIBILITY.md](https://github.com/kochetkov-ma/allure-server/blob/master/docs/COMPATIBILITY.md).

### GitHub Actions

Thx [Xotabu4](https://github.com/Xotabu4)

There is external GitHub Action to sent and generate Allure
Reports: [send-to-allure-server-action](https://github.com/Xotabu4/send-to-allure-server-action)

```
Compresses allure-results, sends to kochetkov-ma/allure-server , and triggers allure report generation on it. Result of this action - is URL to generated report.

Works for any test project languages (java, .net, js/ts, python, etc), for any testing frameworks (junit, pytest, cucumber, mocha, jest ...) that has allure reporter configured.
```

Example:

```
    - name: Send Results and Generate Allure Report
      uses: Xotabu4/send-to-allure-server-action@1
      # always() needed because we want report for failed tests as well
      if: ${{ always() }}
      with:
        allure-server-url: 'http://my-allure-server.com:5001/'
```

![alt text](https://raw.githubusercontent.com/kochetkov-ma/allure-server/master/docs/img/github-action.png)

### Web UI

Allure Server provides a server-rendered Web UI (htmx + JTE + Alpine.js + Tailwind CSS) to
administer reports and results.  
By default the Web UI is available under `/app` and the root path `/` redirects to `/app/reports`.  
Example: `http://localhost:8080/app/reports`  
The Web UI exposes the same operations as the REST API: upload, list, filter, sort,
generate and delete reports / results.

![Reports page](https://raw.githubusercontent.com/kochetkov-ma/allure-server/master/docs/img/reports-dark.png)

The UI is organized into a few pages:

- **Reports** (`/app/reports`) — list, filter, sort, generate and delete reports.
- **Results** (`/app/results`) — upload allure-results archives and manage uploaded results.
- **Profile** (`/app/profile`) — change your password and mint / revoke API tokens.
- **Admin** (`/app/admin`) — manage users (`/app/admin/users`) and flip the require-api-auth setting (`/app/admin/settings`); admin-only.

![Results page](https://raw.githubusercontent.com/kochetkov-ma/allure-server/master/docs/img/results-light.png)

> :warning: **Generated Reports, and their History are grouping by `path` key. This key means something like `project` or `job` or `branch`. The latest report with the same `path` will be active**: It is not a real path - it's a logical path. The same situation with `path` column in the Web UI!

### Logging

Logging properties are located in [application.yaml](src/main/resources/application.yaml)

```yaml
logging:
  level:
    root: INFO
    org.springframework: INFO
    org.springframework.core: WARN
    org.springframework.beans.factory.support: WARN
    ru.iopump.qa.allure: INFO # Allure Server Logs
    ru.iopump.qa.allure.api: DEBUG
```

You may override it by Environment Variables, for example enable `DEBUG` for allure server:
```shell
    export LOGGING_LEVEL_RU_IOPUMP_QA_ALLURE=DEBUG
```

Or switch all logs to `DEBUG`:
```shell
    export LOGGING_LEVEL_ROOT=DEBUG
```

## Goals
See [milestones](https://github.com/kochetkov-ma/allure-server/milestones)
