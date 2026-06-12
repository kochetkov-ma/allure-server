(function () {
    const STORAGE_KEY = "allure-server-theme";

    function currentTheme() {
        return document.documentElement.dataset.theme === "light" ? "light" : "dark";
    }

    function apply(theme) {
        document.documentElement.dataset.theme = theme;
        const toggle = document.getElementById("theme-toggle");
        if (toggle) {
            toggle.setAttribute(
                "aria-label",
                theme === "dark" ? "Switch to light theme" : "Switch to dark theme"
            );
        }
    }

    document.addEventListener("DOMContentLoaded", function () {
        apply(currentTheme());
        const toggle = document.getElementById("theme-toggle");
        if (!toggle) return;
        toggle.addEventListener("click", function () {
            const next = currentTheme() === "dark" ? "light" : "dark";
            try {
                localStorage.setItem(STORAGE_KEY, next);
            } catch (e) {
                /* ignore storage errors (private mode, etc.) */
            }
            apply(next);
        });
    });
})();
