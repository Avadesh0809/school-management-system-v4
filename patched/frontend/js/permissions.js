/* Centralized RBAC permission helpers for the frontend.
   Single source of truth — do NOT hardcode canCreate/canEdit/canDelete in pages. */
(function () {
  // Resource -> roles allowed to perform action.
  // Keep aligned with backend @PreAuthorize rules.
  const MATRIX = {
    users:       { view: ['SUPER_ADMIN','ADMIN'], create: ['SUPER_ADMIN','ADMIN'], edit: ['SUPER_ADMIN','ADMIN'], delete: ['SUPER_ADMIN','ADMIN'] },
    roles:       { view: ['SUPER_ADMIN'], create: ['SUPER_ADMIN'], edit: ['SUPER_ADMIN'], delete: ['SUPER_ADMIN'] },
    teachers:    { view: ['SUPER_ADMIN','ADMIN'], create: ['SUPER_ADMIN','ADMIN'], edit: ['SUPER_ADMIN','ADMIN'], delete: ['SUPER_ADMIN','ADMIN'] },
    students:    { view: ['SUPER_ADMIN','ADMIN','TEACHER'], create: ['SUPER_ADMIN','ADMIN'], edit: ['SUPER_ADMIN','ADMIN'], delete: ['SUPER_ADMIN','ADMIN'] },
    parents:     { view: ['SUPER_ADMIN','ADMIN'], create: ['SUPER_ADMIN','ADMIN'], edit: ['SUPER_ADMIN','ADMIN'], delete: ['SUPER_ADMIN','ADMIN'] },
    classes:     { view: ['SUPER_ADMIN','ADMIN','TEACHER'], create: ['SUPER_ADMIN','ADMIN'], edit: ['SUPER_ADMIN','ADMIN'], delete: ['SUPER_ADMIN','ADMIN'] },
    subjects:    { view: ['SUPER_ADMIN','ADMIN','TEACHER'], create: ['SUPER_ADMIN','ADMIN'], edit: ['SUPER_ADMIN','ADMIN'], delete: ['SUPER_ADMIN','ADMIN'] },
    attendance:  { view: ['SUPER_ADMIN','ADMIN','TEACHER','STUDENT','PARENT'], create: ['SUPER_ADMIN','ADMIN','TEACHER'], edit: ['SUPER_ADMIN','ADMIN','TEACHER'], delete: ['SUPER_ADMIN','ADMIN','TEACHER'] },
    exams:       { view: ['SUPER_ADMIN','ADMIN','TEACHER','STUDENT','PARENT'], create: ['SUPER_ADMIN','ADMIN','TEACHER'], edit: ['SUPER_ADMIN','ADMIN','TEACHER'], delete: ['SUPER_ADMIN','ADMIN','TEACHER'] },
    marks:       { view: ['SUPER_ADMIN','ADMIN','TEACHER','STUDENT','PARENT'], create: ['SUPER_ADMIN','ADMIN','TEACHER'], edit: ['SUPER_ADMIN','ADMIN','TEACHER'], delete: ['SUPER_ADMIN','ADMIN','TEACHER'] },
    fees:        { view: ['SUPER_ADMIN','ADMIN','ACCOUNTANT','STUDENT','PARENT'], create: ['SUPER_ADMIN','ADMIN','ACCOUNTANT'], edit: ['SUPER_ADMIN','ADMIN','ACCOUNTANT'], delete: ['SUPER_ADMIN','ADMIN','ACCOUNTANT'] },
    assignments: { view: ['SUPER_ADMIN','ADMIN','TEACHER','STUDENT','PARENT'], create: ['SUPER_ADMIN','ADMIN','TEACHER'], edit: ['SUPER_ADMIN','ADMIN','TEACHER'], delete: ['SUPER_ADMIN','ADMIN','TEACHER'] },
    library:     { view: ['SUPER_ADMIN','ADMIN','LIBRARIAN','TEACHER','STUDENT'], create: ['SUPER_ADMIN','ADMIN','LIBRARIAN'], edit: ['SUPER_ADMIN','ADMIN','LIBRARIAN'], delete: ['SUPER_ADMIN','ADMIN','LIBRARIAN'] },
    notices:     { view: ['SUPER_ADMIN','ADMIN','TEACHER','STUDENT','PARENT','LIBRARIAN','ACCOUNTANT'], create: ['SUPER_ADMIN','ADMIN'], edit: ['SUPER_ADMIN','ADMIN'], delete: ['SUPER_ADMIN','ADMIN'] }
  };

  function roles() {
    const u = (window.API && API.user && API.user.get()) || null;
    if (!u) return [];
    if (Array.isArray(u.roles)) return u.roles.map(r => typeof r === 'string' ? r : (r.name || r.role));
    if (u.role) return [u.role];
    return [];
  }

  function check(resource, action) {
    const r = MATRIX[resource]; if (!r) return false;
    const allow = r[action]; if (!allow) return false;
    const mine = roles();
    return mine.some(x => allow.includes(x));
  }

  window.Permission = {
    roles,
    hasAnyRole: (...names) => { const mine = roles(); return names.some(n => mine.includes(n)); },
    isAdmin: () => { const mine = roles(); return mine.includes('SUPER_ADMIN') || mine.includes('ADMIN'); },
    isTeacher: () => roles().includes('TEACHER'),
    isStudent: () => roles().includes('STUDENT'),
    isParent: () => roles().includes('PARENT'),
    isAccountant: () => roles().includes('ACCOUNTANT'),
    isLibrarian: () => roles().includes('LIBRARIAN'),
    canView:   (resource) => check(resource, 'view'),
    canCreate: (resource) => check(resource, 'create'),
    canEdit:   (resource) => check(resource, 'edit'),
    canDelete: (resource) => check(resource, 'delete'),
    allowedRoles: (resource, action) => (MATRIX[resource] && MATRIX[resource][action]) || []
  };
})();
