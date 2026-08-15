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
          <p class="muted">Add a resident, edit their details, reset their password, or remove them.</p>
        </div>

        ${content}
      </section>
    `;
  }

  function renderResidentRow(r) {
    return `
      <tr data-resident="${ui.escapeHtml(r.id)}">
        <td><strong>${ui.escapeHtml(r.flat)}</strong></td>
        <td>${ui.escapeHtml(r.name)}</td>
        <td>${r.phone ? ui.escapeHtml(r.phone) : '<span class="muted">—</span>'}</td>
        <td class="row-actions">
          <button type="button" class="btn btn-ghost btn-small" data-action="edit" data-id="${ui.escapeHtml(r.id)}">Edit</button>
          <button type="button" class="btn btn-ghost btn-small" data-action="reset" data-id="${ui.escapeHtml(r.id)}">Reset password</button>
          <button type="button" class="btn btn-ghost btn-small btn-danger-ghost" data-action="delete" data-id="${ui.escapeHtml(r.id)}">Delete</button>
        </td>
      </tr>
    `;
  }

  function renderFilterBar() {
    return `
      <div class="card filter-bar filter-bar-single" role="group" aria-label="Filter residents">
        <label class="filter-field">
          <span>Search by flat number</span>
          <input type="search" id="residentSearch" placeholder="e.g. A-101" autocomplete="off" />
        </label>
        <button type="button" class="filter-clear" id="residentSearchClear" hidden>Clear</button>
      </div>
      <p class="filter-meta" id="residentMeta"></p>
    `;
  }

  function renderTable(residents, isFiltered) {
    if (!residents.length) {
      if (isFiltered) {
        return `
          <div class="empty-state card">
            <h3>No matching flats</h3>
            <p class="muted">No resident's flat number matches your search.</p>
            <button type="button" class="btn btn-primary" data-action="clear-search">Clear search</button>
          </div>
        `;
      }
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

  async function renderAdminResidents(root, ctx) {
    const { navigate } = ctx;
    const storage = window.CM.storage;
    const showToast = window.CM.showToast;

    let residents = [];
    let search = '';

    function residentById(id) {
      return residents.find((r) => String(r.id) === String(id));
    }

    function visibleResidents() {
      const q = search.trim().toLowerCase();
      if (!q) return residents;
      return residents.filter((r) => String(r.flat || '').toLowerCase().includes(q));
    }

    async function load() {
      root.innerHTML = renderShell(ui.loadingBlock('Loading residents…'));
      bindBack();
      try {
        residents = await storage.listResidents();
        root.innerHTML = renderShell(`
          ${renderCreateForm()}
          ${renderFilterBar()}
          <div id="residentTable"></div>
        `);
        bindBack();
        wireForm();
        wireSearch();
        renderList();
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

    /**
     * Re-renders only the table, so typing in the search box never loses focus.
     */
    function renderList() {
      const host = root.querySelector('#residentTable');
      if (!host) return;
      const filtered = visibleResidents();
      const isFiltered = search.trim() !== '';

      host.innerHTML = renderTable(filtered, isFiltered);
      wireRowActions();

      const clearInEmpty = host.querySelector('[data-action="clear-search"]');
      if (clearInEmpty) clearInEmpty.addEventListener('click', clearSearch);

      const meta = root.querySelector('#residentMeta');
      const total = `${residents.length} ${residents.length === 1 ? 'resident' : 'residents'}`;
      if (meta) {
        meta.textContent = isFiltered
          ? `Showing ${filtered.length} of ${total}`
          : total;
      }
      const clearBtn = root.querySelector('#residentSearchClear');
      if (clearBtn) clearBtn.hidden = !isFiltered;
    }

    function clearSearch() {
      search = '';
      const input = root.querySelector('#residentSearch');
      if (input) {
        input.value = '';
        input.focus();
      }
      renderList();
    }

    function wireSearch() {
      const input = root.querySelector('#residentSearch');
      const clearBtn = root.querySelector('#residentSearchClear');
      if (!input) return;
      input.value = search;
      input.addEventListener('input', () => {
        search = input.value;
        renderList();
      });
      if (clearBtn) clearBtn.addEventListener('click', clearSearch);
    }

    function wireForm() {
      const form = root.querySelector('#createResidentForm');
      if (!form) return;
      const submit = form.querySelector('#createResidentSubmit');
      const errorEl = form.querySelector('#createResidentError');

      function setError(msg) {
        errorEl.textContent = msg || '';
        errorEl.hidden = !msg;
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
        submit.querySelector('.btn-label').textContent = 'Adding…';
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

    function wireRowActions() {
      root.querySelectorAll('[data-action]').forEach((btn) => {
        btn.addEventListener('click', () => {
          const resident = residentById(btn.dataset.id);
          if (!resident) return;
          if (btn.dataset.action === 'edit') openEditModal(resident);
          if (btn.dataset.action === 'reset') openResetModal(resident);
          if (btn.dataset.action === 'delete') confirmDelete(resident, btn);
        });
      });
    }

    // ------------------------------------------------------------------ edit

    function openEditModal(resident) {
      const modal = ui.openModal(`
        <header class="modal-head">
          <div class="modal-head-text">
            <p class="modal-eyebrow">Edit resident</p>
            <h2>${ui.escapeHtml(resident.name)} · Flat ${ui.escapeHtml(resident.flat)}</h2>
            <p class="muted">Update the resident's details. Use "Reset password" to change their password.</p>
          </div>
          <button type="button" class="modal-close" data-close aria-label="Close">${ui.CLOSE_ICON}</button>
        </header>
        <form class="modal-form" id="editForm" novalidate>
          <label class="field">
            <span>Flat Number</span>
            <input type="text" name="flatNo" value="${ui.escapeHtml(resident.flat)}" required data-autofocus />
          </label>
          <label class="field">
            <span>Full Name</span>
            <input type="text" name="name" value="${ui.escapeHtml(resident.name)}" required />
          </label>
          <label class="field">
            <span>Phone</span>
            <input type="tel" name="phone" value="${ui.escapeHtml(resident.phone || '')}" placeholder="e.g. +91 98000 12345" />
          </label>
          <p class="login-error" id="editError" hidden></p>
          <div class="modal-actions">
            <button type="button" class="btn btn-ghost" data-close>Cancel</button>
            <button type="submit" class="btn btn-primary" id="editSubmit">
              <span class="btn-label">Save Changes</span>
            </button>
          </div>
        </form>
      `);

      const form = modal.root.querySelector('#editForm');
      const errorEl = modal.root.querySelector('#editError');
      const submit = modal.root.querySelector('#editSubmit');

      function setError(msg) {
        errorEl.textContent = msg || '';
        errorEl.hidden = !msg;
      }

      form.addEventListener('submit', async (e) => {
        e.preventDefault();
        setError('');
        const flatNo = form.elements.flatNo.value.trim();
        const name = form.elements.name.value.trim();
        const phone = form.elements.phone.value.trim();

        if (!flatNo || !name) {
          setError('Flat number and name are required.');
          return;
        }

        submit.disabled = true;
        submit.querySelector('.btn-label').textContent = 'Saving…';
        try {
          await storage.updateResident(resident.id, { flatNo, name, phone });
          modal.close();
          showToast('Successfully edited details');
          await load();
        } catch (err) {
          submit.disabled = false;
          submit.querySelector('.btn-label').textContent = 'Save Changes';
          setError(ui.messageFromError(err, 'Could not save changes'));
        }
      });
    }

    // ---------------------------------------------------------------- delete

    async function confirmDelete(resident, btn) {
      // Complaints carry a hard FK to the resident, so deletion takes them
      // with it. Look up how many first so the warning is specific.
      btn.disabled = true;
      let complaintCount = null;
      try {
        const complaints = await storage.getComplaints(resident.flat);
        complaintCount = complaints.length;
      } catch {
        complaintCount = null; // fall back to a generic warning
      }
      btn.disabled = false;

      const who = `<strong>${ui.escapeHtml(resident.name)}</strong> (Flat ${ui.escapeHtml(resident.flat)})`;
      let message = `Are you sure you want to delete ${who}? They will no longer be able to sign in.`;
      if (complaintCount === null) {
        message += ' Any complaints they raised will be deleted as well.';
      } else if (complaintCount > 0) {
        message += ` Their <strong>${complaintCount} complaint${complaintCount === 1 ? '' : 's'}</strong> will be permanently deleted too.`;
      }
      message += ' This cannot be undone.';

      const confirmed = await ui.confirmDialog({
        eyebrow: 'Delete resident',
        title: 'Are you sure you want to delete this user?',
        message,
        confirmLabel: 'Yes, delete',
        cancelLabel: 'No, keep it',
        danger: true,
      });
      if (!confirmed) return;

      try {
        await storage.deleteResident(resident.id);
        showToast('Successfully deleted');
        await load();
      } catch (err) {
        showToast(ui.messageFromError(err, 'Could not delete resident'), 'error');
      }
    }

    // -------------------------------------------------------- reset password

    function openResetModal(resident) {
      const modal = ui.openModal(`
        <header class="modal-head">
          <div class="modal-head-text">
            <p class="modal-eyebrow">Reset password</p>
            <h2>${ui.escapeHtml(resident.name)} · Flat ${ui.escapeHtml(resident.flat)}</h2>
            <p class="muted">Choose a new password. The resident will use it on next sign-in.</p>
          </div>
          <button type="button" class="modal-close" data-close aria-label="Close">${ui.CLOSE_ICON}</button>
        </header>
        <form class="modal-form" id="resetForm" novalidate>
          <label class="field">
            <span>New Password</span>
            <input type="text" name="newPassword" placeholder="At least 6 characters" minlength="6" required data-autofocus />
          </label>
          <p class="login-error" id="resetError" hidden></p>
          <div class="modal-actions">
            <button type="button" class="btn btn-ghost" data-close>Cancel</button>
            <button type="submit" class="btn btn-primary" id="resetSubmit">
              <span class="btn-label">Set Password</span>
            </button>
          </div>
        </form>
      `);

      const form = modal.root.querySelector('#resetForm');
      const errorEl = modal.root.querySelector('#resetError');
      const submit = modal.root.querySelector('#resetSubmit');

      function setError(msg) {
        errorEl.textContent = msg || '';
        errorEl.hidden = !msg;
      }

      form.addEventListener('submit', async (e) => {
        e.preventDefault();
        setError('');
        const newPassword = form.elements.newPassword.value;
        if (!newPassword || newPassword.length < 6) {
          setError('Password must be at least 6 characters.');
          return;
        }
        submit.disabled = true;
        submit.querySelector('.btn-label').textContent = 'Saving…';
        try {
          await storage.resetResidentPassword(resident.id, { newPassword });
          modal.close();
          showToast(`Reset password for ${resident.name}`);
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
