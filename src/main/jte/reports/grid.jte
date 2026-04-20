@import java.util.List
@import ru.iopump.qa.allure.web.ReportRow
@param List<ReportRow> rows
@param boolean oob = false
@if(oob)
<p id="reports-count" hx-swap-oob="true" class="text-sm text-slate-600">${rows.size()} report(s)</p>
@endif
@if(rows.isEmpty())
  <tr>
    <td colspan="4" class="px-3 py-6 text-center text-sm text-slate-500">
      No reports match the current filter.
    </td>
  </tr>
@else
  @for(ReportRow r : rows)
    <tr class="hover:bg-slate-50">
      <td class="px-3 py-2 align-top">
        <span class="font-medium text-slate-900">${r.path()}</span>
      </td>
      <td class="px-3 py-2 align-top font-mono text-xs text-slate-600" title="${r.uuid()}">
        ${r.uuidShort()}
      </td>
      <td class="px-3 py-2 align-top text-slate-700">
        <time datetime="${r.created()}"
              x-data
              x-init="(() => { try { const iso = '${r.created()}'; if (iso) { $el.textContent = new Intl.DateTimeFormat(undefined, {dateStyle:'short', timeStyle:'short'}).format(new Date(iso)); } } catch(e) {} })()">${r.created()}</time>
      </td>
      <td class="px-3 py-2 align-top">
        <div class="flex items-center gap-3">
          <a href="${r.url()}"
             target="_blank"
             rel="noopener"
             class="text-sky-700 hover:underline">Open</a>
          <button type="button"
                  @click="$dispatch('open-dialog', {id:'confirm-delete-report', uuid:'${r.uuid()}'})"
                  class="text-red-600 hover:underline">Delete</button>
        </div>
      </td>
    </tr>
  @endfor
@endif
