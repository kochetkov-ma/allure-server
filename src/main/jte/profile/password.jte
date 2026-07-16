@import java.util.Map
@import org.springframework.security.web.csrf.CsrfToken
@import ru.iopump.qa.allure.web.dto.CurrentUserView
@param boolean forced = false
@param CurrentUserView currentUser = null
@param boolean authEnabled = true
@param boolean isAdmin = false
@param boolean signInRequired = false
@param CsrfToken csrf = null
@param Map<String, String> flash = null
@template.layout.main(
    title = "Change Password",
    activeNav = "profile",
    currentUser = currentUser,
    authEnabled = authEnabled,
    isAdmin = isAdmin,
    signInRequired = signInRequired,
    csrf = csrf,
    content = @`
        <section class="space-y-6">
            @template.partials.breadcrumb(items = new String[]{"Account", "Change password"})

            <h1 class="text-2xl font-bold text-text">Change password</h1>

            @template.partials.flash(flash = flash)

            @if(forced)
                <div role="alert" class="rounded-md border border-warning/60 bg-warning/10 p-4">
                    <h2 class="text-sm font-semibold text-warning">Password rotation required</h2>
                    <p class="mt-1 text-sm text-text">
                        Your current password was issued by an administrator. Choose a new password to continue using the server.
                    </p>
                </div>
            @endif

            <section class="max-w-md card p-5">
                <form method="post" action="/app/profile/password" class="space-y-4">
                    @template.partials.csrf(csrf = csrf)
                    <label class="flex flex-col text-sm text-text">
                        <span class="mb-1 font-medium">Current password</span>
                        <input type="password" name="currentPassword" required autocomplete="current-password" class="form-field">
                    </label>
                    <label class="flex flex-col text-sm text-text">
                        <span class="mb-1 font-medium">New password</span>
                        <input type="password" name="newPassword" required minlength="8" autocomplete="new-password" class="form-field">
                        <span class="mt-1 text-xs text-text-muted">Minimum 8 characters.</span>
                    </label>
                    <label class="flex flex-col text-sm text-text">
                        <span class="mb-1 font-medium">Confirm new password</span>
                        <input type="password" name="confirmPassword" required minlength="8" autocomplete="new-password" class="form-field">
                    </label>
                    <div class="flex justify-end gap-2 pt-2">
                        <button type="submit" class="btn-primary">Change password</button>
                    </div>
                </form>
            </section>
        </section>
    `
)
