@import java.util.List
@import ru.iopump.qa.allure.web.ReportRow
@param List<ReportRow> rows
@param int totalCount = 0
@param String totalSizeDisplay = "0 B"
@param boolean canMutate = false
@if(rows.isEmpty())
  <tr>
    <td colspan="7" class="px-3 py-6 text-center text-sm text-text-muted">
      No reports match the current filter.
    </td>
  </tr>
@else
  !{ int idx = 0; }
  @for(ReportRow r : rows)
    <tr class="hover:bg-surface/60 transition-colors"
        data-idx="${idx}"
        data-path="${r.path() == null ? "" : r.path()}"
        data-uuid="${r.uuid() == null ? "" : r.uuid()}"
        data-created="${r.created() == null ? "" : r.created()}"
        data-created-epoch="${r.createdEpoch()}"
        data-size-bytes="${r.sizeBytes()}"
        data-size-display="${r.sizeDisplay() == null ? "" : r.sizeDisplay()}"
        data-build="${r.buildLabel()}"
        x-show="rowMatches($el)">
      <td class="px-2 py-3 align-middle text-center">
        @if(canMutate)
        <input type="checkbox"
               value="${r.uuid()}"
               :checked="isSelected('${r.uuid()}')"
               @change="toggleRow('${r.uuid()}', $event.target.checked)"
               aria-label="Select report ${r.uuid()}"
               class="h-4 w-4 rounded border-border bg-surface text-primary accent-primary focus:ring-primary">
        @endif
      </td>
      <td class="px-3 py-3 align-top break-all text-left">
        <div class="flex items-start gap-2">
          <span class="font-medium text-text">${r.path()}</span>
          @template.partials.copy_button(value = r.path())
          @if(r.active())
            <span class="inline-flex items-center rounded-full bg-success/15 px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-success"
                  title="Latest report for this path">Active</span>
          @endif
        </div>
        @if(r.active())
          <a href="${r.latestUrl()}"
             target="_blank"
             rel="noopener"
             title="Stable latest link for this path"
             class="mt-1 inline-flex items-center gap-1 text-xs text-primary hover:underline">latest ↗</a>
        @endif
      </td>
      <td class="px-3 py-3 align-top font-mono text-xs text-text-muted break-all text-left">
        <div class="flex items-start gap-2">
          <span title="${r.uuid()}">${r.uuid()}</span>
          @template.partials.copy_button(value = r.uuid())
        </div>
      </td>
      <td class="px-3 py-3 align-middle text-text-muted whitespace-nowrap tabular-nums text-center">
        ${r.created()}
      </td>
      <td class="px-3 py-3 align-middle text-text-muted whitespace-nowrap tabular-nums text-center">
        ${r.sizeDisplay()}
      </td>
      <td class="px-3 py-3 align-middle text-center text-text-muted">
        @if(r.buildUrl().isEmpty())
          <span class="text-text-muted/60">—</span>
        @elseif(r.buildUrl().startsWith("http://") || r.buildUrl().startsWith("https://"))
          <a href="${r.buildUrl()}"
             target="_blank"
             rel="noopener"
             title="${r.buildUrl()}"
             class="inline-block max-w-[14rem] truncate align-middle text-primary hover:underline">${r.buildLabel()}</a>
        @else
          <span title="${r.buildUrl()}"
                class="inline-block max-w-[14rem] truncate align-middle">${r.buildLabel()}</span>
        @endif
      </td>
      <td class="px-3 py-3 align-middle text-center">
        <div class="flex items-center justify-center gap-1">
          <a href="${r.url()}"
             target="_blank"
             rel="noopener"
             aria-label="Open report in new tab"
             title="Open report"
             class="inline-flex h-8 w-8 items-center justify-center rounded-md text-text-muted hover:text-primary hover:bg-surface focus:outline-none focus:ring-2 focus:ring-primary transition-colors">
            <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" aria-hidden="true">
              <path stroke-linecap="round" stroke-linejoin="round" d="M13.5 6H5.25A2.25 2.25 0 003 8.25v10.5A2.25 2.25 0 005.25 21h10.5A2.25 2.25 0 0018 18.75V10.5m-10.5 6L21 3m0 0h-5.25M21 3v5.25"/>
            </svg>
          </a>
          <button type="button"
                  x-data="{ copied: false }"
                  data-report-href="${r.url()}"
                  @click.stop="navigator.clipboard.writeText(new URL($el.dataset.reportHref, window.location.origin).href).then(() => { copied = true; setTimeout(() => copied = false, 1500); })"
                  :aria-label="copied ? 'Link copied' : 'Copy report link'"
                  :title="copied ? 'Link copied' : 'Copy report link'"
                  :class="copied ? 'text-success' : 'text-text-muted hover:text-primary hover:bg-surface'"
                  class="inline-flex h-8 w-8 items-center justify-center rounded-md focus:outline-none focus:ring-2 focus:ring-primary transition-colors">
            <svg x-show="!copied" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" aria-hidden="true">
              <path stroke-linecap="round" stroke-linejoin="round" d="M13.19 8.688a4.5 4.5 0 011.242 7.244l-4.5 4.5a4.5 4.5 0 01-6.364-6.364l1.757-1.757m13.35-.622l1.757-1.757a4.5 4.5 0 00-6.364-6.364l-4.5 4.5a4.5 4.5 0 001.242 7.244"/>
            </svg>
            <svg x-show="copied" x-cloak class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke-width="2.2" stroke="currentColor" aria-hidden="true">
              <path stroke-linecap="round" stroke-linejoin="round" d="M4.5 12.75l6 6 9-13.5"/>
            </svg>
          </button>
          @if(canMutate)
          <button type="button"
                  @click="$dispatch('open-dialog', {id:'confirm-delete-report', uuid:'${r.uuid()}'})"
                  aria-label="Delete report"
                  title="Delete report"
                  class="inline-flex h-8 w-8 items-center justify-center rounded-md text-text-muted hover:text-error hover:bg-error/10 focus:outline-none focus:ring-2 focus:ring-primary transition-colors">
            <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" aria-hidden="true">
              <path stroke-linecap="round" stroke-linejoin="round" d="M14.74 9l-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 01-2.244 2.077H8.084a2.25 2.25 0 01-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 00-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 013.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 00-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 00-7.5 0"/>
            </svg>
          </button>
          @endif
        </div>
      </td>
    </tr>
    !{ idx++; }
  @endfor
@endif
