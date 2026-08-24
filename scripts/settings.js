/**
 * The Settings menu in the topbar: Change password and Logout, for residents
 * and management alike.
 *
 * It lives here rather than under views/ for the same reason the bell does - it
 * hangs off the topbar, which outlives any single route.
 *
 * The menu does not navigate or clear the session itself. It announces what the
 * user asked for and lets app.js, which owns the router and the session, decide
 * what that means - the same split the `cm:unauthorized` event already uses.
 */
(function () {
  let open = false;

  function els() {
    return {
      wrap: document.getElementById('settingsWrap'),
      btn: document.getElementById('settingsBtn'),
      panel: document.getElementById('settingsPanel'),
    };
  }

  function openMenu() {
    const { btn, panel, wrap } = els();
    if (!btn || !panel) return;
    open = true;
    panel.hidden = false;
    wrap.classList.add('is-open');
    btn.setAttribute('aria-expanded', 'true');
    const first = panel.querySelector('[data-menu-action]');
    if (first) first.focus();
  }

  function closeMenu({ returnFocus } = {}) {
    const { btn, panel, wrap } = els();
    if (!btn || !panel) return;
    open = false;
    panel.hidden = true;
    wrap.classList.remove('is-open');
    btn.setAttribute('aria-expanded', 'false');
    if (returnFocus) btn.focus();
  }

  function toggle() {
    if (open) closeMenu({ returnFocus: true });
    else openMenu();
  }

  function init() {
    const { btn, panel, wrap } = els();
    if (!btn || !panel) return;

    btn.addEventListener('click', (e) => {
      e.stopPropagation();
      toggle();
    });

    panel.querySelectorAll('[data-menu-action]').forEach((item) => {
      item.addEventListener('click', () => {
        const action = item.dataset.menuAction;
        closeMenu();
        window.dispatchEvent(new CustomEvent('cm:settings-action', { detail: { action } }));
      });
    });

    // Click-away and Escape, matching how the bell and ui.openModal behave.
    document.addEventListener('click', (e) => {
      if (open && wrap && !wrap.contains(e.target)) closeMenu();
    });
    document.addEventListener('keydown', (e) => {
      if (e.key === 'Escape' && open) closeMenu({ returnFocus: true });
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }

  window.CM = window.CM || {};
  window.CM.settingsMenu = { close: closeMenu };
})();
