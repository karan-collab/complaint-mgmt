(function () {
  const ui = window.CM.ui;

  function formatDate(iso) {
    if (!iso) return '';
    try {
      const d = new Date(iso);
      return d.toLocaleDateString(undefined, {
        day: '2-digit',
        month: 'short',
        year: 'numeric',
      });
    } catch {
      return iso;
    }
  }

  const CATEGORY_ICONS = {
    Plumber: 'M7 3v4a3 3 0 0 0 3 3h0v11M17 3v4a3 3 0 0 1-3 3h0v11',
    Carpenter: 'M14 3h7v7M21 3 10 14M4 21l5-5M4 14l6 6',
    Electrician: 'M13 2 4 14h7l-1 8 9-12h-7z',
    Painting: 'M19 11V7a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v4a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2zM12 13v3a3 3 0 0 0 0 6',
    Others: 'M12 8v4M12 16h.01M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0z',
  };

  function categoryIcon(category) {
    const path = CATEGORY_ICONS[category] || CATEGORY_ICONS.Others;
    return `<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="${path}"/></svg>`;
  }

  function badgeFor(display) {
    if (display === 'Completed') return 'badge-done';
    if (display === 'Pending Work') return 'badge-work';
    return 'badge-pending';
  }

  function renderComplaintCard(c, getDisplayStatus) {
    const display = getDisplayStatus(c);

    let stateBlock = '';
    if (display === 'Assignment Pending') {
      stateBlock = `
        <div class="state-banner state-wait">
          <span class="pulse-dot" aria-hidden="true"></span>
          <p>Please wait, management is assigning a professional.</p>
        </div>
      `;
    } else if (display === 'Pending Work') {
      stateBlock = `
        <div class="state-banner state-worker">
          <div class="worker-header">
            <span class="worker-avatar" aria-hidden="true">${ui.escapeHtml(c.worker.name.charAt(0).toUpperCase())}</span>
            <div>
              <p class="worker-label">Assigned Worker</p>
              <p class="worker-name">${ui.escapeHtml(c.worker.name)}</p>
              <p class="worker-prof">${ui.escapeHtml(c.worker.profession || c.category)}</p>
            </div>
          </div>
          <a class="worker-phone" href="tel:${ui.escapeHtml(c.worker.phone.replace(/\s/g, ''))}">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z"/>
            </svg>
            <span>${ui.escapeHtml(c.worker.phone)}</span>
          </a>
        </div>
      `;
    }

    // A resolved complaint is the society's record of work done, so only an
    // open one can be withdrawn.
    const canDelete = display !== 'Completed';

    return `
      <article class="complaint-card">
        <header class="complaint-head">
          <div class="cat-chip">
            ${categoryIcon(c.category)}
            <span>${ui.escapeHtml(c.category)}</span>
          </div>
          <span class="badge ${badgeFor(display)}">${ui.escapeHtml(display)}</span>
        </header>
        <p class="complaint-date">Raised on ${formatDate(c.createdAt)}</p>
        <p class="complaint-desc">${ui.escapeHtml(c.description)}</p>
        ${stateBlock}
        ${
          canDelete
            ? `<footer class="complaint-foot">
                <button type="button" class="btn btn-ghost btn-small btn-danger-ghost" data-action="delete" data-id="${ui.escapeHtml(c.id)}">
                  <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                    <path d="M3 6h18"/>
                    <path d="M8 6V4a1 1 0 0 1 1-1h6a1 1 0 0 1 1 1v2"/>
                    <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6"/>
                  </svg>
                  <span>Delete this issue</span>
                </button>
              </footer>`
            : ''
        }
      </article>
    `;
  }

  function renderEmpty(hasFilters) {
    if (hasFilters) {
      return `
        <div class="empty-state card">
          <div class="empty-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" width="40" height="40" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="11" cy="11" r="8"/>
              <path d="m21 21-4.3-4.3"/>
            </svg>
          </div>
          <h3>No matching complaints</h3>
          <p class="muted">Try changing or clearing the filters above.</p>
          <button type="button" class="btn btn-primary" data-action="clear-filters">Clear filters</button>
        </div>
      `;
    }
    return `
      <div class="empty-state card">
        <div class="empty-icon" aria-hidden="true">
          <svg viewBox="0 0 24 24" width="40" height="40" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
            <path d="M14 2v6h6"/>
            <path d="M9 13h6M9 17h4"/>
          </svg>
        </div>
        <h3>No complaints yet</h3>
        <p class="muted">When you raise an issue, it will appear here.</p>
        <button type="button" class="btn btn-primary" data-route="#/raise">Raise a New Issue</button>
      </div>
    `;
  }

  const STATUS_OPTIONS = ['All', 'Assignment Pending', 'Pending Work', 'Completed'];

  function normalizeStatus(s) {
    if (!s) return 'All';
    const v = String(s).trim().toLowerCase();
    if (v === 'assignment pending' || v === 'pending') return 'Assignment Pending';
    if (v === 'pending work' || v === 'work') return 'Pending Work';
    if (v === 'completed' || v === 'complete') return 'Completed';
    return 'All';
  }

  function normalizeCategory(c, allowed) {
    if (!c) return 'All';
    const match = allowed.find((x) => x.toLowerCase() === String(c).toLowerCase());
    return match || 'All';
  }

  function renderShell(session, contentHtml) {
    return `
      <section class="page">
        <button type="button" class="back-link" data-route="#/dashboard">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="m15 18-6-6 6-6"/>
          </svg>
          <span>Back to dashboard</span>
        </button>

        <div class="page-title-row">
          <div class="page-title">
            <h1>Registered Complaints</h1>
            <p class="muted" id="resultMeta">Flat ${ui.escapeHtml(session.flat)}</p>
          </div>
          <button type="button" class="btn btn-primary btn-raise" data-route="#/raise">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <path d="M12 5v14M5 12h14"/>
            </svg>
            <span>Raise New Issue</span>
          </button>
        </div>

        ${contentHtml}
      </section>
    `;
  }

  async function renderComplaints(root, ctx) {
    const { session, navigate, params, replaceParams } = ctx;
    const storage = window.CM.storage;
    const showToast = window.CM.showToast;

    /** Back link and the header's "Raise New Issue" button. */
    function bindChrome() {
      const back = root.querySelector('.back-link');
      if (back) back.addEventListener('click', () => navigate('#/dashboard'));
      const raise = root.querySelector('.btn-raise');
      if (raise) raise.addEventListener('click', () => navigate('#/raise'));
    }

    root.innerHTML = renderShell(session, ui.loadingBlock('Loading complaints\u2026'));
    bindChrome();

    let allComplaints;
    try {
      allComplaints = await storage.getComplaints(session.flat);
    } catch (err) {
      const msg = ui.messageFromError(err, 'Could not load complaints.');
      root.innerHTML = renderShell(session, ui.errorBlock(msg, 'Retry'));
      bindChrome();
      const retry = root.querySelector('[data-retry]');
      if (retry) retry.addEventListener('click', () => renderComplaints(root, ctx));
      return;
    }

    const state = {
      status: normalizeStatus(params && params.status),
      category: normalizeCategory(params && params.category, storage.CATEGORIES),
    };

    const statusOptions = STATUS_OPTIONS.map(
      (s) => `<option value="${s}"${state.status === s ? ' selected' : ''}>${s}</option>`
    ).join('');

    const categoryOptions = ['All']
      .concat(storage.CATEGORIES)
      .map(
        (c) => `<option value="${c}"${state.category === c ? ' selected' : ''}>${c}</option>`
      )
      .join('');

    const interactive = `
      <div class="filter-bar card" role="group" aria-label="Filter complaints">
        <label class="filter-field">
          <span>Status</span>
          <select id="filterStatus">${statusOptions}</select>
        </label>
        <label class="filter-field">
          <span>Professional</span>
          <select id="filterCategory">${categoryOptions}</select>
        </label>
        <button type="button" class="filter-clear" id="filterClear" hidden>Clear</button>
      </div>
      <div class="complaint-list" id="complaintList"></div>
    `;

    root.innerHTML = renderShell(session, interactive);
    bindChrome();

    const listEl = root.querySelector('#complaintList');
    const metaEl = root.querySelector('#resultMeta');
    const clearBtn = root.querySelector('#filterClear');
    const statusSel = root.querySelector('#filterStatus');
    const categorySel = root.querySelector('#filterCategory');

    /**
     * Withdraw a complaint the resident no longer needs. A reason is required
     * so the society can see why complaints get pulled; picking "Other" makes
     * the free-text box mandatory, since "Other" alone says nothing.
     */
    function openDeleteModal(complaint) {
      const reasons = storage.DELETION_REASONS;
      const modal = ui.openModal(`
        <header class="modal-head">
          <div class="modal-head-text">
            <p class="modal-eyebrow">Delete issue</p>
            <h2>Delete this issue?</h2>
            <p class="muted">
              Your <strong>${ui.escapeHtml(complaint.category)}</strong> complaint
              "${ui.escapeHtml(complaint.description)}" will be withdrawn and management
              will no longer see it. This cannot be undone.
            </p>
          </div>
          <button type="button" class="modal-close" data-close aria-label="Close">${ui.CLOSE_ICON}</button>
        </header>
        <form class="modal-form" id="deleteForm" novalidate>
          <label class="field">
            <span>Why are you deleting this? *</span>
            <select name="reason" id="deleteReason" required data-autofocus>
              <option value="" disabled selected>Select a reason</option>
              ${reasons.map((r) => `<option value="${ui.escapeHtml(r.value)}">${ui.escapeHtml(r.label)}</option>`).join('')}
            </select>
          </label>
          <label class="field" id="deleteCommentsField" hidden>
            <span>Please tell us more *</span>
            <textarea name="comments" id="deleteComments" rows="3" maxlength="500"
              placeholder="Briefly describe why you are deleting this issue"></textarea>
          </label>
          <p class="login-error" id="deleteError" hidden></p>
          <div class="modal-actions">
            <button type="button" class="btn btn-ghost" data-close>No, keep it</button>
            <button type="submit" class="btn btn-danger" id="deleteSubmit">
              <span class="btn-label">Yes, delete it</span>
            </button>
          </div>
        </form>
      `);

      const form = modal.root.querySelector('#deleteForm');
      const reasonSel = modal.root.querySelector('#deleteReason');
      const commentsField = modal.root.querySelector('#deleteCommentsField');
      const commentsBox = modal.root.querySelector('#deleteComments');
      const errorEl = modal.root.querySelector('#deleteError');
      const submit = modal.root.querySelector('#deleteSubmit');

      function setError(msg) {
        errorEl.textContent = msg || '';
        errorEl.hidden = !msg;
      }

      // The comments box only appears for "Other".
      reasonSel.addEventListener('change', () => {
        const isOther = reasonSel.value === 'OTHER';
        commentsField.hidden = !isOther;
        if (isOther) setTimeout(() => commentsBox.focus(), 40);
        setError('');
      });

      form.addEventListener('submit', async (e) => {
        e.preventDefault();
        setError('');
        const reason = reasonSel.value;
        const comments = commentsBox.value.trim();

        if (!reason) {
          setError('Please choose a reason.');
          return;
        }
        if (reason === 'OTHER' && !comments) {
          setError('Please describe the reason.');
          commentsBox.focus();
          return;
        }

        submit.disabled = true;
        submit.querySelector('.btn-label').textContent = 'Deleting…';
        try {
          await storage.deleteComplaint(complaint.id, { reason, comments });
          allComplaints = allComplaints.filter((c) => String(c.id) !== String(complaint.id));
          modal.close();
          showToast('Issue deleted');
          applyAndRender();
        } catch (err) {
          submit.disabled = false;
          submit.querySelector('.btn-label').textContent = 'Yes, delete it';
          setError(ui.messageFromError(err, 'Could not delete this issue'));
        }
      });
    }

    function wireDeleteButtons() {
      listEl.querySelectorAll('[data-action="delete"]').forEach((btn) => {
        btn.addEventListener('click', () => {
          const complaint = allComplaints.find((c) => String(c.id) === String(btn.dataset.id));
          if (complaint) openDeleteModal(complaint);
        });
      });
    }

    function applyAndRender() {
      const filtered = allComplaints.filter((c) => {
        if (state.status !== 'All' && storage.getDisplayStatus(c) !== state.status) return false;
        if (state.category !== 'All' && c.category !== state.category) return false;
        return true;
      });

      const hasFilters = state.status !== 'All' || state.category !== 'All';
      clearBtn.hidden = !hasFilters;

      const totalLabel = `${allComplaints.length} ${allComplaints.length === 1 ? 'complaint' : 'complaints'}`;
      metaEl.textContent = hasFilters
        ? `Showing ${filtered.length} of ${totalLabel} \u00b7 Flat ${session.flat}`
        : `Flat ${session.flat} \u00b7 ${totalLabel}`;

      if (filtered.length) {
        listEl.innerHTML = filtered
          .map((c) => renderComplaintCard(c, storage.getDisplayStatus))
          .join('');
        wireDeleteButtons();
      } else {
        listEl.innerHTML = renderEmpty(hasFilters || allComplaints.length > 0);
        const clearInList = listEl.querySelector('[data-action="clear-filters"]');
        if (clearInList) clearInList.addEventListener('click', clearFilters);
        const newBtn = listEl.querySelector('[data-route]');
        if (newBtn) newBtn.addEventListener('click', () => navigate(newBtn.dataset.route));
      }

      const next = {};
      if (state.status !== 'All') next.status = state.status;
      if (state.category !== 'All') next.category = state.category;
      replaceParams('#/complaints', next);
    }

    function clearFilters() {
      state.status = 'All';
      state.category = 'All';
      statusSel.value = 'All';
      categorySel.value = 'All';
      applyAndRender();
    }

    statusSel.addEventListener('change', () => {
      state.status = statusSel.value;
      applyAndRender();
    });
    categorySel.addEventListener('change', () => {
      state.category = categorySel.value;
      applyAndRender();
    });
    clearBtn.addEventListener('click', clearFilters);

    applyAndRender();
  }

  window.CM = window.CM || {};
  window.CM.views = window.CM.views || {};
  window.CM.views.complaints = renderComplaints;
})();
