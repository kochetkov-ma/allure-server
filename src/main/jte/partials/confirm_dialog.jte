@param String id
@param String title
@param String message
@param String confirmUrl
@param String confirmMethod = "delete"
@param String confirmTarget = "body"
<div id="${id}" x-data="{ open: false }"
     @open-dialog.window="if ($event.detail && $event.detail.id === '${id}') open = true"
     @keydown.escape.window="open = false">
  <div x-show="open" x-cloak
       class="fixed inset-0 z-40 flex items-center justify-center bg-black/60 backdrop-blur-sm"
       @click.self="open = false" role="presentation">
    <div role="dialog" aria-modal="true" aria-labelledby="${id}-title"
         class="w-full max-w-md card p-6">
      <h2 id="${id}-title" class="text-lg font-semibold text-text">${title}</h2>
      <p class="mt-2 text-sm text-text-muted">${message}</p>
      <div class="mt-6 flex justify-end gap-2">
        <button type="button" @click="open = false" class="btn-bordered">Cancel</button>
        <button type="button"
                hx-delete="${confirmUrl}"
                hx-target="${confirmTarget}"
                hx-swap="outerHTML"
                hx-on::after-request="this.dispatchEvent(new CustomEvent('htmx-done', { bubbles: true }))"
                @htmx-done="open = false"
                class="inline-flex items-center justify-center gap-1 rounded-md bg-error px-4 py-1.5 text-sm font-semibold text-white transition-colors hover:bg-error/80 focus:outline-none focus:ring-2 focus:ring-error">
          Confirm
        </button>
      </div>
    </div>
  </div>
</div>
