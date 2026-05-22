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

  const STATUS_MODES = {
    pending: {
      key: 'Assignment Pending',
      title: 'Assignment Pending',
      sub: 'Tickets awaiting a worker assignment.',
      badge: 'badge-pending',
      modeClass: 'mode-pending',
      emptyCat: 'No tickets awaiting assignment.',
      emptyFlat: 'No pending tickets in this category.',
    },
    work: {
      key: 'Pending Work',
      title: 'Pending Work',
      sub: 'Tickets with a worker assigned and currently in progress.',
      badge: 'badge-work',
      modeClass: 'mode-work',
      emptyCat: 'No work currently in progress.',
      emptyFlat: 'No work in progress for this category.',
    },
    completed: {
      key: 'Completed',
      title: 'Completed',
      sub: 'Resolved tickets, archived for reference.',
      badge: 'badge-done',
      modeClass: 'mode-done',
      emptyCat: 'No completed tickets yet.',
      emptyFlat: 'No completed tickets in this category.',
    },
  };

  const CATEGORY_PATH = {
    Plumber: 'M9 3v6a3 3 0 0 0 6 0V3M12 9v9M9 21h6',
    Carpenter: 'M14 3h7v7M21 3 10 14M4 21l5-5M4 14l6 6',
    Electrician: 'M14 4 6 14h6l-2 6 8-10h-6z',
    Painting: 'M19 7H5a2 2 0 0 0-2 2v2a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2zM12 13v3a3 3 0 0 0 0 6',
    Others: 'M12 8v4 M12 16h.01 M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0z',
  };

  function categoryIconSvg(category) {
    const path = CATEGORY_PATH[category] || CATEGORY_PATH.Others;
    return `<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="${path}"/></svg>`;
  }

  function renderTicketRow(c, mode, getDisplayStatus) {
    const display = getDisplayStatus(c);
    return `
      <li class="ticket-row" data-ticket="${ui.escapeHtml(c.id)}" tabindex="0" role="button" aria-label="Open ticket details">
        <span class="ticket-cat">${categoryIconSvg(c.category)} <span>${ui.escapeHtml(c.category)}</span></span>
        <div class="ticket-body">
          <p class="ticket-title">Flat ${ui.escapeHtml(c.flat)} <span class="ticket-date">${formatDate(c.createdAt)}</span></p>
          <p class="ticket-desc">${ui.escapeHtml(c.description)}</p>
          ${
            c.worker
              ? `<p class="ticket-worker"><strong>${ui.escapeHtml(c.worker.name)}</strong> \u00b7 ${ui.escapeHtml(c.worker.phone)}</p>`
              : ''
          }
        </div>
        <div class="ticket-end">
          <span class="badge ${mode.badge}">${ui.escapeHtml(display)}</span>
          <span class="ticket-open" aria-hidden="true">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M5 12h14"/>
              <path d="m13 5 7 7-7 7"/>
            </svg>
          </span>
        </div>
      </li>
    `;
  }

  function renderAssignForm(complaint) {
    return `
      <form class="modal-form" id="assignForm" novalidate>
        <h3>Assign a worker</h3>
        <p class="muted">Pick a registered professional in the <strong>${ui.escapeHtml(complaint.category)}</strong> category. The resident will see their details once assigned.</p>
        <div class="field">
          <span class="field-label">Professional</span>
          <select name="professionalId" id="professionalSelect" required>
            <option value="" disabled selected>Loading\u2026</option>
          </select>
          <p class="muted assign-pro-meta" id="assignProMeta" hidden></p>
        </div>
        <p class="login-error" id="assignError" hidden></p>
        <div class="modal-actions">
          <button type="button" class="btn btn-ghost" data-close>Cancel</button>
          <button type="submit" class="btn btn-primary" id="assignSubmit" disabled>
            <span class="btn-label">Assign Worker</span>
          </button>
        </div>
      </form>
    `;
  }

  function renderModal(complaint, storage) {
    if (!complaint) return '';
    const display = storage.getDisplayStatus(complaint);
    const isPending = display === 'Assignment Pending';
    const isWork = display === 'Pending Work';
    const isDone = display === 'Completed';

    const badgeClass = isPending ? 'badge-pending' : isWork ? 'badge-work' : 'badge-done';

    let actionsBlock = '';
    if (isPending) {
      actionsBlock = renderAssignForm(complaint);
    } else if (isWork) {
      actionsBlock = `
        <div class="modal-form">
          <h3>Worker assigned</h3>
          <div class="worker-block">
            <div class="worker-row">
              <span class="worker-avatar" aria-hidden="true">${ui.escapeHtml(complaint.worker.name.charAt(0).toUpperCase())}</span>
              <div class="worker-info">
                <p class="worker-name">${ui.escapeHtml(complaint.worker.name)}</p>
                <p class="worker-prof">${ui.escapeHtml(complaint.worker.profession || complaint.category)}</p>
              </div>
              <a class="worker-phone" href="tel:${ui.escapeHtml(complaint.worker.phone.replace(/\s/g, ''))}">
                <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                  <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z"/>
                </svg>
                <span>${ui.escapeHtml(complaint.worker.phone)}</span>
              </a>
            </div>
            ${complaint.assignedAt ? `<p class="muted" style="margin-top:8px;font-size:.8rem;">Assigned on ${formatDate(complaint.assignedAt)}</p>` : ''}
          </div>
          <div class="modal-actions">
            <button type="button" class="btn btn-ghost" data-close>Close</button>
            <button type="button" class="btn btn-primary" id="completeBtn">
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <path d="M20 6 9 17l-5-5"/>
              </svg>
              <span class="btn-label">Mark as Complete</span>
            </button>
          </div>
        </div>
      `;
    } else if (isDone) {
      actionsBlock = `
        <div class="modal-form">
          <h3>Ticket resolved</h3>
          <div class="worker-block">
            ${
              complaint.worker
                ? `<div class="worker-row">
                    <span class="worker-avatar" aria-hidden="true">${ui.escapeHtml(complaint.worker.name.charAt(0).toUpperCase())}</span>
                    <div class="worker-info">
                      <p class="worker-name">${ui.escapeHtml(complaint.worker.name)}</p>
                      <p class="worker-prof">${ui.escapeHtml(complaint.worker.profession || complaint.category)} \u00b7 ${ui.escapeHtml(complaint.worker.phone)}</p>
                    </div>
                  </div>`
                : `<p class="muted">Resolved without a recorded worker.</p>`
            }
            ${complaint.completedAt ? `<p class="muted" style="margin-top:8px;font-size:.8rem;">Completed on ${formatDate(complaint.completedAt)}</p>` : ''}
          </div>
          <div class="modal-actions">
            <button type="button" class="btn btn-primary" data-close>Close</button>
          </div>
        </div>
      `;
    }

    return `
      <div class="modal-backdrop" id="modalBackdrop" role="dialog" aria-modal="true" aria-labelledby="modalTitle">
        <div class="modal modal-wide">
          <header class="modal-head">
            <div class="modal-head-text">
              <p class="modal-eyebrow">Ticket \u00b7 Flat ${ui.escapeHtml(complaint.flat)}</p>
              <h2 id="modalTitle">${ui.escapeHtml(complaint.category)} issue</h2>
              <div class="modal-status-row">
                <span class="badge ${badgeClass}">${ui.escapeHtml(display)}</span>
                <span class="muted">Raised on ${formatDate(complaint.createdAt)}</span>
              </div>
            </div>
            <button type="button" class="modal-close" data-close aria-label="Close">
              <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <path d="M18 6 6 18M6 6l12 12"/>
              </svg>
            </button>
          </header>
          <div class="modal-body">
            <section class="modal-section">
              <p class="modal-section-label">Description</p>
              <p class="modal-desc">${ui.escapeHtml(complaint.description)}</p>
            </section>
          </div>
          <footer class="modal-foot">${actionsBlock}</footer>
        </div>
      </div>
    `;
  }

  function renderCategoryGrid(complaints, mode) {
    const byCategory = {};
    complaints.forEach((c) => {
      byCategory[c.category] = byCategory[c.category] || [];
      byCategory[c.category].push(c);
    });

    const keys = Object.keys(byCategory).sort();
    if (!keys.length) {
      return `
        <div class="empty-state card">
          <h3>${ui.escapeHtml(mode.emptyCat)}</h3>
          <p class="muted">Tickets will appear here once they reach this stage.</p>
        </div>
      `;
    }

    return `
      <div class="category-grid">
        ${keys
          .map(
            (cat) => `
          <button type="button" class="category-card ${mode.modeClass}" data-category="${ui.escapeHtml(cat)}">
            <span class="category-icon" aria-hidden="true">${categoryIconSvg(cat)}</span>
            <span class="category-text">
              <span class="category-title">${ui.escapeHtml(cat)}</span>
              <span class="category-sub">${byCategory[cat].length} ${byCategory[cat].length === 1 ? 'ticket' : 'tickets'}</span>
            </span>
            <span class="action-arrow" aria-hidden="true">
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M5 12h14"/>
                <path d="m13 5 7 7-7 7"/>
              </svg>
            </span>
          </button>
        `
          )
          .join('')}
      </div>
    `;
  }

  function renderFlatGroups(complaints, mode, storage) {
    const byFlat = {};
    complaints.forEach((c) => {
      byFlat[c.flat] = byFlat[c.flat] || [];
      byFlat[c.flat].push(c);
    });
    const flats = Object.keys(byFlat).sort();
    if (!flats.length) {
      return `<div class="empty-state card"><h3>${ui.escapeHtml(mode.emptyFlat)}</h3><p class="muted">Try another category.</p></div>`;
    }
    return flats
      .map(
        (flat) => `
      <div class="flat-section">
        <header class="flat-head">
          <h3>Flat ${ui.escapeHtml(flat)}</h3>
          <span class="flat-count">${byFlat[flat].length} ${byFlat[flat].length === 1 ? 'ticket' : 'tickets'}</span>
        </header>
        <ul class="ticket-list">
          ${byFlat[flat].map((c) => renderTicketRow(c, mode, storage.getDisplayStatus)).join('')}
        </ul>
      </div>
    `
      )
      .join('');
  }

  function renderShell(mode, statusKey, crumbs, body, modalHtml) {
    return `
      <section class="page admin-page">
        <button type="button" class="back-link" data-back>
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="m15 18-6-6 6-6"/>
          </svg>
          <span>Back to dashboard</span>
        </button>

        <div class="page-title">
          <h1>${ui.escapeHtml(mode.title)}</h1>
          <p class="muted">${ui.escapeHtml(mode.sub)}</p>
        </div>

        <nav class="admin-tabs" role="tablist" aria-label="Ticket status">
          <button type="button" class="admin-tab metric-pending ${statusKey === 'pending' ? 'is-active' : ''}" data-tab="pending">Assignment Pending</button>
          <button type="button" class="admin-tab metric-work ${statusKey === 'work' ? 'is-active' : ''}" data-tab="work">Pending Work</button>
          <button type="button" class="admin-tab metric-done ${statusKey === 'completed' ? 'is-active' : ''}" data-tab="completed">Completed</button>
        </nav>

        <div class="crumbs">${crumbs}</div>

        <div class="admin-content">${body}</div>
      </section>
      ${modalHtml}
    `;
  }

  async function renderAdminTickets(root, ctx) {
    const { navigate, params } = ctx;
    const storage = window.CM.storage;
    const showToast = window.CM.showToast;

    const statusKey = ['pending', 'work', 'completed'].includes(params.status) ? params.status : 'pending';
    const mode = STATUS_MODES[statusKey];
    const category = params.category || '';
    const openTicketId = params.ticket || '';

    // initial loading shell
    root.innerHTML = renderShell(mode, statusKey, '', ui.loadingBlock('Loading tickets\u2026'), '');
    root.querySelector('[data-back]').addEventListener('click', () => navigate('#/admin/dashboard'));
    root.querySelectorAll('[data-tab]').forEach((btn) => {
      btn.addEventListener('click', () => navigate('#/admin/tickets', { status: btn.dataset.tab }));
    });

    let allComplaints;
    try {
      allComplaints = await storage.getAllComplaints();
    } catch (err) {
      const msg = ui.messageFromError(err, 'Could not load tickets.');
      root.innerHTML = renderShell(mode, statusKey, '', ui.errorBlock(msg, 'Retry'), '');
      root.querySelector('[data-back]').addEventListener('click', () => navigate('#/admin/dashboard'));
      root.querySelectorAll('[data-tab]').forEach((btn) => {
        btn.addEventListener('click', () => navigate('#/admin/tickets', { status: btn.dataset.tab }));
      });
      const retry = root.querySelector('[data-retry]');
      if (retry) retry.addEventListener('click', () => renderAdminTickets(root, ctx));
      return;
    }

    const matching = allComplaints.filter((c) => storage.getDisplayStatus(c) === mode.key);

    let body;
    let crumbs;
    if (!category) {
      crumbs = `<span class="crumb crumb-current">${ui.escapeHtml(mode.title)}</span>`;
      body = renderCategoryGrid(matching, mode);
    } else {
      crumbs = `
        <button type="button" class="crumb" data-go-status>${ui.escapeHtml(mode.title)}</button>
        <span class="crumb-sep" aria-hidden="true">/</span>
        <span class="crumb crumb-current">${ui.escapeHtml(category)}</span>
      `;
      const filtered = matching.filter((c) => c.category === category);
      body = renderFlatGroups(filtered, mode, storage);
    }

    const openComplaint = openTicketId
      ? allComplaints.find((c) => String(c.id) === String(openTicketId)) || null
      : null;
    const modalHtml = openComplaint ? renderModal(openComplaint, storage) : '';

    root.innerHTML = renderShell(mode, statusKey, crumbs, body, modalHtml);

    root.querySelector('[data-back]').addEventListener('click', () => navigate('#/admin/dashboard'));

    root.querySelectorAll('[data-tab]').forEach((btn) => {
      btn.addEventListener('click', () => navigate('#/admin/tickets', { status: btn.dataset.tab }));
    });

    const goStatus = root.querySelector('[data-go-status]');
    if (goStatus) goStatus.addEventListener('click', () => navigate('#/admin/tickets', { status: statusKey }));

    root.querySelectorAll('.category-card').forEach((btn) => {
      btn.addEventListener('click', () => {
        navigate('#/admin/tickets', { status: statusKey, category: btn.dataset.category });
      });
    });

    function openTicket(id) {
      const target = { status: statusKey, ticket: id };
      if (category) target.category = category;
      navigate('#/admin/tickets', target);
    }

    function closeTicket() {
      const target = { status: statusKey };
      if (category) target.category = category;
      navigate('#/admin/tickets', target);
    }

    root.querySelectorAll('.ticket-row').forEach((row) => {
      row.addEventListener('click', () => openTicket(row.dataset.ticket));
      row.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          openTicket(row.dataset.ticket);
        }
      });
    });

    if (openComplaint) {
      const backdrop = root.querySelector('#modalBackdrop');

      const onKey = (e) => {
        if (e.key === 'Escape') {
          document.removeEventListener('keydown', onKey);
          closeTicket();
        }
      };
      document.addEventListener('keydown', onKey);

      backdrop.addEventListener('click', (e) => {
        if (e.target === backdrop) closeTicket();
      });

      backdrop.querySelectorAll('[data-close]').forEach((btn) =>
        btn.addEventListener('click', () => closeTicket())
      );

      const assignForm = backdrop.querySelector('#assignForm');
      if (assignForm) {
        await wireAssignForm(assignForm, openComplaint, storage, showToast, closeTicket);
      }

      const completeBtn = backdrop.querySelector('#completeBtn');
      if (completeBtn) {
        completeBtn.addEventListener('click', async () => {
          completeBtn.disabled = true;
          completeBtn.querySelector('.btn-label').textContent = 'Marking\u2026';
          try {
            await storage.markComplete(openComplaint.id);
            showToast('Ticket marked as complete');
            closeTicket();
          } catch (err) {
            completeBtn.disabled = false;
            completeBtn.querySelector('.btn-label').textContent = 'Mark as Complete';
            showToast(ui.messageFromError(err, 'Could not mark as complete'), 'error');
          }
        });
      }

      document.body.classList.add('modal-open');
    } else {
      document.body.classList.remove('modal-open');
    }
  }

  async function wireAssignForm(form, complaint, storage, showToast, closeTicket) {
    const select = form.querySelector('#professionalSelect');
    const meta = form.querySelector('#assignProMeta');
    const submit = form.querySelector('#assignSubmit');
    const errorEl = form.querySelector('#assignError');

    function showError(msg) {
      if (msg) {
        errorEl.textContent = msg;
        errorEl.hidden = false;
      } else {
        errorEl.textContent = '';
        errorEl.hidden = true;
      }
    }

    let pros = [];
    try {
      pros = await storage.listProfessionals(complaint.category);
    } catch (err) {
      select.innerHTML = `<option value="" disabled selected>Failed to load</option>`;
      showError(ui.messageFromError(err, 'Could not load professionals'));
      return;
    }

    if (!pros.length) {
      select.innerHTML = `<option value="" disabled selected>No ${complaint.category} professionals available</option>`;
      showError(`No ${complaint.category} professionals are registered yet. Add one via the API or Swagger before assigning.`);
      return;
    }

    select.innerHTML = `<option value="" disabled selected>Select a ${complaint.category}\u2026</option>` +
      pros.map((p) => `<option value="${p.id}">${ui.escapeHtml(p.name)} \u00b7 ${ui.escapeHtml(p.phone)}</option>`).join('');

    function updateMeta() {
      const id = select.value;
      const chosen = pros.find((p) => String(p.id) === String(id));
      if (chosen) {
        meta.textContent = `${chosen.name} \u2014 ${chosen.phone}`;
        meta.hidden = false;
        submit.disabled = false;
      } else {
        meta.hidden = true;
        submit.disabled = true;
      }
    }

    select.addEventListener('change', updateMeta);

    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      showError('');
      const id = select.value;
      if (!id) {
        showError('Please pick a professional');
        return;
      }
      submit.disabled = true;
      submit.querySelector('.btn-label').textContent = 'Assigning\u2026';
      try {
        const updated = await storage.assignWorker(complaint.id, { professionalId: Number(id) });
        showToast(`Assigned to ${(updated.worker && updated.worker.name) || 'professional'}`);
        closeTicket();
      } catch (err) {
        submit.disabled = false;
        submit.querySelector('.btn-label').textContent = 'Assign Worker';
        showError(ui.messageFromError(err, 'Could not assign worker'));
      }
    });
  }

  window.CM = window.CM || {};
  window.CM.views = window.CM.views || {};
  window.CM.views.adminTickets = renderAdminTickets;
})();
