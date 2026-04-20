@import java.util.List
@import java.util.Map
@import ru.iopump.qa.allure.web.ReportRow
@param List<ReportRow> rows
@param String q
@param String sort
@param String dir
@param Map<String, String> flash = null
@template.layout.main(
    title = "Reports",
    activeNav = "reports",
    content = @`
      <section class="max-w-6xl space-y-6" x-data>
        @template.partials.breadcrumb(items = new String[]{"Admin", "Reports"})

        <div class="flex items-baseline justify-between gap-4 flex-wrap">
          <h1 class="text-2xl font-bold">Reports</h1>
          <p id="reports-count" class="text-sm text-slate-600">${rows.size()} report(s)</p>
        </div>

        <!-- Upload form: plain multipart POST (no htmx) — simpler, leverages flash-toast feedback on redirect. -->
        <form action="/app/reports/upload"
              method="post"
              enctype="multipart/form-data"
              class="rounded border border-slate-200 bg-white p-4 shadow-sm space-y-3">
          <h2 class="text-lg font-semibold">Upload a generated Allure report</h2>
          <p class="text-sm text-slate-600">
            Upload a zipped Allure report directory. The <em>path</em> is a logical grouping key
            (for example <code>myteam/nightly</code>), not a filesystem path. The latest upload
            for a given path becomes the "latest" redirect target.
          </p>
          <div class="grid grid-cols-1 md:grid-cols-3 gap-3">
            <label class="flex flex-col text-sm">
              <span class="mb-1 font-medium text-slate-700">Path</span>
              <input name="path"
                     type="text"
                     required
                     minlength="1"
                     maxlength="255"
                     placeholder="manual_uploaded"
                     value="manual_uploaded"
                     class="rounded border border-slate-300 px-3 py-1.5 focus:border-slate-500 focus:outline-none">
            </label>
            <label class="flex flex-col text-sm md:col-span-2">
              <span class="mb-1 font-medium text-slate-700">Report ZIP</span>
              <input name="file"
                     type="file"
                     accept=".zip,application/zip,application/x-zip-compressed"
                     required
                     class="rounded border border-slate-300 px-3 py-1.5 file:mr-3 file:rounded file:border-0 file:bg-slate-900 file:px-3 file:py-1 file:text-sm file:text-white hover:file:bg-slate-700">
            </label>
          </div>
          <div class="flex justify-end">
            <button type="submit"
                    class="rounded bg-slate-900 px-4 py-1.5 text-sm font-semibold text-white hover:bg-slate-700">
              Upload
            </button>
          </div>
        </form>

        <!-- Filter + sort controls — htmx GETs /app/reports/grid and swaps the tbody only.
             Every control carries name=value so the form is the single source of truth for query state. -->
        <form id="reports-filter"
              class="flex flex-wrap items-end gap-3 rounded border border-slate-200 bg-white p-4 shadow-sm"
              hx-get="/app/reports/grid"
              hx-target="#reports-tbody"
              hx-swap="innerHTML"
              hx-trigger="change from:select, keyup changed delay:500ms from:input[name='q']"
              hx-include="this">
          <label class="flex flex-col text-sm">
            <span class="mb-1 font-medium text-slate-700">Filter</span>
            <input name="q"
                   type="search"
                   placeholder="path or uuid…"
                   value="${q == null ? "" : q}"
                   class="rounded border border-slate-300 px-3 py-1.5 focus:border-slate-500 focus:outline-none">
          </label>
          <label class="flex flex-col text-sm">
            <span class="mb-1 font-medium text-slate-700">Sort</span>
            <select name="sort" class="rounded border border-slate-300 px-3 py-1.5 focus:border-slate-500 focus:outline-none">
              <option value="created" selected="${"created".equals(sort)}">Created</option>
              <option value="path" selected="${"path".equals(sort)}">Path</option>
              <option value="uuid" selected="${"uuid".equals(sort)}">UUID</option>
            </select>
          </label>
          <label class="flex flex-col text-sm">
            <span class="mb-1 font-medium text-slate-700">Direction</span>
            <select name="dir" class="rounded border border-slate-300 px-3 py-1.5 focus:border-slate-500 focus:outline-none">
              <option value="desc" selected="${"desc".equals(dir)}">Desc</option>
              <option value="asc" selected="${"asc".equals(dir)}">Asc</option>
            </select>
          </label>
        </form>

        <div class="overflow-x-auto rounded border border-slate-200 bg-white shadow-sm">
          <table class="min-w-full divide-y divide-slate-200 text-sm">
            <thead class="bg-slate-100">
              <tr>
                <th scope="col" class="px-3 py-2 text-left font-semibold text-slate-700">Path</th>
                <th scope="col" class="px-3 py-2 text-left font-semibold text-slate-700">UUID</th>
                <th scope="col" class="px-3 py-2 text-left font-semibold text-slate-700">Created</th>
                <th scope="col" class="px-3 py-2 text-left font-semibold text-slate-700">Actions</th>
              </tr>
            </thead>
            <tbody id="reports-tbody" class="divide-y divide-slate-100">
              @template.reports.grid(rows = rows)
            </tbody>
          </table>
        </div>

        <!-- Global confirm-delete dialog — row buttons dispatch 'open-dialog' with {id, uuid}.
             The dialog reads Alpine store 'report' to build the final DELETE URL. -->
        <div id="confirm-delete-report"
             x-data="{ open: false, uuid: '' }"
             @open-dialog.window="if ($event.detail && $event.detail.id === 'confirm-delete-report') { uuid = $event.detail.uuid; open = true }"
             @keydown.escape.window="open = false">
          <template x-if="open">
            <div class="fixed inset-0 z-40 flex items-center justify-center bg-slate-900/50"
                 @click.self="open = false" role="presentation">
              <div role="dialog" aria-modal="true" aria-labelledby="confirm-delete-report-title"
                   class="w-full max-w-md rounded-lg bg-white p-6 shadow-xl">
                <h2 id="confirm-delete-report-title" class="text-lg font-semibold text-slate-900">Delete report?</h2>
                <p class="mt-2 text-sm text-slate-700">
                  This will remove the report directory and database row. Any CI job that still
                  links to this UUID will 404 afterwards.
                </p>
                <p class="mt-2 text-xs font-mono text-slate-500" x-text="uuid"></p>
                <form :action="'/app/reports/' + uuid" method="post" class="mt-6 flex justify-end gap-2">
                  <input type="hidden" name="_method" value="delete">
                  <button type="button" @click="open = false"
                          class="rounded border border-slate-300 bg-white px-4 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-100">
                    Cancel
                  </button>
                  <button type="submit"
                          class="rounded bg-red-600 px-4 py-1.5 text-sm font-semibold text-white hover:bg-red-700">
                    Delete
                  </button>
                </form>
              </div>
            </div>
          </template>
        </div>

        <!-- Toast from flash — reused partial renders a single toast inside the layout's #toast-container. -->
        @template.partials.flash(flash = flash)
      </section>
    `
)
