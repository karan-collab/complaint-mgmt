/**
 * The notification bell in the topbar.
 *
 * There is no push channel in this stack, so "a red dot appears when something
 * happens" is done by polling: the unread count is re-fetched on a timer, on
 * every route change, and whenever the tab is brought back to the foreground.
 * Only the count is polled - it is a single integer - and the full list is
 * fetched once, when the panel is actually opened.
 *
 * Polling stops while the tab is hidden and while nobody is signed in, so an
 * idle background tab costs nothing.
 */
(function () {
  const storage = window.CM.storage;
  const escapeHtml = window.CM.ui.escapeHtml;

  const POLL_MS = 45000;

  let timer = null;
  let currentSession = null;
  let unread = 0;
  let panelOpen = false;
  let loading = false;

  function els() {
    return {
      wrap: document.getElementById('bellWrap'),
      btn: document.getElementById('bellBtn'),
      dot: document.getElementById('bellDot'),
      panel: document.getElementById('bellPanel'),
      list: document.getElementById('bellList'),
    };
  }

  // ------------------------------------------------------------- rendering

  function timeAgo(iso) {
    const then = new Date(iso).getTime();
    if (!then || Number.isNaN(then)) return '';
    const secs = Math.max(0, Math.floor((Date.now() - then) / 1000));
    if (secs < 60) return 'Just now';
    const mins = Math.floor(secs / 60);
    if (mins < 60) return `${mins} minute${mins === 1 ? '' : 's'} ago`;
    const hrs = Math.floor(mins / 60);
    if (hrs < 24) return `${hrs} hour${hrs === 1 ? '' : 's'} ago`;
    const days = Math.floor(hrs / 24);
    return `${days} day${days === 1 ? '' : 's'} ago`;
  }

  /** Groups the seven event types into the three colours the panel uses. */
  function toneFor(type) {
    if (type === 'COMPLAINT_COMPLETED') return 'done';
    if (type === 'WORKER_ASSIGNED' || type === 'WORKER_REASSIGNED') return 'work';
    if (type === 'COMPLAINT_WITHDRAWN' || type === 'WORKER_REMOVED') return 'alert';
    return 'pending';
  }

  function updateDot() {
    const { btn, dot } = els();
    if (!btn || !dot) return;
    dot.hidden = unread === 0;
    const label = unread === 0
      ? 'Notifications'
      : `Notifications, ${unread} unread`;
    btn.setAttribute('aria-label', label);
    btn.title = label;
  }

  function renderList(items) {
    const { list } = els();
    if (!list) return;

    if (!items.length) {
      list.innerHTML = `
        <li class="bell-empty">
          <p>No notifications yet.</p>
          <p class="muted">You will be told here when a ticket changes.</p>
        </li>
      `;
      return;
    }

    list.innerHTML = items
      .map(
        (n) => `
        <li class="bell-item ${n.read ? '' : 'is-unread'}">
          <span class="bell-tone bell-tone-${escapeHtml(toneFor(n.type))}" aria-hidden="true"></span>
          <div class="bell-item-body">
            <p class="bell-message">${escapeHtml(n.message)}</p>
            <p class="bell-meta">
              ${escapeHtml(n.category || '')}${n.flat ? ` · Flat ${escapeHtml(n.flat)}` : ''}
              · ${escapeHtml(timeAgo(n.createdAt))}
            </p>
          </div>
        </li>
      `
      )
      .join('');
  }

  // -------------------------------------------------------------- polling

  /**
   * Re-reads the unread count. Failures are swallowed on purpose: a poll that
   * fails because the backend blinked should not throw a toast at somebody who
   * did not ask for anything. A 401 is already handled globally by api.js.
   */
  async function refresh() {
    if (!currentSession) return;
    try {
      unread = await storage.getUnreadNotificationCount();
      updateDot();
    } catch {
      // ignored - the next poll will pick it up
    }
  }

  function schedule() {
    clearTimer();
    timer = setInterval(() => {
      if (document.visibilityState === 'visible') refresh();
    }, POLL_MS);
  }

  function clearTimer() {
    if (timer) {
      clearInterval(timer);
      timer = null;
    }
  }

  // ---------------------------------------------------------------- panel

  async function openPanel() {
    const { wrap, btn, panel } = els();
    if (!wrap || !panel || loading) return;

    panelOpen = true;
    panel.hidden = false;
    wrap.classList.add('is-open');
    btn.setAttribute('aria-expanded', 'true');

    loading = true;
    renderList([]);
    const { list } = els();
    if (list) list.innerHTML = '<li class="bell-empty"><p class="muted">Loading…</p></li>';

    try {
      const items = await storage.getNotifications();
      renderList(items);
      // Opening the panel is the read receipt: everything shown is now seen,
      // so the dot clears in the same breath.
      if (unread > 0) {
        await storage.markNotificationsRead();
        unread = 0;
        updateDot();
      }
    } catch (err) {
      const { list: l } = els();
      if (l) {
        l.innerHTML = `<li class="bell-empty"><p class="muted">${escapeHtml(
          window.CM.ui.messageFromError(err, 'Could not load notifications.')
        )}</p></li>`;
      }
    } finally {
      loading = false;
    }
  }

  function closePanel() {
    const { wrap, btn, panel } = els();
    if (!panel) return;
    panelOpen = false;
    panel.hidden = true;
    wrap.classList.remove('is-open');
    btn.setAttribute('aria-expanded', 'false');
  }

  function togglePanel() {
    if (panelOpen) closePanel();
    else openPanel();
  }

  // ----------------------------------------------------------------- wiring

  function start(session) {
    const changed = !currentSession
      || currentSession.role !== session.role
      || currentSession.token !== session.token;
    currentSession = session;

    const { wrap } = els();
    if (wrap) wrap.hidden = false;

    if (changed) {
      unread = 0;
      closePanel();
      updateDot();
    }
    refresh();
    schedule();
  }

  function stop() {
    currentSession = null;
    unread = 0;
    clearTimer();
    closePanel();
    const { wrap } = els();
    if (wrap) wrap.hidden = true;
  }

  function init() {
    const { btn } = els();
    if (!btn) return;

    btn.addEventListener('click', (e) => {
      e.stopPropagation();
      togglePanel();
    });

    // Click-away and Escape, matching how ui.openModal behaves.
    document.addEventListener('click', (e) => {
      const { wrap } = els();
      if (panelOpen && wrap && !wrap.contains(e.target)) closePanel();
    });
    document.addEventListener('keydown', (e) => {
      if (e.key === 'Escape' && panelOpen) closePanel();
    });

    // Coming back to the tab is the moment a stale dot is most obvious.
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'visible') refresh();
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }

  window.CM = window.CM || {};
  window.CM.notifications = { start, stop, refresh, closePanel };
})();
