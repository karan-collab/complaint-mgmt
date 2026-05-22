# SocietyCare — Complaint Management

Resident and admin UI for a residential society complaint system. Plain HTML/CSS/ES modules on the frontend; Spring Boot REST API and PostgreSQL (or H2 for local dev).

## Quick start

### Option A — Docker (full stack, recommended for prod-like testing)

```bash
cp .env.example .env
# Edit passwords and JWT_SECRET, then:
docker compose --env-file .env up --build
```

Open **http://localhost**. Bootstrap the first admin:

```bash
docker compose exec -T postgres psql -U societycare -d societycare < deploy/bootstrap-admin.sql
```

Login: `admin` / `admin` (change after first login).

See [deploy/README.md](deploy/README.md) for production VPS deployment, CI/CD, and HTTPS.

### Option B — Local dev (API + frontend on two servers)

**Backend** (H2 file database):

```bash
cd backend
export JAVA_HOME=...   # JDK 11+
mvn -s settings.xml spring-boot:run "-Dspring-boot.run.profiles=dev,h2"
```

**Frontend**:

```bash
python3 -m http.server 5500
```

Open **http://localhost:5500**. API default: `http://localhost:8080/api/v1` ([`scripts/config.js`](scripts/config.js)).

| Role | Login | Password |
|------|--------|----------|
| Resident | Flat `A-101` | `pass123` |
| Admin | `admin` | `admin` |

### Option C — Open `index.html` only

Works for static preview; full features require the API (Options A or B).

## Features

- Resident login (flat + password), raise complaints, view status and assigned worker
- Admin login, ticket assignment, resident management, professional directory
- JWT auth, Flyway migrations, OpenAPI docs in dev

## Structure

```text
complaint-mgmt/
  index.html, styles/, scripts/   # SPA (hash router)
  backend/                        # Spring Boot API
  docker/                         # nginx config + web Dockerfile
  deploy/                         # production compose, Caddy, bootstrap SQL
  docker-compose.yml              # local Postgres + api + web
```

## CI/CD

- **CI:** [`.github/workflows/ci.yml`](.github/workflows/ci.yml) — tests on every PR/push to `main`
- **CD:** [`.github/workflows/deploy.yml`](.github/workflows/deploy.yml) — build/push GHCR images; deploy to VPS when secrets are configured

## Backend docs

See [backend/README.md](backend/README.md) for API reference, profiles, and credentials.
