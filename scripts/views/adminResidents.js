(function () {
  const ui = window.CM.ui;

  function renderShell(content) {
    return `
      <section class="page">
        <button type="button" class="back-link" data-back>
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="m15 18-6-6 6-6"/>
          </svg>
          <span>Back to dashboard</span>
        </button>

        <div class="page-title">
          <h1>Manage Residents</h1>
          <p class="muted">Add a new resident or reset a resident's password.</p>
        </div>

        ${content}
      </section>
    `;
  }

  function renderResidentRow(r) {
    return `
      <tr data-resident="${r.id}">
        <td><strong>${ui.escapeHtml(r.flat)}</strong></td>
        <td>${ui.escapeHtml(r.name)}</td>
        <td>${ui.escapeHtml(r.phone || '\u2014')}</td>
        <td class="row-actions">
          <button type="button" class="btn btn-ghost btn-small" data-action="reset" data-id="${r.id}" data-name="${ui.escapeHtml(r.name)}" data-flat="${ui.escapeHtml(r.flat)}">Reset password</button>
        </td>
      </tr>
    `;
  }

  function renderTable(residents) {
    if (!residents.length) {
      return `
        <div class="empty-state card">
          <h3>No residents yet</h3>
          <p class="muted">Add a resident below so they can sign in and raise complaints.</p>
        </div>
      `;
    }
    return `
      <div class="card data-card">
        <table class="data-table">
          <thead>
            <tr>
              <th>Flat</th>
              <th>Name</th>
              <th>Phone</th>
              <th class="row-actions-head">Actions</th>
            </tr>
          </thead>
          <tbody>
            ${residents.map(renderResidentRow).join('')}
          </tbody>
        </table>
      </div>
    `;
  }

  function renderCreateForm() {
    return `
      <form class="card form-card" id="createResidentForm" novalidate>
        <div class="form-section-head">
          <h2>Add a new resident</h2>
          <p class="muted">Their flat number doubles as their username.</p>
        </div>
        <div class="field-grid">
          <label class="field">
            <span>Flat Number</span>
            <input type="text" name="flatNo" placeholder="e.g. F-606" required />
          </label>
          <label class="field">
            <span>Full Name</span>
            <input type="text" name="name" placeholder="e.g. Anita Sharma" required />
          </label>
          <label class="field">
            <span>Phone (optional)</span>
            <input type="tel" name="phone" placeholder="e.g. +91 98000 12345" />
          </label>
          <label class="field">
            <span>Initial Password</span>
            <input type="text" name="password" placeholder="At least 6 characters" required minlength="6" />
          </label>
        </div>
        <p class="login-error" id="createResidentError" hidden></p>
        <button type="submit" class="btn btn-primary" id="createResidentSubmit">
          <span class="btn-label">Add Resident</span>
        </button>
      </form>
    `;
  }

  function renderResetModal(resident) {
    return `
      <div class="modal-backdrop" id="resetBackdrop" role="dialog" aria-modal="true" aria-labelledby="resetTitle">
        <div class="modal">
          <header class="modal-head">
            <div class="modal-head-text">
              <p class="modal-eyebrow">Reset password</p>
              <h2 id="resetTitle">${ui.escapeHtml(resident.name)} \u00b7 Flat ${ui.escapeHtml(resident.flat)}</h2>
              <p class="muted">Choose a new password. The resident will use it on next sign-in.</p>
            </div>
            <button type="button" class="modal-close" data-close aria-label="Close">
              <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <path d="M18 6 6 18M6 6l12 12"/>
              </svg>
            </button>
          </header>
          <form class="modal-form" id="resetForm" novalidate>
            <label class="field">
              <span>New Password</span>
              <input type="text" name="newPassword" placeholder="At least 6 characters" minlength="6" required />
            </label>
            <p class="login-error" id="resetError" hidden></p>
            <div class="modal-actions">
              <button type="button" class="btn btn-ghost" data-close>Cancel</button>
              <button type="submit" class="btn btn-primary" id="resetSubmit">
                <span class="btn-label">Set Password</span>
              </button>
            </div>
          </form>
        </div>
      </div>
    `;
  }

  async function renderAdminResidents(root, ctx) {
    const { navigate } = ctx;
    const storage = window.CM.storage;
    const showToast = window.CM.showToast;

    let residents = [];

    async function load() {
      root.innerHTML = renderShell(ui.loadingBlock('Loading residents\u2026'));
      bindBack();
      try {
        residents = await storage.listResidents();
        const layout = `
          ${renderCreateForm()}
          ${renderTable(residents)}
        `;
        root.innerHTML = renderShell(layout);
        bindBack();
        wireForm();
        wireResetButtons();
      } catch (err) {
        const msg = ui.messageFromError(err, 'Could not load residents.');
        root.innerHTML = renderShell(ui.errorBlock(msg, 'Retry'));
        bindBack();
        const retry = root.querySelector('[data-retry]');
        if (retry) retry.addEventListener('click', load);
      }
    }

    function bindBack() {
      const back = root.querySelector('[data-back]');
      if (back) back.addEventListener('click', () => navigate('#/admin/dashboard'));
    }

    function wireForm() {
      const form = root.querySelector('#createResidentForm');
      if (!form) return;
      const submit = form.querySelector('#createResidentSubmit');
      const errorEl = form.querySelector('#createResidentError');

      function setError(msg) {
        if (msg) {
          errorEl.textContent = msg;
          errorEl.hidden = false;
        } else {
          errorEl.textContent = '';
          errorEl.hidden = true;
        }
      }

      form.addEventListener('submit', async (e) => {
        e.preventDefault();
        setError('');
        const data = {
          flatNo: form.elements.flatNo.value.trim(),
          name: form.elements.name.value.trim(),
          phone: form.elements.phone.value.trim(),
          password: form.elements.password.value,
        };
        if (!data.flatNo || !data.name || !data.password) {
          setError('Flat, name and password are required.');
          return;
        }
        if (data.password.length < 6) {
          setError('Password must be at least 6 characters.');
          return;
        }
        submit.disabled = true;
        submit.querySelector('.btn-label').textContent = 'Adding\u2026';
        try {
          await storage.createResident(data);
          showToast(`Added ${data.name}`);
          await load();
        } catch (err) {
          submit.disabled = false;
          submit.querySelector('.btn-label').textContent = 'Add Resident';
          setError(ui.messageFromError(err, 'Could not add resident'));
        }
      });
    }

    function wireResetButtons() {
      root.querySelectorAll('[data-action="reset"]').forEach((btn) => {
        btn.addEventListener('click', () => {
          openResetModal({
            id: btn.dataset.id,
            name: btn.dataset.name,
            flat: btn.dataset.flat,
          });
        });
      });
    }

    function openResetModal(resident) {
      const modalContainer = document.createElement('div');
      modalContainer.innerHTML = renderResetModal(resident);
      const backdrop = modalContainer.firstElementChild;
      document.body.appendChild(backdrop);
      document.body.classList.add('modal-open');

      const form = backdrop.querySelector('#resetForm');
      const errorEl = backdrop.querySelector('#resetError');
      const submit = backdrop.querySelector('#resetSubmit');

      function close() {
        document.body.classList.remove('modal-open');
        backdrop.remove();
        document.removeEventListener('keydown', onKey);
      }

      function setError(msg) {
        if (msg) {
          errorEl.textContent = msg;
          errorEl.hidden = false;
        } else {
          errorEl.textContent = '';
          errorEl.hidden = true;
        }
      }

      function onKey(e) {
        if (e.key === 'Escape') close();
      }
      document.addEventListener('keydown', onKey);

      backdrop.addEventListener('click', (e) => {
        if (e.target === backdrop) close();
      });
      backdrop.querySelectorAll('[data-close]').forEach((btn) =>
        btn.addEventListener('click', close)
      );

      setTimeout(() => form.elements.newPassword.focus(), 40);

      form.addEventListener('submit', async (e) => {
        e.preventDefault();
        setError('');
        const newPassword = form.elements.newPassword.value;
        if (!newPassword || newPassword.length < 6) {
          setError('Password must be at least 6 characters.');
          return;
        }
        submit.disabled = true;
        submit.querySelector('.btn-label').textContent = 'Saving\u2026';
        try {
          await storage.resetResidentPassword(resident.id, { newPassword });
          showToast(`Reset password for ${resident.name}`);
          close();
        } catch (err) {
          submit.disabled = false;
          submit.querySelector('.btn-label').textContent = 'Set Password';
          setError(ui.messageFromError(err, 'Could not reset password'));
        }
      });
    }

    await load();
  }

  window.CM = window.CM || {};
  window.CM.views = window.CM.views || {};
  window.CM.views.adminResidents = renderAdminResidents;
})();
