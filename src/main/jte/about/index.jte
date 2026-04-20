@import java.time.Instant
@import java.time.ZoneOffset
@import java.time.format.DateTimeFormatter
@param String version
@param Instant buildTime
!{var buildTimeText = buildTime == null ? "unknown" : DateTimeFormatter.ISO_INSTANT.format(buildTime.atOffset(ZoneOffset.UTC).toInstant());}
@template.layout.main(
    title = "About",
    activeNav = "about",
    content = @`
        <article class="max-w-3xl space-y-6">
            <header>
                <h1 class="text-3xl font-bold text-slate-900">About Allure Server</h1>
                <p class="mt-2 text-slate-700">
                    Allure Server stores, aggregates and serves
                    <a href="https://allurereport.org" target="_blank" rel="noopener"
                       class="text-sky-700 hover:underline">Allure 2</a>
                    test results and reports. Upload your <code class="rounded bg-slate-200 px-1">allure-results.zip</code>
                    via the REST API or the UI, then generate shareable HTML reports grouped by a logical path
                    (e.g. <code class="rounded bg-slate-200 px-1">branch/job</code>).
                </p>
            </header>

            <section class="rounded border border-slate-200 bg-white p-4 shadow-sm">
                <h2 class="mb-3 text-lg font-semibold text-slate-900">Project information</h2>
                <dl class="grid grid-cols-[max-content_1fr] gap-x-4 gap-y-2 text-sm">
                    <dt class="font-medium text-slate-600">Version</dt>
                    <dd class="font-mono text-slate-900">${version}</dd>

                    <dt class="font-medium text-slate-600">Build time</dt>
                    <dd class="font-mono text-slate-900">${buildTimeText}</dd>

                    <dt class="font-medium text-slate-600">Project home</dt>
                    <dd>
                        <a href="https://github.com/kochetkov-ma/allure-server" target="_blank" rel="noopener"
                           class="text-sky-700 hover:underline">github.com/kochetkov-ma/allure-server ↗</a>
                    </dd>

                    <dt class="font-medium text-slate-600">Docker Hub</dt>
                    <dd>
                        <a href="https://hub.docker.com/r/kochetkovma/allure-server" target="_blank" rel="noopener"
                           class="text-sky-700 hover:underline">kochetkovma/allure-server ↗</a>
                    </dd>

                    <dt class="font-medium text-slate-600">Author</dt>
                    <dd class="text-slate-900">
                        Maksim Kochetkov
                        (<a href="https://github.com/kochetkov-ma" target="_blank" rel="noopener"
                            class="text-sky-700 hover:underline">@kochetkov-ma</a>)
                    </dd>
                </dl>
            </section>

            <section class="rounded border border-slate-200 bg-white p-4 shadow-sm">
                <h2 class="mb-3 text-lg font-semibold text-slate-900">API documentation</h2>
                <p class="text-sm text-slate-700">
                    Every endpoint exposed by Allure Server is described with OpenAPI. Browse, try and
                    copy <code class="rounded bg-slate-200 px-1">curl</code> commands from Swagger UI:
                </p>
                <p class="mt-3">
                    <a href="/swagger-ui.html" target="_blank" rel="noopener"
                       class="inline-flex items-center gap-1 rounded bg-slate-900 px-3 py-1 text-sm text-white hover:bg-slate-700">
                        Open Swagger UI ↗
                    </a>
                </p>
            </section>
        </article>
    `
)
