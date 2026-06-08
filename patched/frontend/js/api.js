/* Reusable API client using fetch() */
(function () {
  const cfg = window.SMS_CONFIG;

  function getToken() { return localStorage.getItem(cfg.TOKEN_KEY); }
  function setToken(t) { localStorage.setItem(cfg.TOKEN_KEY, t); }
  function setRefresh(t) { localStorage.setItem(cfg.REFRESH_KEY, t); }
  function getRefresh() { return localStorage.getItem(cfg.REFRESH_KEY); }
  function setUser(u) { localStorage.setItem(cfg.USER_KEY, JSON.stringify(u)); }
  function getUser() {
    try { return JSON.parse(localStorage.getItem(cfg.USER_KEY) || 'null'); }
    catch { return null; }
  }
  function clearAuth() {
    localStorage.removeItem(cfg.TOKEN_KEY);
    localStorage.removeItem(cfg.REFRESH_KEY);
    localStorage.removeItem(cfg.USER_KEY);
  }

  async function request(path, options = {}) {
    const url = path.startsWith('http') ? path : cfg.API_BASE + path;
    const headers = Object.assign(
      { 'Content-Type': 'application/json', 'Accept': 'application/json' },
      options.headers || {}
    );
    const token = getToken();
    if (token) headers['Authorization'] = 'Bearer ' + token;

    const res = await fetch(url, {
      method: options.method || 'GET',
      headers,
      body: options.body ? JSON.stringify(options.body) : undefined
    });

    if (res.status === 401) {
      // Try refresh once
      const refreshed = await tryRefresh();
      if (refreshed) {
        headers['Authorization'] = 'Bearer ' + getToken();
        const retry = await fetch(url, {
          method: options.method || 'GET',
          headers,
          body: options.body ? JSON.stringify(options.body) : undefined
        });
        return handleResponse(retry);
      } else {
        clearAuth();
        if (!location.pathname.endsWith('login.html')) {
          location.href = (location.pathname.includes('/pages/') ? '' : 'pages/') + 'login.html';
        }
        throw new Error('Unauthorized');
      }
    }
    return handleResponse(res);
  }

  async function handleResponse(res) {
    const ct = res.headers.get('content-type') || '';
    const data = ct.includes('application/json') ? await res.json().catch(() => ({})) : await res.text();
    if (!res.ok) {
      const msg = (data && (data.message || data.error)) || `Request failed (${res.status})`;
      const err = new Error(msg);
      err.status = res.status;
      err.data = data;
      throw err;
    }
    // Unwrap ApiResponse { success, data, message }
    if (data && typeof data === 'object' && 'data' in data && 'success' in data) return data.data;
    return data;
  }

  async function tryRefresh() {
    const r = getRefresh();
    if (!r) return false;
    try {
      const res = await fetch(cfg.API_BASE + '/auth/refresh', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken: r })
      });
      if (!res.ok) return false;
      const json = await res.json();
      const payload = json.data || json;
      if (payload.accessToken) setToken(payload.accessToken);
      if (payload.refreshToken) setRefresh(payload.refreshToken);
      return !!payload.accessToken;
    } catch { return false; }
  }

  window.API = {
    get: (p) => request(p),
    post: (p, body) => request(p, { method: 'POST', body }),
    put: (p, body) => request(p, { method: 'PUT', body }),
    patch: (p, body) => request(p, { method: 'PATCH', body }),
    del: (p) => request(p, { method: 'DELETE' }),
    upload: async (path, formData) => {
      const url = path.startsWith('http') ? path : cfg.API_BASE + path;
      const headers = {};
      const token = getToken();
      if (token) headers['Authorization'] = 'Bearer ' + token;
      const res = await fetch(url, { method: 'POST', headers, body: formData });
      return handleResponse(res);
    },
    fetchBlob: async (path) => {
      const url = path.startsWith('http') ? path : cfg.API_BASE + path;
      const headers = {};
      const token = getToken();
      if (token) headers['Authorization'] = 'Bearer ' + token;
      const res = await fetch(url, { headers });
      if (!res.ok) throw new Error('Image load failed');
      return res.blob();
    },
    auth: {
      login: async (username, password) => {
        const data = await request('/auth/login', { method: 'POST', body: { username, password } });
        if (data.accessToken) setToken(data.accessToken);
        if (data.refreshToken) setRefresh(data.refreshToken);
        if (data.user) setUser(data.user);
        return data;
      },
      logout: async () => {
        try {
          await request('/auth/logout', {
            method: 'POST',
            body: { refreshToken: getRefresh() }
          });
        } finally {
          clearAuth();
        }
      },
      me: async () => {
        const u = await request('/auth/me');
        if (u) setUser(u);
        return u;
      },
      updateProfile: async (payload) => {
        const u = await request('/auth/me', { method: 'PUT', body: payload });
        if (u) setUser(u);
        return u;
      },
      changePassword: (oldPassword, newPassword) =>
        request('/auth/change-password', { method: 'POST', body: { oldPassword, newPassword } }),
      uploadProfileImage: async (file) => {
        const fd = new FormData();
        fd.append('file', file);
        const url = cfg.API_BASE + '/auth/me/profile-image';
        const headers = {};
        const token = getToken();
        if (token) headers['Authorization'] = 'Bearer ' + token;
        const res = await fetch(url, { method: 'POST', headers, body: fd });
        return handleResponse(res);
      },
      forgot: (email) => request('/auth/forgot-password', { method: 'POST', body: { email } }),
      reset:  (token, newPassword) => request('/auth/reset-password', { method: 'POST', body: { token, newPassword } })
    },
    user: { get: getUser, set: setUser, clear: clearAuth, getToken, setToken }
  };
})();
