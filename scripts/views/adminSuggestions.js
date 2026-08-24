(function () {
  const ui = window.CM.ui;

  function formatDate(iso) {
    if (!iso) return '';
    try {
      const d = new Date(iso);
      return d.toLocaleDateString(undefined, {
        day: '2-digit',
        month: 'short',
        year: 'numeric',
      });
    } catch {
      return iso;
    }
  }

  const SORT_OPTIONS = [
    { value: 'newest', label: 'Newest first' },
    { value: 'oldest', label: 'Oldest first' },
  ];

  // Flat is the only dimension a suggestion has - unlike a ticket there is no
  // category and no professional to group by.
  const GROUP_OPTIONS = [
    { value: 'none', label: 'No grouping' },
    { value: 'flat', label: 'Flat' },
  ];

  function normalizeSort(v) {
    return String(v || '').toLowerCase() === 'oldest' ? 'oldest' : 'newest';
  }

  function normalizeGroup(v) {
    return String(v || '').toLowerCase() === 'flat' ? 'flat' : 'none';
  }

  function renderShell(content) {
    return `
      <section class="page">
        <button type="button" class="back-link" data-back>
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="m15 18-6-6 6-6"/>
          </svg>
          <span>Back to dashboard</span>
        </button>

        <div class="page-title">
          <h1>Resident Suggestions</h1>
          <p class="muted">Feedback and ideas residents have sent to management.</p>
        </div>

        ${content}
      </section>
    `;
  }

  function optionList(options, selected) {
    return options
      .map((o) => `<option value="${o.value}"${selected === o.value ? ' selected' : ''}>${o.label}</option>`)
      .join('');
  }

  function renderControls(state) {
    return `
      <div class="card controls-bar" role="group" aria-label="Filter and group suggestions">
        <label class="filter-field">
          <span>Group by</span>
          <select id="suggestionGroup">${optionList(GROUP_OPTIONS, state.group)}</select>
        </label>
        <label class="filter-field">
          <span>Search by flat number</span>
          <input type="search" id="suggestionSearch" placeholder="e.g. A-101" autocomplete="off" />
        </label>
        <label class="filter-field">
          <span>Sort</span>
          <select id="suggestionSort">${optionList(SORT_OPTIONS, state.sort)}</select>
        </label>
        <button type="button" class="filter-clear" id="suggestionClear" hidden>Clear</button>
      </div>
      <p class="filter-meta" id="suggestionMeta"></p>
    `;
  }

  /**
   * Renders the suggestions, optionally under collapsible flat headings. Each
   * group holds every suggestion from that flat, so a flat appears exactly once
   * however many times it has written in.
   */
  function renderSuggestionList(rows, groupBy, expanded) {
    if (groupBy === 'none') return rows.map(renderCard).join('');

    const groups = new Map();
    rows.forEach((s) => {
      const key = `Flat ${s.flat}`;
      if (!groups.has(key)) groups.set(key, []);
      groups.get(key).push(s);
    });

    return [...groups.keys()]
      .sort((a, b) => a.localeCompare(b, undefined, { numeric: true, sensitivity: 'base' }))
      .map((key) => {
        const items = groups.get(key);
        const isOpen = expanded.has(key);
        return `
      <details class="group-section" data-group="${ui.escapeHtml(key)}"${isOpen ? ' open' : ''}>
        <summary class="group-head">
          <span class="group-chevron" aria-hidden="true">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="m9 18 6-6-6-6"/>
            </svg>
          </span>
          <h3>${ui.escapeHtml(key)}</h3>
          <span class="group-count">${items.length} ${items.length === 1 ? 'suggestion' : 'suggestions'}</span>
        </summary>
        <div class="suggestion-list suggestion-list-grouped">
          ${items.map(renderCard).join('')}
        </div>
      </details>
    `;
      })
      .join('');
  }

  /**
   * The circle beside a suggestion, matching the worker avatar on the resident
   * complaint cards: it is the person's initial, not the flat's.
   *
   * The flat is the wrong thing to take a letter from - this society numbers
   * its flats (289, 291), so every avatar came out as the same digit.
   */
  function avatarLetter(suggestion) {
    const source = (suggestion.residentName || '').trim() || String(suggestion.flat || '');
    return source.charAt(0).toUpperCase() || '?';
  }

  function renderCard(s) {
    const phone = s.residentPhone
      ? `<a class="suggestion-phone" href="tel:${ui.escapeHtml(String(s.residentPhone).replace(/\s/g, ''))}">${ui.escapeHtml(s.residentPhone)}</a>`
      : '';
    return `
      <article class="card suggestion-card">
        <header class="suggestion-head">
          <div class="suggestion-who">
            <span class="suggestion-avatar" aria-hidden="true">${ui.escapeHtml(avatarLetter(s))}</span>
            <div>
              <p class="suggestion-flat">Flat ${ui.escapeHtml(s.flat)}</p>
              <p class="suggestion-name">${ui.escapeHtml(s.residentName || 'Resident')}${phone ? ' · ' : ''}${phone}</p>
            </div>
          </div>
          <span class="suggestion-date">${formatDate(s.createdAt)}</span>
        </header>
        <p class="suggestion-text">${ui.escapeHtml(s.text)}</p>
      </article>
    `;
  }

  function renderEmpty(isFiltered) {
    if (isFiltered) {
      return `
        <div class="empty-state card">
          <div class="empty-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" width="40" height="40" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="11" cy="11" r="8"/>
              <path d="m21 21-4.3-4.3"/>
            </svg>
          </div>
          <h3>No matching suggestions</h3>
          <p class="muted">No suggestion has come from a flat matching your search.</p>
          <button type="button" class="btn btn-primary" data-action="clear-search">Clear search</button>
        </div>
      `;
    }
    return `
      <div class="empty-state card">
        <div class="empty-icon" aria-hidden="true">
          <svg viewBox="0 0 24 24" width="40" height="40" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
            <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
          </svg>
        </div>
        <h3>No suggestions yet</h3>
        <p class="muted">When a resident shares a suggestion it will appear here.</p>
      </div>
    `;
  }

  async function renderAdminSuggestions(root, ctx) {
    const { navigate, params, replaceParams } = ctx;
    const storage = window.CM.storage;

    let suggestions = [];
    const state = {
      flat: (params && params.flat) || '',
      sort: normalizeSort(params && params.sort),
      group: normalizeGroup(params && params.group),
    };

    // Which flat headings are open. Kept in memory rather than the URL, the
    // same way the tickets screen does it - it is a viewing convenience, not
    // something worth sharing in a link.
    const expandedGroups = new Set();

    function bindBack() {
      const back = root.querySelector('[data-back]');
      if (back) back.addEventListener('click', () => navigate('#/admin/dashboard'));
    }

    function visible() {
      const q = state.flat.trim().toLowerCase();
      const rows = q
        ? suggestions.filter((s) => String(s.flat || '').toLowerCase().includes(q))
        : suggestions.slice();
      // The API already returns newest first; only "oldest" needs a re-sort.
      if (state.sort === 'oldest') rows.reverse();
      return rows;
    }

    /**
     * Re-renders only the list, so typing in the search box never loses focus.
     */
    function renderList() {
      const host = root.querySelector('#suggestionList');
      if (!host) return;
      const rows = visible();
      const isFiltered = state.flat.trim() !== '';

      host.innerHTML = rows.length
        ? renderSuggestionList(rows, state.group, expandedGroups)
        : renderEmpty(isFiltered);

      // <details> handles opening itself; we only remember which are open so
      // the state survives the next re-render.
      host.querySelectorAll('.group-section').forEach((section) => {
        section.addEventListener('toggle', () => {
          if (section.open) expandedGroups.add(section.dataset.group);
          else expandedGroups.delete(section.dataset.group);
        });
      });

      const clearInEmpty = host.querySelector('[data-action="clear-search"]');
      if (clearInEmpty) clearInEmpty.addEventListener('click', clearSearch);

      const meta = root.querySelector('#suggestionMeta');
      const total = `${suggestions.length} ${suggestions.length === 1 ? 'suggestion' : 'suggestions'}`;
      if (meta) {
        const scope = isFiltered ? `Showing ${rows.length} of ${total}` : total;
        const groupCount = host.querySelectorAll('.group-section').length;
        meta.textContent = groupCount
          ? `${scope} in ${groupCount} ${groupCount === 1 ? 'flat' : 'flats'}`
          : scope;
      }

      const clearBtn = root.querySelector('#suggestionClear');
      if (clearBtn) clearBtn.hidden = !controlsActive();

      const next = {};
      if (state.flat.trim()) next.flat = state.flat.trim();
      if (state.sort !== 'newest') next.sort = state.sort;
      if (state.group !== 'none') next.group = state.group;
      replaceParams('#/admin/suggestions', next);
    }

    function controlsActive() {
      return state.flat.trim() !== '' || state.sort !== 'newest' || state.group !== 'none';
    }

    function clearSearch() {
      state.flat = '';
      const input = root.querySelector('#suggestionSearch');
      if (input) {
        input.value = '';
        input.focus();
      }
      renderList();
    }

    /** The toolbar's Clear: puts every control back to its default. */
    function clearAll() {
      state.flat = '';
      state.sort = 'newest';
      state.group = 'none';
      const input = root.querySelector('#suggestionSearch');
      const sortSel = root.querySelector('#suggestionSort');
      const groupSel = root.querySelector('#suggestionGroup');
      if (input) input.value = '';
      if (sortSel) sortSel.value = 'newest';
      if (groupSel) groupSel.value = 'none';
      renderList();
    }

    function wireControls() {
      const input = root.querySelector('#suggestionSearch');
      const sortSel = root.querySelector('#suggestionSort');
      const groupSel = root.querySelector('#suggestionGroup');
      const clearBtn = root.querySelector('#suggestionClear');
      if (input) {
        input.value = state.flat;
        input.addEventListener('input', () => {
          state.flat = input.value;
          renderList();
        });
      }
      if (sortSel) {
        sortSel.addEventListener('change', () => {
          state.sort = normalizeSort(sortSel.value);
          renderList();
        });
      }
      if (groupSel) {
        groupSel.addEventListener('change', () => {
          state.group = normalizeGroup(groupSel.value);
          renderList();
        });
      }
      if (clearBtn) clearBtn.addEventListener('click', clearAll);
    }

    async function load() {
      root.innerHTML = renderShell(ui.loadingBlock('Loading suggestions…'));
      bindBack();
      try {
        suggestions = await storage.listSuggestions();
        root.innerHTML = renderShell(`
          ${renderControls(state)}
          <div class="suggestion-list" id="suggestionList"></div>
        `);
        bindBack();
        wireControls();
        renderList();
      } catch (err) {
        const msg = ui.messageFromError(err, 'Could not load suggestions.');
        root.innerHTML = renderShell(ui.errorBlock(msg, 'Retry'));
        bindBack();
        const retry = root.querySelector('[data-retry]');
        if (retry) retry.addEventListener('click', load);
      }
    }

    await load();
  }

  window.CM = window.CM || {};
  window.CM.views = window.CM.views || {};
  window.CM.views.adminSuggestions = renderAdminSuggestions;
})();
