(function () {
  // Where the Spring Boot backend lives. Override on a different host by
  // setting `window.CM_API_BASE = '...'` before this script is loaded, or by
  // editing this default for local dev.
  const DEFAULT_API_BASE =
    window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1'
      ? 'http://localhost:8080/api/v1'
      : '/api/v1';

  window.CM = window.CM || {};
  window.CM.config = {
    apiBase: (window.CM_API_BASE || DEFAULT_API_BASE).replace(/\/+$/, ''),
  };
})();
