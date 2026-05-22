(function () {
  function renderRaise(root, ctx) {
    const { session, navigate } = ctx;
    const storage = window.CM.storage;
    const showToast = window.CM.showToast;

    const options = storage.CATEGORIES.map(
      (c) => `<option value="${c}">${c}</option>`
    ).join('');

    root.innerHTML = `
      <section class="page page-narrow">
        <button type="button" class="back-link" data-route="#/dashboard">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="m15 18-6-6 6-6"/>
          </svg>
          <span>Back to dashboard</span>
        </button>

        <div class="page-title">
          <h1>Raise a New Issue</h1>
          <p class="muted">Tell us what's wrong in Flat ${session.flat} and we'll assign a professional.</p>
        </div>

        <form class="card form-card" id="raiseForm" novalidate>
          <div class="form-section-head">
            <h2>Complaint details</h2>
            <p class="muted">Choose a category and describe the issue clearly.</p>
          </div>

          <label class="field">
            <span>Category</span>
            <select name="category" id="raiseCategory" required>
              <option value="" disabled selected>Select an issue category</option>
              ${options}
            </select>
          </label>

          <label class="field">
            <span>Description</span>
            <textarea
              name="description"
              id="raiseDescription"
              rows="6"
              maxlength="2000"
              placeholder="Describe the problem, where it is, and when it started..."
              required
            ></textarea>
            <span class="counter" id="raiseCounter">0 / 2000</span>
          </label>

          <button type="submit" class="btn btn-primary btn-block" id="raiseSubmit">
            <span class="btn-label">Submit Complaint</span>
          </button>
        </form>
      </section>
    `;

    const form = root.querySelector('#raiseForm');
    const desc = form.elements.description;
    const counter = root.querySelector('#raiseCounter');
    const submit = root.querySelector('#raiseSubmit');

    desc.addEventListener('input', () => {
      counter.textContent = `${desc.value.length} / 2000`;
    });

    root.querySelector('.back-link').addEventListener('click', () => navigate('#/dashboard'));

    function setBusy(busy) {
      submit.disabled = busy;
      submit.querySelector('.btn-label').textContent = busy ? 'Submitting\u2026' : 'Submit Complaint';
    }

    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      const category = form.elements.category.value;
      const description = desc.value.trim();

      if (!category) {
        showToast('Please select a category', 'error');
        form.elements.category.focus();
        return;
      }
      if (description.length < 5) {
        showToast('Please add a short description (at least 5 characters)', 'error');
        desc.focus();
        return;
      }

      setBusy(true);
      try {
        await storage.addComplaint({ category, description });
        showToast('Complaint submitted successfully');
        navigate('#/complaints');
      } catch (err) {
        const ui = window.CM.ui;
        showToast(ui.messageFromError(err, 'Could not submit complaint'), 'error');
        setBusy(false);
      }
    });
  }

  window.CM = window.CM || {};
  window.CM.views = window.CM.views || {};
  window.CM.views.raise = renderRaise;
})();
