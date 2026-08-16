/* Brew.QA Swagger UI branding.
   (a) Pre-paint theme selection — sync with main site's localStorage key.
   (b) Replace the Swagger topbar logo with the Brew.QA badge + wordmark once
       Swagger UI finishes mounting. Disconnects observer after first swap. */
(function () {
  try {
    var s = localStorage.getItem('allure-server-theme');
    var p = matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark';
    document.documentElement.dataset.theme = s || p;
  } catch (e) {
    document.documentElement.dataset.theme = 'dark';
  }
})();

(function () {
  // Badge = canonical theme-invariant mark. Reuse the context-path-aware icon
  // href injected into <head> by SwaggerBrandingFilter (Swagger's own
  // favicon-32x32.png rel="icon" links come first, hence the href filter).
  var iconLink = document.querySelector('link[rel="icon"][href$="/icon.svg"]');
  var iconHref = (iconLink && iconLink.getAttribute('href')) || '/icon.svg';

  var BRAND_HTML =
    '<a href="/" class="brew-logo" aria-label="Brew.QA home">' +
      '<img class="brew-logo__badge" src="' + iconHref + '" alt="" aria-hidden="true">' +
      '<span class="brew-logo__wordmark">Brew' +
        '<span class="brew-logo__dot">.</span>' +
        '<span class="brew-logo__accent">QA</span>' +
      '</span>' +
    '</a>';

  function rebrand(root) {
    var wrapper = root.querySelector('.topbar-wrapper');
    if (!wrapper) return false;
    var link = wrapper.querySelector('.link');
    if (link) {
      link.outerHTML = BRAND_HTML;
    } else if (!wrapper.querySelector('.brew-logo')) {
      wrapper.insertAdjacentHTML('afterbegin', BRAND_HTML);
    }
    return true;
  }

  function start() {
    document.title = 'API — Brew Reporting';
    if (rebrand(document)) return;
    var observer = new MutationObserver(function () {
      if (rebrand(document)) {
        observer.disconnect();
      }
    });
    observer.observe(document.body, { childList: true, subtree: true });
    // Safety net: stop observing after 10s to avoid a lingering observer if
    // Swagger UI never mounts (e.g. JS error).
    setTimeout(function () { observer.disconnect(); }, 10000);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', start, { once: true });
  } else {
    start();
  }
})();
