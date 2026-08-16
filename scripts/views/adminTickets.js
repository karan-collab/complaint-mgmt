(function () {
  const ui = window.CM.ui;

  /** `Date` -> the `YYYY-MM-DD` string an <input type="date"> expects. */
  function toDateValue(date) {
    const m = String(date.getMonth() + 1).padStart(2, '0');
    const d = String(date.getDate()).padStart(2, '0');
    return `${date.getFullYear()}-${m}-${d}`;
  }

  function defaultFromDate() {
    const now = new Date();
    return toDateValue(new Date(now.getFullYear(), now.getMonth(), 1));
  }

  function defaultToDate() {
    return toDateValue(new Date());
  }

  function isDateValue(value) {
    return typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value);
  }

  /**
   * True when `iso` falls on or between `from` and `to`. Compared as local
   * calendar days so the result matches the date shown on the ticket.
   */
  function withinDateRange(iso, from, to) {
    if (!iso) return false;
    const day = toDateValue(new Date(iso));
    return day >= from && day <= to;
  }

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
      empty: 'No tickets awaiting assignment.',
    },
    work: {
      key: 'Pending Work',
      title: 'Pending Work',
      sub: 'Tickets with a worker assigned and currently in progress.',
      badge: 'badge-work',
      empty: 'No work currently in progress.',
    },
    completed: {
      key: 'Completed',
      title: 'Completed',
      sub: 'Resolved tickets, archived for reference.',
      badge: 'badge-done',
      empty: 'No completed tickets yet.',
    },
    deleted: {
      key: 'Deleted',
      title: 'Deleted',
      sub: 'Complaints withdrawn by residents, kept for the record.',
      badge: 'badge-deleted',
      empty: 'No complaints have been withdrawn.',
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

  /** Whole days between `iso` and now. */
  function daysSince(iso) {
    if (!iso) return null;
    const ms = Date.now() - new Date(iso).getTime();
    if (Number.isNaN(ms)) return null;
    return Math.max(0, Math.floor(ms / 86400000));
  }

  /**
   * The time detail that matters for the row's state: how long an open ticket
   * has been waiting (flagged stale past a week, so a month-old request doesn't
   * look like one raised this morning), or when a finished one was completed —
   * the latter being what the Completed tab filters on.
   */
  function renderAge(complaint, display) {
    if (display === 'Deleted') {
      return complaint.deletedAt
        ? `<span class="ticket-age is-deleted">Deleted ${formatDate(complaint.deletedAt)}</span>`
        : '';
    }
    if (display === 'Completed') {
      return complaint.completedAt
        ? `<span class="ticket-age is-done">Completed ${formatDate(complaint.completedAt)}</span>`
        : '';
    }
    const days = daysSince(complaint.createdAt);
    if (days === null) return '';
    const label = days === 0 ? 'today' : days === 1 ? '1 day old' : `${days} days old`;
    const stale = days > 7 ? ' is-stale' : '';
    return `<span class="ticket-age${stale}">${label}</span>`;
  }

  function renderTicketRow(c, mode, getDisplayStatus) {
    const display = getDisplayStatus(c);
    return `
      <li class="ticket-row" data-ticket="${ui.escapeHtml(c.id)}" tabindex="0" role="button" aria-label="Open ticket details">
        <span class="ticket-cat">${categoryIconSvg(c.category)} <span>${ui.escapeHtml(c.category)}</span></span>
        <div class="ticket-body">
          <p class="ticket-title">Flat ${ui.escapeHtml(c.flat)} <span class="ticket-date">${display === 'Completed' || display === 'Deleted' ? 'Raised ' : ''}${formatDate(c.createdAt)}</span> ${renderAge(c, display)}</p>
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

  function renderAssignForm(complaint, isReassign) {
    return `
      <form class="modal-form" id="assignForm" novalidate>
        <h3>${isReassign ? 'Reassign to a different worker' : 'Assign a worker'}</h3>
        <p class="muted">${
          isReassign
            ? `Currently with <strong>${ui.escapeHtml((complaint.worker && complaint.worker.name) || 'someone')}</strong>. Pick another registered <strong>${ui.escapeHtml(complaint.category)}</strong> — the resident will immediately see the new details.`
            : `Pick a registered professional in the <strong>${ui.escapeHtml(complaint.category)}</strong> category. The resident will see their details once assigned.`
        }</p>
        <div class="field">
          <span class="field-label">Professional</span>
          <select name="professionalId" id="professionalSelect" required>
            <option value="" disabled selected>Loading\u2026</option>
          </select>
          <p class="muted assign-pro-meta" id="assignProMeta" hidden></p>
        </div>
        <p class="login-error" id="assignError" hidden></p>
        <div class="modal-actions">
          <button type="button" class="btn btn-ghost" ${isReassign ? 'data-cancel-action' : 'data-close'}>Cancel</button>
          <button type="submit" class="btn btn-primary" id="assignSubmit" disabled>
            <span class="btn-label">${isReassign ? 'Reassign Worker' : 'Assign Worker'}</span>
          </button>
        </div>
      </form>
    `;
  }

  function renderUnassignConfirm(complaint) {
    const worker = (complaint.worker && complaint.worker.name) || 'the assigned worker';
    return `
      <div class="modal-form">
        <h3>Remove the assigned worker?</h3>
        <p class="muted">
          <strong>${ui.escapeHtml(worker)}</strong> will be taken off this ticket and it goes back to
          <strong>Assignment Pending</strong>. The resident stops seeing their contact details.
          Nothing else about the complaint changes, and you can assign someone else at any time.
        </p>
        <p class="login-error" id="unassignError" hidden></p>
        <div class="modal-actions">
          <button type="button" class="btn btn-ghost" data-cancel-action>No, keep them</button>
          <button type="button" class="btn btn-danger" id="unassignConfirmBtn">
            <span class="btn-label">Yes, remove assignee</span>
          </button>
        </div>
      </div>
    `;
  }

  function renderReopenConfirm(complaint) {
    const worker = complaint.worker ? complaint.worker.name : null;
    return `
      <div class="modal-form">
        <h3>Reopen this ticket?</h3>
        <p class="muted">
          It goes back to <strong>Assignment Pending</strong> so a worker can be assigned again —
          use this when the job was not actually fixed.
          ${
            worker
              ? `The completion record and the link to <strong>${ui.escapeHtml(worker)}</strong> are cleared, so the ticket starts over.`
              : 'The completion record is cleared, so the ticket starts over.'
          }
        </p>
        <p class="login-error" id="reopenError" hidden></p>
        <div class="modal-actions">
          <button type="button" class="btn btn-ghost" data-cancel-action>Cancel</button>
          <button type="button" class="btn btn-primary" id="reopenConfirmBtn">
            <span class="btn-label">Yes, reopen ticket</span>
          </button>
        </div>
      </div>
    `;
  }

  function renderModal(complaint, storage, action) {
    if (!complaint) return '';
    const display = storage.getDisplayStatus(complaint);
    const isPending = display === 'Assignment Pending';
    const isWork = display === 'Pending Work';
    const isDone = display === 'Completed';
    const isDeleted = display === 'Deleted';

    const badgeClass = isDeleted
      ? 'badge-deleted'
      : isPending ? 'badge-pending' : isWork ? 'badge-work' : 'badge-done';

    let actionsBlock = '';
    if (isDeleted) {
      actionsBlock = `
        <div class="modal-form">
          <h3>Withdrawn by the resident</h3>
          <div class="worker-block">
            <p class="modal-section-label">Reason</p>
            <p class="modal-desc">${ui.escapeHtml(complaint.deletionReasonLabel || 'Not recorded')}</p>
            ${
              complaint.deletionComments
                ? `<p class="modal-section-label" style="margin-top:12px;">Their comments</p>
                   <p class="modal-desc">${ui.escapeHtml(complaint.deletionComments)}</p>`
                : ''
            }
            ${
              complaint.deletedAt
                ? `<p class="muted" style="margin-top:8px;font-size:.8rem;">Deleted on ${formatDate(complaint.deletedAt)}</p>`
                : ''
            }
          </div>
          <div class="modal-actions">
            <button type="button" class="btn btn-primary" data-close>Close</button>
          </div>
        </div>
      `;
    } else if (isPending) {
      actionsBlock = renderAssignForm(complaint, false);
    } else if (isWork && action === 'reassign') {
      actionsBlock = renderAssignForm(complaint, true);
    } else if (isWork && action === 'unassign') {
      actionsBlock = renderUnassignConfirm(complaint);
    } else if (isDone && action === 'reopen') {
      actionsBlock = renderReopenConfirm(complaint);
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
          <div class="modal-actions modal-actions-split">
            <button type="button" class="btn btn-ghost btn-danger-ghost" data-action="unassign">
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/>
                <circle cx="9" cy="7" r="4"/>
                <path d="M17 11h6"/>
              </svg>
              <span>Remove assignee</span>
            </button>
            <span class="modal-actions-spacer"></span>
            <button type="button" class="btn btn-ghost" data-action="reassign">
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <path d="M17 2.1 21 6l-4 3.9"/>
                <path d="M3 12v-1a4 4 0 0 1 4-4h14"/>
                <path d="M7 21.9 3 18l4-3.9"/>
                <path d="M21 12v1a4 4 0 0 1-4 4H3"/>
              </svg>
              <span>Reassign</span>
            </button>
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
          <div class="modal-actions modal-actions-split">
            <button type="button" class="btn btn-ghost" data-action="reopen">
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <path d="M3 2v6h6"/>
                <path d="M3.5 12a8.5 8.5 0 1 1 2.5 6"/>
              </svg>
              <span>Reopen ticket</span>
            </button>
            <span class="modal-actions-spacer"></span>
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
              <p class="modal-section-label">Raised by</p>
              <div class="raiser-row">
                <p class="raiser-name">${ui.escapeHtml(complaint.residentName || `Flat ${complaint.flat}`)}</p>
                ${
                  complaint.residentPhone
                    ? `<a class="worker-phone" href="tel:${ui.escapeHtml(complaint.residentPhone.replace(/\s/g, ''))}">
                        <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                          <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z"/>
                        </svg>
                        <span>${ui.escapeHtml(complaint.residentPhone)}</span>
                      </a>`
                    : `<span class="raiser-nophone">No phone on record</span>`
                }
              </div>
            </section>
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

  const GROUP_OPTIONS = [
    { value: 'none', label: 'No grouping' },
    { value: 'flat', label: 'Flat' },
    { value: 'category', label: 'Category' },
    { value: 'professional', label: 'Professional' },
  ];

  /** Plural noun for the meta line, e.g. "6 tickets in 4 flats". */
  const GROUP_UNITS = {
    flat: 'flats',
    category: 'categories',
    professional: 'professionals',
  };

  /** Group options that make sense for a tab. */
  function groupOptionsFor(statusKey) {
    // Assignment Pending tickets have no professional yet, so grouping by one
    // would put every row in a single "Unassigned" bucket.
    return statusKey === 'pending'
      ? GROUP_OPTIONS.filter((o) => o.value !== 'professional')
      : GROUP_OPTIONS;
  }

  function groupKeyFor(complaint, groupBy) {
    if (groupBy === 'flat') return `Flat ${complaint.flat}`;
    if (groupBy === 'category') return complaint.category;
    if (groupBy === 'professional') {
      return complaint.worker ? complaint.worker.name : 'Unassigned';
    }
    return '';
  }

  /**
   * Renders the tickets, optionally under collapsible group headings. Each
   * group holds every ticket with that key, so a flat or category appears
   * exactly once no matter how many tickets it has.
   */
  function renderTicketList(complaints, mode, storage, groupBy, expanded) {
    if (!complaints.length) return '';

    if (groupBy === 'none') {
      return `<ul class="ticket-list ticket-list-flush">${complaints
        .map((c) => renderTicketRow(c, mode, storage.getDisplayStatus))
        .join('')}</ul>`;
    }

    const groups = new Map();
    complaints.forEach((c) => {
      const key = groupKeyFor(c, groupBy);
      if (!groups.has(key)) groups.set(key, []);
      groups.get(key).push(c);
    });

    const keys = [...groups.keys()].sort((a, b) =>
      a.localeCompare(b, undefined, { numeric: true, sensitivity: 'base' })
    );

    return keys
      .map((key) => {
        const items = groups.get(key);
        const isOpen = expanded.has(key);
        return `
      <details class="group-section" data-group="${ui.escapeHtml(key)}"${isOpen ? ' open' : ''}>
        <summary class="group-head">
          <span class="group-chevron" aria-hidden="true">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="m9 18 6-6-6-6"/>
            </svg>
          </span>
          <h3>${ui.escapeHtml(key)}</h3>
          <span class="group-count">${items.length} ${items.length === 1 ? 'ticket' : 'tickets'}</span>
        </summary>
        <ul class="ticket-list">
          ${items.map((c) => renderTicketRow(c, mode, storage.getDisplayStatus)).join('')}
        </ul>
      </details>
    `;
      })
      .join('');
  }

  function renderEmptyState(mode, filtered) {
    if (filtered) {
      return `
        <div class="empty-state card">
          <h3>No matching tickets</h3>
          <p class="muted">Nothing here matches the current filters.</p>
          <button type="button" class="btn btn-primary" data-action="clear-filters">Clear filters</button>
        </div>
      `;
    }
    return `
      <div class="empty-state card">
        <h3>${ui.escapeHtml(mode.empty)}</h3>
        <p class="muted">Tickets will appear here once they reach this stage.</p>
      </div>
    `;
  }

  function optionList(options, selected) {
    return options
      .map(
        (o) =>
          `<option value="${ui.escapeHtml(o.value)}"${o.value === selected ? ' selected' : ''}>${ui.escapeHtml(o.label)}</option>`
      )
      .join('');
  }

  function renderControls(statusKey, state, categories) {
    const categoryOptions = [{ value: '', label: 'All categories' }].concat(
      categories.map((c) => ({ value: c, label: c }))
    );
    const sortOptions = [
      { value: 'newest', label: 'Newest first' },
      { value: 'oldest', label: 'Oldest first' },
    ];

    return `
      <div class="card controls-bar" role="group" aria-label="Filter and group tickets">
        <label class="filter-field">
          <span>Group by</span>
          <select id="groupBy">${optionList(groupOptionsFor(statusKey), state.group)}</select>
        </label>
        <label class="filter-field">
          <span>Category</span>
          <select id="categoryFilter">${optionList(categoryOptions, state.category)}</select>
        </label>
        <label class="filter-field">
          <span>Search flat</span>
          <input type="search" id="flatSearch" placeholder="e.g. A-101" autocomplete="off" value="${ui.escapeHtml(state.flat)}" />
        </label>
        <label class="filter-field">
          <span>Sort</span>
          <select id="sortBy">${optionList(sortOptions, state.sort)}</select>
        </label>
        ${
          statusKey === 'completed'
            ? `
          <label class="filter-field">
            <span>Completed from</span>
            <input type="date" id="fromDate" value="${ui.escapeHtml(state.from)}" />
          </label>
          <label class="filter-field">
            <span>Completed to</span>
            <input type="date" id="toDate" value="${ui.escapeHtml(state.to)}" />
          </label>
        `
            : ''
        }
        <button type="button" class="filter-clear" id="controlsReset">Reset</button>
      </div>
      <p class="filter-meta" id="ticketMeta"></p>
    `;
  }

  function renderShell(mode, statusKey, body, modalHtml, controlsHtml) {
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
          <button type="button" class="admin-tab metric-deleted ${statusKey === 'deleted' ? 'is-active' : ''}" data-tab="deleted">Deleted</button>
        </nav>

        ${controlsHtml || ''}

        <div class="admin-content">${body}</div>
      </section>
      ${modalHtml}
    `;
  }

  async function renderAdminTickets(root, ctx) {
    const { navigate, params, replaceParams } = ctx;
    const storage = window.CM.storage;
    const showToast = window.CM.showToast;

    const statusKey = ['pending', 'work', 'completed', 'deleted'].includes(params.status) ? params.status : 'pending';
    const mode = STATUS_MODES[statusKey];
    const isCompletedTab = statusKey === 'completed';
    const openTicketId = params.ticket || '';
    // Sub-state of the open ticket modal: '', 'reassign', 'unassign' or 'reopen'.
    const ticketAction = ['reassign', 'unassign', 'reopen'].includes(params.action) ? params.action : '';

    const allowedGroups = groupOptionsFor(statusKey).map((o) => o.value);
    const state = {
      group: allowedGroups.includes(params.group) ? params.group : 'none',
      category: storage.CATEGORIES.includes(params.category) ? params.category : '',
      flat: params.flat || '',
      sort: params.sort === 'oldest' ? 'oldest' : 'newest',
      from: isDateValue(params.from) ? params.from : defaultFromDate(),
      to: isDateValue(params.to) ? params.to : defaultToDate(),
    };

    // Which group headings are expanded. Kept in memory (not the URL) so it
    // survives re-renders while typing without cluttering a shared link.
    const expandedGroups = new Set();

    /** The whole view state, for URL sync and for modal round-trips. */
    function currentParams(extra) {
      const target = {
        status: statusKey,
        group: state.group !== 'none' ? state.group : null,
        category: state.category || null,
        flat: state.flat.trim() || null,
        sort: state.sort !== 'newest' ? state.sort : null,
      };
      if (isCompletedTab) {
        target.from = state.from;
        target.to = state.to;
      }
      return Object.assign(target, extra || {});
    }

    /** Keeps the address bar in step without re-rendering the view. */
    function syncUrl() {
      replaceParams('#/admin/tickets', currentParams());
    }

    function wireChrome() {
      const back = root.querySelector('[data-back]');
      if (back) back.addEventListener('click', () => navigate('#/admin/dashboard'));
      root.querySelectorAll('[data-tab]').forEach((btn) => {
        btn.addEventListener('click', () => {
          // A flat you are chasing stays as you move across tabs; everything
          // else starts fresh for the new tab.
          navigate('#/admin/tickets', { status: btn.dataset.tab, flat: state.flat.trim() || null });
        });
      });
    }

    root.innerHTML = renderShell(mode, statusKey, ui.loadingBlock('Loading tickets…'), '', '');
    wireChrome();

    const isDeletedTab = statusKey === 'deleted';

    let allComplaints;
    try {
      // Withdrawn complaints are excluded from the normal listings, so the
      // Deleted tab has to ask for them explicitly.
      allComplaints = isDeletedTab
        ? await storage.getDeletedComplaints()
        : await storage.getAllComplaints();
    } catch (err) {
      const msg = ui.messageFromError(err, 'Could not load tickets.');
      root.innerHTML = renderShell(mode, statusKey, ui.errorBlock(msg, 'Retry'), '', '');
      wireChrome();
      const retry = root.querySelector('[data-retry]');
      if (retry) retry.addEventListener('click', () => renderAdminTickets(root, ctx));
      return;
    }

    const forThisTab = allComplaints.filter((c) => storage.getDisplayStatus(c) === mode.key);

    function visibleTickets() {
      let list = forThisTab;
      if (isCompletedTab) {
        list = list.filter((c) => withinDateRange(c.completedAt, state.from, state.to));
      }
      if (state.category) {
        list = list.filter((c) => c.category === state.category);
      }
      const query = state.flat.trim().toLowerCase();
      if (query) {
        list = list.filter((c) => String(c.flat || '').toLowerCase().includes(query));
      }
      const direction = state.sort === 'oldest' ? 1 : -1;
      return list
        .slice()
        .sort((a, b) => direction * (new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()));
    }

    function filtersActive() {
      return Boolean(
        state.category
        || state.flat.trim()
        || (isCompletedTab && (state.from !== defaultFromDate() || state.to !== defaultToDate()))
      );
    }

    const openComplaint = openTicketId
      ? allComplaints.find((c) => String(c.id) === String(openTicketId)) || null
      : null;
    const modalHtml = openComplaint ? renderModal(openComplaint, storage, ticketAction) : '';

    root.innerHTML = renderShell(
      mode,
      statusKey,
      '<div id="ticketList"></div>',
      modalHtml,
      renderControls(statusKey, state, storage.CATEGORIES)
    );
    wireChrome();

    // Only the list is re-rendered as controls change, so the search box keeps
    // focus while you type and nothing is re-fetched.
    function renderList() {
      const host = root.querySelector('#ticketList');
      if (!host) return;

      const list = visibleTickets();

      host.innerHTML = list.length
        ? renderTicketList(list, mode, storage, state.group, expandedGroups)
        : renderEmptyState(mode, filtersActive());

      const meta = root.querySelector('#ticketMeta');
      if (meta) {
        const groupCount = host.querySelectorAll('.group-section').length;
        const tickets = `${list.length} ticket${list.length === 1 ? '' : 's'}`;
        const scope = list.length === forThisTab.length
          ? tickets
          : `Showing ${list.length} of ${forThisTab.length} ticket${forThisTab.length === 1 ? '' : 's'}`;
        if (isCompletedTab && state.from > state.to) {
          meta.textContent = 'The "completed from" date is after the "completed to" date, so nothing can match.';
        } else if (groupCount) {
          const unit = GROUP_UNITS[state.group] || 'groups';
          meta.textContent = `${scope} in ${groupCount} ${groupCount === 1 ? unit.slice(0, -1) : unit}`;
        } else {
          meta.textContent = scope;
        }
      }

      // <details> handles the open/close itself; we only remember which are open
      // so the state survives the next re-render.
      host.querySelectorAll('.group-section').forEach((section) => {
        section.addEventListener('toggle', () => {
          const key = section.dataset.group;
          if (section.open) expandedGroups.add(key);
          else expandedGroups.delete(key);
        });
      });

      host.querySelectorAll('.ticket-row').forEach((row) => {
        row.addEventListener('click', () => openTicket(row.dataset.ticket));
        row.addEventListener('keydown', (e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            openTicket(row.dataset.ticket);
          }
        });
      });

      const clearBtn = host.querySelector('[data-action="clear-filters"]');
      if (clearBtn) clearBtn.addEventListener('click', resetControls);
    }

    function applyChange(mutate) {
      mutate();
      renderList();
      syncUrl();
    }

    function resetControls() {
      state.group = 'none';
      state.category = '';
      state.flat = '';
      state.sort = 'newest';
      state.from = defaultFromDate();
      state.to = defaultToDate();
      expandedGroups.clear();

      const groupSel = root.querySelector('#groupBy');
      const catSel = root.querySelector('#categoryFilter');
      const flatInput = root.querySelector('#flatSearch');
      const sortSel = root.querySelector('#sortBy');
      const fromInput = root.querySelector('#fromDate');
      const toInput = root.querySelector('#toDate');
      if (groupSel) groupSel.value = 'none';
      if (catSel) catSel.value = '';
      if (flatInput) flatInput.value = '';
      if (sortSel) sortSel.value = 'newest';
      if (fromInput) fromInput.value = state.from;
      if (toInput) toInput.value = state.to;

      renderList();
      syncUrl();
    }

    const groupSel = root.querySelector('#groupBy');
    if (groupSel) {
      groupSel.addEventListener('change', () => applyChange(() => { state.group = groupSel.value; }));
    }
    const catSel = root.querySelector('#categoryFilter');
    if (catSel) {
      catSel.addEventListener('change', () => applyChange(() => { state.category = catSel.value; }));
    }
    const sortSel = root.querySelector('#sortBy');
    if (sortSel) {
      sortSel.addEventListener('change', () => applyChange(() => { state.sort = sortSel.value; }));
    }
    const flatInput = root.querySelector('#flatSearch');
    if (flatInput) {
      flatInput.addEventListener('input', () => applyChange(() => { state.flat = flatInput.value; }));
    }
    const fromInput = root.querySelector('#fromDate');
    if (fromInput) {
      fromInput.addEventListener('change', () => {
        if (!isDateValue(fromInput.value)) return;
        applyChange(() => { state.from = fromInput.value; });
      });
    }
    const toInput = root.querySelector('#toDate');
    if (toInput) {
      toInput.addEventListener('change', () => {
        if (!isDateValue(toInput.value)) return;
        applyChange(() => { state.to = toInput.value; });
      });
    }
    const controlsReset = root.querySelector('#controlsReset');
    if (controlsReset) controlsReset.addEventListener('click', resetControls);

    // Filters travel with the modal so closing a ticket returns you to exactly
    // the filtered page you opened it from.
    function openTicket(id) {
      navigate('#/admin/tickets', currentParams({ ticket: id }));
    }

    function closeTicket() {
      navigate('#/admin/tickets', currentParams());
    }

    /** Switches the open modal between its detail / reassign / unassign / reopen states. */
    function setTicketAction(nextAction) {
      navigate('#/admin/tickets', currentParams({
        ticket: openTicketId,
        action: nextAction || null,
      }));
    }

    renderList();

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

      // Reassign / Remove-assignee open a sub-state of this same modal, so the
      // back button and a refresh both land somewhere sensible.
      backdrop.querySelectorAll('[data-action]').forEach((btn) => {
        btn.addEventListener('click', () => setTicketAction(btn.dataset.action));
      });
      backdrop.querySelectorAll('[data-cancel-action]').forEach((btn) => {
        btn.addEventListener('click', () => setTicketAction(''));
      });

      const assignForm = backdrop.querySelector('#assignForm');
      if (assignForm) {
        await wireAssignForm(assignForm, openComplaint, storage, showToast, closeTicket, ticketAction === 'reassign');
      }

      const reopenBtn = backdrop.querySelector('#reopenConfirmBtn');
      if (reopenBtn) {
        const errorEl = backdrop.querySelector('#reopenError');
        reopenBtn.addEventListener('click', async () => {
          reopenBtn.disabled = true;
          reopenBtn.querySelector('.btn-label').textContent = 'Reopening…';
          try {
            await storage.reopenComplaint(openComplaint.id);
            showToast('Ticket reopened — back in Assignment Pending');
            closeTicket();
          } catch (err) {
            reopenBtn.disabled = false;
            reopenBtn.querySelector('.btn-label').textContent = 'Yes, reopen ticket';
            if (errorEl) {
              errorEl.textContent = ui.messageFromError(err, 'Could not reopen the ticket');
              errorEl.hidden = false;
            }
          }
        });
      }

      const unassignBtn = backdrop.querySelector('#unassignConfirmBtn');
      if (unassignBtn) {
        const errorEl = backdrop.querySelector('#unassignError');
        unassignBtn.addEventListener('click', async () => {
          unassignBtn.disabled = true;
          unassignBtn.querySelector('.btn-label').textContent = 'Removing…';
          try {
            await storage.unassignWorker(openComplaint.id);
            showToast('Assignee removed — ticket is back in Assignment Pending');
            closeTicket();
          } catch (err) {
            unassignBtn.disabled = false;
            unassignBtn.querySelector('.btn-label').textContent = 'Yes, remove assignee';
            if (errorEl) {
              errorEl.textContent = ui.messageFromError(err, 'Could not remove the assignee');
              errorEl.hidden = false;
            }
          }
        });
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

  async function wireAssignForm(form, complaint, storage, showToast, closeTicket, isReassign) {
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

    // Reassigning to the same person is a no-op, so drop them from the list.
    const currentId = complaint.worker ? complaint.worker.id : null;
    if (isReassign && currentId != null) {
      pros = pros.filter((p) => String(p.id) !== String(currentId));
    }

    if (!pros.length) {
      if (isReassign) {
        select.innerHTML = `<option value="" disabled selected>No other ${complaint.category} available</option>`;
        showError(`There is no other ${complaint.category} to reassign this ticket to. Add one under Manage Professionals first.`);
        return;
      }
      select.innerHTML = `<option value="" disabled selected>No ${complaint.category} professionals available</option>`;
      showError(`No ${complaint.category} professionals are registered yet. Add one under Manage Professionals before assigning.`);
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
      const restoreLabel = isReassign ? 'Reassign Worker' : 'Assign Worker';
      submit.disabled = true;
      submit.querySelector('.btn-label').textContent = isReassign ? 'Reassigning\u2026' : 'Assigning\u2026';
      try {
        const updated = await storage.assignWorker(complaint.id, { professionalId: Number(id) });
        const name = (updated.worker && updated.worker.name) || 'professional';
        showToast(isReassign ? `Reassigned to ${name}` : `Assigned to ${name}`);
        closeTicket();
      } catch (err) {
        submit.disabled = false;
        submit.querySelector('.btn-label').textContent = restoreLabel;
        showError(ui.messageFromError(err, isReassign ? 'Could not reassign worker' : 'Could not assign worker'));
      }
    });
  }

  window.CM = window.CM || {};
  window.CM.views = window.CM.views || {};
  window.CM.views.adminTickets = renderAdminTickets;
})();
