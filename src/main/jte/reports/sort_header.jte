@param String label
@param String sortKey
@param String extraClass = ""
<th scope="col"
    class="px-3 py-2 font-semibold text-text-muted select-none cursor-pointer hover:bg-surface hover:text-primary transition-colors ${extraClass}"
    @click="toggleSort('${sortKey}')"
    :aria-sort="sortKey === '${sortKey}' ? (sortDir === 'asc' ? 'ascending' : sortDir === 'desc' ? 'descending' : 'none') : 'none'">
  <span class="inline-flex items-center gap-1">
    ${label}
    <span class="text-xs text-text-muted"
          x-text="sortKey === '${sortKey}' ? (sortDir === 'asc' ? '▲' : sortDir === 'desc' ? '▼' : '↕') : '↕'"></span>
  </span>
</th>
