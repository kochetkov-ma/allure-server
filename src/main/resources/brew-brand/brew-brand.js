/* Brew.QA branding — injects logo into Allure sidebar. Idempotent. */
(function () {
  var MARKER = 'brew-brand-link';
  var HOME_URL = '/';

  function buildLink() {
    var a = document.createElement('a');
    a.className = MARKER;
    a.href = HOME_URL;
    a.setAttribute('aria-label', 'Brew.QA home');

    var badge = document.createElement('span');
    badge.className = 'brew-brand-badge';
    badge.setAttribute('aria-hidden', 'true');
    badge.textContent = 'B';

    var word = document.createElement('span');
    word.className = 'brew-brand-word';
    var brew = document.createTextNode('Brew');
    var dot = document.createElement('span');
    dot.className = 'brew-dot';
    dot.textContent = '.';
    var qa = document.createElement('span');
    qa.className = 'brew-qa';
    qa.textContent = 'QA';
    word.appendChild(brew);
    word.appendChild(dot);
    word.appendChild(qa);

    a.appendChild(badge);
    a.appendChild(word);
    return a;
  }

  function apply(root) {
    var brand = root.querySelector('.side-nav__brand');
    if (!brand) return false;
    if (brand.querySelector('.' + MARKER)) return true;
    while (brand.firstChild) brand.removeChild(brand.firstChild);
    brand.appendChild(buildLink());
    return true;
  }

  function start() {
    if (apply(document)) return;
    var observer = new MutationObserver(function () {
      if (apply(document)) observer.disconnect();
    });
    observer.observe(document.body, { childList: true, subtree: true });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', start);
  } else {
    start();
  }
})();
