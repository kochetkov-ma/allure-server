@param String level
@param String message
!{
  var palette = switch (level == null ? "info" : level) {
    case "error" -> "bg-red-50 border-red-300 text-red-900";
    case "success" -> "bg-emerald-50 border-emerald-300 text-emerald-900";
    default -> "bg-slate-50 border-slate-300 text-slate-900";
  };
  var role = "error".equals(level) ? "alert" : "status";
}
<div role="${role}"
     x-data="{ shown: true }"
     x-show="shown"
     x-init="setTimeout(() => $el.remove(), 5000)"
     class="pointer-events-auto flex items-start gap-3 rounded border px-4 py-2 shadow-md ${palette}">
  <span class="text-sm leading-5">${message}</span>
  <button type="button"
          @click="$el.parentElement.remove()"
          aria-label="Dismiss notification"
          class="ml-auto text-sm font-bold opacity-70 hover:opacity-100">×</button>
</div>
