(function () {
  const session = window.CM.session;
  const views = window.CM.views;
  const showToast = window.CM.showToast;

  const routes = {
    '#/role':            { render: views.role,            public: true,  role: null },
    '#/login':           { render: views.login,           public: true,  role: null },
    '#/admin/login':     { render: views.adminLogin,      public: true,  role: null },
    '#/dashboard':       { render: views.dashboard,       public: false, role: 'resident' },
    '#/raise':           { render: views.raise,           public: false, role: 'resident' },
    '#/complaints':      { render: views.complaints,      public: false, role: 'resident' },
    '#/admin/dashboard': { render: views.adminDashboard,  public: false, role: 'admin' },
    '#/admin/tickets':   { render: views.adminTickets,    public: false, role: 'admin' },
    '#/admin/residents': { render: views.adminResidents,  public: false, role: 'admin' },
    '#/admin/professionals': { render: views.adminProfessionals, public: false, role: 'admin' },
    '#/admin/suggestions': { render: views.adminSuggestions, public: false, role: 'admin' },
    '#/change-password': { render: views.changePassword,  public: false, role: null },
  };

  function parseHash() {
    const raw = location.hash || '#/role';
    const qIdx = raw.indexOf('?');
    const path = qIdx === -1 ? raw : raw.slice(0, qIdx);
    const params = {};
    if (qIdx !== -1) {
      raw
        .slice(qIdx + 1)
        .split('&')
        .filter(Boolean)
        .forEach((pair) => {
          const eq = pair.indexOf('=');
          const k = decodeURIComponent(eq === -1 ? pair : pair.slice(0, eq));
          const v = eq === -1 ? '' : decodeURIComponent(pair.slice(eq + 1));
          if (k) params[k] = v;
        });
    }
    return { path, params };
  }

  function buildHash(path, params) {
    const qs = params
      ? Object.keys(params)
          .filter((k) => params[k] != null && params[k] !== '')
          .map((k) => `${encodeURIComponent(k)}=${encodeURIComponent(params[k])}`)
          .join('&')
      : '';
    return qs ? `${path}?${qs}` : path;
  }

  function navigate(target, params) {
    const hash = params ? buildHash(target, params) : target;
    if (location.hash === hash) {
      handleRoute();
    } else {
      location.hash = hash;
    }
  }

  function replaceParams(path, params) {
    const hash = buildHash(path, params);
    history.replaceState(null, '', `${location.pathname}${location.search}${hash}`);
  }

  function homeForRole(role) {
    return role === 'admin' ? '#/admin/dashboard' : '#/dashboard';
  }

  /**
   * The signed-in topbar. Both roles get the bell and Settings; the middle
   * button differs, because the two roles are on opposite ends of the same
   * flows - a resident writes suggestions, management reads and reports on
   * them.
   *
   *   resident:   bell | Add Suggestion  | Settings
   *   management: bell | Export to Excel | Settings
   */
  function updateTopbar(s) {
    const settingsWrap = document.getElementById('settingsWrap');
    const suggestBtn = document.getElementById('suggestBtn');
    const exportBtn = document.getElementById('exportBtn');
    const topbarUser = document.getElementById('topbarUser');
    const topbarName = document.getElementById('topbarName');
    const topbarFlat = document.getElementById('topbarFlat');
    const notifications = window.CM.notifications;
    if (!settingsWrap) return;

    const isAdmin = !!s && s.role === 'admin';
    settingsWrap.hidden = !s;
    if (suggestBtn) suggestBtn.hidden = !s || isAdmin;
    if (exportBtn) exportBtn.hidden = !isAdmin;
    if (topbarUser) topbarUser.hidden = !s;

    if (s) {
      // Also re-reads the unread count, so the dot reacts to whatever the user
      // just did without waiting for the next poll.
      if (notifications) notifications.start(s);
      if (isAdmin) {
        if (topbarName) topbarName.textContent = 'Management';
        if (topbarFlat) topbarFlat.textContent = `@${s.username || s.displayName || 'admin'}`;
      } else {
        if (topbarName) topbarName.textContent = s.name || s.displayName || 'Resident';
        if (topbarFlat) topbarFlat.textContent = `Flat ${s.flat || s.flatNo || ''}`;
      }
    } else {
      if (notifications) notifications.stop();
      // A menu left open across a logout would hang over the login screen.
      if (window.CM.settingsMenu) window.CM.settingsMenu.close();
    }
  }

  function handleRoute() {
    const s = session.get();
    const { path, params } = parseHash();
    const finalPath = routes[path] ? path : '#/role';
    const route = routes[finalPath];

    if (!route.public && !s) {
      navigate('#/role');
      return;
    }
    if (!route.public && s && route.role && route.role !== s.role) {
      navigate(homeForRole(s.role));
      return;
    }
    if (route.public && s) {
      if (finalPath === '#/role' || finalPath === '#/login' || finalPath === '#/admin/login') {
        navigate(homeForRole(s.role));
        return;
      }
    }

    updateTopbar(s);

    const view = document.getElementById('view');
    view.className = 'view';
    view.innerHTML = '';
    // render may be async; we don't await, route just lets it manage its own state.
    Promise.resolve(route.render(view, { session: s, navigate, params, replaceParams }))
      .catch((err) => {
        console.error('View render failed', err);
      });
    window.scrollTo({ top: 0 });
  }

  function onLogout() {
    session.clear();
    showToast('Logged out', 'info');
    navigate('#/role');
  }

  // When ANY API call returns 401, kick the user out and force re-login.
  window.addEventListener('cm:unauthorized', () => {
    // api.js already cleared the session.
    showToast('Your session has expired. Please sign in again.', 'error');
    if (!location.hash.startsWith('#/role')
        && !location.hash.startsWith('#/login')
        && !location.hash.startsWith('#/admin/login')) {
      navigate('#/role');
    } else {
      handleRoute();
    }
  });

  async function init() {
    // The Settings menu reports what was chosen rather than acting on it: the
    // router owns navigation and the session, so the decision belongs here.
    window.addEventListener('cm:settings-action', (e) => {
      const action = e.detail && e.detail.action;
      if (action === 'logout') onLogout();
      if (action === 'change-password') navigate('#/change-password');
    });
    window.addEventListener('hashchange', handleRoute);

    const initial = session.get();
    if (initial) {
      // Best-effort: verify the token is still valid. If /me 401s, api.js fires
      // cm:unauthorized which clears the session and toasts.
      try {
        await window.CM.storage.fetchMe();
      } catch {
        // ignored \u2014 handler above already covered the 401 path
      }
    }

    if (!location.hash) location.hash = '#/role';
    handleRoute();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
