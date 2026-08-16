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

  function updateTopbar(s) {
    const logoutBtn = document.getElementById('logoutBtn');
    const changePwBtn = document.getElementById('changePwBtn');
    const topbarUser = document.getElementById('topbarUser');
    const topbarName = document.getElementById('topbarName');
    const topbarFlat = document.getElementById('topbarFlat');
    const notifications = window.CM.notifications;
    if (!logoutBtn) return;
    if (s) {
      logoutBtn.hidden = false;
      if (changePwBtn) changePwBtn.hidden = false;
      if (topbarUser) topbarUser.hidden = false;
      // Also re-reads the unread count, so the dot reacts to whatever the user
      // just did without waiting for the next poll.
      if (notifications) notifications.start(s);
      if (s.role === 'admin') {
        if (topbarName) topbarName.textContent = 'Management';
        if (topbarFlat) topbarFlat.textContent = `@${s.username || s.displayName || 'admin'}`;
      } else {
        if (topbarName) topbarName.textContent = s.name || s.displayName || 'Resident';
        if (topbarFlat) topbarFlat.textContent = `Flat ${s.flat || s.flatNo || ''}`;
      }
    } else {
      logoutBtn.hidden = true;
      if (changePwBtn) changePwBtn.hidden = true;
      if (topbarUser) topbarUser.hidden = true;
      if (notifications) notifications.stop();
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
    document.getElementById('logoutBtn').addEventListener('click', onLogout);
    const changePwBtn = document.getElementById('changePwBtn');
    if (changePwBtn) {
      changePwBtn.addEventListener('click', () => navigate('#/change-password'));
    }
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
