# Reference Patterns — allure-server

[DICT: AP=anti-pattern, CDI=constructor injection, DTO=data transfer object, SPI=service provider interface]

> Stack + versions -> `versions.md` + `project-architecture.md`. Open-source: strict standards. Copy from etalons. No shortcuts.

## Etalon Map

All paths relative to `src/main/java/ru/iopump/qa/allure/`.

| Layer | Etalon | Path | Score |
|-------|--------|------|-------|
| L4 Utility | `PathUtil` | `service/PathUtil.java` | 9 |
| L4 Converter | `LocalTimeConverter` | `properties/LocalTimeConverter.java` | 9 |
| L5 Controller | `AllureResultController` | `controller/AllureResultController.java` | 7 |
| L6 Plugin SPI | `AllureServerPlugin` | `helper/plugin/AllureServerPlugin.java` | 9 |
| L6 Plugin impl | `YouTrackPlugin` | `helper/plugin/YouTrackPlugin.java` | 8 |
| L7 Feign client | `IssuesClient` | `api/youtrack/IssuesClient.java` | 7 |
| L8 Repository | `JpaReportRepository` | `repo/JpaReportRepository.java` | 6 |
| L10 Record DTO | `MarkdownStatisticModel` | `helper/plugin/youtrack/MarkdownStatisticModel.java` | 10 |
| L11 Properties | `AllureProperties`, `BasicProperties` | `properties/*.java` | 9 |
| L14 Security | `SecurityConfiguration` | `security/SecurityConfiguration.java` | 7 |
| L14 Feign config | `FeignConfiguration` | `api/FeignConfiguration.java` | 8 |

## L4 — Utilities & Converters

`@UtilityClass` for stateless helpers — Lombok enforces `final` + private constructor. Copy `PathUtil`:

```java
@SuppressWarnings("RedundantModifiersUtilityClassLombok")
@UtilityClass
public class PathUtil {

    public final static String UUID_PATTERN = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}";

    public static String str(@Nullable Path path) {
        if (path == null) {
            return "";
        }
        return path.toString().replaceAll("\\\\", "/");
    }
}
```

Property binding: implement `Converter<From, To>` + `@ConfigurationPropertiesBinding`:

```java
@Component
@ConfigurationPropertiesBinding
public class LocalTimeConverter implements Converter<String, LocalTime> {

    @Override
    public LocalTime convert(@NonNull String source) {
        return LocalTime.parse(source, DateTimeFormatter.ofPattern("HH[:mm][:ss]"));
    }
}
```

APs: generic `Util.java` mixes file + URL + string concerns -> split per concern (`PathUtil`, `UrlUtil`, `StringUtil`); standardize singular `*Util` (etalon `PathUtil`); `static ObjectMapper` fields in utils -> inject via DI; side-effecting util constructors -> `@UtilityClass`.

## L5 — Controllers

CDI + `@Validated` + cache annotations gated by a constant name. Copy `AllureResultController`:

```java
@RequiredArgsConstructor
@RestController
@Slf4j
@Validated
@RequestMapping(path = "/api/result")
public class AllureResultController {
    final static String CACHE = "results";
    private final ResultService resultService;

    @Operation(summary = "Get all uploaded allure results archives")
    @GetMapping
    @Cacheable(CACHE)
    public Collection<ResultResponse> getAllResult() throws IOException {
        // ...
    }

    @DeleteMapping
    @CacheEvict(value = CACHE, allEntries = true)
    public Collection<ResultResponse> deleteAllResults() throws IOException { /* ... */ }
}
```

| AP | Fix |
|----|-----|
| `@SneakyThrows` on REST endpoints (see `uploadResults`) | declare `throws IOException`, map in `@RestControllerAdvice` |
| Duplicate per-controller `@ExceptionHandler` | central `@RestControllerAdvice` returning `ProblemDetail` |
| Business logic in controller (zip/content-type checks) | move to service; controller validates DTOs only |
| `@Cacheable` + self-invocation | != call a cached method from the same bean |

## L6 + L9 — Services & Plugins

SPI: plugins receive a `Context` (properties + `BeanFactory`) — no field injection:

```java
public interface AllureServerPlugin {

    void onGenerationStart(Collection<Path> resultsDirectories, Context context);

    void onGenerationFinish(Path reportDirectory, Collection<LaunchResults> launchResults, Context context);

    String getName();

    default boolean isEnabled(Context context) { return true; }

    interface Context {
        AllureProperties getAllureProperties();
        TmsProperties tmsProperties();
        BeanFactory beanFactory();
        String getReportUrl();
    }
}
```

Inject `Collection<AllureServerPlugin>` to iterate — never wire by name. Impl pattern (`YouTrackPlugin`):

```java
@Slf4j
@RequiredArgsConstructor
public class YouTrackPlugin implements AllureServerPlugin {

    private final boolean dryRun;

    public YouTrackPlugin() { this.dryRun = false; }

    @Override
    public boolean isEnabled(Context context) {
        return context.tmsProperties().isEnabled();
    }
    // onGenerationFinish(...) — parallelStream per issue, structured logging
}
```

Service: CDI + one class-level transactional boundary. Copy `JpaReportService`:

```java
@Component
@Slf4j
@Transactional
public class JpaReportService {
    public JpaReportService(AllureProperties cfg,
                            ObjectMapper objectMapper,
                            JpaReportRepository repository,
                            AllureReportGenerator reportGenerator,
                            ServeRedirectHelper redirection) {
        this.reportsDir = cfg.reports().dirPath();
        this.repository = repository;
        // ...
    }
}
```

| AP | Fix |
|----|-----|
| `jakarta.transaction.Transactional` on Spring service | `org.springframework.transaction.annotation.Transactional` — rollback semantics differ |
| Manual `new ResultService(reportsDir)` inside service | inject as bean; bypassing DI breaks lifecycle |
| Service locator `context.beanFactory().getBean(...)` | CDI; `BeanFactory` only in SPI glue |
| `@SneakyThrows` on `uploadReport`/`copyHistory` | declare checked exceptions; `@RestControllerAdvice` handles |
| Zip Slip in `ResultService.unzipAndStore` | normalize path, verify `resolved.startsWith(targetDir)` before write |

## L7 — Feign Clients

Extend the generated API — contract lives in the OpenAPI spec, not hand-rolled Java:

```java
@FeignClient(name = "youtrack-issues", url = "${tms.api-base-url}")
public interface IssuesClient extends org.brewcode.api.youtrack.IssuesApi {
}
```

Central `FeignConfiguration` owns `@EnableFeignClients`, shared `Retryer`, token interceptor (presence-checked — keep it):

```java
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableFeignClients(basePackages = {"ru.iopump.qa.allure.api"})
@ImportAutoConfiguration({FeignAutoConfiguration.class, HttpMessageConvertersAutoConfiguration.class, JacksonAutoConfiguration.class})
public class FeignConfiguration {

    @Bean
    public RequestInterceptor feignRequestInterceptor(TmsProperties props) {
        return requestTemplate -> {
            var token = props.getToken();
            var hasAuthorization = requestTemplate.headers().containsKey(AUTHORIZATION) && requestTemplate.headers().get(AUTHORIZATION).contains(token);
            if (!hasAuthorization) {
                requestTemplate.removeHeader(AUTHORIZATION);
                requestTemplate.header(AUTHORIZATION, BEARER + " " + token);
            }
        };
    }

    @Bean
    public Retryer retryer() {
        return new Retryer.Default(100, 1000, 2);
    }
}
```

APs: missing circuit breaker -> add Resilience4j (`@CircuitBreaker`, `@TimeLimiter`) on outbound calls; hand-written DTOs duplicating the OpenAPI schema -> generate; `@EnableFeignClients` on `@SpringBootApplication` -> keep in `FeignConfiguration`.

## L8 — Repositories

Derived queries + caller-supplied `Sort.by` + bulk `deleteBy`:

```java
@Repository
public interface JpaReportRepository extends JpaRepository<ReportEntity, UUID> {
    @NonNull Optional<ReportEntity> findOneByUuid(@NonNull UUID uuid);
    @NonNull Collection<ReportEntity> findByPathOrderByCreatedDateTimeDesc(@NonNull String path);
    @NonNull Collection<ReportEntity> deleteByActiveFalse();
    @NonNull Collection<ReportEntity> findByActiveTrue();
    @NonNull Collection<ReportEntity> findAllByCreatedDateTimeIsBefore(@NonNull LocalDateTime date);
}
```

```java
return repository.findAll(Sort.by("createdDateTime").descending());
```

APs: `Jpa*` prefix -> rename `ReportRepository` (implementation detail != domain name); per-id delete loop -> bulk `deleteBy*`; return loosest interface (`Collection`/`Stream`); `@Query` only for joins/projections.

## L10 + L11 — DTOs, Entities, Properties

Immutability ranking:

| Rank | Choice | When |
|------|--------|------|
| 1 | `record` | all new DTOs, value objects, API models |
| 2 | `@ConstructorBinding` + `@Getter` + `final` fields | `@ConfigurationProperties` needing `@PostConstruct` / inner classes |
| 3 | `@Value` (Lombok) | legacy/interop only |
| X | `@Data` on `@Entity` — FORBIDDEN | breaks JPA identity (hashCode over lazy collections) |

Record DTO with behaviour (`MarkdownStatisticModel` — parse, merge, render; all pure):

```java
public record MarkdownStatisticModel(
    String title,
    List<Row> scenarioStatisticRows,
    Total total,
    Footer footer
) {
    public String toMarkdown() { /* text block + formatted */ }

    public MarkdownStatisticModel merge(MarkdownStatisticModel other) {
        var rows = this.mergeRows(other.scenarioStatisticRows);
        return new MarkdownStatisticModel(this.title, rows, new Total(rows.size()), this.footer.merge(other.footer));
    }

    public record Row(String scenario, Statistic passed, Statistic failed) { /* ... */ }
}
```

`@ConfigurationProperties` with defaults + secret-safe `toString` (`BasicProperties`):

```java
@ConfigurationProperties(prefix = "basic.auth")
@Getter
@Accessors(fluent = true)
@Slf4j
@ToString(exclude = "password")
public class BasicProperties {

    private final String username;
    private final String password;
    private final boolean enable;

    @ConstructorBinding
    public BasicProperties(String username, String password, boolean enable) {
        this.username = defaultIfNull(username, "admin");
        this.password = defaultIfNull(password, "admin");
        this.enable = defaultIfNull(enable, false);
    }
}
```

Nested properties with bind-time derived fields (`AllureProperties.Reports`):

```java
@Getter
@ToString
public static class Reports {
    private final transient Path dirPath;
    private final String dir;
    private final String path;
    private final long historyLevel;

    @ConstructorBinding
    public Reports(String dir, String path, long historyLevel) {
        this.dir = dir;
        this.path = path;
        this.historyLevel = historyLevel;
        this.dirPath = Paths.get(this.dir);
    }
}
```

APs: `@Data` on `@Entity ReportEntity` -> `@Getter @Setter @EqualsAndHashCode(onlyExplicitlyIncluded = true)` ID-only; mutable `@Data` DTO -> `record`; secrets in `toString` -> `@ToString(exclude = ...)`; setter-based properties -> `@ConstructorBinding` + final.

## L14 — Infrastructure / Security

Auth is ALWAYS ON, DB-backed (`DbUserDetailsService` over `UserRepository`); `basic.auth.enable` is a deprecated no-op; `/api/**` protection is a runtime flag read through `SystemSettingsService`. CDI only — no field injection. Current etalon (`SecurityConfiguration`):

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Slf4j
public class SecurityConfiguration {

    private final ApiTokenAuthenticationFilter apiTokenFilter;
    private final UserRepository userRepository;
    private final DbUserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final SystemSettingsService systemSettingsService;
    private final boolean enableOAuth2;

    public SecurityConfiguration(ApiTokenAuthenticationFilter apiTokenFilter,
                                 UserRepository userRepository,
                                 DbUserDetailsService userDetailsService,
                                 PasswordEncoder passwordEncoder,
                                 SystemSettingsService systemSettingsService,
                                 BasicProperties basicProperties,
                                 AppSecurityProperties appSecurityProperties) {
        // ...
        this.enableOAuth2 = appSecurityProperties.enableOauth2();

        if (basicProperties.enable()) {
            log.warn("[ALLURE SERVER SECURITY] 'basic.auth.enable' is deprecated and ignored — "
                + "authentication is always enabled. Remove this property from your configuration.");
        }
        log.info("[ALLURE SERVER SECURITY] Always-on auth | OAuth2: {} | API auth bootstrap default: {}",
            enableOAuth2, appSecurityProperties.requireApiAuth());
    }
}
```

`SecurityFilterChain` lambda DSL (no deprecated `WebSecurityConfigurerAdapter`); filter ordering is load-bearing:

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    final AuthorizationManager<RequestAuthorizationContext> apiAuthorizationManager = apiAuthorizationManager();

    http
        .headers(it -> it.frameOptions(FrameOptionsConfig::sameOrigin))
        .csrf(AbstractHttpConfigurer::disable)
        // API-token filter runs before the Basic-auth filter so a valid X-API-Token header
        // short-circuits username/password evaluation.
        .addFilterBefore(apiTokenFilter, UsernamePasswordAuthenticationFilter.class)
        // Force-password-change runs AFTER authorization so only the resolved authenticated
        // principal is inspected. Unauthenticated requests are never redirected.
        .addFilterAfter(new ForcePasswordChangeFilter(userRepository), AuthorizationFilter.class)
        .authorizeHttpRequests(it -> it
            .requestMatchers(WebConfiguration.CSS_PATH_PATTERN,
                WebConfiguration.JS_PATH_PATTERN,
                WebConfiguration.IMG_PATH_PATTERN,
                "/favicon.ico",
                "/allure/**").permitAll()
            .requestMatchers("/api/**").access(apiAuthorizationManager)
            .requestMatchers("/app/signin").authenticated()
            .requestMatchers(WebConfiguration.APP_PATH_PATTERN).permitAll()
            .anyRequest().permitAll())
        .httpBasic(it -> it.realmName("Allure Server"));

    if (enableOAuth2) {
        http.oauth2Login(withDefaults());
    }

    return http.build();
}
```

APs: `@Autowired` field injection in `@Configuration` -> CDI; `and()` chain style -> lambda DSL; CSRF disabled without an inline why-comment -> document the rationale at the `csrf(...)` line.

## Quick Reference

| When writing... | Copy from... |
|-----------------|--------------|
| Stateless helper | `service/PathUtil.java` |
| Property converter | `properties/LocalTimeConverter.java` |
| REST controller | `controller/AllureResultController.java` (minus `@SneakyThrows` + local `@ExceptionHandler`) |
| Plugin SPI | `helper/plugin/AllureServerPlugin.java` |
| Plugin impl | `helper/plugin/YouTrackPlugin.java` |
| Feign client | `api/youtrack/IssuesClient.java` + `api/FeignConfiguration.java` |
| Spring Data repository | `repo/JpaReportRepository.java` (rename — drop `Jpa` prefix) |
| Immutable DTO | `helper/plugin/youtrack/MarkdownStatisticModel.java` |
| `@ConfigurationProperties` | `properties/BasicProperties.java` |
| Nested properties | `properties/AllureProperties.java` |
| Security/infra `@Configuration` | `security/SecurityConfiguration.java` |

### Top APs to Eradicate

1. `@Data` on `@Entity` — JPA identity hazard
2. `jakarta.transaction.Transactional` on Spring services
3. `@SneakyThrows` on REST endpoints — use `@RestControllerAdvice`
4. `beanFactory.getBean(...)` service locator — CDI
5. `Jpa*Repository` naming — domain name only
6. Zip Slip in unzip helpers — validate resolved paths
7. Duplicate per-controller `@ExceptionHandler` — centralize in advice
8. Missing Resilience4j on Feign clients — add circuit breaker + timeout
9. Manual `new ResultService(...)` in service — inject as bean
10. Generic `Util.java` — split per concern, singular `*Util` naming
