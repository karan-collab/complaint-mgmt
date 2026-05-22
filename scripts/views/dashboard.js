(function () {
  const ui = window.CM.ui;

  function formatDate(iso) {
    if (!iso) return '';
    try {
      const d = new Date(iso);
      return d.toLocaleDateString(undefined, {
        day: '2-digit',
        month: 'short',
      });
    } catch {
      return '';
    }
  }

  const RECENT_CATEGORY_ICON = {
    Plumber: '<path d="M9 3v6a3 3 0 0 0 6 0V3"/><path d="M12 9v9"/><path d="M9 21h6"/>',
    Electrician: '<path d="M14 4 6 14h6l-2 6 8-10h-6z"/>',
    Carpenter: '<path d="M14 3h7v7"/><path d="M21 3 10 14"/><path d="M4 21l5-5"/><path d="M4 14l6 6"/>',
    Painting: '<path d="M19 7H5a2 2 0 0 0-2 2v2a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2z"/><path d="M12 13v3a3 3 0 0 0 0 6"/>',
    Others: '<circle cx="12" cy="12" r="10"/><path d="M12 8v4"/><path d="M12 16h.01"/>',
  };

  function badgeFor(display) {
    if (display === 'Completed') return 'badge-done';
    if (display === 'Pending Work') return 'badge-work';
    return 'badge-pending';
  }

  function renderRecentItem(c, getDisplayStatus) {
    const display = getDisplayStatus(c);
    const iconBody = RECENT_CATEGORY_ICON[c.category] || RECENT_CATEGORY_ICON.Others;
    return `
      <li class="recent-item">
        <span class="recent-icon" aria-hidden="true">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            ${iconBody}
          </svg>
        </span>
        <div class="recent-text">
          <p class="recent-title">${ui.escapeHtml(c.category)}</p>
          <p class="recent-sub">${ui.escapeHtml(c.description)}</p>
        </div>
        <div class="recent-meta">
          <span class="badge ${badgeFor(display)}">${ui.escapeHtml(display)}</span>
          <span class="recent-date">${formatDate(c.createdAt)}</span>
        </div>
      </li>
    `;
  }

  function renderShell(session, contentHtml) {
    return `
      <section class="dashboard">
        <div class="welcome-card">
          <p class="welcome-eyebrow">Welcome back</p>
          <h1 class="welcome-name">${ui.escapeHtml(session.name)}</h1>
          <p class="welcome-meta">
            <span class="flat-chip">Flat ${ui.escapeHtml(session.flat)}</span>
            <span class="welcome-tag">Here's a quick look at your complaints.</span>
          </p>
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
    const recent = complaints.slice(0, 4);

    const recentBlock = `
      <section class="recent-section" aria-label="Recent activity">
        <header class="recent-head">
          <h2>Recent Activity</h2>
          ${recent.length ? `<button type="button" class="recent-link" data-status="All">View all</button>` : ''}
        </header>
        ${
          recent.length
            ? `<ul class="recent-list">${recent.map((c) => renderRecentItem(c, storage.getDisplayStatus)).join('')}</ul>`
            : `<div class="recent-empty card">
                <div class="empty-icon" aria-hidden="true">
                  <svg viewBox="0 0 24 24" width="36" height="36" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                    <path d="M14 2v6h6"/>
                    <path d="M9 13h6M9 17h4"/>
                  </svg>
                </div>
                <h3>No complaints raised yet</h3>
                <p class="muted">When you raise an issue, it will show up here so you can track its status.</p>
                <button type="button" class="btn btn-primary" data-route="#/raise">Raise your first issue</button>
              </div>`
        }
      </section>
    `;

    return `
      <div class="stats-row" role="group" aria-label="Complaint statistics">
        <button type="button" class="stat-card metric-pending" data-status="Assignment Pending" aria-label="View ${counts['Assignment Pending']} tickets awaiting assignment">
          <span class="stat-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="12" cy="12" r="9"/>
              <path d="M12 7v5l3 2"/>
            </svg>
          </span>
          <span class="stat-value">${counts['Assignment Pending']}</span>
          <span class="stat-label">Assignment Pending</span>
        </button>
        <button type="button" class="stat-card metric-work" data-status="Pending Work" aria-label="View ${counts['Pending Work']} tickets being worked on">
          <span class="stat-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M14.7 6.3a4 4 0 1 0-5.7 5.7l-6 6V21h3.3l6-6a4 4 0 0 0 5.7-5.7"/>
              <path d="m13 11 6 6"/>
            </svg>
          </span>
          <span class="stat-value">${counts['Pending Work']}</span>
          <span class="stat-label">Pending Work</span>
        </button>
        <button type="button" class="stat-card metric-done" data-status="Completed" aria-label="View ${counts['Completed']} completed tickets">
          <span class="stat-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="12" cy="12" r="9"/>
              <path d="m8.5 12.5 2.5 2.5 4.5-5"/>
            </svg>
          </span>
          <span class="stat-value">${counts['Completed']}</span>
          <span class="stat-label">Completed</span>
        </button>
      </div>

      <div class="action-grid">
        <button type="button" class="action-card" data-route="#/raise">
          <span class="action-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="12" cy="12" r="9"/>
              <path d="M12 8v8M8 12h8"/>
            </svg>
          </span>
          <span class="action-text">
            <span class="action-title">Raise a New Issue</span>
            <span class="action-sub">Report a plumbing, electrical, or other concern.</span>
          </span>
          <span class="action-arrow" aria-hidden="true">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M5 12h14"/>
              <path d="m13 5 7 7-7 7"/>
            </svg>
          </span>
        </button>

        <button type="button" class="action-card" data-status="All">
          <span class="action-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <rect x="8" y="3" width="8" height="4" rx="1"/>
              <path d="M9 5H6a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2h-3"/>
              <path d="M9 13h6M9 17h4"/>
            </svg>
          </span>
          <span class="action-text">
            <span class="action-title">View Registered Complaints</span>
            <span class="action-sub">Track status of complaints you've already raised.</span>
          </span>
          <span class="action-arrow" aria-hidden="true">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M5 12h14"/>
              <path d="m13 5 7 7-7 7"/>
            </svg>
          </span>
        </button>
      </div>

      ${recentBlock}
    `;
  }

  function bindActions(root, navigate) {
    root.querySelectorAll('[data-route]').forEach((btn) => {
      btn.addEventListener('click', () => navigate(btn.dataset.route));
    });
    root.querySelectorAll('[data-status]').forEach((btn) => {
      btn.addEventListener('click', () => {
        const status = btn.dataset.status;
        const params = status && status !== 'All' ? { status } : null;
        navigate('#/complaints', params);
      });
    });
  }

  async function renderDashboard(root, ctx) {
    const { session, navigate } = ctx;
    const storage = window.CM.storage;

    root.innerHTML = renderShell(session, ui.loadingBlock('Loading your complaints\u2026'));

    try {
      const complaints = await storage.getComplaints(session.flat);
      root.innerHTML = renderShell(session, renderContent(complaints, storage));
      bindActions(root, navigate);
    } catch (err) {
      const msg = ui.messageFromError(err, 'Could not load complaints.');
      root.innerHTML = renderShell(session, ui.errorBlock(msg, 'Retry'));
      const retry = root.querySelector('[data-retry]');
      if (retry) retry.addEventListener('click', () => renderDashboard(root, ctx));
    }
  }

  window.CM = window.CM || {};
  window.CM.views = window.CM.views || {};
  window.CM.views.dashboard = renderDashboard;
})();
