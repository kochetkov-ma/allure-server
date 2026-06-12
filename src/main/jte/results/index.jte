@import java.util.List
@import java.util.Map
@import ru.iopump.qa.allure.web.dto.CurrentUserView
@import ru.iopump.qa.allure.web.dto.ResultRow
@param List<ResultRow> rows
@param int totalCount = 0
@param String totalSizeDisplay = "0 B"
@param Map<String, String> flash = null
@param CurrentUserView currentUser = null
@param boolean authEnabled = false
@param boolean isAdmin = false
@param boolean signInRequired = false
!{ var canMutate = currentUser != null && currentUser.authenticated(); }
@template.layout.main(
    title = "Results",
    activeNav = "results",
    currentUser = currentUser,
    authEnabled = authEnabled,
    isAdmin = isAdmin,
    signInRequired = signInRequired,
    content = @`
        <section class="space-y-6"
                 x-data="{
                   selected: [],
                   generateOpen: false,
                   uploadOpen: false,
                   bulkConfirmOpen: false,
                   cols: { uuid: '' },
                   sortKey: '',
                   sortDir: 'none',
                   totals: { count: ${totalCount}, sizeDisplay: '${totalSizeDisplay}' },
                   form: { executorName: '', executorType: '', executorUrl: '', buildName: '', buildUrl: '', reportUrl: '', reportName: '', reportPath: '', deleteResults: true },
                   visibleUuids() {
                     const tbody = document.getElementById('results-tbody');
                     if (!tbody) return [];
                     return Array.from(tbody.querySelectorAll('tr[data-uuid]'))
                       .filter(r => r.offsetParent !== null)
                       .map(r => r.dataset.uuid);
                   },
                   toggleAllVisible(checked) {
                     const uuids = this.visibleUuids();
                     const set = new Set(this.selected);
                     if (checked) uuids.forEach(u => set.add(u));
                     else uuids.forEach(u => set.delete(u));
                     this.selected = Array.from(set);
                   },
                   selectAllState() {
                     const uuids = this.visibleUuids();
                     if (uuids.length === 0) return { checked: false, indeterminate: false };
                     const set = new Set(this.selected);
                     const picked = uuids.filter(u => set.has(u)).length;
                     if (picked === 0) return { checked: false, indeterminate: false };
                     if (picked === uuids.length) return { checked: true, indeterminate: false };
                     return { checked: false, indeterminate: true };
                   },
                   formatBytes(bytes) {
                     const KB = 1024, MB = KB * 1024, GB = MB * 1024, TB = GB * 1024;
                     if (!Number.isFinite(bytes) || bytes < 0) return '0 B';
                     if (bytes < KB) return bytes + ' B';
                     if (bytes < MB) return (bytes / KB).toFixed(1) + ' KB';
                     if (bytes < GB) return (bytes / MB).toFixed(1) + ' MB';
                     if (bytes < TB) return (bytes / GB).toFixed(1) + ' GB';
                     return (bytes / TB).toFixed(1) + ' TB';
                   },
                   recomputeTotals() {
                     const tbody = document.getElementById('results-tbody');
                     if (!tbody) return;
                     const rows = Array.from(tbody.querySelectorAll('tr[data-uuid]'))
                       .filter(r => r.offsetParent !== null);
                     let sum = 0;
                     rows.forEach(r => { sum += parseInt(r.dataset.sizeBytes || '0', 10); });
                     this.totals = { count: rows.length, sizeDisplay: this.formatBytes(sum) };
                   },
                   toggleSort(key) {
                     if (this.sortKey !== key) { this.sortKey = key; this.sortDir = 'asc'; return; }
                     if (this.sortDir === 'asc') { this.sortDir = 'desc'; return; }
                     if (this.sortDir === 'desc') { this.sortKey = ''; this.sortDir = 'none'; return; }
                     this.sortDir = 'asc';
                   },
                   sortRows() {
                     const tbody = document.getElementById('results-tbody');
                     if (!tbody) return;
                     const rows = Array.from(tbody.querySelectorAll('tr[data-uuid]'));
                     if (this.sortKey === '' || this.sortDir === 'none') {
                       rows.sort((a, b) => (parseInt(a.dataset.idx,10) - parseInt(b.dataset.idx,10)));
                     } else {
                       const key = this.sortKey, dir = this.sortDir === 'asc' ? 1 : -1;
                       rows.sort((a, b) => {
                         if (key === 'size') {
                           return (parseInt(a.dataset.sizeBytes,10) - parseInt(b.dataset.sizeBytes,10)) * dir;
                         }
                         if (key === 'createdat') {
                           return (parseInt(a.dataset.createdEpoch || '0',10) - parseInt(b.dataset.createdEpoch || '0',10)) * dir;
                         }
                         const av = (a.dataset[key] || '').toLowerCase();
                         const bv = (b.dataset[key] || '').toLowerCase();
                         return av < bv ? -1 * dir : av > bv ? 1 * dir : 0;
                       });
                     }
                     rows.forEach(r => tbody.appendChild(r));
                   }
                 }"
                 x-effect="sortRows(); cols.uuid; recomputeTotals()"
                 @keydown.escape.window="generateOpen = false">

            @template.partials.breadcrumb(items = new String[]{"Home", "Results"})

            <div class="flex items-baseline justify-between gap-4 flex-wrap">
                <h1 class="text-2xl font-bold text-text">Results</h1>
                <div class="flex items-center gap-3">
                    <span id="results-count" class="text-sm text-text-muted" x-text="'Selected: ' + selected.length + ' / ${rows.size()}'">Selected: 0 / ${rows.size()}</span>
                    @if(canMutate)
                    <button type="button"
                            @click="generateOpen = true"
                            :disabled="selected.length === 0"
                            class="inline-flex items-center justify-center gap-1 rounded-md bg-success px-4 py-1.5 text-sm font-semibold text-white transition-colors hover:bg-success/80 focus:outline-none focus:ring-2 focus:ring-success disabled:cursor-not-allowed disabled:opacity-50">
                        Generate report
                    </button>
                    <button type="button"
                            @click="bulkConfirmOpen = true"
                            :disabled="selected.length === 0"
                            class="inline-flex items-center justify-center gap-1 rounded-md border border-error/60 bg-transparent px-3 py-1.5 text-sm font-semibold text-error transition-colors hover:bg-error/10 focus:outline-none focus:ring-2 focus:ring-error disabled:cursor-not-allowed disabled:opacity-40"
                            x-text="'Delete selected (' + selected.length + ')'">Delete selected (0)</button>
                    <button type="button"
                            @click="uploadOpen = !uploadOpen"
                            :aria-expanded="uploadOpen"
                            aria-controls="results-upload-panel"
                            aria-label="Toggle upload panel"
                            title="Upload allure-results.zip"
                            class="inline-flex h-9 w-9 items-center justify-center rounded-md border border-border-subtle bg-transparent text-text-muted hover:text-primary hover:border-primary transition-colors focus:outline-none focus:ring-2 focus:ring-primary">
                        <svg class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke-width="1.8" stroke="currentColor" aria-hidden="true">
                            <path stroke-linecap="round" stroke-linejoin="round"
                                  d="M21.44 11.05l-9.19 9.19a6 6 0 01-8.49-8.49l9.19-9.19a4 4 0 015.66 5.66l-9.2 9.19a2 2 0 01-2.83-2.83l8.49-8.48"/>
                        </svg>
                    </button>
                    @endif
                </div>
            </div>

            @template.partials.flash(flash = flash)

            <!-- Collapsible upload panel -->
            @if(canMutate)
            <div id="results-upload-panel"
                 x-show="uploadOpen"
                 x-cloak
                 x-transition.opacity
                 class="card p-4">
                <h2 class="text-lg font-semibold text-text">Upload allure-results.zip</h2>
                <p class="mt-1 text-sm text-text-muted">Upload a ZIP archive of raw Allure results. The server will unpack it under a new UUID and make it available for report generation.</p>
                <form method="post"
                      action="/app/results/upload"
                      enctype="multipart/form-data"
                      class="mt-4 space-y-4">
                    @template.partials.dropzone(name = "file", label = "allure-results archive", hint = "allure-results ZIP · drop here or click to browse")
                    <div class="flex justify-end">
                        <button type="submit" class="btn-primary">Upload</button>
                    </div>
                </form>
            </div>
            @endif

            <!-- Results grid -->
            <div class="overflow-x-auto card">
                <table class="min-w-full divide-y divide-border-subtle text-sm">
                    <thead class="bg-surface">
                        <tr>
                            <th scope="col" class="w-10 px-4 py-2 text-center">
                                @if(canMutate)
                                <input type="checkbox"
                                       aria-label="Select all visible results"
                                       :checked="selectAllState().checked"
                                       x-effect="$el.indeterminate = selectAllState().indeterminate"
                                       @change="toggleAllVisible($event.target.checked)"
                                       class="h-4 w-4 rounded border-border bg-surface text-primary accent-primary focus:ring-primary">
                                @endif
                            </th>
                            @template.reports.sort_header(label = "UUID", sortKey = "uuid", extraClass = "text-left w-[50%]")
                            @template.reports.sort_header(label = "Created", sortKey = "createdat", extraClass = "text-center w-[20%]")
                            @template.reports.sort_header(label = "Size", sortKey = "size", extraClass = "text-center w-[12%]")
                            <th scope="col" class="px-4 py-2 text-center font-semibold text-text-muted w-[10%]">Actions</th>
                        </tr>
                        <tr class="bg-surface/60">
                            <th scope="col" class="px-4 py-1.5"></th>
                            <th scope="col" class="px-4 py-1.5 text-left">
                                <input type="text"
                                       x-model="cols.uuid"
                                       placeholder="filter uuid"
                                       aria-label="Filter by uuid"
                                       class="form-field text-xs font-normal">
                            </th>
                            <th scope="col" class="px-4 py-1.5"></th>
                            <th scope="col" class="px-4 py-1.5"></th>
                            <th scope="col" class="px-4 py-1.5"></th>
                        </tr>
                    </thead>
                    @template.results.grid(rows = rows, totalCount = totalCount, totalSizeDisplay = totalSizeDisplay, canMutate = canMutate)
                    <tfoot class="bg-surface/60">
                      <tr id="results-totals-row" class="bg-surface/60">
                        <td colspan="5" class="px-4 py-2 text-right text-sm text-text-muted">
                          <span class="font-medium text-text" x-text="totals.count">${totalCount}</span> result(s)
                          · Total size: <span class="font-medium text-text" x-text="totals.sizeDisplay">${totalSizeDisplay}</span>
                        </td>
                      </tr>
                    </tfoot>
                </table>
            </div>

            <!-- Bulk delete confirmation dialog -->
            @if(canMutate)
            <template x-if="bulkConfirmOpen">
                <div class="fixed inset-0 z-40 flex items-center justify-center bg-black/60 backdrop-blur-sm"
                     @click.self="bulkConfirmOpen = false" role="presentation">
                    <div role="dialog" aria-modal="true" aria-labelledby="bulk-delete-results-title"
                         class="w-full max-w-md card p-6">
                        <h2 id="bulk-delete-results-title" class="text-lg font-semibold text-text">Delete selected results?</h2>
                        <p class="mt-2 text-sm text-text-muted"
                           x-text="'Delete ' + selected.length + ' result(s)? This cannot be undone.'"></p>
                        <form action="/app/results/bulk-delete" method="post" class="mt-6 flex justify-end gap-2">
                            <template x-for="u in selected" :key="u">
                                <input type="hidden" name="uuids" :value="u">
                            </template>
                            <button type="button" @click="bulkConfirmOpen = false" class="btn-bordered">Cancel</button>
                            <button type="submit"
                                    class="inline-flex items-center justify-center gap-1 rounded-md bg-error px-4 py-1.5 text-sm font-semibold text-white hover:bg-error/80 focus:outline-none focus:ring-2 focus:ring-error">
                                Delete
                            </button>
                        </form>
                    </div>
                </div>
            </template>

            <!-- Generate-report dialog -->
            <template x-if="generateOpen">
                <div class="fixed inset-0 z-40 flex items-center justify-center bg-black/60 backdrop-blur-sm"
                     @click.self="generateOpen = false"
                     role="presentation">
                    <div role="dialog"
                         aria-modal="true"
                         aria-labelledby="generate-title"
                         class="max-h-[90vh] w-full max-w-2xl overflow-y-auto card p-6">
                        <h2 id="generate-title" class="text-lg font-semibold text-text">Generate report</h2>
                        <p class="mt-1 text-sm text-text-muted">
                            <span x-text="selected.length"></span> result(s) selected. Path and executor metadata below are attached to the generated report.
                        </p>

                        <form method="post" action="/app/results/generate" class="mt-4 space-y-4">
                            <template x-for="u in selected" :key="u">
                                <input type="hidden" name="resultUuids" :value="u">
                            </template>

                            <div>
                                <label for="gen-report-path" class="block text-sm font-medium text-text">Report path <span class="text-error">*</span></label>
                                <input id="gen-report-path"
                                       type="text"
                                       x-model="form.reportPath"
                                       required
                                       placeholder="e.g. my-project/main"
                                       class="mt-1 form-field">
                                <template x-for="(seg, i) in form.reportPath.split('/').map(s => s.trim()).filter(s => s.length > 0)" :key="i">
                                    <input type="hidden" name="reportPath" :value="seg">
                                </template>
                                <p class="mt-1 text-xs text-text-muted">Segments separated by <code class="font-mono text-primary">/</code>; 1..32 segments. Latest report per path wins.</p>
                            </div>

                            <fieldset class="rounded-md border border-border-subtle p-3">
                                <legend class="px-1 text-sm font-semibold text-text">Executor (optional)</legend>
                                <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
                                    <label class="flex flex-col text-sm text-text">
                                        <span>Name</span>
                                        <input type="text" name="executorName" x-model="form.executorName" class="mt-1 form-field">
                                    </label>
                                    <label class="flex flex-col text-sm text-text">
                                        <span>Type</span>
                                        <input type="text" name="executorType" x-model="form.executorType" placeholder="CI" class="mt-1 form-field">
                                    </label>
                                    <label class="flex flex-col text-sm text-text sm:col-span-2">
                                        <span>URL</span>
                                        <input type="url" name="executorUrl" x-model="form.executorUrl" placeholder="https://..." class="mt-1 form-field">
                                    </label>
                                    <label class="flex flex-col text-sm text-text">
                                        <span>Build name</span>
                                        <input type="text" name="buildName" x-model="form.buildName" class="mt-1 form-field">
                                    </label>
                                    <label class="flex flex-col text-sm text-text">
                                        <span>Build URL</span>
                                        <input type="url" name="buildUrl" x-model="form.buildUrl" placeholder="https://..." class="mt-1 form-field">
                                    </label>
                                    <label class="flex flex-col text-sm text-text">
                                        <span>Report name</span>
                                        <input type="text" name="reportName" x-model="form.reportName" class="mt-1 form-field">
                                    </label>
                                    <label class="flex flex-col text-sm text-text">
                                        <span>Report URL</span>
                                        <input type="url" name="reportUrl" x-model="form.reportUrl" placeholder="https://..." class="mt-1 form-field">
                                    </label>
                                </div>
                            </fieldset>

                            <label class="flex items-center gap-2 text-sm text-text">
                                <input type="checkbox" name="deleteResults" value="true"
                                       x-model="form.deleteResults"
                                       class="h-4 w-4 rounded border-border bg-surface text-primary focus:ring-primary">
                                Delete selected results after successful generation
                            </label>

                            <div class="flex justify-end gap-2 pt-2">
                                <button type="button" @click="generateOpen = false" class="btn-bordered">Cancel</button>
                                <button type="submit"
                                        :disabled="selected.length === 0 || !form.reportPath"
                                        class="inline-flex items-center justify-center gap-1 rounded-md bg-success px-4 py-1.5 text-sm font-semibold text-white transition-colors hover:bg-success/80 focus:outline-none focus:ring-2 focus:ring-success disabled:cursor-not-allowed disabled:opacity-50">
                                    Generate
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            </template>
            @endif
        </section>
    `
)
