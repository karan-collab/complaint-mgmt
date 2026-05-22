(function () {
  const ui = window.CM.ui;

  function renderShell(session, contentHtml) {
    const homeRoute = session && session.role === 'admin' ? '#/admin/dashboard' : '#/dashboard';
    const subtitle = session && session.role === 'admin'
      ? `Signed in as @${ui.escapeHtml(session.username || session.displayName || 'admin')}`
      : `Signed in as ${ui.escapeHtml(session.name || session.displayName || '')} \u00b7 Flat ${ui.escapeHtml(session.flat || session.flatNo || '')}`;

    return `
      <section class="page page-narrow">
        <button type="button" class="back-link" data-route="${homeRoute}">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="m15 18-6-6 6-6"/>
          </svg>
          <span>Back to dashboard</span>
        </button>

        <div class="page-title">
          <h1>Change Password</h1>
          <p class="muted">${subtitle}</p>
        </div>

        ${contentHtml}
      </section>
    `;
  }

  function renderForm() {
    return `
      <form class="card form-card" id="changePwForm" novalidate>
        <div class="form-section-head">
          <h2>Update your password</h2>
          <p class="muted">Pick a new password that is at least 6 characters long.</p>
        </div>

        <label class="field">
          <span>Current Password</span>
          <input type="password" name="currentPassword" id="currentPassword"
                 autocomplete="current-password" placeholder="Your current password" required />
        </label>

        <label class="field">
          <span>New Password</span>
          <input type="password" name="newPassword" id="newPassword"
                 autocomplete="new-password" placeholder="At least 6 characters"
                 minlength="6" maxlength="100" required />
        </label>

        <label class="field">
          <span>Confirm New Password</span>
          <input type="password" name="confirmPassword" id="confirmPassword"
                 autocomplete="new-password" placeholder="Re-enter the new password"
                 minlength="6" maxlength="100" required />
        </label>

        <p class="login-error" id="changePwError" hidden></p>

        <div class="form-actions">
          <button type="button" class="btn btn-ghost" id="changePwCancel">Cancel</button>
          <button type="submit" class="btn btn-primary" id="changePwSubmit">
            <span class="btn-label">Update Password</span>
          </button>
        </div>
      </form>
    `;
  }

  function renderChangePassword(root, ctx) {
    const { session, navigate } = ctx;
    const storage = window.CM.storage;
    const showToast = window.CM.showToast;

    if (!session) {
      navigate('#/role');
      return;
    }

    const homeRoute = session.role === 'admin' ? '#/admin/dashboard' : '#/dashboard';

    root.innerHTML = renderShell(session, renderForm());

    const form = root.querySelector('#changePwForm');
    const submit = root.querySelector('#changePwSubmit');
    const cancel = root.querySelector('#changePwCancel');
    const errorEl = root.querySelector('#changePwError');
    const back = root.querySelector('.back-link');

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
      submit.querySelector('.btn-label').textContent = busy ? 'Updating\u2026' : 'Update Password';
    }

    back.addEventListener('click', () => navigate(homeRoute));
    cancel.addEventListener('click', () => navigate(homeRoute));

    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      setError('');
      const currentPassword = form.elements.currentPassword.value;
      const newPassword = form.elements.newPassword.value;
      const confirmPassword = form.elements.confirmPassword.value;

      if (!currentPassword) {
        setError('Please enter your current password.');
        form.elements.currentPassword.focus();
        return;
      }
      if (!newPassword || newPassword.length < 6) {
        setError('New password must be at least 6 characters.');
        form.elements.newPassword.focus();
        return;
      }
      if (newPassword !== confirmPassword) {
        setError('The two new passwords do not match.');
        form.elements.confirmPassword.focus();
        return;
      }
      if (newPassword === currentPassword) {
        setError('New password must be different from your current password.');
        form.elements.newPassword.focus();
        return;
      }

      setBusy(true);
      try {
        await storage.changeMyPassword({ currentPassword, newPassword });
        showToast('Password updated. Use your new password next time you sign in.');
        navigate(homeRoute);
      } catch (err) {
        // Backend returns 400 with detail "Current password is incorrect" for a
        // wrong current password (the JWT is still valid, only the form is bad).
        // We pattern-match the detail so the error message stays user-friendly.
        let msg;
        if (err && err.status === 400 && /current password is incorrect/i.test(err.detail || '')) {
          msg = 'Your current password is incorrect.';
        } else {
          msg = ui.messageFromError(err, 'Could not update password.');
        }
        setError(msg);
        form.elements.currentPassword.value = '';
        form.elements.currentPassword.focus();
        setBusy(false);
      }
    });
  }

  window.CM = window.CM || {};
  window.CM.views = window.CM.views || {};
  window.CM.views.changePassword = renderChangePassword;
})();
