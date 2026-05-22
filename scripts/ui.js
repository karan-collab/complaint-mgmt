/**
 * Shared rendering helpers for views: loading/error states, escaping, and
 * extracting a user-friendly message from an ApiError.
 */
(function () {
  function escapeHtml(s) {
    return String(s == null ? '' : s).replace(/[&<>"']/g, (c) => ({
      '&': '&amp;',
      '<': '&lt;',
      '>': '&gt;',
      '"': '&quot;',
      "'": '&#39;',
    }[c]));
  }

  function loadingBlock(label) {
    return `
      <div class="loading-block" role="status" aria-live="polite">
        <div class="loading-spinner" aria-hidden="true"></div>
        <p class="loading-text">${escapeHtml(label || 'Loading\u2026')}</p>
      </div>
    `;
  }

  function errorBlock(message, retryLabel) {
    return `
      <div class="error-block card" role="alert">
        <div class="error-icon" aria-hidden="true">
          <svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="12" r="10"/>
            <path d="M12 8v4"/>
            <path d="M12 16h.01"/>
          </svg>
        </div>
        <h3>Something went wrong</h3>
        <p class="muted">${escapeHtml(message || 'Please try again.')}</p>
        ${retryLabel ? `<button type="button" class="btn btn-primary" data-retry>${escapeHtml(retryLabel)}</button>` : ''}
      </div>
    `;
  }

  function messageFromError(err, fallback) {
    if (!err) return fallback || 'Unexpected error';
    if (err.status === 0) {
      return err.detail || 'Could not reach the server. Is the backend running?';
    }
    if (err.status === 401) return 'Your session has expired. Please sign in again.';
    if (err.status === 403) return 'You do not have access to this resource.';
    if (err.status === 404) return 'We could not find what you were looking for.';
    if (err.detail) return err.detail;
    if (err.message) return err.message;
    return fallback || 'Unexpected error';
  }

  window.CM = window.CM || {};
  window.CM.ui = {
    escapeHtml,
    loadingBlock,
    errorBlock,
    messageFromError,
  };
})();
