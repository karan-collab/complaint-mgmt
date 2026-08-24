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

  const EYE_ICON = `
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
      <path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7z"/>
      <circle cx="12" cy="12" r="3"/>
    </svg>
  `;

  const EYE_OFF_ICON = `
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
      <path d="M17.94 17.94A10.07 10.07 0 0 1 12 19c-7 0-10-7-10-7a18.4 18.4 0 0 1 5.06-5.94"/>
      <path d="M9.9 4.24A9.1 9.1 0 0 1 12 4c7 0 10 7 10 7a18.5 18.5 0 0 1-2.16 3.19"/>
      <path d="M14.12 14.12a3 3 0 1 1-4.24-4.24"/>
      <path d="m2 2 20 20"/>
    </svg>
  `;

  /**
   * The show/hide control that sits inside a password field. Wrap the input and
   * this button in `.password-field`, then call wirePasswordToggles(root) once
   * the markup is in the DOM.
   */
  function passwordToggle() {
    return `
      <button type="button" class="password-toggle" data-password-toggle
              aria-label="Show password" title="Show password">
        <span class="password-icon" data-icon-show>${EYE_ICON}</span>
        <span class="password-icon" data-icon-hide hidden>${EYE_OFF_ICON}</span>
      </button>
    `;
  }

  /**
   * Wires every [data-password-toggle] under `root` to the input beside it.
   *
   * Nothing is remembered between renders on purpose: re-rendering a view
   * rebuilds the input as type="password", so a revealed password can never
   * survive a navigation or a re-login.
   */
  function wirePasswordToggles(root) {
    root.querySelectorAll('[data-password-toggle]').forEach((btn) => {
      const input = btn.parentElement && btn.parentElement.querySelector('input');
      if (!input) return;

      function paint(revealed) {
        const label = revealed ? 'Hide password' : 'Show password';
        btn.setAttribute('aria-label', label);
        btn.title = label;
        btn.querySelector('[data-icon-show]').hidden = revealed;
        btn.querySelector('[data-icon-hide]').hidden = !revealed;
      }

      // Not every field starts masked. An admin setting a password *for* a
      // resident has to read it back to them, so that box opens visible - paint
      // from what the input actually is rather than assuming it is hidden.
      paint(input.type !== 'password');

      btn.addEventListener('click', () => {
        const reveal = input.type === 'password';
        input.type = reveal ? 'text' : 'password';
        paint(reveal);
        // Changing `type` drops the caret, so put it back at the end. Losing
        // your place mid-password is worse than having no toggle at all.
        const end = input.value.length;
        input.focus();
        try {
          input.setSelectionRange(end, end);
        } catch {
          // Some browsers refuse setSelectionRange on a password input; the
          // focus above is the part that matters.
        }
      });
    });
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
    passwordToggle,
    wirePasswordToggles,
    CLOSE_ICON,
  };
})();
