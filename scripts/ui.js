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

  const CLOSE_ICON = `
    <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
      <path d="M18 6 6 18M6 6l12 12"/>
    </svg>
  `;

  /**
   * Mount a modal built from `innerHtml` (the contents of `.modal`).
   *
   * Handles the boilerplate every modal in the app needs: backdrop click,
   * Escape, `[data-close]` buttons, and the `modal-open` body class. Returns
   * `{ root, close }` where `root` is the `.modal-backdrop` element.
   */
  function openModal(innerHtml, options) {
    const opts = options || {};
    const holder = document.createElement('div');
    holder.innerHTML = `
      <div class="modal-backdrop" role="dialog" aria-modal="true">
        <div class="modal ${opts.wide ? 'modal-wide' : ''}">${innerHtml}</div>
      </div>
    `;
    const root = holder.firstElementChild;
    document.body.appendChild(root);
    document.body.classList.add('modal-open');

    let closed = false;
    function close() {
      if (closed) return;
      closed = true;
      document.removeEventListener('keydown', onKey);
      document.body.classList.remove('modal-open');
      root.remove();
      if (typeof opts.onClose === 'function') opts.onClose();
    }

    function onKey(e) {
      if (e.key === 'Escape') close();
    }
    document.addEventListener('keydown', onKey);

    root.addEventListener('click', (e) => {
      if (e.target === root) close();
    });
    root.querySelectorAll('[data-close]').forEach((btn) =>
      btn.addEventListener('click', close)
    );

    const autofocus = root.querySelector('[data-autofocus]');
    if (autofocus) setTimeout(() => autofocus.focus(), 40);

    return { root, close };
  }

  /**
   * Yes/no confirmation dialog. Resolves to true only when the user explicitly
   * confirms; closing, cancelling or pressing Escape resolves to false.
   */
  function confirmDialog({ eyebrow, title, message, confirmLabel, cancelLabel, danger }) {
    return new Promise((resolve) => {
      let answered = false;
      const modal = openModal(
        `
        <header class="modal-head">
          <div class="modal-head-text">
            ${eyebrow ? `<p class="modal-eyebrow">${escapeHtml(eyebrow)}</p>` : ''}
            <h2>${escapeHtml(title || 'Are you sure?')}</h2>
          </div>
          <button type="button" class="modal-close" data-close aria-label="Close">${CLOSE_ICON}</button>
        </header>
        <div class="modal-form">
          <p class="confirm-message">${message || ''}</p>
          <div class="modal-actions">
            <button type="button" class="btn btn-ghost" data-close>${escapeHtml(cancelLabel || 'No, cancel')}</button>
            <button type="button" class="btn ${danger ? 'btn-danger' : 'btn-primary'}" data-confirm data-autofocus>
              ${escapeHtml(confirmLabel || 'Yes, continue')}
            </button>
          </div>
        </div>
      `,
        {
          onClose: () => {
            if (!answered) {
              answered = true;
              resolve(false);
            }
          },
        }
      );

      modal.root.querySelector('[data-confirm]').addEventListener('click', () => {
        answered = true;
        modal.close();
        resolve(true);
      });
    });
  }

  window.CM = window.CM || {};
  window.CM.ui = {
    escapeHtml,
    loadingBlock,
    errorBlock,
    messageFromError,
    openModal,
    confirmDialog,
    CLOSE_ICON,
  };
})();
