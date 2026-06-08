/* Sidebar + Navbar layout with role-based visibility */
(function () {
  const NAV = [
    { key: 'dashboard',  label: 'Dashboard',  icon: '🏠', href: 'dashboard.html',  roles: '*' },
    { key: 'users',      label: 'Users',      icon: '👤', href: 'users.html',      roles: ['SUPER_ADMIN', 'ADMIN'] },
    { key: 'roles',      label: 'Roles',      icon: '🛡️', href: 'roles.html',      roles: ['SUPER_ADMIN'] },
    { key: 'teachers',   label: 'Teachers',   icon: '👨‍🏫', href: 'teachers.html',   roles: ['SUPER_ADMIN', 'ADMIN'] },
    { key: 'students',   label: 'Students',   icon: '🎓', href: 'students.html',   roles: ['SUPER_ADMIN', 'ADMIN', 'TEACHER'] },
    { key: 'parents',    label: 'Parents',    icon: '👪', href: 'parents.html',    roles: ['SUPER_ADMIN', 'ADMIN'] },
    { key: 'classes',    label: 'Classes',    icon: '🏫', href: 'classes.html',    roles: ['SUPER_ADMIN', 'ADMIN', 'TEACHER'] },
    { key: 'subjects',   label: 'Subjects',   icon: '📚', href: 'subjects.html',   roles: ['SUPER_ADMIN', 'ADMIN', 'TEACHER'] },
    { key: 'attendance', label: 'Attendance', icon: '📋', href: 'attendance.html', roles: '*' },
    { key: 'marks',      label: 'Marks',      icon: '📝', href: 'marks.html',      roles: '*' },
    { key: 'exams',      label: 'Exams',      icon: '🧪', href: 'exams.html',      roles: ['SUPER_ADMIN', 'ADMIN', 'TEACHER', 'STUDENT', 'PARENT'] },
    { key: 'fees',       label: 'Fees',       icon: '💳', href: 'fees.html',       roles: ['SUPER_ADMIN', 'ADMIN', 'ACCOUNTANT', 'STUDENT', 'PARENT'] },
    { key: 'assignments',label: 'Assignments',icon: '📄', href: 'assignments.html',roles: ['SUPER_ADMIN', 'ADMIN', 'TEACHER', 'STUDENT', 'PARENT'] },
    { key: 'library',    label: 'Library',    icon: '📖', href: 'library.html',    roles: ['SUPER_ADMIN', 'ADMIN', 'LIBRARIAN', 'TEACHER', 'STUDENT'] },
    { key: 'notices',    label: 'Notices',    icon: '📢', href: 'notices.html',    roles: '*' },
    { key: 'settings',   label: 'Settings',   icon: '⚙️', href: 'settings.html',   roles: '*' }
  ];

  function userRoles() {
    const u = API.user.get();
    if (!u) return [];
    if (Array.isArray(u.roles)) return u.roles.map(r => typeof r === 'string' ? r : (r.name || r.role));
    if (u.role) return [u.role];
    return [];
  }

  function hasAccess(item) {
    if (item.roles === '*') return true;
    const roles = userRoles();
    return roles.some(r => item.roles.includes(r));
  }

  function renderLayout(activeKey, pageTitle) {
    // Auth guard
    if (!API.user.getToken()) { location.href = 'login.html'; return; }
    const user = API.user.get() || { fullName: 'User', username: 'user' };

    const sidebarItems = NAV.filter(hasAccess).map(it => `
      <a href="${it.href}" class="nav-item ${it.key === activeKey ? 'active' : ''}">
        <span class="ico">${it.icon}</span>${it.label}
      </a>`).join('');

    const initials = (user.fullName || user.username || 'U').split(' ').map(s=>s[0]).slice(0,2).join('').toUpperCase();
    const rolesLabel = userRoles().join(', ') || '—';

    const html = `
      <div class="layout">
        <aside class="sidebar" id="sidebar">
          <div class="brand"><div class="logo-sq">S</div><div>SMS Admin</div></div>
          <div class="nav-section">
            <div class="label">Main</div>
            ${sidebarItems}
          </div>
          <div class="nav-section">
            <div class="label">Account</div>
            <a href="#" id="logout-link" class="nav-item"><span class="ico">🚪</span>Logout</a>
          </div>
        </aside>
        <div class="main">
          <div class="navbar">
            <div style="display:flex; align-items:center; gap:10px;">
              <button class="icon-btn menu-toggle" id="menu-toggle">☰</button>
              <div class="page-title">${UI.escapeHtml(pageTitle)}</div>
            </div>
            <div class="user-box">
              <a href="settings.html" class="icon-btn" title="Settings" style="font-size:18px; text-decoration:none;">⚙️</a>
              <div style="text-align:right;">
                <div style="font-size:13px; font-weight:600;">${UI.escapeHtml(user.fullName || user.username)}</div>
                <div style="font-size:11px; color:var(--muted);">${UI.escapeHtml(rolesLabel)}</div>
              </div>
              <a href="settings.html" class="avatar" id="nav-avatar" title="Profile settings" style="text-decoration:none; display:inline-flex; align-items:center; justify-content:center; overflow:hidden;">${UI.escapeHtml(initials)}</a>
            </div>
          </div>
          <div class="content" id="page-content"></div>
        </div>
      </div>`;
    document.body.insertAdjacentHTML('afterbegin', html);

    // Load profile image into navbar avatar if available
    if (user.profileImagePath) {
      API.fetchBlob('/files/' + encodeURIComponent(user.profileImagePath) + '/view')
        .then(b => {
          const el = document.getElementById('nav-avatar');
          if (el) {
            const url = URL.createObjectURL(b);
            el.innerHTML = `<img src="${url}" alt="avatar" style="width:100%;height:100%;object-fit:cover;">`;
          }
        }).catch(() => {});
    }

    document.getElementById('menu-toggle').onclick = () => {
      document.getElementById('sidebar').classList.toggle('open');
    };
    document.getElementById('logout-link').onclick = async (e) => {
      e.preventDefault();
      const ok = await UI.confirmDialog('Log out of your account?', { confirmText: 'Logout', danger: true });
      if (!ok) return;
      await API.auth.logout();
      location.href = 'login.html';
    };
  }

  function requireRoles(allowed) {
    const roles = userRoles();
    if (allowed === '*') return true;
    const ok = roles.some(r => allowed.includes(r));
    if (!ok) {
      const c = document.getElementById('page-content');
      if (c) c.innerHTML = `<div class="card empty-state"><div class="ico">🚫</div><h3>Access denied</h3><p>You don't have permission to view this page.</p></div>`;
    }
    return ok;
  }

  window.Layout = { render: renderLayout, requireRoles, userRoles, hasAccess, NAV };
})();
