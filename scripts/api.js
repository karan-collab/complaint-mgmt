/**
 * Tiny fetch wrapper for the SocietyCare API.
 *
 *   const data = await api.get('/auth/me');
 *   const out  = await api.post('/auth/resident/login', { flatNo, password });
 *
 * All requests:
 *  - prefix `apiBase` from window.CM.config
 *  - add `Authorization: Bearer <jwt>` if a session exists
 *  - send/receive JSON
 *  - throw ApiError for non-2xx, with `status`, `title`, `detail`, `fieldErrors`
 *
 * On 401 the session is cleared and a `cm:unauthorized` event is dispatched on
 * window so the router can boot the user back to login.
 */
(function () {
  const cfg = (window.CM && window.CM.config) || { apiBase: '/api/v1' };

  class ApiError extends Error {
    constructor({ status, title, detail, fieldErrors, body }) {
      super(detail || title || `HTTP ${status}`);
      this.name = 'ApiError';
      this.status = status;
      this.title = title || null;
      this.detail = detail || null;
      this.fieldErrors = fieldErrors || null;
      this.body = body;
    }
  }

  function buildUrl(path) {
    if (/^https?:/i.test(path)) return path;
    if (!path.startsWith('/')) path = '/' + path;
    return cfg.apiBase + path;
  }

  function currentToken() {
    const session = window.CM && window.CM.session && window.CM.session.get();
    return session && session.token ? session.token : null;
  }

  function summariseFieldErrors(errors) {
    if (!Array.isArray(errors) || !errors.length) return null;
    return errors
      .map((e) => {
        const field = e.field || e.objectName || 'field';
        const msg = e.message || e.defaultMessage || 'invalid';
        return `${field}: ${msg}`;
      })
      .join(' \u00b7 ');
  }

  async function parseBody(res) {
    const ct = res.headers.get('content-type') || '';
    if (res.status === 204) return null;
    if (ct.includes('application/json')) {
      try {
        return await res.json();
      } catch {
        return null;
      }
    }
    try {
      const text = await res.text();
      return text || null;
    } catch {
      return null;
    }
  }

  async function request(method, path, { body, headers, signal } = {}) {
    const finalHeaders = {
      Accept: 'application/json',
      ...(headers || {}),
    };
    let payload;
    if (body !== undefined && body !== null) {
      finalHeaders['Content-Type'] = 'application/json';
      payload = typeof body === 'string' ? body : JSON.stringify(body);
    }
    const token = currentToken();
    if (token) finalHeaders.Authorization = `Bearer ${token}`;

    let res;
    try {
      res = await fetch(buildUrl(path), {
        method,
        headers: finalHeaders,
        body: payload,
        signal,
      });
    } catch (networkErr) {
      throw new ApiError({
        status: 0,
        title: 'Network error',
        detail:
          'Could not reach the server. Make sure the backend is running on '
          + cfg.apiBase + '.',
      });
    }

    const body2 = await parseBody(res);

    if (res.ok) return body2;

    const isObj = body2 && typeof body2 === 'object';
    const errInfo = {
      status: res.status,
      title: (isObj && (body2.title || body2.error)) || res.statusText,
      detail: (isObj && (body2.detail || body2.message)) || null,
      fieldErrors: (isObj && body2.errors) || null,
      body: body2,
    };
    if (!errInfo.detail && errInfo.fieldErrors) {
      errInfo.detail = summariseFieldErrors(errInfo.fieldErrors);
    }

    if (res.status === 401) {
      if (window.CM && window.CM.session) window.CM.session.clear();
      window.dispatchEvent(new CustomEvent('cm:unauthorized', { detail: errInfo }));
    }

    throw new ApiError(errInfo);
  }

  const api = {
    ApiError,
    get: (path, opts) => request('GET', path, opts),
    post: (path, body, opts) => request('POST', path, { ...(opts || {}), body }),
    patch: (path, body, opts) => request('PATCH', path, { ...(opts || {}), body }),
    put: (path, body, opts) => request('PUT', path, { ...(opts || {}), body }),
    delete: (path, opts) => request('DELETE', path, opts),
  };

  window.CM = window.CM || {};
  window.CM.api = api;
  window.CM.ApiError = ApiError;
})();
