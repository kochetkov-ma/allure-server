@import java.time.Instant
@import java.time.ZoneOffset
@import java.time.format.DateTimeFormatter
@import ru.iopump.qa.allure.web.dto.CurrentUserView
@param String version
@param Instant buildTime
@param String gitCommit = null
@param CurrentUserView currentUser = null
@param boolean authEnabled = false
@param boolean isAdmin = false
@param boolean signInRequired = false
!{var buildTimeText = buildTime == null ? "unknown" : DateTimeFormatter.ISO_INSTANT.format(buildTime.atOffset(ZoneOffset.UTC).toInstant());}
@template.layout.main(
    title = "About",
    activeNav = "about",
    currentUser = currentUser,
    authEnabled = authEnabled,
    isAdmin = isAdmin,
    signInRequired = signInRequired,
    content = @`
        <article class="space-y-6">
            <header>
                <h1 class="text-3xl font-bold text-text">About Allure Server</h1>
                <p class="mt-2 text-text-muted">
                    Allure Server stores, aggregates and serves
                    <a href="https://allurereport.org" target="_blank" rel="noopener"
                       class="text-primary hover:underline">Allure 2</a>
                    test results and reports. Upload your <code class="rounded bg-surface px-1 font-mono text-primary">allure-results.zip</code>
                    via the REST API or the UI, then generate shareable HTML reports grouped by a logical path
                    (e.g. <code class="rounded bg-surface px-1 font-mono text-primary">branch/job</code>).
                </p>
            </header>

            <section class="card p-5">
                <h2 class="mb-4 flex items-center gap-2 text-lg font-semibold text-text">
                    <svg class="h-5 w-5 text-text-muted" fill="none" viewBox="0 0 24 24" stroke-width="1.8" stroke="currentColor" aria-hidden="true">
                        <path stroke-linecap="round" stroke-linejoin="round" d="M11.25 11.25l.041-.02a.75.75 0 011.063.852l-.708 2.836a.75.75 0 001.063.853l.041-.021M21 12a9 9 0 11-18 0 9 9 0 0118 0zm-9-3.75h.008v.008H12V8.25z"/>
                    </svg>
                    Project information
                </h2>
                <ul class="space-y-3 text-sm">
                    <li class="flex items-start gap-3">
                        <svg class="mt-0.5 h-5 w-5 flex-none text-text-muted" fill="none" viewBox="0 0 24 24" stroke-width="1.8" stroke="currentColor" aria-hidden="true" title="Version">
                            <path stroke-linecap="round" stroke-linejoin="round" d="M9.568 3H5.25A2.25 2.25 0 003 5.25v4.318c0 .597.237 1.17.659 1.591l9.581 9.581c.699.699 1.78.872 2.607.33a18.095 18.095 0 005.223-5.223c.542-.827.369-1.908-.33-2.607L11.16 3.66A2.25 2.25 0 009.568 3z"/>
                            <path stroke-linecap="round" stroke-linejoin="round" d="M6 6h.008v.008H6V6z"/>
                        </svg>
                        <div>
                            <div class="font-medium text-text-muted">Version</div>
                            <div class="font-mono text-text">${version}</div>
                        </div>
                    </li>
                    <li class="flex items-start gap-3">
                        <svg class="mt-0.5 h-5 w-5 flex-none text-text-muted" fill="none" viewBox="0 0 24 24" stroke-width="1.8" stroke="currentColor" aria-hidden="true" title="Build time">
                            <path stroke-linecap="round" stroke-linejoin="round" d="M12 6v6h4.5m4.5 0a9 9 0 11-18 0 9 9 0 0118 0z"/>
                        </svg>
                        <div>
                            <div class="font-medium text-text-muted">Build time</div>
                            <div class="font-mono text-text">${buildTimeText}</div>
                        </div>
                    </li>
                    @if(gitCommit != null && !gitCommit.isBlank() && !"unknown".equals(gitCommit))
                    <li class="flex items-start gap-3">
                        <svg class="mt-0.5 h-5 w-5 flex-none text-text-muted" fill="none" viewBox="0 0 24 24" stroke-width="1.8" stroke="currentColor" aria-hidden="true" title="Git commit">
                            <path stroke-linecap="round" stroke-linejoin="round" d="M6 6.878V6a2.25 2.25 0 012.25-2.25h7.5A2.25 2.25 0 0118 6v.878m-12 0c.235-.083.487-.128.75-.128h10.5c.263 0 .515.045.75.128m-12 0A2.25 2.25 0 004.5 9v.878m13.5-3A2.25 2.25 0 0119.5 9v.878m0 0a2.246 2.246 0 00-.75-.128H5.25c-.263 0-.515.045-.75.128m15 0A2.25 2.25 0 0121 12v6a2.25 2.25 0 01-2.25 2.25H5.25A2.25 2.25 0 013 18v-6c0-.98.626-1.813 1.5-2.122"/>
                        </svg>
                        <div>
                            <div class="font-medium text-text-muted">Git commit</div>
                            <div class="font-mono text-text">${gitCommit}</div>
                        </div>
                    </li>
                    @endif
                    <li class="flex items-start gap-3">
                        <svg class="mt-0.5 h-5 w-5 flex-none text-text-muted" fill="currentColor" viewBox="0 0 24 24" aria-hidden="true" title="GitHub">
                            <path fill-rule="evenodd" clip-rule="evenodd" d="M12 .296C5.37.296 0 5.667 0 12.297c0 5.302 3.438 9.8 8.205 11.387.6.11.82-.26.82-.577 0-.285-.011-1.04-.017-2.04-3.338.726-4.042-1.61-4.042-1.61-.546-1.387-1.333-1.757-1.333-1.757-1.09-.745.082-.729.082-.729 1.205.085 1.838 1.237 1.838 1.237 1.07 1.835 2.809 1.305 3.495.998.108-.776.42-1.306.763-1.606-2.665-.305-5.467-1.334-5.467-5.933 0-1.31.468-2.382 1.236-3.222-.124-.303-.536-1.524.116-3.176 0 0 1.008-.323 3.3 1.23a11.5 11.5 0 013.003-.404c1.02.005 2.047.138 3.006.404 2.29-1.553 3.296-1.23 3.296-1.23.655 1.652.243 2.873.12 3.176.77.84 1.235 1.911 1.235 3.222 0 4.61-2.807 5.625-5.48 5.923.431.372.814 1.102.814 2.222 0 1.604-.015 2.898-.015 3.293 0 .32.216.694.825.576C20.565 22.092 24 17.594 24 12.297 24 5.667 18.627.296 12 .296z"/>
                        </svg>
                        <div>
                            <div class="font-medium text-text-muted">GitHub</div>
                            <a href="https://github.com/kochetkov-ma/allure-server" target="_blank" rel="noopener"
                               class="text-primary hover:underline">github.com/kochetkov-ma/allure-server ↗</a>
                        </div>
                    </li>
                    <li class="flex items-start gap-3">
                        <svg class="mt-0.5 h-5 w-5 flex-none text-[#2396ED]" fill="currentColor" viewBox="0 0 24 24" aria-hidden="true" title="Docker Hub">
                            <path d="M13.983 11.078h2.119a.186.186 0 00.186-.185V9.006a.186.186 0 00-.186-.186h-2.119a.185.185 0 00-.185.185v1.888c0 .102.083.185.185.185m-2.954-5.43h2.118a.186.186 0 00.186-.186V3.574a.186.186 0 00-.186-.185h-2.118a.185.185 0 00-.185.185v1.888c0 .102.082.185.185.185m0 2.716h2.118a.187.187 0 00.186-.186V6.29a.186.186 0 00-.186-.185h-2.118a.185.185 0 00-.185.185v1.887c0 .102.082.185.185.186m-2.93 0h2.12a.186.186 0 00.184-.186V6.29a.185.185 0 00-.185-.185H8.1a.185.185 0 00-.185.185v1.887c0 .102.083.185.185.186m-2.964 0h2.119a.186.186 0 00.185-.186V6.29a.185.185 0 00-.185-.185H5.136a.186.186 0 00-.186.185v1.887c0 .102.084.185.186.186m5.893 2.715h2.118a.186.186 0 00.186-.185V9.006a.186.186 0 00-.186-.186h-2.118a.185.185 0 00-.185.185v1.888c0 .102.082.185.185.185m-2.93 0h2.12a.185.185 0 00.184-.185V9.006a.185.185 0 00-.184-.186h-2.12a.185.185 0 00-.185.185v1.888c0 .102.083.185.185.185m-2.964 0h2.119a.185.185 0 00.185-.185V9.006a.185.185 0 00-.184-.186h-2.12a.186.186 0 00-.186.186v1.887c0 .102.084.185.186.185m-2.92 0h2.12a.185.185 0 00.184-.185V9.006a.185.185 0 00-.184-.186h-2.12a.185.185 0 00-.184.185v1.888c0 .102.082.185.185.185M23.763 9.89c-.065-.051-.672-.51-1.954-.51-.338.001-.676.03-1.01.087-.248-1.7-1.653-2.53-1.716-2.566l-.344-.199-.226.327c-.284.438-.49.922-.612 1.43-.23.97-.09 1.882.403 2.661-.595.332-1.55.413-1.744.42H.751a.751.751 0 00-.75.748 11.376 11.376 0 00.692 4.062c.545 1.428 1.355 2.48 2.41 3.124 1.18.723 3.1 1.137 5.275 1.137.983.003 1.963-.086 2.93-.266a12.248 12.248 0 003.823-1.389c.98-.567 1.86-1.288 2.61-2.136 1.252-1.418 1.998-2.997 2.553-4.4h.221c1.372 0 2.215-.549 2.68-1.009.309-.293.55-.65.707-1.046l.098-.288z"/>
                        </svg>
                        <div>
                            <div class="font-medium text-text-muted">Docker Hub</div>
                            <a href="https://hub.docker.com/r/kochetkovma/allure-server" target="_blank" rel="noopener"
                               class="text-primary hover:underline">kochetkovma/allure-server ↗</a>
                        </div>
                    </li>
                    <li class="flex items-start gap-3">
                        <svg class="mt-0.5 h-5 w-5 flex-none text-text-muted" fill="none" viewBox="0 0 24 24" stroke-width="1.8" stroke="currentColor" aria-hidden="true" title="Author">
                            <path stroke-linecap="round" stroke-linejoin="round" d="M15.75 6a3.75 3.75 0 11-7.5 0 3.75 3.75 0 017.5 0zM4.501 20.118a7.5 7.5 0 0114.998 0A17.933 17.933 0 0112 21.75c-2.676 0-5.216-.584-7.499-1.632z"/>
                        </svg>
                        <div>
                            <div class="font-medium text-text-muted">Author</div>
                            <div class="text-text">
                                Maksim Kochetkov
                                (<a href="https://github.com/kochetkov-ma" target="_blank" rel="noopener"
                                    class="text-primary hover:underline">@kochetkov-ma</a>)
                            </div>
                        </div>
                    </li>
                </ul>
            </section>

            <section class="card p-5">
                <h2 class="mb-3 flex items-center gap-2 text-lg font-semibold text-text">
                    <svg class="h-5 w-5 text-text-muted" fill="none" viewBox="0 0 24 24" stroke-width="1.8" stroke="currentColor" aria-hidden="true">
                        <path stroke-linecap="round" stroke-linejoin="round" d="M12 6.042A8.967 8.967 0 006 3.75c-1.052 0-2.062.18-3 .512v14.25A8.987 8.987 0 016 18c2.305 0 4.408.867 6 2.292m0-14.25a8.966 8.966 0 016-2.292c1.052 0 2.062.18 3 .512v14.25A8.987 8.987 0 0018 18a8.967 8.967 0 00-6 2.292m0-14.25v14.25"/>
                    </svg>
                    API documentation
                </h2>
                <p class="text-sm text-text-muted">
                    Every endpoint exposed by Allure Server is described with OpenAPI. Browse, try and
                    copy <code class="rounded bg-surface px-1 font-mono text-primary">curl</code> commands from Swagger UI:
                </p>
                <p class="mt-3">
                    <a href="/swagger-ui.html" target="_blank" rel="noopener" class="btn-primary">
                        Open Swagger UI ↗
                    </a>
                </p>
            </section>
        </article>
    `
)
