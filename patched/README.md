# School Management System — Full-Stack

End-to-end build that wires the existing Kotlin/Spring Boot backend to the
vanilla HTML/CSS/JS frontend through a single Nginx entrypoint.

```
┌─────────────┐     /          ┌──────────────┐    JDBC      ┌──────────┐
│  Browser    │ ─────────────► │   Nginx :80  │              │          │
│             │     /api/*     │  static + ────┼─► backend ─►│  MySQL   │
└─────────────┘ ──────────────►│   reverse    │     :8080    │  :3306   │
                               └──────────────┘              └──────────┘
```

## Repository layout

```
school-management-fullstack/
├── backend/                  Spring Boot + Kotlin (Gradle)
│   ├── src/main/kotlin/...
│   ├── src/main/resources/   application.properties, schema.sql, seed.sql
│   ├── build.gradle.kts
│   └── Dockerfile
├── frontend/                 HTML / CSS / Vanilla JS SPA-style pages
│   ├── index.html
│   ├── css/  js/  pages/  assets/
│   └── js/config.js          API_BASE = '/api' (same-origin via Nginx)
├── deploy/nginx/
│   ├── nginx.conf            serves frontend, proxies /api to backend
│   └── Dockerfile
├── docker-compose.yml        mysql + backend + frontend (one command)
└── README.md
```

## 1. Run the whole stack with Docker (recommended)

```bash
docker compose up --build
```

Then open:

| URL                                      | What                |
|------------------------------------------|---------------------|
| http://localhost                         | Frontend (Nginx)    |
| http://localhost/api/actuator/health     | Backend health      |
| http://localhost:8080/swagger-ui.html    | API documentation   |

Seed accounts (from `backend/src/main/resources/seed.sql`):

| Username     | Password    | Role         |
|--------------|-------------|--------------|
| superadmin   | Admin@123   | SUPER_ADMIN  |
| admin        | Admin@123   | ADMIN        |
| teacher1     | Teacher@123 | TEACHER      |
| student1     | Student@123 | STUDENT      |
| parent1      | Parent@123  | PARENT       |

Stop everything: `docker compose down` (add `-v` to wipe the database).

## 2. Run locally inside IntelliJ IDEA

1. **Open the backend** — `File → Open…` and point at
   `school-management-fullstack/backend`. Let Gradle import.
2. **Start MySQL** — either `docker compose up mysql` or use a local
   install with database `school_db`, user `root`, password `root`.
3. **Run** `com.school.sms.SchoolManagementApplication` (green ► icon).
   The backend listens on `http://localhost:8080`.
4. **Open the frontend** — easiest is to serve the `frontend/` folder
   with any static server, e.g.:
   ```bash
   cd frontend
   python3 -m http.server 5173
   ```
   Open `http://localhost:5173`. Because the origin differs from the
   backend, `frontend/js/config.js` automatically falls back to
   `http://localhost:8080/api`. CORS on the backend already allows it.

## 3. How the connection works

* **Frontend** issues `fetch()` calls from `frontend/js/api.js`
  against `window.SMS_CONFIG.API_BASE`.
* **`API_BASE`** is `'/api'` when the page is served over HTTP (same
  origin → Nginx proxies to the backend) and
  `'http://localhost:8080/api'` when opened directly from disk.
* **Auth** — `POST /api/auth/login` returns
  `{ accessToken, refreshToken, user }`. The client stores them in
  `localStorage` and adds `Authorization: Bearer <token>` to every
  subsequent request. A 401 triggers a one-shot refresh via
  `POST /api/auth/refresh`.
* **CORS** — `SecurityConfig.cors()` in
  `backend/src/main/kotlin/com/school/sms/security/Security.kt`
  allows all origins / headers / methods, so dev setups on any port
  work without further configuration. Tighten it before going to
  production.
* **Role-based UI** — `frontend/js/layout.js` reads the cached user’s
  roles and hides nav items the user is not allowed to see; the
  backend enforces the same rules with `@PreAuthorize` and the custom
  permission evaluator.

## 4. Useful endpoints

| Method | Path                          | Notes                       |
|--------|-------------------------------|-----------------------------|
| POST   | `/api/auth/login`             | `{ usernameOrEmail, password }` |
| POST   | `/api/auth/refresh`           | `{ refreshToken }`          |
| POST   | `/api/auth/logout`            | invalidates the refresh token |
| GET    | `/api/dashboard`              | role-aware summary          |
| CRUD   | `/api/users`, `/api/students`, `/api/teachers`, `/api/parents`, `/api/classes`, `/api/subjects`, `/api/attendance`, `/api/exams`, `/api/marks`, `/api/fees`, `/api/assignments`, `/api/library`, `/api/notices` | standard `?page&size&sort&search` |
| POST   | `/api/files/upload`           | multipart profile / assignment uploads |
| GET    | `/api/reports/*`              | PDF / Excel / CSV exports   |

Full schemas live at `http://localhost:8080/swagger-ui.html`.

## 5. Production checklist

- Replace `JWT_SECRET` with a freshly generated 256-bit base64 secret.
- Restrict CORS origins in `SecurityConfig.cors()` to your real domain.
- Put Nginx behind TLS (terminate HTTPS at the proxy / load balancer).
- Point the backend at a managed MySQL with backups.
- Mount `sms_backend_storage` to durable storage (S3, EFS, …).
- Set `MAIL_ENABLED=true` and provide SMTP credentials for
  notifications.
