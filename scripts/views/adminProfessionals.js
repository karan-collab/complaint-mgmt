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
          <h1>Manage Professionals</h1>
          <p class="muted">Add a worker, edit their details, or remove them. These are the people you assign to tickets.</p>
        </div>

        ${content}
      </section>
    `;
  }

  function renderProfessionalRow(p) {
    return `
      <tr data-professional="${ui.escapeHtml(p.id)}">
        <td><strong>${ui.escapeHtml(p.category)}</strong></td>
        <td>${ui.escapeHtml(p.name)}</td>
        <td>${p.phone ? ui.escapeHtml(p.phone) : '<span class="muted">—</span>'}</td>
        <td class="row-actions">
          <button type="button" class="btn btn-ghost btn-small" data-action="edit" data-id="${ui.escapeHtml(p.id)}">Edit</button>
          <button type="button" class="btn btn-ghost btn-small btn-danger-ghost" data-action="delete" data-id="${ui.escapeHtml(p.id)}">Delete</button>
        </td>
      </tr>
    `;
  }

  function renderFilterBar(categories) {
    const options = ['All']
      .concat(categories)
      .map((c) => `<option value="${ui.escapeHtml(c)}">${ui.escapeHtml(c)}</option>`)
      .join('');
    return `
      <div class="card filter-bar filter-bar-admin" role="group" aria-label="Filter professionals">
        <label class="filter-field">
          <span>Category</span>
          <select id="professionalCategoryFilter">${options}</select>
        </label>
        <label class="filter-field">
          <span>Search by name</span>
          <input type="search" id="professionalSearch" placeholder="e.g. Ramesh" autocomplete="off" />
        </label>
        <button type="button" class="filter-clear" id="professionalFilterClear" hidden>Clear</button>
      </div>
      <p class="filter-meta" id="professionalMeta"></p>
    `;
  }

  function renderTable(professionals, isFiltered) {
    if (!professionals.length) {
      if (isFiltered) {
        return `
          <div class="empty-state card">
            <h3>No matching professionals</h3>
            <p class="muted">No one matches this category and name. Try clearing the filters.</p>
            <button type="button" class="btn btn-primary" data-action="clear-filters">Clear filters</button>
          </div>
        `;
      }
      return `
        <div class="empty-state card">
          <h3>No professionals yet</h3>
          <p class="muted">Add a plumber, electrician or carpenter above so you can assign them to tickets.</p>
        </div>
      `;
    }
    return `
      <div class="card data-card">
        <table class="data-table">
          <thead>
            <tr>
              <th>Category</th>
              <th>Name</th>
              <th>Phone</th>
              <th class="row-actions-head">Actions</th>
            </tr>
          </thead>
          <tbody>
            ${professionals.map(renderProfessionalRow).join('')}
          </tbody>
        </table>
      </div>
    `;
  }

  function categoryOptions(selected) {
    const storage = window.CM.storage;
    return storage.CATEGORIES.map(
      (c) =>
        `<option value="${ui.escapeHtml(c)}"${c === selected ? ' selected' : ''}>${ui.escapeHtml(c)}</option>`
    ).join('');
  }

  function renderCreateForm() {
    return `
      <form class="card form-card" id="createProfessionalForm" novalidate>
        <div class="form-section-head">
          <h2>Add a new professional</h2>
          <p class="muted">Their category decides which tickets they can be assigned to.</p>
        </div>
        <div class="field-grid">
          <label class="field">
            <span>Category</span>
            <select name="category" required>
              <option value="" disabled selected>Select a category</option>
              ${categoryOptions(null)}
            </select>
          </label>
          <label class="field">
            <span>Full Name</span>
            <input type="text" name="name" placeholder="e.g. Ramesh Kumar" required />
          </label>
          <label class="field">
            <span>Phone</span>
            <input type="tel" name="phone" placeholder="e.g. +91 98765 43210" required />
          </label>
        </div>
        <p class="login-error" id="createProfessionalError" hidden></p>
        <button type="submit" class="btn btn-primary" id="createProfessionalSubmit">
          <span class="btn-label">Add Professional</span>
        </button>
      </form>
    `;
  }

  async function renderAdminProfessionals(root, ctx) {
    const { navigate } = ctx;
    const storage = window.CM.storage;
    const showToast = window.CM.showToast;

    let professionals = [];
    const filters = { category: 'All', name: '' };

    function professionalById(id) {
      return professionals.find((p) => String(p.id) === String(id));
    }

    function isFiltered() {
      return filters.category !== 'All' || filters.name.trim() !== '';
    }

    function visibleProfessionals() {
      const q = filters.name.trim().toLowerCase();
      return professionals.filter((p) => {
        if (filters.category !== 'All' && p.category !== filters.category) return false;
        if (q && !String(p.name || '').toLowerCase().includes(q)) return false;
        return true;
      });
    }

    async function load() {
      root.innerHTML = renderShell(ui.loadingBlock('Loading professionals…'));
      bindBack();
      try {
        professionals = await storage.listProfessionals();
        root.innerHTML = renderShell(`
          ${renderCreateForm()}
          ${renderFilterBar(storage.CATEGORIES)}
          <div id="professionalTable"></div>
        `);
        bindBack();
        wireForm();
        wireFilters();
        renderList();
      } catch (err) {
        const msg = ui.messageFromError(err, 'Could not load professionals.');
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
      const host = root.querySelector('#professionalTable');
      if (!host) return;
      const filtered = visibleProfessionals();
      const filtering = isFiltered();

      host.innerHTML = renderTable(filtered, filtering);
      wireRowActions();

      const clearInEmpty = host.querySelector('[data-action="clear-filters"]');
      if (clearInEmpty) clearInEmpty.addEventListener('click', clearFilters);

      const meta = root.querySelector('#professionalMeta');
      const total = `${professionals.length} ${professionals.length === 1 ? 'professional' : 'professionals'}`;
      if (meta) {
        meta.textContent = filtering ? `Showing ${filtered.length} of ${total}` : total;
      }
      const clearBtn = root.querySelector('#professionalFilterClear');
      if (clearBtn) clearBtn.hidden = !filtering;
    }

    function clearFilters() {
      filters.category = 'All';
      filters.name = '';
      const cat = root.querySelector('#professionalCategoryFilter');
      const search = root.querySelector('#professionalSearch');
      if (cat) cat.value = 'All';
      if (search) {
        search.value = '';
        search.focus();
      }
      renderList();
    }

    function wireFilters() {
      const cat = root.querySelector('#professionalCategoryFilter');
      const search = root.querySelector('#professionalSearch');
      const clearBtn = root.querySelector('#professionalFilterClear');

      if (cat) {
        cat.value = filters.category;
        cat.addEventListener('change', () => {
          filters.category = cat.value;
          renderList();
        });
      }
      if (search) {
        search.value = filters.name;
        search.addEventListener('input', () => {
          filters.name = search.value;
          renderList();
        });
      }
      if (clearBtn) clearBtn.addEventListener('click', clearFilters);
    }

    function wireForm() {
      const form = root.querySelector('#createProfessionalForm');
      if (!form) return;
      const submit = form.querySelector('#createProfessionalSubmit');
      const errorEl = form.querySelector('#createProfessionalError');

      function setError(msg) {
        errorEl.textContent = msg || '';
        errorEl.hidden = !msg;
      }

      form.addEventListener('submit', async (e) => {
        e.preventDefault();
        setError('');
        const data = {
          category: form.elements.category.value,
          name: form.elements.name.value.trim(),
          phone: form.elements.phone.value.trim(),
        };
        if (!data.category) {
          setError('Please choose a category.');
          return;
        }
        if (!data.name || !data.phone) {
          setError('Name and phone are required.');
          return;
        }
        submit.disabled = true;
        submit.querySelector('.btn-label').textContent = 'Adding…';
        try {
          await storage.createProfessional(data);
          showToast(`Added ${data.name}`);
          await load();
        } catch (err) {
          submit.disabled = false;
          submit.querySelector('.btn-label').textContent = 'Add Professional';
          setError(ui.messageFromError(err, 'Could not add professional'));
        }
      });
    }

    function wireRowActions() {
      root.querySelectorAll('[data-action]').forEach((btn) => {
        btn.addEventListener('click', () => {
          const professional = professionalById(btn.dataset.id);
          if (!professional) return;
          if (btn.dataset.action === 'edit') openEditModal(professional);
          if (btn.dataset.action === 'delete') confirmDelete(professional);
        });
      });
    }

    // ------------------------------------------------------------------ edit

    function openEditModal(professional) {
      const modal = ui.openModal(`
        <header class="modal-head">
          <div class="modal-head-text">
            <p class="modal-eyebrow">Edit professional</p>
            <h2>${ui.escapeHtml(professional.name)} · ${ui.escapeHtml(professional.category)}</h2>
            <p class="muted">Update their contact details.</p>
          </div>
          <button type="button" class="modal-close" data-close aria-label="Close">${ui.CLOSE_ICON}</button>
        </header>
        <form class="modal-form" id="editForm" novalidate>
          <label class="field">
            <span>Full Name</span>
            <input type="text" name="name" value="${ui.escapeHtml(professional.name)}" required data-autofocus />
          </label>
          <label class="field">
            <span>Phone</span>
            <input type="tel" name="phone" value="${ui.escapeHtml(professional.phone || '')}" required />
          </label>
          <label class="field">
            <span>Category</span>
            <input type="text" value="${ui.escapeHtml(professional.category)}" disabled />
            <span class="field-hint">Category cannot be changed — past tickets reference it. Add a new professional instead.</span>
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
        const name = form.elements.name.value.trim();
        const phone = form.elements.phone.value.trim();
        if (!name || !phone) {
          setError('Name and phone are required.');
          return;
        }
        submit.disabled = true;
        submit.querySelector('.btn-label').textContent = 'Saving…';
        try {
          await storage.updateProfessional(professional.id, { name, phone });
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

    async function confirmDelete(professional) {
      const who = `<strong>${ui.escapeHtml(professional.name)}</strong> (${ui.escapeHtml(professional.category)})`;
      const confirmed = await ui.confirmDialog({
        eyebrow: 'Delete professional',
        title: 'Are you sure you want to delete this professional?',
        message: `Are you sure you want to delete ${who}? They will no longer appear when assigning tickets. This cannot be undone.`,
        confirmLabel: 'Yes, delete',
        cancelLabel: 'No, keep it',
        danger: true,
      });
      if (!confirmed) return;

      try {
        await storage.deleteProfessional(professional.id);
        showToast('Successfully deleted');
        await load();
      } catch (err) {
        // The API refuses to delete anyone still referenced by a complaint.
        showToast(ui.messageFromError(err, 'Could not delete professional'), 'error');
      }
    }

    await load();
  }

  window.CM = window.CM || {};
  window.CM.views = window.CM.views || {};
  window.CM.views.adminProfessionals = renderAdminProfessionals;
})();
