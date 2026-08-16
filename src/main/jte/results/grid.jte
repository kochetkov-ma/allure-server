@import java.util.List
@import ru.iopump.qa.allure.web.dto.ResultRow
@param List<ResultRow> rows
@param int totalCount = 0
@param String totalSizeDisplay = "0 B"
@param boolean canMutate = false
<tbody id="results-tbody" class="divide-y divide-border-subtle">
  @if(rows == null || rows.isEmpty())
    <tr>
      <td colspan="5" class="px-4 py-6 text-center text-sm text-text-muted">No results yet — upload <code class="font-mono text-primary">allure-results.zip</code> above.</td>
    </tr>
  @else
    !{ int idx = 0; }
    @for(var row : rows)
      <tr class="hover:bg-surface/60 transition-colors"
          data-idx="${idx}"
          data-uuid="${row.uuid()}"
          data-createdat="${row.createdAt() == null ? "" : row.createdAt()}"
          data-created-epoch="${row.createdEpoch()}"
          data-size-bytes="${row.sizeBytes()}"
          x-show="!cols.uuid || $el.dataset.uuid.toLowerCase().includes(cols.uuid.toLowerCase())">
        <td class="px-4 py-3 align-middle text-center">
          @if(canMutate)
          <input type="checkbox"
                 value="${row.uuid()}"
                 x-model="selected"
                 aria-label="Select result ${row.uuid()}"
                 class="h-4 w-4 rounded border-border bg-surface text-primary focus:ring-primary">
          @endif
        </td>
        <td class="px-4 py-3 align-top font-mono text-xs text-text break-all text-left">
          <div class="flex items-start gap-2">
            <span>${row.uuid()}</span>
            @template.partials.copy_button(value = row.uuid())
          </div>
        </td>
        <td class="px-4 py-3 align-middle text-text-muted whitespace-nowrap tabular-nums text-center">${row.createdAt()}</td>
        <td class="px-4 py-3 align-middle tabular-nums text-sm text-text-muted text-center">${row.sizeDisplay()}</td>
        <td class="px-4 py-3 align-middle text-center">
          @if(canMutate)
          <div class="flex items-center justify-center gap-1">
            <button type="button"
                    @click="$dispatch('open-dialog', { id: 'confirm-delete-' + '${row.uuid()}' })"
                    aria-label="Delete result"
                    title="Delete result"
                    class="inline-flex h-8 w-8 items-center justify-center rounded-md text-text-muted hover:text-error hover:bg-error/10 focus:outline-none focus:ring-2 focus:ring-primary transition-colors">
              <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" aria-hidden="true">
                <path stroke-linecap="round" stroke-linejoin="round" d="M14.74 9l-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 01-2.244 2.077H8.084a2.25 2.25 0 01-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 00-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 013.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 00-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 00-7.5 0"/>
              </svg>
            </button>
          </div>
          @template.partials.confirm_dialog(
              id = "confirm-delete-" + row.uuid(),
              title = "Delete result?",
              message = "Permanently remove result " + row.uuid() + "?",
              confirmUrl = "/app/results/" + row.uuid(),
              confirmMethod = "delete",
              confirmTarget = "body"
          )
          @endif
        </td>
      </tr>
      !{ idx++; }
    @endfor
  @endif
</tbody>
