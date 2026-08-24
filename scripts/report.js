/**
 * "Export to Excel" in the management topbar, and the date-range dialog behind
 * it.
 *
 * The workbook itself is built on the server (GET /reports/activity). Doing it
 * here would mean committing a spreadsheet library into a repo that has no
 * build step, and - more to the point - it would put the date-boundary and
 * timezone arithmetic in the one place this project has no tests for.
 */
(function () {
  const ui = window.CM.ui;
  const api = window.CM.api;

  /** yyyy-mm-dd for an <input type="date">, in the browser's own calendar. */
  function isoDate(d) {
    const pad = (n) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
  }

  function firstOfThisMonth() {
    const now = new Date();
    return isoDate(new Date(now.getFullYear(), now.getMonth(), 1));
  }

  function today() {
    return isoDate(new Date());
  }

  /**
   * Hands the blob to the browser as a download.
   *
   * The object URL is revoked afterwards; without that the whole workbook stays
   * pinned in memory until the tab is closed, and somebody pulling a year of
   * data twice a day would notice.
   */
  function saveBlob(blob, fileName) {
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = fileName;
    document.body.appendChild(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  function openExportModal() {
    const modal = ui.openModal(`
      <header class="modal-head">
        <div class="modal-head-text">
          <p class="modal-eyebrow">Reports</p>
          <h2>Export to Excel</h2>
          <p class="muted">
            Downloads every complaint and suggestion <strong>raised</strong> between
            these dates, as a spreadsheet. Both days are included; complaints
            residents withdrew are not.
          </p>
        </div>
        <button type="button" class="modal-close" data-close aria-label="Close">${ui.CLOSE_ICON}</button>
      </header>
      <form class="modal-form" id="exportForm" novalidate>
        <div class="field-grid">
          <label class="field">
            <span>From</span>
            <input type="date" name="from" id="exportFrom" value="${firstOfThisMonth()}" required data-autofocus />
          </label>
          <label class="field">
            <span>To</span>
            <input type="date" name="to" id="exportTo" value="${today()}" required />
          </label>
        </div>
        <p class="login-error" id="exportError" hidden></p>
        <div class="modal-actions">
          <button type="button" class="btn btn-ghost" data-close>Cancel</button>
          <button type="submit" class="btn btn-primary" id="exportSubmit">
            <span class="btn-label">Download</span>
          </button>
        </div>
      </form>
    `);

    const form = modal.root.querySelector('#exportForm');
    const fromEl = modal.root.querySelector('#exportFrom');
    const toEl = modal.root.querySelector('#exportTo');
    const errorEl = modal.root.querySelector('#exportError');
    const submit = modal.root.querySelector('#exportSubmit');

    function setError(msg) {
      errorEl.textContent = msg || '';
      errorEl.hidden = !msg;
    }

    [fromEl, toEl].forEach((el) => el.addEventListener('change', () => setError('')));

    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      setError('');
      const from = fromEl.value;
      const to = toEl.value;

      if (!from || !to) {
        setError('Please choose both dates.');
        return;
      }
      // Checked here as well as on the server: the server is the one that
      // cannot be bypassed, but catching it in the dialog explains the problem
      // without a round trip.
      if (from > to) {
        setError('The "from" date is after the "to" date, so nothing could match.');
        return;
      }

      submit.disabled = true;
      submit.querySelector('.btn-label').textContent = 'Preparing…';
      try {
        const path = `/reports/activity?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`;
        const { blob, fileName } = await api.getBlob(path);
        // The server names the file; the fallback covers the case where the
        // header is not readable, which is what a missing CORS exposure looks
        // like from in here.
        saveBlob(blob, fileName || `societycare-report-${from}-to-${to}.xlsx`);
        modal.close();
        window.CM.showToast('Report downloaded');
      } catch (err) {
        submit.disabled = false;
        submit.querySelector('.btn-label').textContent = 'Download';
        setError(ui.messageFromError(err, 'Could not build the report'));
      }
    });
  }

  function init() {
    const btn = document.getElementById('exportBtn');
    if (btn) btn.addEventListener('click', openExportModal);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }

  window.CM = window.CM || {};
  window.CM.report = { open: openExportModal };
})();
