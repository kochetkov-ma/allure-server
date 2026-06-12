@param String[] items
<nav aria-label="Breadcrumb" class="mb-4 text-sm text-text-muted">
  <ol class="flex flex-wrap gap-1">
    @for(var i = 0; i < items.length; i++)
      @if(i > 0)
        <li aria-hidden="true" class="px-1 text-border">/</li>
      @endif
      <li>
        @if(i == items.length - 1)
          <span aria-current="page" class="font-medium text-text">${items[i]}</span>
        @else
          <span>${items[i]}</span>
        @endif
      </li>
    @endfor
  </ol>
</nav>
