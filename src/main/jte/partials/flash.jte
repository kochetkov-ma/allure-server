@import java.util.Map
@param Map<String, String> flash = null
@if(flash != null && !flash.isEmpty())
  !{ var level = flash.getOrDefault("level", "info"); var message = flash.getOrDefault("message", ""); }
  @if(!message.isBlank())
    @template.partials.toast(level = level, message = message)
  @endif
@endif
