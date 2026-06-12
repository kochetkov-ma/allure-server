@import ru.iopump.qa.allure.web.dto.CurrentUserView
@param CurrentUserView user
@param boolean authEnabled
@param boolean isAdmin = false
@param boolean signInRequired = false
!{
  var displayName = user == null ? "Guest" : user.displayName();
  var username = user == null ? "guest" : user.username();
  var isGuest = user == null || user.guest();
  var initial = displayName == null || displayName.isBlank() ? "?" : displayName.substring(0, 1).toUpperCase();
  var avatarPalette = isGuest ? "bg-surface text-text-muted" : "bg-primary text-bg";
  var avatarTitle = isGuest ? "Guest (anonymous)" : displayName + " (" + username + ")";
}
<div class="relative" x-data="{ open: false }" @keydown.escape.window="open = false" @click.outside="open = false">
  <button type="button"
          @click="open = !open"
          :aria-expanded="open"
          aria-haspopup="menu"
          aria-label="Open user menu"
          title="${avatarTitle}"
          class="w-9 h-9 rounded-full ${avatarPalette} flex items-center justify-center font-semibold focus:outline-none focus:ring-2 focus:ring-primary">
    ${initial}
  </button>
  <div x-show="open"
       x-cloak
       x-transition.opacity
       role="menu"
       aria-label="User menu"
       class="absolute right-0 mt-2 w-56 rounded-md border border-border-subtle bg-card shadow-lg z-50 text-text">
    <div class="px-3 py-2 border-b border-border-subtle">
      <p class="text-sm font-semibold text-text">${displayName}</p>
      <p class="text-xs text-text-muted">${username}</p>
    </div>
    <div class="py-1">
      <a href="/app/profile"
         class="block px-3 py-1.5 text-sm text-text hover:bg-surface hover:text-primary"
         role="menuitem">Profile</a>
      @if(!isGuest)
        <a href="/app/profile/password"
           class="block px-3 py-1.5 text-sm text-text hover:bg-surface hover:text-primary"
           role="menuitem">Change password</a>
      @endif
      @if(isAdmin)
        <div class="my-1 border-t border-border-subtle"></div>
        <a href="/app/admin/users"
           class="block px-3 py-1.5 text-sm text-text hover:bg-surface hover:text-primary"
           role="menuitem">Manage users</a>
        <a href="/app/admin/settings"
           class="block px-3 py-1.5 text-sm text-text hover:bg-surface hover:text-primary"
           role="menuitem">System settings</a>
      @endif
      <div class="my-1 border-t border-border-subtle"></div>
      @if(signInRequired)
        <a href="/app/signin"
           class="block px-3 py-1.5 text-sm font-semibold text-primary hover:bg-surface"
           role="menuitem">Sign in</a>
      @else
        <form action="/logout" method="post" class="block">
          <button type="submit"
                  class="w-full text-left px-3 py-1.5 text-sm text-text hover:bg-surface hover:text-primary"
                  role="menuitem">Logout</button>
        </form>
      @endif
    </div>
  </div>
</div>
