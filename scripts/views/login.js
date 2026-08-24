(function () {
  const ui = window.CM.ui;

  function renderLogin(root, ctx) {
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
          <div class="login-logo" aria-hidden="true">
            <svg viewBox="0 0 24 24" width="40" height="40" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M3 11 12 4l9 7" />
              <path d="M5 10v10h14V10" />
              <path d="M10 20v-6h4v6" />
            </svg>
          </div>
          <h1>Resident Login</h1>
          <p class="muted">Sign in with your flat number and password.</p>
        </div>

        <form class="card login-card" id="loginForm" novalidate>
          <h2>Sign in</h2>
          <label class="field">
            <span>Flat Number</span>
            <input type="text" name="flat" id="loginFlat" autocomplete="off" placeholder="e.g. A-101" required />
          </label>
          <label class="field">
            <span>Password</span>
            <div class="password-field">
              <input type="password" name="password" id="loginPassword" autocomplete="current-password" placeholder="\u2022\u2022\u2022\u2022\u2022\u2022" required />
              ${ui.passwordToggle()}
            </div>
          </label>
          <p class="login-error" id="loginError" hidden></p>
          <button type="submit" class="btn btn-primary btn-block" id="loginSubmit">
            <span class="btn-label">Continue</span>
          </button>
        </form>
      </section>
    `;

    root.querySelector('.back-link').addEventListener('click', () => navigate('#/role'));
    ui.wirePasswordToggles(root);

    const form = root.querySelector('#loginForm');
    const submit = root.querySelector('#loginSubmit');
    const errorEl = root.querySelector('#loginError');

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
      submit.querySelector('.btn-label').textContent = busy ? 'Signing in\u2026' : 'Continue';
    }

    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      setError('');
      const flat = form.elements.flat.value.trim();
      const password = form.elements.password.value;

      if (!flat) {
        setError('Please enter your flat number');
        form.elements.flat.focus();
        return;
      }
      if (!password) {
        setError('Please enter your password');
        form.elements.password.focus();
        return;
      }

      setBusy(true);
      try {
        const resp = await storage.loginResident({ flatNo: flat, password });
        const s = session.set(resp);
        showToast(`Welcome, ${s.displayName || s.flatNo}`);
        navigate('#/dashboard');
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
  window.CM.views.login = renderLogin;
})();
