(function () {
  const ui = window.CM.ui;

  function formatDate(iso) {
    if (!iso) return '';
    try {
      const d = new Date(iso);
      return d.toLocaleDateString(undefined, { day: '2-digit', month: 'short' });
    } catch {
      return '';
    }
  }

  function badgeFor(displayStatus) {
    if (displayStatus === 'Completed') return 'badge-done';
    if (displayStatus === 'Pending Work') return 'badge-work';
    return 'badge-pending';
  }

  function renderRecentItem(c, getDisplayStatus) {
    const display = getDisplayStatus(c);
    return `
      <li class="recent-item">
        <span class="recent-icon" aria-hidden="true">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="12" r="10"/>
            <path d="M12 8v4"/>
            <path d="M12 16h.01"/>
          </svg>
        </span>
        <div class="recent-text">
          <p class="recent-title">${ui.escapeHtml(c.category)} <span class="recent-flat">Flat ${ui.escapeHtml(c.flat)}</span></p>
          <p class="recent-sub">${ui.escapeHtml(c.description)}</p>
        </div>
        <div class="recent-meta">
          <span class="badge ${badgeFor(display)}">${ui.escapeHtml(display)}</span>
          <span class="recent-date">${formatDate(c.createdAt)}</span>
        </div>
      </li>
    `;
  }

  function renderShell(contentHtml) {
    return `
      <section class="dashboard">
        <div class="welcome-card admin-hero">
          <p class="welcome-eyebrow">Management Console</p>
          <h1 class="welcome-name">Complaint Overview</h1>
          <p class="welcome-meta" id="adminMeta">
            <span class="welcome-tag">Loading\u2026</span>
          </p>
        </div>

        <div class="action-grid admin-shortcut-grid">
          <button type="button" class="action-card" data-nav="#/admin/residents">
            <span class="action-icon" aria-hidden="true">
              <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/>
                <circle cx="9" cy="7" r="4"/>
                <path d="M22 21v-2a4 4 0 0 0-3-3.87"/>
                <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
              </svg>
            </span>
            <span class="action-text">
              <span class="action-title">Manage Residents</span>
              <span class="action-sub">Add a new resident or reset a password.</span>
            </span>
            <span class="action-arrow" aria-hidden="true">
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M5 12h14"/>
                <path d="m13 5 7 7-7 7"/>
              </svg>
            </span>
          </button>
        </div>

        ${contentHtml}
      </section>
    `;
  }

  function renderContent(complaints, storage) {
    const counts = { 'Assignment Pending': 0, 'Pending Work': 0, 'Completed': 0 };
    complaints.forEach((c) => {
      counts[storage.getDisplayStatus(c)]++;
    });

    const recent = complaints.slice(0, 5);

    return `
      <div class="stats-row" role="group" aria-label="Ticket statistics">
        <button type="button" class="stat-card metric-pending" data-status="pending" aria-label="View ${counts['Assignment Pending']} tickets awaiting assignment">
          <span class="stat-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="12" cy="12" r="9"/>
              <path d="M12 7v5l3 2"/>
            </svg>
          </span>
          <span class="stat-value">${counts['Assignment Pending']}</span>
          <span class="stat-label">Assignment Pending</span>
          <span class="stat-cta">Drill down by category \u2192</span>
        </button>

        <button type="button" class="stat-card metric-work" data-status="work" aria-label="View ${counts['Pending Work']} tickets currently being worked on">
          <span class="stat-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M14.7 6.3a4 4 0 1 0-5.7 5.7l-6 6V21h3.3l6-6a4 4 0 0 0 5.7-5.7"/>
              <path d="m13 11 6 6"/>
            </svg>
          </span>
          <span class="stat-value">${counts['Pending Work']}</span>
          <span class="stat-label">Pending Work</span>
          <span class="stat-cta">View assigned tickets \u2192</span>
        </button>

        <button type="button" class="stat-card metric-done" data-status="completed" aria-label="View ${counts['Completed']} completed tickets">
          <span class="stat-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="12" cy="12" r="9"/>
              <path d="m8.5 12.5 2.5 2.5 4.5-5"/>
            </svg>
          </span>
          <span class="stat-value">${counts['Completed']}</span>
          <span class="stat-label">Completed</span>
          <span class="stat-cta">View resolved tickets \u2192</span>
        </button>
      </div>

      <section class="recent-section" aria-label="Recent tickets">
        <header class="recent-head">
          <h2>Recent Tickets</h2>
          ${recent.length ? `<button type="button" class="recent-link" data-status="all">View all</button>` : ''}
        </header>
        ${
          recent.length
            ? `<ul class="recent-list">${recent.map((c) => renderRecentItem(c, storage.getDisplayStatus)).join('')}</ul>`
            : `<div class="recent-empty card">
                <h3>No tickets yet</h3>
                <p class="muted">When residents raise issues they will appear here for assignment.</p>
              </div>`
        }
      </section>
    `;
  }

  function bindActions(root, navigate, total, flats) {
    const metaEl = root.querySelector('#adminMeta');
    if (metaEl) {
      metaEl.innerHTML = `
        <span class="flat-chip">${total} tickets</span>
        <span class="flat-chip">${flats} flats</span>
        <span class="welcome-tag">Click a card to drill into tickets.</span>
      `;
    }

    root.querySelectorAll('[data-nav]').forEach((btn) => {
      btn.addEventListener('click', () => navigate(btn.dataset.nav));
    });

    root.querySelectorAll('[data-status]').forEach((btn) => {
      btn.addEventListener('click', () => {
        const status = btn.dataset.status;
        if (status === 'all') {
          navigate('#/admin/tickets', { status: 'pending' });
        } else {
          navigate('#/admin/tickets', { status });
        }
      });
    });
  }

  async function renderAdminDashboard(root, ctx) {
    const { navigate } = ctx;
    const storage = window.CM.storage;

    root.innerHTML = renderShell(ui.loadingBlock('Loading tickets\u2026'));
    root.querySelectorAll('[data-nav]').forEach((btn) => {
      btn.addEventListener('click', () => navigate(btn.dataset.nav));
    });

    try {
      const complaints = await storage.getAllComplaints();
      root.innerHTML = renderShell(renderContent(complaints, storage));
      const flatsTouched = new Set(complaints.map((c) => c.flat)).size;
      bindActions(root, navigate, complaints.length, flatsTouched);
    } catch (err) {
      const msg = ui.messageFromError(err, 'Could not load tickets.');
      root.innerHTML = renderShell(ui.errorBlock(msg, 'Retry'));
      root.querySelectorAll('[data-nav]').forEach((btn) => {
        btn.addEventListener('click', () => navigate(btn.dataset.nav));
      });
      const retry = root.querySelector('[data-retry]');
      if (retry) retry.addEventListener('click', () => renderAdminDashboard(root, ctx));
    }
  }

  window.CM = window.CM || {};
  window.CM.views = window.CM.views || {};
  window.CM.views.adminDashboard = renderAdminDashboard;
})();
