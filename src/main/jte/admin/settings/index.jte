@import java.util.Map
@import ru.iopump.qa.allure.web.dto.CurrentUserView
@import ru.iopump.qa.allure.web.dto.SystemSettingsView
@param SystemSettingsView settings
@param CurrentUserView currentUser = null
@param boolean authEnabled = true
@param boolean isAdmin = false
@param boolean signInRequired = false
@param Map<String, String> flash = null
@template.layout.main(
    title = "System Settings",
    activeNav = "admin-settings",
    currentUser = currentUser,
    authEnabled = authEnabled,
    isAdmin = isAdmin,
    signInRequired = signInRequired,
    content = @`
        <section class="space-y-6">
            @template.partials.breadcrumb(items = new String[]{"Admin", "Settings"})

            <h1 class="text-2xl font-bold text-text">System settings</h1>

            @template.partials.flash(flash = flash)

            <section class="card p-5 space-y-4">
                <header>
                    <h2 class="text-lg font-semibold text-text">API authentication</h2>
                    <p class="mt-1 text-sm text-text-muted">
                        When <strong>required</strong>, every <code class="font-mono text-primary">/api/**</code> request must carry either
                        HTTP Basic credentials or an <code class="font-mono text-primary">X-API-Token</code> header. Anonymous callers are
                        rejected with <code class="font-mono text-primary">401</code>.
                        When <strong>optional</strong>, anonymous requests are accepted as the shared <code class="font-mono text-primary">guest</code>
                        user (backward compatible with older Allure Maven/Gradle plugins).
                    </p>
                </header>
                <form method="post" action="/app/admin/settings/require-api-auth" class="flex items-center gap-3">
                    <input type="hidden" name="requireApiAuth" value="${!settings.requireApiAuth()}">
                    <div class="flex items-center gap-3 rounded-md border border-border-subtle bg-surface px-4 py-2">
                        <span class="text-sm font-medium text-text-muted">Current:</span>
                        @if(settings.requireApiAuth())
                            <span class="inline-flex items-center rounded px-2 py-0.5 text-xs font-semibold bg-success/20 text-success">REQUIRED</span>
                        @else
                            <span class="inline-flex items-center rounded px-2 py-0.5 text-xs font-semibold bg-warning/20 text-warning">OPTIONAL (guest fallback)</span>
                        @endif
                    </div>
                    <button type="submit" class="btn-primary">
                        ${settings.requireApiAuth() ? "Disable requirement" : "Require API authentication"}
                    </button>
                </form>
                @if(settings.updatedByUsername() != null)
                    <p class="text-xs text-text-muted">
                        Last updated by <span class="font-mono text-text">${settings.updatedByUsername()}</span> at
                        <span class="font-mono text-text">${settings.updatedAt().toString()}</span>
                    </p>
                @endif
            </section>
        </section>
    `
)
