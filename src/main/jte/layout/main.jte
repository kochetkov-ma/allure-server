@import gg.jte.Content
@param String title
@param String activeNav
@param Content content
<!DOCTYPE html>
<html lang="en" class="h-full bg-slate-50 text-slate-900">
  <head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>${title} — Allure Server</title>
    <link rel="stylesheet" href="/css/app.css">
    <script src="/js/htmx.min.js" defer></script>
    <script src="/js/alpine.min.js" defer></script>
  </head>
  <body class="min-h-full flex flex-col">
    <header class="bg-slate-900 text-white px-6 py-3 flex gap-4 items-center">
      <span class="font-semibold text-base">Allure Server</span>
      <nav class="flex gap-3 text-sm" aria-label="Primary">
        <a href="/app/reports"
           class="px-2 py-1 rounded ${"reports".equals(activeNav) ? "bg-slate-700 font-semibold" : "hover:underline"}">Reports</a>
        <a href="/app/results"
           class="px-2 py-1 rounded ${"results".equals(activeNav) ? "bg-slate-700 font-semibold" : "hover:underline"}">Results</a>
        <a href="/app/about"
           class="px-2 py-1 rounded ${"about".equals(activeNav) ? "bg-slate-700 font-semibold" : "hover:underline"}">About</a>
        <a href="/swagger-ui.html" target="_blank" rel="noopener"
           class="px-2 py-1 rounded hover:underline">Swagger ↗</a>
      </nav>
    </header>
    <main class="flex-1 px-6 py-6">
      ${content}
    </main>
    <div id="toast-container" aria-live="polite" aria-atomic="true"
         class="fixed bottom-4 right-4 flex flex-col gap-2 z-50"></div>
  </body>
</html>
