@import org.springframework.security.web.csrf.CsrfToken
@param CsrfToken csrf = null
@if(csrf != null)
<input type="hidden" name="${csrf.getParameterName()}" value="${csrf.getToken()}">
@endif
