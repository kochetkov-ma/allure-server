@param String value
!{ var safeValue = value == null ? "" : value; }
<button type="button"
        x-data="{ copied: false }"
        data-copy-value="${safeValue}"
        @click.stop="navigator.clipboard.writeText($el.dataset.copyValue).then(() => { copied = true; setTimeout(() => copied = false, 1500); })"
        :class="copied ? 'text-success' : 'text-text-muted hover:text-primary'"
        :aria-label="copied ? 'Copied' : 'Copy to clipboard'"
        :title="copied ? 'Copied' : 'Copy'"
        class="inline-flex shrink-0 items-center justify-center rounded p-0.5 focus:outline-none focus:ring-1 focus:ring-primary">
  <svg x-show="!copied" class="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" aria-hidden="true">
    <path stroke-linecap="round" stroke-linejoin="round"
          d="M15.75 17.25v3.375c0 .621-.504 1.125-1.125 1.125h-9.75a1.125 1.125 0 01-1.125-1.125V7.875c0-.621.504-1.125 1.125-1.125H6.75a9 9 0 011.5.124m7.5 10.376h3.375c.621 0 1.125-.504 1.125-1.125V11.25c0-4.46-3.243-8.161-7.5-8.876a9.06 9.06 0 00-1.5-.124H9.375c-.621 0-1.125.504-1.125 1.125v3.5m7.5 10.375H9.375a1.125 1.125 0 01-1.125-1.125v-9.25m11.25 3.75v-3.375c0-.621-.504-1.125-1.125-1.125H9.375c-.621 0-1.125.504-1.125 1.125v3.5"/>
  </svg>
  <svg x-show="copied" x-cloak class="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke-width="2.2" stroke="currentColor" aria-hidden="true">
    <path stroke-linecap="round" stroke-linejoin="round" d="M4.5 12.75l6 6 9-13.5"/>
  </svg>
</button>
