# SocietyCare production deployment

Deploy the complaint-management app to an Ubuntu VPS with Docker Compose, GitHub Actions CI/CD, and Caddy for HTTPS.

## Architecture

- **postgres** — persistent data (`pgdata` volume)
- **api** — Spring Boot (`prod` profile, Flyway migrations on startup)
- **web** — nginx serves the static UI and proxies `/api/` to the API
- **Caddy** (on the host) — TLS termination and reverse proxy to `127.0.0.1:8080`

Images are built in GitHub Actions and stored in **GHCR** (`ghcr.io/<owner>/societycare-api` and `societycare-web`).

## 1. Local Docker (prod-like stack)

```bash
cp .env.example .env
# Edit .env: set POSTGRES_PASSWORD, DB_PASSWORD, JWT_SECRET (openssl rand -base64 32)

docker compose --env-file .env up --build
```

Open **http://localhost** (nginx on port 80). The UI calls `/api/v1` through the proxy.

### First admin (prod seed is disabled)

```bash
docker compose exec -T postgres psql -U societycare -d societycare < deploy/bootstrap-admin.sql
```

Login: username `admin`, password `admin` — **change immediately** via the admin UI or a new SQL update.

Generate a custom password hash:

```bash
pip install bcrypt   # once
./deploy/scripts/hash-password.sh 'your-strong-password'
```

### Optional debug ports

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml --env-file .env up --build
```

Exposes Postgres `5432`, API `8080`, web `8081`.

## 2. GitHub Actions CI

Workflow: [`.github/workflows/ci.yml`](../.github/workflows/ci.yml)

- Runs on every push/PR to `main`
- `mvn -s settings.xml verify` in `backend/`
- Docker build smoke test for API and web images

## 3. GitHub Actions CD

Workflow: [`.github/workflows/deploy.yml`](../.github/workflows/deploy.yml)

On push to `main`:

1. Runs tests
2. Builds and pushes images to GHCR (`:latest` and `:<git-sha>`)
3. **Deploy job** runs only when `DEPLOY_HOST` secret is set

### Repository secrets

| Secret | Description |
|--------|-------------|
| `DEPLOY_HOST` | VPS IP or hostname |
| `DEPLOY_USER` | SSH user (e.g. `deploy`) |
| `DEPLOY_SSH_KEY` | Private SSH key (PEM) |
| `POSTGRES_PASSWORD` | Postgres superuser/app password |
| `DB_PASSWORD` | Same value as `POSTGRES_PASSWORD` for the app datasource |
| `JWT_SECRET` | Base64 HS256 secret (`openssl rand -base64 32`) — **not** the dev default |
| `GHCR_PAT` | GitHub PAT with `read:packages` for the VPS to pull images |

### GHCR package visibility

After the first workflow run, open **Packages** on GitHub and set both `societycare-api` and `societycare-web` to **public**, *or* keep them private and use `GHCR_PAT` on the server (required by the deploy workflow).

## 4. VPS one-time setup

### 4.1 Create the server

- Ubuntu 22.04 or 24.04 (1 GB RAM minimum)
- Open firewall: **22** (SSH), **80** and **443** (HTTP/S)

### 4.2 Install Docker

```bash
sudo apt update && sudo apt install -y ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt update && sudo apt install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
sudo usermod -aG docker deploy   # replace deploy with your user
```

### 4.3 Deploy directory

```bash
sudo mkdir -p /opt/societycare
sudo chown deploy:deploy /opt/societycare
```

GitHub Actions copies `docker-compose.prod.yml`, `Caddyfile`, and `bootstrap-admin.sql` here on each deploy.

### 4.4 HTTPS with Caddy

```bash
sudo apt install -y debian-keyring debian-archive-keyring apt-transport-https
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | sudo gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' | sudo tee /etc/apt/sources.list.d/caddy-stable.list
sudo apt update && sudo apt install -y caddy
```

Edit `/opt/societycare/Caddyfile` — replace `example.com` with your domain — then:

```bash
sudo cp /opt/societycare/Caddyfile /etc/caddy/Caddyfile
sudo systemctl reload caddy
```

Point your DNS **A record** to the VPS IP.

### 4.5 Bootstrap admin on production

After the first successful deploy:

```bash
cd /opt/societycare
docker compose -f docker-compose.prod.yml --env-file .env exec -T postgres \
  psql -U societycare -d societycare < bootstrap-admin.sql
```

## 5. Operations

### Logs

```bash
cd /opt/societycare
docker compose -f docker-compose.prod.yml --env-file .env logs -f api web
```

### Rollback

Redeploy a previous image tag by editing `.env` on the server:

```env
API_IMAGE=ghcr.io/<owner>/societycare-api:<older-sha>
WEB_IMAGE=ghcr.io/<owner>/societycare-web:<older-sha>
```

Then `docker compose -f docker-compose.prod.yml --env-file .env pull && docker compose -f docker-compose.prod.yml --env-file .env up -d`.

### Database backup

```bash
docker compose -f docker-compose.prod.yml --env-file .env exec -T postgres \
  pg_dump -U societycare societycare > backup-$(date +%F).sql
```

### Manual deploy (without GitHub)

On a machine with Docker:

```bash
export API_IMAGE=ghcr.io/<owner>/societycare-api:latest
export WEB_IMAGE=ghcr.io/<owner>/societycare-web:latest
# ... plus POSTGRES_PASSWORD, DB_PASSWORD, JWT_SECRET in .env
docker compose -f deploy/docker-compose.prod.yml --env-file .env pull
docker compose -f deploy/docker-compose.prod.yml --env-file .env up -d
```

## 6. Security checklist

- [ ] Unique `JWT_SECRET` and DB passwords in production
- [ ] Change default admin password after bootstrap
- [ ] `seed.enabled` stays `false` in prod (default in `application-prod.yml`)
- [ ] Swagger disabled in prod (default)
- [ ] HTTPS only via Caddy
- [ ] Do not commit `.env` or `backend/.h2-data/`
