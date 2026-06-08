/* UI helpers: toast, modal, spinner, confirm */
(function () {
  // Toast container
  function ensureToastContainer() {
    let c = document.getElementById('toast-container');
    if (!c) {
      c = document.createElement('div');
      c.id = 'toast-container';
      c.className = 'toast-container';
      document.body.appendChild(c);
    }
    return c;
  }
  function toast(message, type = 'info', duration = 3500) {
    const c = ensureToastContainer();
    const el = document.createElement('div');
    el.className = 'toast ' + type;
    el.textContent = message;
    c.appendChild(el);
    setTimeout(() => { el.style.opacity = '0'; el.style.transition='opacity .3s'; }, duration - 300);
    setTimeout(() => el.remove(), duration);
  }

  // Loading overlay
  function showLoading() {
    if (document.getElementById('loading-overlay')) return;
    const d = document.createElement('div');
    d.id = 'loading-overlay';
    d.className = 'loading-overlay';
    d.innerHTML = '<div class="spinner"></div>';
    document.body.appendChild(d);
  }
  function hideLoading() {
    const d = document.getElementById('loading-overlay');
    if (d) d.remove();
  }

  // Modal
  function openModal({ title, body, footer, onClose }) {
    const backdrop = document.createElement('div');
    backdrop.className = 'modal-backdrop';
    backdrop.innerHTML = `
      <div class="modal" role="dialog" aria-modal="true">
        <div class="modal-header">
          <h3>${escapeHtml(title || '')}</h3>
          <button class="close-btn" aria-label="Close">&times;</button>
        </div>
        <div class="modal-body"></div>
        ${footer ? '<div class="modal-footer"></div>' : ''}
      </div>`;
    document.body.appendChild(backdrop);
    const bodyEl = backdrop.querySelector('.modal-body');
    if (typeof body === 'string') bodyEl.innerHTML = body;
    else if (body instanceof Node) bodyEl.appendChild(body);
    if (footer) {
      const ftEl = backdrop.querySelector('.modal-footer');
      if (typeof footer === 'string') ftEl.innerHTML = footer;
      else if (footer instanceof Node) ftEl.appendChild(footer);
    }
    requestAnimationFrame(() => backdrop.classList.add('show'));
    function close() {
      backdrop.classList.remove('show');
      setTimeout(() => backdrop.remove(), 150);
      if (onClose) onClose();
    }
    backdrop.querySelector('.close-btn').addEventListener('click', close);
    backdrop.addEventListener('click', (e) => { if (e.target === backdrop) close(); });
    return { el: backdrop, close, body: bodyEl };
  }

  function confirmDialog(message, { title = 'Confirm', confirmText = 'Confirm', danger = false } = {}) {
    return new Promise((resolve) => {
      const footer = document.createElement('div');
      footer.style.display = 'flex'; footer.style.gap = '8px';
      footer.innerHTML = `
        <button class="btn btn-secondary" data-act="cancel">Cancel</button>
        <button class="btn ${danger ? 'btn-danger' : ''}" data-act="ok">${escapeHtml(confirmText)}</button>`;
      const m = openModal({ title, body: `<p>${escapeHtml(message)}</p>`, footer });
      footer.querySelector('[data-act=cancel]').onclick = () => { m.close(); resolve(false); };
      footer.querySelector('[data-act=ok]').onclick = () => { m.close(); resolve(true); };
    });
  }

  function escapeHtml(s) {
    return String(s == null ? '' : s)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }

  // Simple form validation
  function validate(form, rules) {
    let ok = true;
    Object.entries(rules).forEach(([name, rs]) => {
      const input = form.querySelector(`[name="${name}"]`);
      if (!input) return;
      const errId = `err-${name}`;
      let err = form.querySelector(`#${errId}`);
      if (!err) {
        err = document.createElement('div');
        err.id = errId; err.className = 'form-error';
        input.parentElement.appendChild(err);
      }
      err.textContent = '';
      const v = (input.value || '').trim();
      for (const r of rs) {
        if (r.required && !v) { err.textContent = r.message || 'Required'; ok = false; break; }
        if (r.minLength && v.length < r.minLength) { err.textContent = r.message || `Min ${r.minLength} chars`; ok = false; break; }
        if (r.email && v && !/^\S+@\S+\.\S+$/.test(v)) { err.textContent = r.message || 'Invalid email'; ok = false; break; }
        if (r.pattern && v && !r.pattern.test(v)) { err.textContent = r.message || 'Invalid format'; ok = false; break; }
      }
    });
    return ok;
  }

  window.UI = { toast, showLoading, hideLoading, openModal, confirmDialog, escapeHtml, validate };
})();
