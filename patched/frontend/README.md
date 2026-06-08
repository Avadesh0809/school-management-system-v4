# School Management System — Frontend

A pure **HTML + CSS + Vanilla JavaScript** admin dashboard that consumes the Spring Boot backend REST APIs.

## Structure

```
sms-frontend/
├── index.html              # Entry redirect (login or dashboard)
├── assets/                 # Place logos / images here
├── css/
│   └── styles.css          # All UI styles
├── js/
│   ├── config.js           # API base URL & storage keys
│   ├── api.js              # fetch() based API client + JWT + refresh
│   ├── ui.js               # toast, modal, spinner, confirm, validation
│   ├── layout.js           # Sidebar + navbar + role-based nav
│   ├── crud.js             # Reusable CRUD table (search/sort/filter/paginate)
│   └── charts.js           # Canvas bar + doughnut charts
└── pages/
    ├── login.html
    ├── forgot-password.html
    ├── dashboard.html
    ├── users.html
    ├── roles.html
    ├── teachers.html
    ├── students.html
    ├── parents.html
    ├── classes.html
    ├── subjects.html
    ├── attendance.html
    ├── marks.html
    ├── exams.html
    ├── fees.html
    ├── assignments.html
    ├── library.html
    └── notices.html
```

## Configuration

Edit `js/config.js` and set `API_BASE` to point to your backend, e.g.:

```js
window.SMS_CONFIG = { API_BASE: 'http://localhost:8080/api', ... };
```

## Run

Just open `index.html` in a browser, or serve the folder:

```bash
# Python
python3 -m http.server 5500
# Node
npx serve .
```

Then open http://localhost:5500

## Features

- JWT login with token + refresh token storage in `localStorage`.
- Automatic 401 → refresh-token retry → redirect to login if refresh fails.
- Protected pages — every page calls `Layout.render()` which checks the token.
- **Role-based UI visibility**: sidebar items and pages are filtered by user roles (`SUPER_ADMIN`, `ADMIN`, `TEACHER`, `STUDENT`, `PARENT`, `ACCOUNTANT`, `LIBRARIAN`).
- Reusable **CRUD table** with search, sort, pagination, filters, create/edit modals, confirmation dialogs.
- Toast notifications, loading overlay, form validation, modal dialogs.
- Responsive sidebar (collapses to drawer under 900px).
- Dashboard with stat cards + bar/doughnut charts driven by `GET /api/dashboard`.

## Expected API conventions

- Auth: `POST /auth/login` → `{ accessToken, refreshToken, user }`.
- All responses may be wrapped as `{ success, data, message }` — the client auto-unwraps `.data`.
- Pageable responses: `{ content, totalElements }` or `{ items, total }`.
- Query params: `page`, `size`, `sort=field,asc|desc`, `search`, plus any filter keys.
