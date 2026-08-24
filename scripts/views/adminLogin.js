(function () {
  const ui = window.CM.ui;

  function renderAdminLogin(root, ctx) {
    const navigate = ctx.navigate;
    const storage = window.CM.storage;
    const session = window.CM.session;
    const showToast = window.CM.showToast;

    root.classList.add('view-login');
    root.innerHTML = `
      <section class="login-shell">
        <button type="button" class="back-link back-link-center" data-route="#/role">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="m15 18-6-6 6-6"/>
          </svg>
          <span>Choose a different role</span>
        </button>

        <div class="login-hero">
          <div class="login-logo login-logo-admin" aria-hidden="true">
            <svg viewBox="0 0 24 24" width="36" height="36" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <rect x="3" y="7" width="18" height="13" rx="2"/>
              <path d="M8 7V5a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
              <path d="M3 12h18"/>
            </svg>
          </div>
          <h1>Management Login</h1>
          <p class="muted">Sign in to manage complaint tickets across all flats.</p>
        </div>

        <form class="card login-card" id="adminLoginForm" novalidate>
          <h2>Sign in</h2>
          <label class="field">
            <span>Username</span>
            <input type="text" name="username" id="adminUsername" autocomplete="username" placeholder="Enter your username" required />
          </label>
          <label class="field">
            <span>Password</span>
            <div class="password-field">
              <input type="password" name="password" id="adminPassword" autocomplete="current-password" placeholder="\u2022\u2022\u2022\u2022\u2022" required />
              ${ui.passwordToggle()}
            </div>
          </label>
          <p class="login-error" id="adminLoginError" hidden></p>
          <button type="submit" class="btn btn-primary btn-block" id="adminLoginSubmit">
            <span class="btn-label">Sign in</span>
          </button>
        </form>
      </section>
    `;

    const form = root.querySelector('#adminLoginForm');
    const submit = root.querySelector('#adminLoginSubmit');
    const errorEl = root.querySelector('#adminLoginError');

    function setError(msg) {
      if (msg) {
        errorEl.textContent = msg;
        errorEl.hidden = false;
      } else {
        errorEl.textContent = '';
        errorEl.hidden = true;
      }
    }
    function setBusy(busy) {
      submit.disabled = busy;
      submit.querySelector('.btn-label').textContent = busy ? 'Signing in\u2026' : 'Sign in';
    }

    root.querySelector('.back-link').addEventListener('click', () => navigate('#/role'));
    ui.wirePasswordToggles(root);

    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      setError('');
      const username = form.elements.username.value.trim();
      const password = form.elements.password.value;

      if (!username || !password) {
        setError('Please enter both username and password');
        (username ? form.elements.password : form.elements.username).focus();
        return;
      }

      setBusy(true);
      try {
        const resp = await storage.loginAdmin({ username, password });
        session.set(resp);
        showToast('Welcome, Management');
        navigate('#/admin/dashboard');
      } catch (err) {
        const msg = err && err.status === 401
          ? 'Invalid credentials. Please try again.'
          : (err && (err.detail || err.message)) || 'Could not sign in';
        setError(msg);
        form.elements.password.value = '';
        form.elements.password.focus();
      } finally {
        setBusy(false);
      }
    });
  }

  window.CM = window.CM || {};
  window.CM.views = window.CM.views || {};
  window.CM.views.adminLogin = renderAdminLogin;
})();
