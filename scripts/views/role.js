(function () {
  function renderRole(root, ctx) {
    const navigate = ctx.navigate;

    root.classList.add('view-role');
    root.innerHTML = `
      <section class="role-shell">
        <div class="role-hero">
          <div class="role-logo" aria-hidden="true">
            <svg viewBox="0 0 24 24" width="44" height="44" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M3 11 12 4l9 7" />
              <path d="M5 10v10h14V10" />
              <path d="M10 20v-6h4v6" />
            </svg>
          </div>
          <h1>Welcome to SocietyCare</h1>
          <p class="muted">How are you signing in today?</p>
        </div>

        <div class="role-grid">
          <button type="button" class="role-card role-resident" data-route="#/login">
            <span class="role-icon" aria-hidden="true">
              <svg viewBox="0 0 24 24" width="34" height="34" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M3 11 12 4l9 7" />
                <path d="M5 10v10h14V10" />
                <path d="M10 20v-6h4v6" />
              </svg>
            </span>
            <span class="role-title">I'm a Resident</span>
            <span class="role-sub">Raise and track complaints for your flat.</span>
            <span class="role-arrow" aria-hidden="true">
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M5 12h14"/>
                <path d="m13 5 7 7-7 7"/>
              </svg>
            </span>
          </button>

          <button type="button" class="role-card role-admin" data-route="#/admin/login">
            <span class="role-icon" aria-hidden="true">
              <svg viewBox="0 0 24 24" width="34" height="34" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <rect x="3" y="7" width="18" height="13" rx="2"/>
                <path d="M8 7V5a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
                <path d="M3 12h18"/>
              </svg>
            </span>
            <span class="role-title">I'm Management</span>
            <span class="role-sub">Assign workers and manage complaint tickets.</span>
            <span class="role-arrow" aria-hidden="true">
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M5 12h14"/>
                <path d="m13 5 7 7-7 7"/>
              </svg>
            </span>
          </button>
        </div>
      </section>
    `;

    root.querySelectorAll('[data-route]').forEach((btn) => {
      btn.addEventListener('click', () => navigate(btn.dataset.route));
    });
  }

  window.CM = window.CM || {};
  window.CM.views = window.CM.views || {};
  window.CM.views.role = renderRole;
})();
