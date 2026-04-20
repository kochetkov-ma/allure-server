@param String[] items
<nav aria-label="Breadcrumb" class="mb-4 text-sm text-slate-600">
  <ol class="flex flex-wrap gap-1">
    @for(var i = 0; i < items.length; i++)
      @if(i > 0)
        <li aria-hidden="true" class="px-1 text-slate-400">/</li>
      @endif
      <li>
        @if(i == items.length - 1)
          <span aria-current="page" class="font-medium text-slate-900">${items[i]}</span>
        @else
          <span>${items[i]}</span>
        @endif
      </li>
    @endfor
  </ol>
</nav>
