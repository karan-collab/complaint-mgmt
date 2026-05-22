/**
 * Session/JWT state for the SocietyCare web UI.
 *
 * Stored shape in localStorage (key `cm.session`):
 *   {
 *     token,                 // JWT
 *     tokenType,             // "Bearer"
 *     role,                  // "resident" | "admin" (lower-case)
 *     userId,                // numeric id from backend
 *     displayName,           // resident name or admin display name
 *     flatNo,                // resident only
 *     expiresAt              // ms since epoch
 *   }
 *
 * The router/views read a `session` object that ALSO carries legacy aliases
 *   - session.name      -> displayName (residents read this today)
 *   - session.flat      -> flatNo      (residents read this today)
 *   - session.username  -> displayName (admin views read this today)
 * so existing view code keeps working with minimal diff.
 */
(function () {
  const SESSION_KEY = 'cm.session';

  function safeParse(raw) {
    if (!raw) return null;
    try {
      return JSON.parse(raw);
    } catch {
      return null;
    }
  }

  function decorate(raw) {
    if (!raw) return null;
    const role = (raw.role || '').toLowerCase();
    return {
      ...raw,
      role,
      name: raw.displayName || raw.name || '',
      flat: raw.flatNo || raw.flat || '',
      username: raw.displayName || raw.username || '',
    };
  }

  function get() {
    const raw = safeParse(localStorage.getItem(SESSION_KEY));
    if (!raw) return null;
    if (raw.expiresAt && Date.now() >= raw.expiresAt) {
      localStorage.removeItem(SESSION_KEY);
      return null;
    }
    return decorate(raw);
  }

  function set(loginResponse) {
    // loginResponse comes straight from POST /auth/.../login
    const expiresAt = Date.now() + (Number(loginResponse.expiresInSeconds || 0) * 1000);
    const stored = {
      token: loginResponse.token,
      tokenType: loginResponse.tokenType || 'Bearer',
      role: (loginResponse.role || '').toLowerCase(),
      userId: loginResponse.userId || null,
      displayName: loginResponse.displayName || '',
      flatNo: loginResponse.flatNo || null,
      expiresAt,
    };
    localStorage.setItem(SESSION_KEY, JSON.stringify(stored));
    return decorate(stored);
  }

  function clear() {
    localStorage.removeItem(SESSION_KEY);
  }

  function isResident(session) {
    return !!session && session.role === 'resident';
  }

  function isAdmin(session) {
    return !!session && session.role === 'admin';
  }

  window.CM = window.CM || {};
  window.CM.session = { get, set, clear, isResident, isAdmin };
})();
