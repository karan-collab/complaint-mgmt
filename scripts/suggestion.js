/**
 * "Add Suggestion" in the topbar, and the modal behind it.
 *
 * This lives alongside notifications.js rather than under views/ for the same
 * reason the bell does: it hangs off the topbar, which outlives any single
 * route, so it is not a screen the router can own.
 *
 * The button is resident-only. Management reads suggestions on their own page
 * (#/admin/suggestions) and has nobody to send one to.
 */
(function () {
  const storage = window.CM.storage;
  const ui = window.CM.ui;

  const MAX_LENGTH = 2000;
  const MIN_LENGTH = 5;

  /**
   * Ask the resident for a suggestion and post it.
   *
   * Unlike a complaint this has no category and no follow-up: management reads
   * it and that is the whole of the interaction, so the modal says as much
   * rather than implying a ticket has been opened.
   */
  function openSuggestionModal() {
    const modal = ui.openModal(`
      <header class="modal-head">
        <div class="modal-head-text">
          <p class="modal-eyebrow">Suggestions</p>
          <h2>Share a suggestion</h2>
          <p class="muted">
            Tell management what you think could be better about the society.
            This is not a complaint and no worker will be assigned - it goes
            straight to the management team to read.
          </p>
        </div>
        <button type="button" class="modal-close" data-close aria-label="Close">${ui.CLOSE_ICON}</button>
      </header>
      <form class="modal-form" id="suggestionForm" novalidate>
        <label class="field">
          <span>Your suggestion *</span>
          <textarea name="suggestion" id="suggestionText" rows="5" maxlength="${MAX_LENGTH}"
            placeholder="e.g. The lobby lights stay on all day - a timer would save electricity."
            required data-autofocus></textarea>
        </label>
        <p class="char-count" id="suggestionCount">0 / ${MAX_LENGTH}</p>
        <p class="login-error" id="suggestionError" hidden></p>
        <div class="modal-actions">
          <button type="button" class="btn btn-ghost" data-close>Cancel</button>
          <button type="submit" class="btn btn-primary" id="suggestionSubmit">
            <span class="btn-label">Submit Suggestion</span>
          </button>
        </div>
      </form>
    `);

    const form = modal.root.querySelector('#suggestionForm');
    const box = modal.root.querySelector('#suggestionText');
    const count = modal.root.querySelector('#suggestionCount');
    const errorEl = modal.root.querySelector('#suggestionError');
    const submit = modal.root.querySelector('#suggestionSubmit');

    function setError(msg) {
      errorEl.textContent = msg || '';
      errorEl.hidden = !msg;
    }

    box.addEventListener('input', () => {
      count.textContent = `${box.value.length} / ${MAX_LENGTH}`;
      if (!errorEl.hidden) setError('');
    });

    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      setError('');
      const text = box.value.trim();

      // Mirrors CreateSuggestionRequest so the common mistakes are caught
      // before a round trip; the service enforces the same bounds regardless.
      if (!text) {
        setError('Please write your suggestion before submitting.');
        box.focus();
        return;
      }
      if (text.length < MIN_LENGTH) {
        setError(`Please write at least ${MIN_LENGTH} characters.`);
        box.focus();
        return;
      }

      submit.disabled = true;
      submit.querySelector('.btn-label').textContent = 'Submitting…';
      try {
        await storage.addSuggestion({ text });
        modal.close();
        window.CM.showToast('Thank you! Your suggestion has been sent to management.');
      } catch (err) {
        submit.disabled = false;
        submit.querySelector('.btn-label').textContent = 'Submit Suggestion';
        setError(ui.messageFromError(err, 'Could not send your suggestion'));
      }
    });
  }

  function init() {
    const btn = document.getElementById('suggestBtn');
    if (btn) btn.addEventListener('click', openSuggestionModal);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }

  window.CM = window.CM || {};
  window.CM.suggestion = { open: openSuggestionModal };
})();
