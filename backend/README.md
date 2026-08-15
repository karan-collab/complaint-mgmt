# SocietyCare Backend

Spring Boot 2.7 + Java 11 REST backend for the SocietyCare complaint-management
app. **Batches 1–3 are done**: scaffold + read APIs (Batch 1), write APIs +
validation + global error handler (Batch 2), and JWT-based authentication +
role / ownership authorization (Batch 3). Docker, CI/CD, and frontend API wiring
are in Batch 4 (see repo root README and `deploy/README.md`).

## Requirements

- **JDK 11** (Spring Boot 2.7.x is the last line that supports Java 11; the
  project is configured for 11).
- **Maven 3.9+** (or use the Maven wrapper once added).
- **PostgreSQL 16** running locally for the `dev` profile, OR rely on H2 for
  tests only.
- Network access to **Maven Central** (`https://repo.maven.apache.org`).

## Maven settings

This project ships its own [`settings.xml`](./settings.xml) that pulls
dependencies from Maven Central directly and ignores any corporate mirror or
internal repository configured in `~/.m2/settings.xml`. Always pass it on the
command line:

```powershell
mvn -s settings.xml <goal>
```

For example: `mvn -s settings.xml verify`. The flag is required for every
Maven invocation in this project; without it you will pick up whatever global
mirror your machine has configured.

## Project layout

```
backend/
├── pom.xml
├── src/main/java/com/societycare/
│   ├── SocietyCareApplication.java
│   ├── config/         OpenAPI configuration
│   ├── common/         NotFoundException, MetaController
│   ├── admin/          Admin entity + repository
│   ├── resident/       Resident entity + repository
│   ├── professional/   Professional entity + repository
│   ├── status/         Status entity + repository (lookup table)
│   ├── complaint/      Complaint entity, repository, service, controller, DTOs, mapper, Category enum
│   └── seed/           DevSeeder (mirrors scripts/storage.js buildSeed())
├── src/main/resources/
│   ├── application.yml          base config
│   ├── application-dev.yml      local Postgres + Swagger + seeder on
│   ├── application-prod.yml     env-driven DB creds, Swagger off
│   └── db/migration/
│       ├── V1__schema.sql       all 5 tables + CHECK constraints + indexes
│       ├── V2__seed_status.sql  3 status rows
│       └── V3__resident_phone.sql  optional phone on t_resident
└── src/test/
    ├── java/com/societycare/
    │   ├── SocietyCareApplicationTests.java
    │   └── complaint/ComplaintControllerTest.java
    └── resources/application-test.yml   H2 (PostgreSQL mode)
```

## Running locally (dev profile)

1. Start a Postgres database:

   ```powershell
   docker run --name societycare-pg -d `
     -e POSTGRES_DB=societycare `
     -e POSTGRES_USER=societycare `
     -e POSTGRES_PASSWORD=societycare `
     -p 5432:5432 postgres:16-alpine
   ```

2. Run the app:

   ```powershell
   mvn -s settings.xml spring-boot:run
   ```

   The `dev` profile is active by default. On first run, Flyway creates the
   schema and seeds `t_status`, then `DevSeeder` inserts 5 residents, 4
   professionals, and 8 complaints (the same set rendered today by
   `scripts/storage.js`).

3. Try it out:

   ```powershell
   curl http://localhost:8080/api/v1/complaints
   curl "http://localhost:8080/api/v1/complaints?flat=A-101"
   curl http://localhost:8080/api/v1/meta/categories
   curl http://localhost:8080/api/v1/meta/statuses
   ```

   Browse the API docs: <http://localhost:8080/swagger-ui.html>

   Health check: <http://localhost:8080/actuator/health>

## Running tests

```powershell
mvn -s settings.xml verify
```

Tests use an in-memory H2 database in PostgreSQL compatibility mode and run
the same Flyway migrations as production.

## API surface

Authentication is required on every endpoint except the ones marked
**public**. Send `Authorization: Bearer <jwt>` after logging in.

| Method | Path                                  | Auth                | Description                              |
| ------ | ------------------------------------- | ------------------- | ---------------------------------------- |
| POST   | `/api/v1/auth/resident/login`         | public              | flatNo + password ⇒ JWT                  |
| POST   | `/api/v1/auth/admin/login`            | public              | username + password ⇒ JWT                |
| GET    | `/api/v1/auth/me`                     | any role            | Echo the current principal               |
| GET    | `/api/v1/meta/categories`             | public              | The five complaint categories            |
| GET    | `/api/v1/meta/statuses`               | public              | The three lifecycle statuses             |
| GET    | `/actuator/health`                    | public              | Liveness                                 |
| GET    | `/api/v1/complaints`                  | admin or resident   | Admin: all flats. Resident: own flat.    |
| GET    | `/api/v1/complaints?flat=A-101`       | admin or resident   | Resident may pass only their own flat    |
| GET    | `/api/v1/complaints/{id}`             | admin or resident   | Resident may only view own-flat items    |
| POST   | `/api/v1/complaints`                  | resident            | residentId derived from JWT              |
| POST   | `/api/v1/complaints/{id}/assign`      | admin               | Assign **or reassign**; status ⇒ Pending Work |
| POST   | `/api/v1/complaints/{id}/unassign`    | admin               | Drop the worker; status ⇒ Assignment Pending |
| POST   | `/api/v1/complaints/{id}/complete`    | admin               | Status ⇒ Complete                        |
| POST   | `/api/v1/complaints/{id}/reopen`      | admin               | Complete ⇒ Assignment Pending; clears worker + timestamps |
| GET    | `/api/v1/professionals`               | admin or resident   | Optional `?category=`                    |
| POST/PATCH/DELETE `/api/v1/professionals[/{id}]` | admin   | CRUD; delete blocked if referenced       |
| GET    | `/api/v1/admin/residents`             | admin               | List residents                           |
| POST   | `/api/v1/admin/residents`             | admin               | Create a resident with a password        |
| PATCH  | `/api/v1/admin/residents/{id}`        | admin               | Edit name / flatNo / phone (null = keep) |
| DELETE | `/api/v1/admin/residents/{id}`        | admin               | Delete resident **and their complaints** |
| POST   | `/api/v1/admin/residents/{id}/password` | admin             | Force-reset a resident's password        |

### Authentication

- HS256 JWTs signed with `security.jwt.secret` (base64 ≥ 256 bits).
- TTL is configured via `security.jwt.ttl-minutes` (default **120**, i.e. 2 h).
- The token carries `role`, `sub` (userId), `name`, and `flat` (residents only).

### Dev credentials (when `seed.enabled=true`)

| Role     | Username / Flat | Password |
| -------- | --------------- | -------- |
| Admin    | `admin`         | `admin`  |
| Resident | `A-101`         | `pass123`|
| Resident | `B-202`         | `pass123`|
| Resident | `C-303`         | `pass123`|
| Resident | `D-404`         | `pass123`|
| Resident | `E-505`         | `pass123`|

### Error format

Every non-2xx response uses the same body shape (RFC 7807-style):

```json
{
  "title": "Access denied",
  "status": 403,
  "detail": "You do not have permission to perform this action",
  "instance": "/api/v1/complaints",
  "timestamp": "2026-05-19T01:02:03.456Z",
  "errors": []
}
```

## Docker

From the repository root:

```bash
cp .env.example .env
docker compose --env-file .env up --build
```

The API runs with `prod` profile against Compose Postgres. See [`deploy/README.md`](../deploy/README.md).

For zero-install manual testing, keep using the H2 profile:

```powershell
mvn -s settings.xml spring-boot:run "-Dspring-boot.run.profiles=dev,h2"
```

## CI/CD

- CI: `.github/workflows/ci.yml` — `mvn -s settings.xml verify`
- Deploy: `.github/workflows/deploy.yml` — GHCR images + optional VPS deploy

## Post-Batch 4

- Photo upload
- Additional password-reset flows beyond admin reset
