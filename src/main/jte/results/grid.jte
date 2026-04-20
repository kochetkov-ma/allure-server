@import java.util.List
@import ru.iopump.qa.allure.web.dto.ResultRow
@param List<ResultRow> rows
@param boolean oob = false
@if(oob)
<span id="results-count" hx-swap-oob="true" class="text-sm text-slate-500" x-text="'Selected: ' + selected.length + ' / ${rows.size()}'">Selected: 0 / ${rows.size()}</span>
@endif
<tbody id="results-tbody" class="divide-y divide-slate-200">
  @if(rows == null || rows.isEmpty())
    <tr>
      <td colspan="5" class="px-4 py-6 text-center text-sm text-slate-500">No results yet — upload <code>allure-results.zip</code> above.</td>
    </tr>
  @else
    @for(var row : rows)
      <tr class="hover:bg-slate-50">
        <td class="px-4 py-2 align-middle">
          <input type="checkbox"
                 value="${row.uuid()}"
                 x-model="selected"
                 aria-label="Select result ${row.uuid()}"
                 class="h-4 w-4 rounded border-slate-300 text-slate-900 focus:ring-slate-500">
        </td>
        <td class="px-4 py-2 font-mono text-xs text-slate-800">${row.uuid()}</td>
        <td class="px-4 py-2 text-right tabular-nums text-sm text-slate-700">${row.sizeBytes() / 1024} KB</td>
        <td class="px-4 py-2 text-sm text-slate-700"
            x-data="{ iso: '${row.createdAt()}' }"
            x-text="iso ? new Date(iso).toLocaleString() : '—'">
          ${row.createdAt()}
        </td>
        <td class="px-4 py-2 text-right">
          <button type="button"
                  @click="$dispatch('open-dialog', { id: 'confirm-delete-' + '${row.uuid()}' })"
                  class="rounded border border-red-300 bg-white px-2 py-1 text-xs font-semibold text-red-700 hover:bg-red-50">
            Delete
          </button>
          @template.partials.confirm_dialog(
              id = "confirm-delete-" + row.uuid(),
              title = "Delete result?",
              message = "Permanently remove result " + row.uuid() + "?",
              confirmUrl = "/app/results/" + row.uuid(),
              confirmMethod = "delete",
              confirmTarget = "body"
          )
        </td>
      </tr>
    @endfor
  @endif
</tbody>
