/* Generic CRUD table renderer with search, sort, pagination, filters */
(function () {
  /**
   * mount(container, {
   *   endpoint, columns: [{key, label, sortable, render(row)}],
   *   searchFields: ['name','email'],
   *   filters: [{key,label,options:[{value,label}]}],
   *   pageSize, formFields: [{name,label,type,options,required,placeholder}],
   *   canCreate, canEdit, canDelete, title
   * })
   */
  function mount(container, opts) {
    // Centralized RBAC: if `resource` is set, derive permissions from window.Permission
    // and IGNORE any hardcoded canCreate/canEdit/canDelete passed by callers.
    if (opts.resource && window.Permission) {
      opts.canCreate = Permission.canCreate(opts.resource);
      opts.canEdit   = Permission.canEdit(opts.resource);
      opts.canDelete = Permission.canDelete(opts.resource);
    }
    const state = {
      page: 0,
      size: opts.pageSize || 10,
      sortKey: opts.defaultSort || null,
      sortDir: 'asc',
      search: '',
      filters: {},
      total: 0,
      items: []
    };

    container.innerHTML = `
      <div class="card">
        <div class="card-header">
          <div class="card-title">${UI.escapeHtml(opts.title || 'Records')}</div>
          ${opts.canCreate ? `<button class="btn" id="btn-create">+ Add New</button>` : ''}
        </div>
        <div class="toolbar">
          <input type="text" class="input search" id="search-input" placeholder="Search..." />
          ${(opts.filters || []).map(f => `
            <select class="input" data-filter="${f.key}" style="max-width:180px;">
              <option value="">${UI.escapeHtml(f.label)}: All</option>
              ${f.options.map(o => `<option value="${UI.escapeHtml(o.value)}">${UI.escapeHtml(o.label)}</option>`).join('')}
            </select>`).join('')}
        </div>
        <div class="table-wrap">
          <table class="data-table" id="data-table">
            <thead><tr>
              ${opts.columns.map(c => `<th data-key="${c.key}" ${c.sortable ? 'class="sortable"' : ''}>${UI.escapeHtml(c.label)}<span class="sort-ind"></span></th>`).join('')}
              ${(opts.canEdit || opts.canDelete) ? '<th>Actions</th>' : ''}
            </tr></thead>
            <tbody><tr><td colspan="99"><div class="spinner"></div></td></tr></tbody>
          </table>
        </div>
        <div class="pagination">
          <div id="pg-info" style="font-size:13px; color:var(--muted);"></div>
          <div class="pages" id="pg-pages"></div>
        </div>
      </div>`;

    const table = container.querySelector('#data-table');
    const tbody = table.querySelector('tbody');
    const searchInput = container.querySelector('#search-input');
    const pgInfo = container.querySelector('#pg-info');
    const pgPages = container.querySelector('#pg-pages');

    let searchTimer;
    searchInput.addEventListener('input', () => {
      clearTimeout(searchTimer);
      searchTimer = setTimeout(() => { state.search = searchInput.value.trim(); state.page = 0; load(); }, 300);
    });

    container.querySelectorAll('[data-filter]').forEach(sel => {
      sel.addEventListener('change', () => {
        state.filters[sel.dataset.filter] = sel.value;
        state.page = 0; load();
      });
    });

    table.querySelectorAll('th.sortable').forEach(th => {
      th.addEventListener('click', () => {
        const k = th.dataset.key;
        if (state.sortKey === k) state.sortDir = state.sortDir === 'asc' ? 'desc' : 'asc';
        else { state.sortKey = k; state.sortDir = 'asc'; }
        load();
      });
    });

    if (opts.canCreate) {
      container.querySelector('#btn-create').onclick = () => openForm(null);
    }

    async function load() {
      tbody.innerHTML = '<tr><td colspan="99"><div class="spinner"></div></td></tr>';
      const params = new URLSearchParams();
      params.set('page', state.page);
      params.set('size', state.size);
      if (state.search) params.set('search', state.search);
      if (state.sortKey) params.set('sort', `${state.sortKey},${state.sortDir}`);
      Object.entries(state.filters).forEach(([k, v]) => { if (v) params.set(k, v); });
      try {
        const res = await API.get(`${opts.endpoint}?${params.toString()}`);
        const items = res.content || res.items || res.data || (Array.isArray(res) ? res : []);
        const total = res.totalElements ?? res.total ?? items.length;
        state.items = items;
        state.total = total;
        renderRows();
        renderPager();
        updateSortIndicators();
      } catch (e) {
        tbody.innerHTML = `<tr><td colspan="99" class="empty-state">Failed to load: ${UI.escapeHtml(e.message)}</td></tr>`;
      }
    }

    function updateSortIndicators() {
      table.querySelectorAll('th').forEach(th => {
        const ind = th.querySelector('.sort-ind'); if (!ind) return;
        ind.textContent = th.dataset.key === state.sortKey ? (state.sortDir === 'asc' ? '▲' : '▼') : '';
      });
    }

    function renderRows() {
      if (!state.items.length) {
        tbody.innerHTML = `<tr><td colspan="99"><div class="empty-state"><div class="ico">📭</div>No records found</div></td></tr>`;
        return;
      }
      tbody.innerHTML = state.items.map((row, idx) => {
        const cells = opts.columns.map(c => {
          const val = c.render ? c.render(row) : UI.escapeHtml(getPath(row, c.key));
          return `<td>${val}</td>`;
        }).join('');
        const actions = (opts.canEdit || opts.canDelete) ? `<td>
          ${opts.canEdit ? `<button class="btn btn-sm btn-secondary" data-act="edit" data-i="${idx}">Edit</button>` : ''}
          ${opts.canDelete ? `<button class="btn btn-sm btn-danger" data-act="del" data-i="${idx}">Delete</button>` : ''}
        </td>` : '';
        return `<tr>${cells}${actions}</tr>`;
      }).join('');
      tbody.querySelectorAll('[data-act=edit]').forEach(b => b.onclick = () => openForm(state.items[+b.dataset.i]));
      tbody.querySelectorAll('[data-act=del]').forEach(b => b.onclick = () => deleteRow(state.items[+b.dataset.i]));
    }

    function renderPager() {
      const pages = Math.max(1, Math.ceil(state.total / state.size));
      const cur = state.page + 1;
      pgInfo.textContent = `Showing ${state.items.length ? state.page * state.size + 1 : 0}–${state.page * state.size + state.items.length} of ${state.total}`;
      let html = `<button class="page-btn" ${state.page === 0 ? 'disabled' : ''} data-p="prev">‹</button>`;
      const start = Math.max(1, cur - 2), end = Math.min(pages, cur + 2);
      for (let p = start; p <= end; p++) html += `<button class="page-btn ${p === cur ? 'active' : ''}" data-p="${p - 1}">${p}</button>`;
      html += `<button class="page-btn" ${cur >= pages ? 'disabled' : ''} data-p="next">›</button>`;
      pgPages.innerHTML = html;
      pgPages.querySelectorAll('.page-btn').forEach(b => b.onclick = () => {
        if (b.dataset.p === 'prev') state.page = Math.max(0, state.page - 1);
        else if (b.dataset.p === 'next') state.page = Math.min(pages - 1, state.page + 1);
        else state.page = +b.dataset.p;
        load();
      });
    }

    function openForm(row) {
      const isEdit = !!row;
      const form = document.createElement('form');
      form.innerHTML = (opts.formFields || []).map(f => fieldHtml(f, row)).join('');
      const footer = document.createElement('div');
      footer.innerHTML = `<button type="button" class="btn btn-secondary" data-act="cancel">Cancel</button>
                         <button type="submit" class="btn">${isEdit ? 'Update' : 'Create'}</button>`;
      const m = UI.openModal({ title: (isEdit ? 'Edit ' : 'Create ') + (opts.title || 'Record'), body: form, footer });
      footer.querySelector('[data-act=cancel]').onclick = () => m.close();
      form.onsubmit = async (e) => {
        e.preventDefault();
        const rules = {};
        (opts.formFields || []).forEach(f => { if (f.required) rules[f.name] = [{ required: true }]; });
        if (!UI.validate(form, rules)) return;
        const payload = {};
        (opts.formFields || []).forEach(f => {
          const el = form.querySelector(`[name="${f.name}"]`);
          let v = el.value;
          if (f.type === 'number') v = v === '' ? null : Number(v);
          if (f.type === 'checkbox') v = el.checked;
          // Skip empty optional fields so backend update DTOs don't get blank overwrites
          if ((v === '' || v === null || v === undefined) && !f.required) return;
          payload[f.name] = v;
        });
        try {
          UI.showLoading();
          if (isEdit) await API.put(`${opts.endpoint}/${row.id}`, payload);
          else await API.post(opts.endpoint, payload);
          UI.toast(isEdit ? 'Updated successfully' : 'Created successfully', 'success');
          m.close();
          load();
        } catch (err) {
          UI.toast(err.message || 'Save failed', 'error');
        } finally { UI.hideLoading(); }
      };
      footer.querySelector('button[type=submit]').onclick = () => form.requestSubmit();
    }

    async function deleteRow(row) {
      const ok = await UI.confirmDialog('Delete this record? This cannot be undone.', { confirmText: 'Delete', danger: true });
      if (!ok) return;
      try {
        UI.showLoading();
        await API.del(`${opts.endpoint}/${row.id}`);
        UI.toast('Deleted', 'success');
        load();
      } catch (e) { UI.toast(e.message || 'Delete failed', 'error'); }
      finally { UI.hideLoading(); }
    }

    function fieldHtml(f, row) {
      const v = row ? getPath(row, f.name) : (f.default ?? '');
      if (f.type === 'select') {
        return `<div class="form-group"><label>${UI.escapeHtml(f.label)}</label>
          <select name="${f.name}" class="input">${(f.options || []).map(o => `<option value="${UI.escapeHtml(o.value)}" ${String(v)===String(o.value)?'selected':''}>${UI.escapeHtml(o.label)}</option>`).join('')}</select></div>`;
      }
      if (f.type === 'textarea') {
        return `<div class="form-group"><label>${UI.escapeHtml(f.label)}</label>
          <textarea name="${f.name}" class="input" rows="3">${UI.escapeHtml(v || '')}</textarea></div>`;
      }
      if (f.type === 'checkbox') {
        return `<div class="form-group"><label><input type="checkbox" name="${f.name}" ${v ? 'checked' : ''}/> ${UI.escapeHtml(f.label)}</label></div>`;
      }
      return `<div class="form-group"><label>${UI.escapeHtml(f.label)}</label>
        <input class="input" name="${f.name}" type="${f.type || 'text'}" value="${UI.escapeHtml(v ?? '')}" placeholder="${UI.escapeHtml(f.placeholder || '')}"/></div>`;
    }

    function getPath(obj, path) {
      if (obj == null) return '';
      return path.split('.').reduce((o, k) => (o == null ? '' : o[k]), obj) ?? '';
    }

    load();
    return { reload: load, state };
  }

  window.CRUD = { mount };
})();
