@import java.util.List
@import java.util.Map
@import ru.iopump.qa.allure.web.dto.ResultRow
@param List<ResultRow> rows
@param String q
@param String sort
@param String dir
@param Map<String, String> flash = null
@template.layout.main(
    title = "Results",
    activeNav = "results",
    content = @`
        <section class="max-w-6xl space-y-6"
                 x-data="{ selected: [], generateOpen: false, form: { executorName: '', executorType: '', executorUrl: '', buildName: '', buildUrl: '', reportUrl: '', reportName: '', reportPath: '', deleteResults: true } }"
                 @keydown.escape.window="generateOpen = false">

            @template.partials.breadcrumb(items = new String[]{"Home", "Results"})

            <div class="flex items-baseline justify-between">
                <h1 class="text-2xl font-bold text-slate-900">Results</h1>
                <span id="results-count" class="text-sm text-slate-500" x-text="'Selected: ' + selected.length + ' / ${rows.size()}'">Selected: 0 / ${rows.size()}</span>
            </div>

            @template.partials.flash(flash = flash)

            <!-- Upload panel -->
            <section class="rounded border border-slate-200 bg-white p-4 shadow-sm">
                <h2 class="text-lg font-semibold text-slate-900">Upload allure-results.zip</h2>
                <p class="mt-1 text-sm text-slate-600">Upload a ZIP archive of raw Allure results. The server will unpack it under a new UUID and make it available for report generation.</p>
                <form method="post"
                      action="/app/results/upload"
                      enctype="multipart/form-data"
                      class="mt-4 flex flex-wrap items-center gap-3">
                    <input type="file"
                           name="file"
                           accept=".zip,application/zip,application/x-zip-compressed"
                           required
                           class="block text-sm text-slate-700 file:mr-3 file:rounded file:border-0 file:bg-slate-900 file:px-3 file:py-1.5 file:text-sm file:font-semibold file:text-white hover:file:bg-slate-700">
                    <button type="submit"
                            class="rounded bg-slate-900 px-4 py-1.5 text-sm font-semibold text-white hover:bg-slate-700">
                        Upload
                    </button>
                </form>
            </section>

            <!-- Filter + action toolbar -->
            <section class="rounded border border-slate-200 bg-white p-4 shadow-sm">
                <form method="get"
                      action="/app/results"
                      hx-get="/app/results/grid"
                      hx-trigger="input delay:300ms from:find input[name='q'], change from:find select"
                      hx-target="#results-tbody"
                      hx-swap="outerHTML"
                      class="flex flex-wrap items-end gap-3">
                    <label class="flex flex-col text-sm text-slate-700">
                        <span class="mb-1 font-medium">Filter by UUID</span>
                        <input type="search"
                               name="q"
                               value="${q}"
                               placeholder="part of uuid..."
                               class="w-72 rounded border border-slate-300 px-2 py-1 text-sm focus:border-slate-500 focus:outline-none focus:ring-1 focus:ring-slate-500">
                    </label>
                    <label class="flex flex-col text-sm text-slate-700">
                        <span class="mb-1 font-medium">Sort by</span>
                        <select name="sort"
                                class="rounded border border-slate-300 px-2 py-1 text-sm focus:border-slate-500 focus:outline-none focus:ring-1 focus:ring-slate-500">
                            <option value="createdAt" selected="${"createdAt".equals(sort)}">Created</option>
                            <option value="uuid" selected="${"uuid".equals(sort)}">UUID</option>
                            <option value="size" selected="${"size".equals(sort)}">Size</option>
                        </select>
                    </label>
                    <label class="flex flex-col text-sm text-slate-700">
                        <span class="mb-1 font-medium">Direction</span>
                        <select name="dir"
                                class="rounded border border-slate-300 px-2 py-1 text-sm focus:border-slate-500 focus:outline-none focus:ring-1 focus:ring-slate-500">
                            <option value="desc" selected="${"desc".equals(dir)}">Descending</option>
                            <option value="asc" selected="${"asc".equals(dir)}">Ascending</option>
                        </select>
                    </label>
                    <button type="submit"
                            class="rounded border border-slate-300 bg-white px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-100">
                        Apply
                    </button>
                    <div class="ml-auto flex gap-2">
                        <button type="button"
                                @click="generateOpen = true"
                                :disabled="selected.length === 0"
                                class="rounded bg-emerald-600 px-4 py-1.5 text-sm font-semibold text-white hover:bg-emerald-700 disabled:cursor-not-allowed disabled:bg-slate-300">
                            Generate report
                        </button>
                    </div>
                </form>
            </section>

            <!-- Results grid -->
            <section class="rounded border border-slate-200 bg-white shadow-sm">
                <table class="min-w-full divide-y divide-slate-200">
                    <thead class="bg-slate-100">
                        <tr>
                            <th scope="col" class="w-10 px-4 py-2 text-left">
                                <input type="checkbox"
                                       aria-label="Select all visible results"
                                       @change="selected = $event.target.checked ? Array.from(document.querySelectorAll('#results-tbody input[type=checkbox]')).map(el => el.value) : []"
                                       class="h-4 w-4 rounded border-slate-300 text-slate-900 focus:ring-slate-500">
                            </th>
                            <th scope="col" class="px-4 py-2 text-left text-xs font-semibold uppercase tracking-wider text-slate-600">UUID</th>
                            <th scope="col" class="px-4 py-2 text-right text-xs font-semibold uppercase tracking-wider text-slate-600">Size</th>
                            <th scope="col" class="px-4 py-2 text-left text-xs font-semibold uppercase tracking-wider text-slate-600">Created</th>
                            <th scope="col" class="px-4 py-2 text-right text-xs font-semibold uppercase tracking-wider text-slate-600">Actions</th>
                        </tr>
                    </thead>
                    @template.results.grid(rows = rows)
                </table>
            </section>

            <!-- Generate-report dialog (Alpine <dialog>-like overlay) -->
            <template x-if="generateOpen">
                <div class="fixed inset-0 z-40 flex items-center justify-center bg-slate-900/50"
                     @click.self="generateOpen = false"
                     role="presentation">
                    <div role="dialog"
                         aria-modal="true"
                         aria-labelledby="generate-title"
                         class="max-h-[90vh] w-full max-w-2xl overflow-y-auto rounded-lg bg-white p-6 shadow-xl">
                        <h2 id="generate-title" class="text-lg font-semibold text-slate-900">Generate report</h2>
                        <p class="mt-1 text-sm text-slate-600">
                            <span x-text="selected.length"></span> result(s) selected. Path and executor metadata below are attached to the generated report.
                        </p>

                        <form method="post" action="/app/results/generate" class="mt-4 space-y-4">
                            <!-- Hidden inputs: one per selected UUID, wired via Alpine -->
                            <template x-for="u in selected" :key="u">
                                <input type="hidden" name="resultUuids" :value="u">
                            </template>

                            <div>
                                <label for="gen-report-path" class="block text-sm font-medium text-slate-700">Report path <span class="text-red-600">*</span></label>
                                <input id="gen-report-path"
                                       type="text"
                                       x-model="form.reportPath"
                                       required
                                       placeholder="e.g. my-project/main"
                                       class="mt-1 block w-full rounded border border-slate-300 px-2 py-1.5 text-sm focus:border-slate-500 focus:outline-none focus:ring-1 focus:ring-slate-500">
                                <!-- Split path into per-segment hidden inputs so Spring binds List<String> -->
                                <template x-for="seg in form.reportPath.split('/').map(s => s.trim()).filter(s => s.length > 0)" :key="seg">
                                    <input type="hidden" name="reportPath" :value="seg">
                                </template>
                                <p class="mt-1 text-xs text-slate-500">Segments separated by <code>/</code>; 1..32 segments. Latest report per path wins.</p>
                            </div>

                            <fieldset class="rounded border border-slate-200 p-3">
                                <legend class="px-1 text-sm font-semibold text-slate-700">Executor (optional)</legend>
                                <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
                                    <label class="flex flex-col text-sm text-slate-700">
                                        <span>Name</span>
                                        <input type="text" name="executorName" x-model="form.executorName"
                                               class="mt-1 rounded border border-slate-300 px-2 py-1 text-sm focus:border-slate-500 focus:outline-none focus:ring-1 focus:ring-slate-500">
                                    </label>
                                    <label class="flex flex-col text-sm text-slate-700">
                                        <span>Type</span>
                                        <input type="text" name="executorType" x-model="form.executorType" placeholder="CI"
                                               class="mt-1 rounded border border-slate-300 px-2 py-1 text-sm focus:border-slate-500 focus:outline-none focus:ring-1 focus:ring-slate-500">
                                    </label>
                                    <label class="flex flex-col text-sm text-slate-700 sm:col-span-2">
                                        <span>URL</span>
                                        <input type="url" name="executorUrl" x-model="form.executorUrl" placeholder="https://..."
                                               class="mt-1 rounded border border-slate-300 px-2 py-1 text-sm focus:border-slate-500 focus:outline-none focus:ring-1 focus:ring-slate-500">
                                    </label>
                                    <label class="flex flex-col text-sm text-slate-700">
                                        <span>Build name</span>
                                        <input type="text" name="buildName" x-model="form.buildName"
                                               class="mt-1 rounded border border-slate-300 px-2 py-1 text-sm focus:border-slate-500 focus:outline-none focus:ring-1 focus:ring-slate-500">
                                    </label>
                                    <label class="flex flex-col text-sm text-slate-700">
                                        <span>Build URL</span>
                                        <input type="url" name="buildUrl" x-model="form.buildUrl" placeholder="https://..."
                                               class="mt-1 rounded border border-slate-300 px-2 py-1 text-sm focus:border-slate-500 focus:outline-none focus:ring-1 focus:ring-slate-500">
                                    </label>
                                    <label class="flex flex-col text-sm text-slate-700">
                                        <span>Report name</span>
                                        <input type="text" name="reportName" x-model="form.reportName"
                                               class="mt-1 rounded border border-slate-300 px-2 py-1 text-sm focus:border-slate-500 focus:outline-none focus:ring-1 focus:ring-slate-500">
                                    </label>
                                    <label class="flex flex-col text-sm text-slate-700">
                                        <span>Report URL</span>
                                        <input type="url" name="reportUrl" x-model="form.reportUrl" placeholder="https://..."
                                               class="mt-1 rounded border border-slate-300 px-2 py-1 text-sm focus:border-slate-500 focus:outline-none focus:ring-1 focus:ring-slate-500">
                                    </label>
                                </div>
                            </fieldset>

                            <label class="flex items-center gap-2 text-sm text-slate-700">
                                <input type="checkbox" name="deleteResults" value="true"
                                       x-model="form.deleteResults"
                                       class="h-4 w-4 rounded border-slate-300 text-slate-900 focus:ring-slate-500">
                                Delete selected results after successful generation
                            </label>

                            <div class="flex justify-end gap-2 pt-2">
                                <button type="button"
                                        @click="generateOpen = false"
                                        class="rounded border border-slate-300 bg-white px-4 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-100">
                                    Cancel
                                </button>
                                <button type="submit"
                                        :disabled="selected.length === 0 || !form.reportPath"
                                        class="rounded bg-emerald-600 px-4 py-1.5 text-sm font-semibold text-white hover:bg-emerald-700 disabled:cursor-not-allowed disabled:bg-slate-300">
                                    Generate
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            </template>
        </section>
    `
)
