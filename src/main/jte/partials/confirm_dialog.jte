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
       class="fixed inset-0 z-40 flex items-center justify-center bg-slate-900/50"
       @click.self="open = false" role="presentation">
    <div role="dialog" aria-modal="true" aria-labelledby="${id}-title"
         class="w-full max-w-md rounded-lg bg-white p-6 shadow-xl">
      <h2 id="${id}-title" class="text-lg font-semibold text-slate-900">${title}</h2>
      <p class="mt-2 text-sm text-slate-700">${message}</p>
      <div class="mt-6 flex justify-end gap-2">
        <button type="button" @click="open = false"
                class="rounded border border-slate-300 bg-white px-4 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-100">
          Cancel
        </button>
        <button type="button"
                hx-delete="${confirmUrl}"
                hx-target="${confirmTarget}"
                hx-swap="outerHTML"
                hx-on::after-request="$dispatch('htmx-done')"
                @htmx-done="open = false"
                class="rounded bg-red-600 px-4 py-1.5 text-sm font-semibold text-white hover:bg-red-700">
          Confirm
        </button>
      </div>
    </div>
  </div>
</div>
