@import java.util.List
@import java.util.Map
@import ru.iopump.qa.allure.entity.UserRole
@import ru.iopump.qa.allure.web.dto.CurrentUserView
@import ru.iopump.qa.allure.web.dto.UserRow
@param List<UserRow> users
@param CurrentUserView currentUser = null
@param boolean authEnabled = true
@param boolean isAdmin = false
@param boolean signInRequired = false
@param Map<String, String> flash = null
@param Map<String, String> justCreatedUser = null
@param Map<String, String> justResetUser = null
@template.layout.main(
    title = "Users",
    activeNav = "admin-users",
    currentUser = currentUser,
    authEnabled = authEnabled,
    isAdmin = isAdmin,
    signInRequired = signInRequired,
    content = @`
        <section class="space-y-6" x-data="{ createOpen: false, confirmDelete: { open: false, id: '', username: '', typed: '' } }"
                 @keydown.escape.window="createOpen = false; confirmDelete.open = false">
            @template.partials.breadcrumb(items = new String[]{"Admin", "Users"})

            <div class="flex items-baseline justify-between gap-4 flex-wrap">
                <h1 class="text-2xl font-bold text-text">Users</h1>
                <button type="button" @click="createOpen = true" class="btn-primary">+ New user</button>
            </div>

            @template.partials.flash(flash = flash)

            @if(justCreatedUser != null && justCreatedUser.get("temporaryPassword") != null)
                !{var tempPassword = justCreatedUser.get("temporaryPassword"); var newUsername = justCreatedUser.getOrDefault("username", "");}
                <div role="alert" x-data="{ copied: false }"
                     class="rounded-md border border-success/60 bg-success/10 p-4">
                    <h2 class="text-sm font-semibold text-success">
                        User "${newUsername}" created — copy the temporary password now
                    </h2>
                    <p class="mt-1 text-xs text-text-muted">
                        This password is shown exactly once. The user must change it on first sign-in.
                    </p>
                    <div class="mt-3 flex items-center gap-2">
                        <input id="temp-password-created" type="text" readonly value="${tempPassword}" class="flex-1 form-field font-mono text-xs">
                        <button type="button"
                                @click="navigator.clipboard.writeText('${tempPassword}'); copied = true; setTimeout(() => copied = false, 2000)"
                                class="inline-flex items-center justify-center gap-1 rounded-md bg-success px-3 py-1 text-xs font-semibold text-white hover:bg-success/80">
                            <span x-show="!copied">Copy</span>
                            <span x-show="copied" x-cloak>Copied!</span>
                        </button>
                    </div>
                </div>
            @endif

            @if(justResetUser != null && justResetUser.get("temporaryPassword") != null)
                !{var rp = justResetUser.get("temporaryPassword"); var rn = justResetUser.getOrDefault("username", "");}
                <div role="alert" x-data="{ copied: false }"
                     class="rounded-md border border-warning/60 bg-warning/10 p-4">
                    <h2 class="text-sm font-semibold text-warning">
                        Password reset for "${rn}" — copy the temporary password now
                    </h2>
                    <p class="mt-1 text-xs text-text-muted">
                        The user must change it on next sign-in.
                    </p>
                    <div class="mt-3 flex items-center gap-2">
                        <input id="temp-password-reset" type="text" readonly value="${rp}" class="flex-1 form-field font-mono text-xs">
                        <button type="button"
                                @click="navigator.clipboard.writeText('${rp}'); copied = true; setTimeout(() => copied = false, 2000)"
                                class="inline-flex items-center justify-center gap-1 rounded-md bg-warning px-3 py-1 text-xs font-semibold text-white hover:bg-warning/80">
                            <span x-show="!copied">Copy</span>
                            <span x-show="copied" x-cloak>Copied!</span>
                        </button>
                    </div>
                </div>
            @endif

            <section class="card">
                @if(users == null || users.isEmpty())
                    <p class="px-5 py-6 text-sm text-text-muted">No users yet.</p>
                @else
                    <div class="overflow-x-auto">
                        <table class="min-w-full divide-y divide-border-subtle text-sm">
                            <thead class="bg-surface">
                                <tr>
                                    <th scope="col" class="px-3 py-2 text-left font-semibold text-text-muted">Username</th>
                                    <th scope="col" class="px-3 py-2 text-left font-semibold text-text-muted">Role</th>
                                    <th scope="col" class="px-3 py-2 text-left font-semibold text-text-muted">Status</th>
                                    <th scope="col" class="px-3 py-2 text-left font-semibold text-text-muted">Created</th>
                                    <th scope="col" class="px-3 py-2 text-left font-semibold text-text-muted">Last login</th>
                                    <th scope="col" class="px-3 py-2 text-right font-semibold text-text-muted">Actions</th>
                                </tr>
                            </thead>
                            <tbody class="divide-y divide-border-subtle">
                                @for(var row : users)
                                    <tr class="hover:bg-surface/60 transition-colors">
                                        <td class="px-3 py-2 font-medium text-text">
                                            <div class="flex items-center gap-2">
                                                <span>${row.username()}</span>
                                                @if(row.mainAdmin())
                                                    <span class="inline-flex items-center rounded px-1.5 py-0.5 text-[10px] font-bold uppercase bg-primary text-bg">main</span>
                                                @endif
                                                @if(row.isSelf())
                                                    <span class="inline-flex items-center rounded px-1.5 py-0.5 text-[10px] font-bold uppercase bg-surface text-text-muted">you</span>
                                                @endif
                                            </div>
                                            <div class="text-xs text-text-muted">${row.displayName()}</div>
                                        </td>
                                        <td class="px-3 py-2">
                                            <span class="inline-flex items-center rounded px-2 py-0.5 text-xs font-semibold ${row.role() == UserRole.ADMIN ? "bg-primary/20 text-primary" : row.role() == UserRole.USER ? "bg-success/20 text-success" : "bg-surface text-text-muted"}">
                                                ${row.role().name()}
                                            </span>
                                            @if(row.passwordTemporary())
                                                <span class="ml-1 inline-flex items-center rounded px-2 py-0.5 text-xs font-semibold bg-warning/20 text-warning">temp pwd</span>
                                            @endif
                                        </td>
                                        <td class="px-3 py-2">
                                            @if(row.blocked())
                                                <span class="inline-flex items-center rounded px-2 py-0.5 text-xs font-semibold bg-error/20 text-error">blocked</span>
                                            @else
                                                <span class="inline-flex items-center rounded px-2 py-0.5 text-xs font-semibold bg-success/20 text-success">active</span>
                                            @endif
                                        </td>
                                        <td class="px-3 py-2 font-mono text-xs text-text-muted">${row.createdAt()}</td>
                                        <td class="px-3 py-2 font-mono text-xs text-text-muted">
                                            ${row.lastLoginAt().isBlank() ? "—" : row.lastLoginAt()}
                                        </td>
                                        <td class="px-3 py-2 text-right space-x-1 whitespace-nowrap">
                                            @if(row.canBlock())
                                                @if(row.blocked())
                                                    <form method="post" action="/app/admin/users/${row.id().toString()}/unblock" class="inline">
                                                        <button type="submit"
                                                                class="rounded-md border border-success/60 bg-transparent px-2 py-1 text-xs font-semibold text-success hover:bg-success/10">
                                                            Unblock
                                                        </button>
                                                    </form>
                                                @else
                                                    <form method="post" action="/app/admin/users/${row.id().toString()}/block" class="inline">
                                                        <button type="submit"
                                                                class="rounded-md border border-warning/60 bg-transparent px-2 py-1 text-xs font-semibold text-warning hover:bg-warning/10">
                                                            Block
                                                        </button>
                                                    </form>
                                                @endif
                                            @else
                                                <button type="button" disabled title="You cannot block yourself"
                                                        class="rounded-md border border-border-subtle bg-transparent px-2 py-1 text-xs font-semibold text-text-muted cursor-not-allowed opacity-50">
                                                    Block
                                                </button>
                                            @endif
                                            @if(row.canToggleAdmin())
                                                @if(row.role() == UserRole.ADMIN)
                                                    <form method="post" action="/app/admin/users/${row.id().toString()}/revoke-admin" class="inline">
                                                        <button type="submit"
                                                                class="rounded-md border border-border bg-transparent px-2 py-1 text-xs font-semibold text-text hover:text-primary hover:border-primary">
                                                            Revoke admin
                                                        </button>
                                                    </form>
                                                @else
                                                    <form method="post" action="/app/admin/users/${row.id().toString()}/grant-admin" class="inline">
                                                        <button type="submit"
                                                                class="rounded-md border border-primary/60 bg-transparent px-2 py-1 text-xs font-semibold text-primary hover:bg-primary/10">
                                                            Grant admin
                                                        </button>
                                                    </form>
                                                @endif
                                            @else
                                                <button type="button" disabled
                                                        title="${row.mainAdmin() ? "The main administrator cannot be demoted" : "You cannot change your own role"}"
                                                        class="rounded-md border border-border-subtle bg-transparent px-2 py-1 text-xs font-semibold text-text-muted cursor-not-allowed opacity-50">
                                                    ${row.role() == UserRole.ADMIN ? "Revoke admin" : "Grant admin"}
                                                </button>
                                            @endif
                                            @if(row.canResetPassword())
                                                <form method="post" action="/app/admin/users/${row.id().toString()}/reset-password"
                                                      onsubmit="return confirm('Generate a new temporary password for ${row.username()}?');"
                                                      class="inline">
                                                    <button type="submit"
                                                            class="rounded-md border border-border bg-transparent px-2 py-1 text-xs font-semibold text-text hover:text-primary hover:border-primary">
                                                        Reset password
                                                    </button>
                                                </form>
                                            @else
                                                <button type="button" disabled
                                                        title="Only the main administrator can reset the main administrator's password"
                                                        class="rounded-md border border-border-subtle bg-transparent px-2 py-1 text-xs font-semibold text-text-muted cursor-not-allowed opacity-50">
                                                    Reset password
                                                </button>
                                            @endif
                                            @if(row.canDelete())
                                                <button type="button"
                                                        @click="confirmDelete.open = true; confirmDelete.id = '${row.id().toString()}'; confirmDelete.username = '${row.username()}'; confirmDelete.typed = ''"
                                                        class="rounded-md border border-error/60 bg-transparent px-2 py-1 text-xs font-semibold text-error hover:bg-error/10">
                                                    Delete
                                                </button>
                                            @else
                                                <button type="button" disabled
                                                        title="${row.mainAdmin() ? "The main administrator cannot be deleted" : "You cannot delete yourself"}"
                                                        class="rounded-md border border-border-subtle bg-transparent px-2 py-1 text-xs font-semibold text-text-muted cursor-not-allowed opacity-50">
                                                    Delete
                                                </button>
                                            @endif
                                        </td>
                                    </tr>
                                @endfor
                            </tbody>
                        </table>
                    </div>
                @endif
            </section>

            <!-- New user dialog -->
            <template x-if="createOpen">
                <div class="fixed inset-0 z-40 flex items-center justify-center bg-black/60 backdrop-blur-sm"
                     @click.self="createOpen = false" role="presentation">
                    <div role="dialog" aria-modal="true" aria-labelledby="create-user-title"
                         class="w-full max-w-md card p-6">
                        <h2 id="create-user-title" class="text-lg font-semibold text-text">New user</h2>
                        <p class="mt-1 text-sm text-text-muted">
                            A temporary password will be generated and shown once. The user must change it on first sign-in.
                        </p>
                        <form method="post" action="/app/admin/users" class="mt-4 space-y-4">
                            <label class="flex flex-col text-sm text-text">
                                <span class="mb-1 font-medium">Username</span>
                                <input type="text" name="username" required minlength="1" maxlength="128"
                                       pattern="[A-Za-z0-9._-]+"
                                       placeholder="alice"
                                       class="form-field font-mono">
                            </label>
                            <label class="flex flex-col text-sm text-text">
                                <span class="mb-1 font-medium">Display name <span class="text-text-muted font-normal">(optional)</span></span>
                                <input type="text" name="displayName" maxlength="128"
                                       placeholder="Alice Cooper"
                                       class="form-field">
                            </label>
                            <div class="flex justify-end gap-2 pt-2">
                                <button type="button" @click="createOpen = false" class="btn-bordered">Cancel</button>
                                <button type="submit" class="btn-primary">Create</button>
                            </div>
                        </form>
                    </div>
                </div>
            </template>

            <!-- Delete confirmation dialog (typing-confirmation) -->
            <template x-if="confirmDelete.open">
                <div class="fixed inset-0 z-40 flex items-center justify-center bg-black/60 backdrop-blur-sm"
                     @click.self="confirmDelete.open = false" role="presentation">
                    <div role="dialog" aria-modal="true" aria-labelledby="delete-user-title"
                         class="w-full max-w-md card p-6">
                        <h2 id="delete-user-title" class="text-lg font-semibold text-error">Delete user</h2>
                        <p class="mt-1 text-sm text-text">
                            This will permanently delete user <strong class="font-mono" x-text="confirmDelete.username"></strong>
                            and all their API tokens. Type the username to confirm.
                        </p>
                        <form method="post" :action="'/app/admin/users/' + confirmDelete.id" class="mt-4 space-y-4">
                            <input type="hidden" name="_method" value="delete">
                            <input type="text" x-model="confirmDelete.typed"
                                   :placeholder="confirmDelete.username"
                                   class="form-field font-mono border-error/60 focus:border-error focus:ring-error">
                            <div class="flex justify-end gap-2 pt-2">
                                <button type="button" @click="confirmDelete.open = false" class="btn-bordered">Cancel</button>
                                <button type="submit"
                                        :disabled="confirmDelete.typed !== confirmDelete.username"
                                        class="inline-flex items-center justify-center gap-1 rounded-md bg-error px-4 py-1.5 text-sm font-semibold text-white hover:bg-error/80 focus:outline-none focus:ring-2 focus:ring-error disabled:cursor-not-allowed disabled:opacity-50">
                                    Delete permanently
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            </template>
        </section>
    `
)
