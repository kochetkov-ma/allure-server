@param String level
@param String message
!{
  var variant = switch (level == null ? "info" : level) {
    case "error" -> "toast--error";
    case "success" -> "toast--success";
    case "warning" -> "toast--warning";
    default -> "";
  };
  var role = "error".equals(level) ? "alert" : "status";
}
<div role="${role}"
     x-data="{ shown: true }"
     x-show="shown"
     x-init="(document.getElementById('toast-container') || $el.parentElement).appendChild($el); setTimeout(() => $el.remove(), 3000)"
     class="toast ${variant}">
  <span class="leading-5">${message}</span>
  <button type="button"
          @click="$el.remove()"
          aria-label="Dismiss notification"
          class="ml-auto text-sm font-bold opacity-70 hover:opacity-100">×</button>
</div>
