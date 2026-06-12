@import java.util.List
@import java.util.Map
@import ru.iopump.qa.allure.web.ReportRow
@import ru.iopump.qa.allure.web.dto.CurrentUserView
@param List<ReportRow> rows
@param int totalCount = 0
@param String totalSizeDisplay = "0 B"
@param Map<String, String> flash = null
@param CurrentUserView currentUser = null
@param boolean authEnabled = false
@param boolean isAdmin = false
@param boolean signInRequired = false
!{ var canMutate = currentUser != null && currentUser.authenticated(); }
@template.layout.main(
    title = "Reports",
    activeNav = "reports",
    currentUser = currentUser,
    authEnabled = authEnabled,
    isAdmin = isAdmin,
    signInRequired = signInRequired,
    content = @`
      <section class="space-y-6"
               x-data="{
                 uploadOpen: false,
                 cols: { path: '', uuid: '' },
                 sortKey: '',
                 sortDir: 'none',
                 selected: new Set(),
                 bulkConfirmOpen: false,
                 totals: { count: ${totalCount}, sizeDisplay: '${totalSizeDisplay}' },
                 isSelected(uuid) { return this.selected.has(uuid); },
                 toggleRow(uuid, checked) {
                   if (checked) this.selected.add(uuid); else this.selected.delete(uuid);
                   this.selected = new Set(this.selected);
                 },
                 rowMatches(r) {
                   const path = (r.dataset.path || '').toLowerCase();
                   const uuid = (r.dataset.uuid || '').toLowerCase();
                   const pf = (this.cols.path || '').toLowerCase();
                   const uf = (this.cols.uuid || '').toLowerCase();
                   return (!pf || path.includes(pf)) && (!uf || uuid.includes(uf));
                 },
                 visibleUuids() {
                   const tbody = document.getElementById('reports-tbody');
                   if (!tbody) return [];
                   return Array.from(tbody.querySelectorAll('tr[data-uuid]'))
                     .filter(r => this.rowMatches(r))
                     .map(r => r.dataset.uuid);
                 },
                 toggleAllVisible(checked) {
                   const uuids = this.visibleUuids();
                   if (checked) uuids.forEach(u => this.selected.add(u));
                   else uuids.forEach(u => this.selected.delete(u));
                   this.selected = new Set(this.selected);
                 },
                 selectAllState() {
                   const uuids = this.visibleUuids();
                   if (uuids.length === 0) return { checked: false, indeterminate: false };
                   const picked = uuids.filter(u => this.selected.has(u)).length;
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
                   const tbody = document.getElementById('reports-tbody');
                   if (!tbody) return;
                   const rows = Array.from(tbody.querySelectorAll('tr[data-uuid]'))
                     .filter(r => this.rowMatches(r));
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
                   const tbody = document.getElementById('reports-tbody');
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
                       if (key === 'created') {
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
               x-effect="sortRows(); cols.path; cols.uuid; recomputeTotals()">
        @template.partials.breadcrumb(items = new String[]{"Admin", "Reports"})

        <div class="flex items-baseline justify-between gap-4 flex-wrap">
          <h1 class="text-2xl font-bold text-text">Reports</h1>
          <div class="flex items-center gap-3">
            <p id="reports-count" class="text-sm text-text-muted">${rows.size()} report(s)</p>
            @if(canMutate)
            <button type="button"
                    @click="bulkConfirmOpen = true"
                    :disabled="selected.size === 0"
                    class="inline-flex items-center justify-center gap-1 rounded-md border border-error/60 bg-transparent px-3 py-1.5 text-sm font-semibold text-error transition-colors hover:bg-error/10 focus:outline-none focus:ring-2 focus:ring-error disabled:cursor-not-allowed disabled:opacity-40"
                    x-text="'Delete selected (' + selected.size + ')'">Delete selected (0)</button>
            <button type="button"
                    @click="uploadOpen = !uploadOpen"
                    :aria-expanded="uploadOpen"
                    aria-controls="reports-upload-panel"
                    aria-label="Toggle upload panel"
                    title="Upload a report"
                    class="inline-flex h-9 w-9 items-center justify-center rounded-md border border-border-subtle bg-transparent text-text-muted hover:text-primary hover:border-primary transition-colors focus:outline-none focus:ring-2 focus:ring-primary">
              <svg class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke-width="1.8" stroke="currentColor" aria-hidden="true">
                <path stroke-linecap="round" stroke-linejoin="round"
                      d="M21.44 11.05l-9.19 9.19a6 6 0 01-8.49-8.49l9.19-9.19a4 4 0 015.66 5.66l-9.2 9.19a2 2 0 01-2.83-2.83l8.49-8.48"/>
              </svg>
            </button>
            @endif
          </div>
        </div>

        <!-- Collapsible upload panel -->
        @if(canMutate)
        <div id="reports-upload-panel"
             x-show="uploadOpen"
             x-cloak
             x-transition.opacity
             class="card p-4">
          <form action="/app/reports/upload"
                method="post"
                enctype="multipart/form-data"
                class="space-y-3">
            <p class="text-sm text-text-muted">
              Upload a zipped Allure report directory. The <em>path</em> is a logical grouping key
              (for example <code class="font-mono text-primary">myteam/nightly</code>), not a filesystem path. The latest upload
              for a given path becomes the "latest" redirect target.
            </p>
            <label class="flex flex-col text-sm max-w-md">
              <span class="mb-1 font-medium text-text">Path</span>
              <input name="path"
                     type="text"
                     required
                     minlength="1"
                     maxlength="255"
                     placeholder="manual_uploaded"
                     value="manual_uploaded"
                     class="form-field">
            </label>
            <div class="flex flex-col text-sm">
              <span class="mb-1 font-medium text-text">Report ZIP</span>
              @template.partials.dropzone(name = "file", label = "report archive", hint = "zipped Allure report directory · drop here or click to browse")
            </div>
            <div class="flex justify-end">
              <button type="submit" class="btn-primary">Upload</button>
            </div>
          </form>
        </div>
        @endif

        <div class="overflow-x-auto card">
          <table class="min-w-full divide-y divide-border-subtle text-sm">
            <thead class="bg-surface">
              <tr>
                <th scope="col" class="w-8 px-2 py-2 text-center">
                  @if(canMutate)
                  <input type="checkbox"
                         aria-label="Select all visible reports"
                         :checked="selectAllState().checked"
                         x-effect="$el.indeterminate = selectAllState().indeterminate"
                         @change="toggleAllVisible($event.target.checked)"
                         class="h-4 w-4 rounded border-border bg-surface text-primary accent-primary focus:ring-primary">
                  @endif
                </th>
                @template.reports.sort_header(label = "Path", sortKey = "path", extraClass = "text-left w-[28%]")
                @template.reports.sort_header(label = "UUID", sortKey = "uuid", extraClass = "text-left w-[30%]")
                @template.reports.sort_header(label = "Created", sortKey = "created", extraClass = "text-center w-[18%]")
                @template.reports.sort_header(label = "Size", sortKey = "size", extraClass = "text-center w-[8%]")
                <th scope="col" class="px-3 py-2 text-center font-semibold text-text-muted w-[14%]">Build</th>
                <th scope="col" class="px-3 py-2 text-center font-semibold text-text-muted w-[10%]">Actions</th>
              </tr>
              <tr class="bg-surface/60">
                <th scope="col" class="px-2 py-1.5"></th>
                <th scope="col" class="px-3 py-1.5 text-left">
                  <input type="text"
                         x-model="cols.path"
                         placeholder="filter path"
                         aria-label="Filter by path"
                         class="form-field text-xs font-normal">
                </th>
                <th scope="col" class="px-3 py-1.5 text-left">
                  <input type="text"
                         x-model="cols.uuid"
                         placeholder="filter uuid"
                         aria-label="Filter by uuid"
                         class="form-field text-xs font-normal">
                </th>
                <th scope="col" class="px-3 py-1.5"></th>
                <th scope="col" class="px-3 py-1.5"></th>
                <th scope="col" class="px-3 py-1.5"></th>
                <th scope="col" class="px-3 py-1.5"></th>
              </tr>
            </thead>
            <tbody id="reports-tbody" class="divide-y divide-border-subtle">
              @template.reports.grid(rows = rows, totalCount = totalCount, totalSizeDisplay = totalSizeDisplay, canMutate = canMutate)
            </tbody>
            <tfoot class="bg-surface/60">
              <tr id="reports-totals-row" class="bg-surface/60">
                <td colspan="7" class="px-3 py-2 text-right text-sm text-text-muted">
                  <span class="font-medium text-text" x-text="totals.count">${totalCount}</span> report(s)
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
               @click.self="bulkConfirmOpen = false" role="presentation"
               @keydown.escape.window="bulkConfirmOpen = false">
            <div role="dialog" aria-modal="true" aria-labelledby="bulk-delete-reports-title"
                 class="w-full max-w-md card p-6">
              <h2 id="bulk-delete-reports-title" class="text-lg font-semibold text-text">Delete selected reports?</h2>
              <p class="mt-2 text-sm text-text-muted">
                <span x-text="'Delete ' + selected.size + ' report(s)? This cannot be undone.'"></span>
              </p>
              <form action="/app/reports/bulk-delete" method="post" class="mt-6 flex justify-end gap-2">
                <template x-for="u in Array.from(selected)" :key="u">
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

        <!-- Global confirm-delete dialog — row buttons dispatch 'open-dialog' with {id, uuid}. -->
        <div id="confirm-delete-report"
             x-data="{ open: false, uuid: '' }"
             @open-dialog.window="if ($event.detail && $event.detail.id === 'confirm-delete-report') { uuid = $event.detail.uuid; open = true }"
             @keydown.escape.window="open = false">
          <template x-if="open">
            <div class="fixed inset-0 z-40 flex items-center justify-center bg-black/60 backdrop-blur-sm"
                 @click.self="open = false" role="presentation">
              <div role="dialog" aria-modal="true" aria-labelledby="confirm-delete-report-title"
                   class="w-full max-w-md card p-6">
                <h2 id="confirm-delete-report-title" class="text-lg font-semibold text-text">Delete report?</h2>
                <p class="mt-2 text-sm text-text-muted">
                  This will remove the report directory and database row. Any CI job that still
                  links to this UUID will 404 afterwards.
                </p>
                <p class="mt-2 text-xs font-mono text-text-muted" x-text="uuid"></p>
                <form :action="'/app/reports/' + uuid" method="post" class="mt-6 flex justify-end gap-2">
                  <input type="hidden" name="_method" value="delete">
                  <button type="button" @click="open = false" class="btn-bordered">Cancel</button>
                  <button type="submit"
                          class="inline-flex items-center justify-center gap-1 rounded-md bg-error px-4 py-1.5 text-sm font-semibold text-white hover:bg-error/80 focus:outline-none focus:ring-2 focus:ring-error">
                    Delete
                  </button>
                </form>
              </div>
            </div>
          </template>
        </div>
        @endif

        @template.partials.flash(flash = flash)
      </section>
    `
)
