# School Management System - Backend

Production-ready Spring Boot (Kotlin) backend for a school management system with JWT auth, RBAC, file uploads, reports (PDF/Excel/CSV), email notifications, audit logging, caching, and Docker deployment.

## Tech Stack
- Kotlin 1.9 / Spring Boot 3.3 / Java 17
- Spring Security + JWT (jjwt 0.12)
- Spring Data JPA / MySQL 8 (H2 for tests)
- Caffeine cache, Spring AOP audit logging
- iText 7 (PDF), Apache POI (Excel), Commons CSV
- springdoc-openapi 2 (Swagger UI)

## Project Setup

### Prerequisites
- JDK 17+
- MySQL 8 (or use docker-compose)
- Gradle wrapper (or system gradle 8.x)

### Database Setup
```sql
CREATE DATABASE school_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```
Hibernate auto-creates tables on first run (`ddl-auto=update`).
Optional manual indexes: `src/main/resources/db/schema.sql`.

### Environment variables (optional)
| Variable | Default |
|---|---|
| `DB_USER` | root |
| `DB_PASSWORD` | root |
| `JWT_SECRET` | (built-in dev key) |
| `MAIL_HOST` / `MAIL_USER` / `MAIL_PASS` | smtp.gmail.com |
| `MAIL_ENABLED` | false |
| `STORAGE_ROOT` | ./storage |

## IntelliJ Import Guide
1. **File → Open** → choose the project root (`build.gradle.kts`).
2. IntelliJ detects Gradle. Click **Trust Project**.
3. Set Project SDK to **Java 17**.
4. Wait for Gradle sync.
5. Enable annotation processing (Settings → Build → Compiler → Annotation Processors).
6. Run `SchoolManagementApplication.kt` (green arrow next to `main`).

## Run Instructions

### Local (Gradle)
```bash
./gradlew bootRun
```

### Tests
```bash
./gradlew test
```

### Docker
```bash
docker compose up --build
```
App: http://localhost:8080  
Swagger: http://localhost:8080/swagger-ui.html

## Default Accounts
| Username | Password | Role |
|---|---|---|
| superadmin | Admin@123 | SUPER_ADMIN |
| admin | Admin@123 | ADMIN |

## Key Modules
- `entity/` JPA entities (Users, Roles, Students, Teachers, Fees, Attendance, etc.)
- `repository/` Spring Data + JpaSpecificationExecutor
- `service/` Business logic with caching (`@Cacheable`)
- `controller/` REST endpoints with `@PreAuthorize` RBAC
- `security/` JWT filter, custom AccessDenied / Unauthorized handlers
- `audit/` `@Auditable` AOP aspect → `audit_logs` table
- `files/` Multipart upload + download with metadata persistence
- `reports/` PDF / Excel / CSV export
- `email/` Async login alerts, fee reminders, assignment notifications

## API Examples
```bash
# Login
curl -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"superadmin","password":"Admin@123"}'

# Upload profile image
curl -X POST http://localhost:8080/api/users/1/profile-image \
  -H "Authorization: Bearer $TOKEN" -F file=@avatar.png

# Download student report card PDF
curl -OJ -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/reports/student/1?format=PDF"
```

## Performance
- Caffeine cache on `users`, `roles`, `students`, `teachers`, `classes`, `dashboard`
- DB indexes on email, username, attendance date, fee student_id, audit user_id
- Pageable endpoints with search/sort via `JpaSpecificationExecutor`
- Lazy fetch on all `@ManyToOne`/`@OneToOne`

## Logging
- Console + rolling file: `logs/school-management.log`
- Error-only file: `logs/error.log`
- Configurable via `logback-spring.xml`
