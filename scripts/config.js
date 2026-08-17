(function () {
  // Where the Spring Boot backend lives. Override on a different host by
  // setting `window.CM_API_BASE = '...'` before this script is loaded, or by
  // editing this default for local dev.
  //
  // Only the split dev setup needs an absolute URL: there a plain static server
  // hosts the UI on 5500 (or 8000) while the API runs as a separate process on
  // 8080, so the two are different origins and CORS applies.
  //
  // Everywhere else - Docker Compose, staging, production - nginx serves the UI
  // *and* proxies /api/ to the API, so the relative path is the correct answer
  // and there is no CORS involved at all. Testing on hostname alone got this
  // wrong for the local Docker stack, which is also served from localhost but
  // does not publish the API's port.
  const DEV_STATIC_PORTS = ['5500', '8000'];
  const isLocalHost =
    window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1';
  const DEFAULT_API_BASE =
    isLocalHost && DEV_STATIC_PORTS.indexOf(window.location.port) !== -1
      ? 'http://localhost:8080/api/v1'
      : '/api/v1';

  window.CM = window.CM || {};
  window.CM.config = {
    apiBase: (window.CM_API_BASE || DEFAULT_API_BASE).replace(/\/+$/, ''),
  };
})();
