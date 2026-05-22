(function () {
  let hideTimer = null;

  function showToast(message, type) {
    type = type || 'success';
    const el = document.getElementById('toast');
    if (!el) return;

    const icon =
      type === 'success'
        ? '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M20 6 9 17l-5-5"/></svg>'
        : type === 'error'
        ? '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="10"/><path d="M12 8v4"/><path d="M12 16h.01"/></svg>'
        : '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="10"/><path d="M12 16v-4"/><path d="M12 8h.01"/></svg>';

    el.className = `toast toast-${type}`;
    el.innerHTML = `<span class="toast-icon">${icon}</span><span class="toast-msg"></span>`;
    el.querySelector('.toast-msg').textContent = message;

    requestAnimationFrame(() => el.classList.add('show'));

    clearTimeout(hideTimer);
    hideTimer = setTimeout(() => {
      el.classList.remove('show');
    }, 2800);
  }

  window.CM = window.CM || {};
  window.CM.showToast = showToast;
})();
