@import java.util.List
@import java.util.Map
@import org.springframework.security.web.csrf.CsrfToken
@import ru.iopump.qa.allure.entity.UserRole
@import ru.iopump.qa.allure.web.dto.CreateTokenForm
@import ru.iopump.qa.allure.web.dto.CurrentUserView
@import ru.iopump.qa.allure.web.dto.TokenRow
@param List<TokenRow> tokens
@param CreateTokenForm.TokenExpiration[] expirations
@param CurrentUserView currentUser = null
@param boolean authEnabled = false
@param boolean isAdmin = false
@param boolean signInRequired = false
@param CsrfToken csrf = null
@param Map<String, String> flash = null
@param Map<String, String> justCreatedToken = null
@param long activeTokenCount = 0
@param int maxActiveTokens = 0
!{
  var displayName = currentUser == null ? "Guest" : currentUser.displayName();
  var username = currentUser == null ? "guest" : currentUser.username();
  var role = currentUser == null ? UserRole.GUEST : currentUser.role();
  var hasPersistedUser = currentUser != null && currentUser.persisted();
  var isAnonymousFallback = !hasPersistedUser;
  var canManageTokens = currentUser != null && currentUser.authenticated();
  var initial = displayName == null || displayName.isBlank() ? "?" : displayName.substring(0, 1).toUpperCase();
}
@template.layout.main(
    title = "Profile",
    activeNav = "profile",
    currentUser = currentUser,
    authEnabled = authEnabled,
    isAdmin = isAdmin,
    signInRequired = signInRequired,
    csrf = csrf,
    content = @`
        <section class="space-y-6" x-data="{ createOpen: false, form: { name: '', expiration: 'DAYS_30' } }"
                 @keydown.escape.window="createOpen = false">
            @template.partials.breadcrumb(items = new String[]{"Account", "Profile"})

            <div class="flex items-baseline justify-between gap-4 flex-wrap">
                <h1 class="text-2xl font-bold text-text">Profile</h1>
            </div>

            @template.partials.flash(flash = flash)

            @if(justCreatedToken != null && justCreatedToken.get("plain") != null)
                !{var plain = justCreatedToken.get("plain"); var createdName = justCreatedToken.getOrDefault("name", "");}
                <div role="alert" x-data="{ open: true, copied: false }" x-show="open" x-cloak
                     class="fixed bottom-4 right-4 z-[100] w-[min(32rem,calc(100vw-2rem))] rounded-md border border-success/60 bg-card p-4 shadow-xl">
                    <div class="flex items-start justify-between gap-3">
                        <h2 class="text-sm font-semibold text-success">
                            Token "${createdName}" created — copy it now
                        </h2>
                        <button type="button" @click="open = false" aria-label="Dismiss"
                                class="text-text-muted opacity-70 hover:opacity-100 text-lg leading-none">×</button>
                    </div>
                    <p class="mt-1 text-xs text-text-muted">
                        This is the only time the plain value is shown. If you lose it, revoke it and create a new one.
                    </p>
                    <div class="mt-3 flex items-center gap-2">
                        <input id="just-created-token" type="text" readonly value="${plain}" class="flex-1 form-field font-mono text-xs">
                        <button type="button"
                                @click="navigator.clipboard.writeText('${plain}'); copied = true; setTimeout(() => copied = false, 2000)"
                                class="inline-flex items-center justify-center gap-1 rounded-md bg-success px-3 py-1 text-xs font-semibold text-white hover:bg-success/80">
                            <span x-show="!copied">Copy</span>
                            <span x-show="copied" x-cloak>Copied!</span>
                        </button>
                    </div>
                    <p class="mt-3 text-xs font-medium text-text">Example usage:</p>
                    <pre class="mt-1 overflow-x-auto rounded-md bg-bg-elevated px-3 py-2 font-mono text-xs text-success"
                         x-text="'curl -H &quot;X-API-Token: ${plain}&quot; ' + ((window.location && window.location.origin) || 'https://&lt;your-server&gt;') + '/api/report'">curl -H "X-API-Token: ${plain}" https://&lt;your-server&gt;/api/report</pre>
                </div>
            @endif

            <!-- User block -->
            <section class="card p-5">
                <div class="flex items-center gap-4">
                    <div class="w-12 h-12 rounded-full ${isAnonymousFallback ? "bg-surface text-text-muted" : "bg-primary text-bg"} flex items-center justify-center text-xl font-semibold">
                        ${initial}
                    </div>
                    <div>
                        <p class="text-lg font-semibold text-text">${displayName}</p>
                        <p class="text-sm text-text-muted">
                            <span class="font-mono">${username}</span>
                            <span class="ml-2 inline-flex items-center rounded px-2 py-0.5 text-xs font-semibold ${role == UserRole.ADMIN ? "bg-primary/20 text-primary" : role == UserRole.USER ? "bg-success/20 text-success" : "bg-surface text-text-muted"}">
                                ${role.name()}
                            </span>
                        </p>
                    </div>
                </div>
                @if(isAnonymousFallback)
                    <p class="mt-3 text-sm text-text-muted">
                        You are browsing as a guest. API tokens are not available for anonymous users.
                    </p>
                @endif
            </section>

            <!-- Tokens block -->
            <section class="card">
                <header class="flex items-center justify-between gap-4 border-b border-border-subtle px-5 py-3">
                    <h2 class="text-lg font-semibold text-text">API tokens</h2>
                    @if(canManageTokens)
                        <button type="button" @click="createOpen = true" class="btn-primary">+ New token</button>
                    @endif
                </header>
                @if(!canManageTokens)
                    <p class="px-5 py-6 text-sm text-text-muted">Sign in to manage API tokens.</p>
                @elseif(tokens == null || tokens.isEmpty())
                    <p class="px-5 py-6 text-sm text-text-muted">You have no API tokens yet. Create one to authenticate CI pipelines.</p>
                @else
                    <div class="overflow-x-auto">
                        <table class="min-w-full divide-y divide-border-subtle text-sm">
                            <thead class="bg-surface">
                                <tr>
                                    <th scope="col" class="px-3 py-2 text-left font-semibold text-text-muted">Name</th>
                                    <th scope="col" class="px-3 py-2 text-left font-semibold text-text-muted">Status</th>
                                    <th scope="col" class="px-3 py-2 text-left font-semibold text-text-muted">Created</th>
                                    <th scope="col" class="px-3 py-2 text-left font-semibold text-text-muted">Expires</th>
                                    <th scope="col" class="px-3 py-2 text-left font-semibold text-text-muted">Last used</th>
                                    <th scope="col" class="px-3 py-2 text-right font-semibold text-text-muted">Actions</th>
                                </tr>
                            </thead>
                            <tbody class="divide-y divide-border-subtle">
                                @for(var row : tokens)
                                    !{var status = row.status();}
                                    <tr class="hover:bg-surface/60 transition-colors">
                                        <td class="px-3 py-2 font-medium text-text">${row.name()}</td>
                                        <td class="px-3 py-2">
                                            <span class="inline-flex items-center rounded px-2 py-0.5 text-xs font-semibold ${"active".equals(status) ? "bg-success/20 text-success" : "revoked".equals(status) ? "bg-error/20 text-error" : "bg-surface text-text-muted"}">${status}</span>
                                        </td>
                                        <td class="px-3 py-2 font-mono text-xs text-text-muted">${row.createdAt()}</td>
                                        <td class="px-3 py-2 font-mono text-xs text-text-muted">
                                            ${row.expiresAt().isBlank() ? "never" : row.expiresAt()}
                                        </td>
                                        <td class="px-3 py-2 font-mono text-xs text-text-muted">
                                            ${row.lastUsedAt().isBlank() ? "—" : row.lastUsedAt()}
                                        </td>
                                        <td class="px-3 py-2 text-right">
                                            @if(!row.revoked())
                                                <form method="post" action="/app/profile/tokens/${row.id().toString()}"
                                                      onsubmit="return confirm('Revoke token ${row.name()}? This cannot be undone.');"
                                                      class="inline">
                                                    @template.partials.csrf(csrf = csrf)
                                                    <input type="hidden" name="_method" value="delete">
                                                    <button type="submit"
                                                            class="rounded-md border border-error/60 bg-transparent px-2 py-1 text-xs font-semibold text-error hover:bg-error/10">
                                                        Revoke
                                                    </button>
                                                </form>
                                            @else
                                                <span class="text-xs text-text-muted">—</span>
                                            @endif
                                        </td>
                                    </tr>
                                @endfor
                            </tbody>
                        </table>
                    </div>
                @endif
                @if(canManageTokens)
                    <footer class="border-t border-border-subtle px-5 py-3 text-xs text-text-muted">
                        @if(role == UserRole.ADMIN)
                            Active tokens: ${activeTokenCount}
                        @else
                            Active tokens: ${activeTokenCount} / ${maxActiveTokens}
                        @endif
                    </footer>
                @endif
            </section>

            <!-- New token dialog -->
            @if(canManageTokens)
                <template x-if="createOpen">
                    <div class="fixed inset-0 z-40 flex items-center justify-center bg-black/60 backdrop-blur-sm"
                         @click.self="createOpen = false" role="presentation">
                        <div role="dialog" aria-modal="true" aria-labelledby="create-token-title"
                             class="w-full max-w-md card p-6">
                            <h2 id="create-token-title" class="text-lg font-semibold text-text">New API token</h2>
                            <p class="mt-1 text-sm text-text-muted">
                                The plain token value will be shown exactly once after creation. Store it safely.
                            </p>
                            <form method="post" action="/app/profile/tokens" class="mt-4 space-y-4">
                                @template.partials.csrf(csrf = csrf)
                                <label class="flex flex-col text-sm text-text">
                                    <span class="mb-1 font-medium">Name</span>
                                    <input type="text" name="name" x-model="form.name"
                                           required minlength="1" maxlength="128"
                                           placeholder="ci-pipeline"
                                           class="form-field">
                                </label>
                                <label class="flex flex-col text-sm text-text">
                                    <span class="mb-1 font-medium">Expiration</span>
                                    <select name="expiration" x-model="form.expiration" class="form-field">
                                        @for(var exp : expirations)
                                            <option value="${exp.name()}" selected="${"DAYS_30".equals(exp.name())}">${exp.label()}</option>
                                        @endfor
                                    </select>
                                </label>
                                <div class="flex justify-end gap-2 pt-2">
                                    <button type="button" @click="createOpen = false" class="btn-bordered">Cancel</button>
                                    <button type="submit" :disabled="!form.name" class="btn-primary">Create</button>
                                </div>
                            </form>
                        </div>
                    </div>
                </template>
            @endif
        </section>
    `
)
