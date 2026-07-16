@import gg.jte.Content
@import org.springframework.security.web.csrf.CsrfToken
@import ru.iopump.qa.allure.web.dto.CurrentUserView
@param String title
@param String activeNav
@param Content content
@param CurrentUserView currentUser = null
@param boolean authEnabled = false
@param boolean isAdmin = false
@param boolean signInRequired = false
@param CsrfToken csrf = null
<!DOCTYPE html>
<html lang="en" class="h-full">
  <head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>${title} — Brew Reporting</title>
    <link rel="icon" type="image/svg+xml" href="/icon.svg">
    <link rel="stylesheet" href="/css/app.css">
    @if(csrf != null)
    <meta name="_csrf" content="${csrf.getToken()}">
    <meta name="_csrf_header" content="${csrf.getHeaderName()}">
    @endif
    <script>
      (function () {
        try {
          var s = localStorage.getItem('allure-server-theme');
          var p = matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark';
          document.documentElement.dataset.theme = s || p;
        } catch (e) { document.documentElement.dataset.theme = 'dark'; }
      })();
      // Attach the CSRF token to every HTMX mutation (the /app surface is CSRF-protected).
      document.addEventListener('htmx:configRequest', function (evt) {
        var token = document.querySelector('meta[name="_csrf"]');
        var header = document.querySelector('meta[name="_csrf_header"]');
        if (token && header && token.content) { evt.detail.headers[header.content] = token.content; }
      });
    </script>
    <script src="/js/htmx.min.js" defer></script>
    <script src="/js/alpine.min.js" defer></script>
    <script src="/js/theme.js" defer></script>
  </head>
  <body class="min-h-full flex flex-col bg-bg text-text font-sans">
    <header class="sticky top-0 z-40 border-b border-border-subtle bg-bg-elevated/95 backdrop-blur">
      <div class="w-full max-w-screen-2xl mx-auto px-4 sm:px-6 lg:px-8 py-3 flex gap-4 items-center">
        <a href="/" class="flex items-center gap-2 focus:outline-none focus:ring-2 focus:ring-primary rounded">
          <span aria-hidden="true"
                class="inline-flex h-9 w-9 items-center justify-center rounded-md bg-primary font-bold text-on-primary">B</span>
          <span class="font-semibold text-base tracking-tight text-text">Brew<span class="text-primary">.</span><span class="text-success">QA</span></span>
        </a>
        <span class="mx-1 h-5 w-px bg-border-subtle" aria-hidden="true"></span>
        <nav class="flex gap-1 text-sm" aria-label="Primary">
          <a href="/app/reports"
             class="px-3 py-1.5 rounded-md transition-colors ${"reports".equals(activeNav) ? "bg-surface text-primary font-semibold" : "text-text-muted hover:text-text hover:bg-surface"}">Reports</a>
          <a href="/app/results"
             class="px-3 py-1.5 rounded-md transition-colors ${"results".equals(activeNav) ? "bg-surface text-primary font-semibold" : "text-text-muted hover:text-text hover:bg-surface"}">Results</a>
          <a href="/app/about"
             class="px-3 py-1.5 rounded-md transition-colors ${"about".equals(activeNav) ? "bg-surface text-primary font-semibold" : "text-text-muted hover:text-text hover:bg-surface"}">About</a>
          <a href="/swagger-ui.html" target="_blank" rel="noopener"
             class="px-3 py-1.5 rounded-md text-text-muted hover:text-text hover:bg-surface transition-colors">Swagger ↗</a>
        </nav>
        <div class="ml-auto flex items-center gap-2">
          <button type="button"
                  id="theme-toggle"
                  aria-label="Toggle theme"
                  title="Toggle theme"
                  class="inline-flex h-9 w-9 items-center justify-center rounded-md border border-border-subtle bg-transparent text-text-muted hover:text-primary hover:border-primary focus:outline-none focus:ring-2 focus:ring-primary transition-colors">
            <svg class="theme-icon theme-icon--moon h-4 w-4" aria-hidden="true" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M21 12.79A9 9 0 1111.21 3 7 7 0 0021 12.79z"/>
            </svg>
            <svg class="theme-icon theme-icon--sun h-4 w-4" aria-hidden="true" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="12" cy="12" r="5"/>
              <line x1="12" y1="1" x2="12" y2="3"/>
              <line x1="12" y1="21" x2="12" y2="23"/>
              <line x1="4.22" y1="4.22" x2="5.64" y2="5.64"/>
              <line x1="18.36" y1="18.36" x2="19.78" y2="19.78"/>
              <line x1="1" y1="12" x2="3" y2="12"/>
              <line x1="21" y1="12" x2="23" y2="12"/>
              <line x1="4.22" y1="19.78" x2="5.64" y2="18.36"/>
              <line x1="18.36" y1="5.64" x2="19.78" y2="4.22"/>
            </svg>
          </button>
          @template.partials.user_menu(user = currentUser, authEnabled = authEnabled, isAdmin = isAdmin, signInRequired = signInRequired, csrf = csrf)
        </div>
      </div>
    </header>
    <main class="flex-1 container mx-auto px-4 py-6">
      ${content}
    </main>
    <div id="toast-container" aria-live="polite" aria-atomic="true"
         class="fixed bottom-4 right-4 flex flex-col gap-2 z-50"></div>
    <footer class="mt-auto border-t border-border-subtle bg-bg-elevated">
      <div class="w-full max-w-screen-2xl mx-auto px-4 sm:px-6 lg:px-8 py-4 flex flex-wrap items-center justify-between gap-3 text-sm text-text-muted">
        <div class="flex items-center gap-2">
          <span class="font-semibold text-text">Brew<span class="text-primary">.</span><span class="text-success">QA</span></span>
          <span class="text-border">·</span>
          <span>© 2026</span>
        </div>
        <nav class="flex flex-wrap items-center gap-4" aria-label="Footer">
          <a href="https://github.com/kochetkov-ma/allure-server" target="_blank" rel="noopener"
             class="hover:text-primary transition-colors">Allure Server on GitHub ↗</a>
          <a href="https://brewcode.app" target="_blank" rel="noopener"
             class="hover:text-primary transition-colors">BrewCode ↗</a>
          <a href="#" aria-disabled="true" title="Coming soon"
             class="text-border cursor-not-allowed">BrewQA (soon)</a>
        </nav>
      </div>
    </footer>
  </body>
</html>
