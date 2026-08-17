# SocietyCare deployment

Two environments on one Ubuntu VPS, deployed by GitHub Actions, with Caddy for
HTTPS.

| Environment | Domain | Host port | Compose project | Deployed |
|---|---|---|---|---|
| **beta** | `beta.example.com` | 8081 | `societycare-beta` | automatically, on every push to `main` |
| **production** | `app.example.com` | 8080 | `societycare-prod` | after beta succeeds **and** you approve |

## Architecture

Each environment is its own stack of three containers:

- **postgres** — data, in a `pgdata` volume
- **api** — Spring Boot (`prod` profile, Flyway migrations on startup)
- **web** — nginx serving the static UI and proxying `/api/` to the api

Plus one shared **Caddy** running on the host (not in Docker). Caddy owns ports
80 and 443, terminates HTTPS, and routes by domain name to the right stack. It is
the only thing the internet can reach; both web containers bind to
`127.0.0.1` only.

The two stacks are separated by the Compose **project name** (`-p`), which
prefixes containers, networks and volumes. That is what gives beta its own
database — nothing beta does can touch production's data.

Images are built once in GitHub Actions and stored in **GHCR**
(`ghcr.io/<owner>/societycare-api` and `societycare-web`). The server only pulls;
it never compiles.

---

## 1. Local prod-like stack (do this first)

The fastest way to catch a deployment problem is on your own machine. This runs
real Postgres 16 with the `prod` profile — the same shape as the server.

```bash
cp .env.example .env
```

Edit `.env`: set `POSTGRES_PASSWORD` and `DB_PASSWORD` to the **same** value
(one creates the database user, the other is what the app logs in with), and
generate a real secret:

```bash
openssl rand -base64 32
```

Then:

```bash
docker compose --env-file .env up --build
```

Open **http://localhost** — or set `WEB_PORT=8090` in `.env` if port 80 is
taken, and use http://localhost:8090.

**On an Apple Silicon Mac**, the API's base image (`eclipse-temurin:11-jre-alpine`)
is only published for amd64, so prefix the *build* with:

```bash
DOCKER_DEFAULT_PLATFORM=linux/amd64 docker compose --env-file .env up --build -d
```

Do **not** set that variable when merely starting an already-built stack — it
forces Postgres to amd64 too, which is not cached locally. Plain
`docker compose up -d` is correct for restarts. Docker will warn about the
platform mismatch and run the amd64 image under emulation, which is what you
want: it is the same image production runs.

### First admin (prod seeding is disabled)

```bash
docker compose exec -T postgres psql -U societycare -d societycare < deploy/bootstrap-admin.sql
```

Login `admin` / `admin` — **change it immediately**. Generate a replacement hash:

```bash
pip install bcrypt   # once
./deploy/scripts/hash-password.sh 'your-strong-password'
```

### Debug ports

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml --env-file .env up -d
```

Exposes Postgres on 5432 (for TablePlus/DBeaver), the API on 8080, web on 8081.
Never add these mappings on the server.

---

## 2. CI

[`.github/workflows/ci.yml`](../.github/workflows/ci.yml) — on every push and PR
to `main`:

1. `mvn -s settings.xml verify` in `backend/` (126 tests)
2. Docker build smoke test for both images

Note the gap: **the tests run against H2 in PostgreSQL-compatibility mode, not
real Postgres.** Section 1 is how you cover that before deploying.

---

## 3. CD — build once, promote the same images

[`.github/workflows/deploy.yml`](../.github/workflows/deploy.yml):

```
push to main ──► build-and-push ──► deploy-beta ──► deploy-production
                 tests, images       automatic      waits for approval
```

`build-and-push` tags both images with the commit SHA. Beta and production then
deploy **that same tag** — production never rebuilds. What you approved on beta
is bit-for-bit what residents receive.

The deploy itself is [`deploy/scripts/deploy-stack.sh`](scripts/deploy-stack.sh),
which runs on the server. Keeping the logic there rather than in the workflow
means the two environments cannot drift apart, and you can run a deploy by hand
if GitHub is unavailable. It also **rolls back automatically**: it records the
previous image tags, and if the new stack does not report healthy within three
minutes it restores them and fails the build.

Rollback returns the *code*, not the database. Migrations only run forwards —
which is the whole reason beta exists.

### The approval gate

This is configured in GitHub, not in the workflow file:

**Settings → Environments → New environment**

1. Create **`beta`** — no protection rules.
2. Create **`production`** — tick **Required reviewers** and add yourself.

That is what makes `deploy-production` pause until you press Approve.

### Repository variable

| Variable | Value | Where |
|---|---|---|
| `DEPLOY_ENABLED` | `true` | Settings → Secrets and variables → Actions → Variables |

Until this is set, pushes build and test only. Nothing is deployed.

### Secrets

**Repository-level** (same for both environments — one server):

| Secret | Description |
|---|---|
| `DEPLOY_HOST` | VPS IP or hostname |
| `DEPLOY_USER` | SSH user, e.g. `deploy` |
| `DEPLOY_SSH_KEY` | Private SSH key (PEM) |
| `GHCR_PAT` | GitHub PAT with `read:packages`, so the server can pull images |

**Environment-level** — set these *separately* under both `beta` and
`production`, with **different values**:

| Secret | Description |
|---|---|
| `POSTGRES_PASSWORD` | Database password for that environment |
| `DB_PASSWORD` | Same value as that environment's `POSTGRES_PASSWORD` |
| `JWT_SECRET` | `openssl rand -base64 32` — a different one per environment |

Different values matter: it means a leaked beta secret cannot be used against
production, and a beta token is not valid in production.

### GHCR visibility

After the first run, open **Packages** on GitHub and either set both images to
public, or keep them private and rely on `GHCR_PAT` (which the workflow does).

---

## 4. VPS one-time setup

### 4.1 The server

- Ubuntu 24.04 LTS, **2 GB RAM minimum** (two Postgres instances plus two app
  stacks; 1 GB is not enough)
- An Indian region if your residents are in India — DigitalOcean Bangalore or
  Vultr Mumbai
- Firewall: open **22**, **80**, **443** and nothing else

### 4.2 Docker

```bash
sudo apt update && sudo apt install -y ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt update && sudo apt install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
sudo usermod -aG docker deploy
```

### 4.3 Directory layout

```bash
sudo mkdir -p /opt/societycare/{beta,prod,backups/beta,backups/prod,scripts}
sudo chown -R deploy:deploy /opt/societycare
```

Resulting in:

```
/opt/societycare/
├── docker-compose.prod.yml     copied by CI, shared by both stacks
├── Caddyfile                   copied by CI
├── bootstrap-admin.sql         copied by CI
├── scripts/                    copied by CI
├── beta/.env                   written by deploy-stack.sh (secrets, 0600)
├── prod/.env                   written by deploy-stack.sh
└── backups/{beta,prod}/        nightly dumps
```

### 4.4 DNS

Two **A records**, both pointing at the server's IP:

```
app.yourdomain.com   → 203.0.113.5
beta.yourdomain.com  → 203.0.113.5
```

Do this **before** installing Caddy — certificate issuance requires the names to
already resolve here.

### 4.5 Caddy

```bash
sudo apt install -y debian-keyring debian-archive-keyring apt-transport-https
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | sudo gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' | sudo tee /etc/apt/sources.list.d/caddy-stable.list
sudo apt update && sudo apt install -y caddy
```

Edit `/opt/societycare/Caddyfile`: replace `example.com` with your domain, and
set the beta password hash:

```bash
caddy hash-password
```

Then:

```bash
sudo cp /opt/societycare/Caddyfile /etc/caddy/Caddyfile
sudo systemctl reload caddy
```

Beta sits behind HTTP basic auth because refreshing it from a production backup
puts real resident data there. Remove that block if you would rather it be open.

### 4.6 First deploy

Set `DEPLOY_ENABLED=true`, push to `main`, and watch the run. Beta deploys on its
own; production waits for your approval.

### 4.7 Bootstrap each admin

Once a stack is up:

```bash
cd /opt/societycare
docker compose -p societycare-prod -f docker-compose.prod.yml --env-file prod/.env \
  exec -T postgres psql -U societycare -d societycare < bootstrap-admin.sql
```

Same for `societycare-beta` / `beta/.env`. **Change the password immediately** —
`admin`/`admin` on a public domain is an open door.

---

## 5. Backups

The single most important thing in this document. The app permanently deletes
data by design (removing a resident destroys their complaints), migrations only
go forwards, and `docker compose down -v` is one flag away from wiping a
database. The `pgdata` volume is **not** a backup — it dies with the disk.

### Install the nightly timers

The unit files arrive on the server with every deploy, so install them after the
first successful run:

```bash
sudo cp /opt/societycare/systemd/societycare-backup@.* /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now societycare-backup@prod.timer
sudo systemctl enable --now societycare-backup@beta.timer
```

Check them:

```bash
systemctl list-timers 'societycare-backup*'
journalctl -u societycare-backup@prod -n 50
```

[`backup-db.sh`](scripts/backup-db.sh) dumps, compresses, and **verifies** each
dump three ways (non-empty, passes `gzip -t`, contains the expected schema)
before keeping it. It writes to a `.partial` name and moves the file into place
only once verified, so a half-written dump never sits there looking restorable.
Retention: every dump for 7 days, plus Sunday dumps for 4 weeks.

### Get them off the server

A backup that only exists on the machine it came from is not a backup. Install
`rclone`, configure a remote (Backblaze B2 and Cloudflare R2 both cost pennies at
this size), then uncomment the `RCLONE_REMOTE` line in
`/etc/systemd/system/societycare-backup@.service`:

```
Environment=RCLONE_REMOTE=b2:societycare-backups
```

The script warns loudly on every run until you do this.

### Restore — and test it

```bash
/opt/societycare/scripts/restore-db.sh beta /opt/societycare/backups/prod/societycare-prod-2026-08-18-Tue-031000.sql.gz
```

It requires typing the environment name to confirm, and refuses `prod` outright
unless you also pass `I_MEAN_IT=yes`.

**Restore production's dump into beta regularly.** It does two jobs at once: it
proves the backups genuinely restore, and it gives beta realistic data. An
untested backup is a guess, and the first real restore is the worst moment to
discover a problem.

---

## 6. Operations

### Logs

```bash
cd /opt/societycare
docker compose -p societycare-prod -f docker-compose.prod.yml --env-file prod/.env logs -f api web
```

### Manual rollback

Deploys roll back automatically on a failed health check. To go back further,
edit `prod/.env` with an older SHA tag and re-run:

```bash
docker compose -p societycare-prod -f docker-compose.prod.yml --env-file prod/.env pull
docker compose -p societycare-prod -f docker-compose.prod.yml --env-file prod/.env up -d
```

### Deploy by hand, without GitHub

```bash
POSTGRES_PASSWORD=... DB_PASSWORD=... JWT_SECRET=... \
  /opt/societycare/scripts/deploy-stack.sh prod \
  ghcr.io/<owner>/societycare-api:<sha> \
  ghcr.io/<owner>/societycare-web:<sha> 8080
```

---

## 7. Before you tell a single resident the address

- [ ] `admin`/`admin` changed on production
- [ ] Fresh `JWT_SECRET` per environment — **never** the dev default in
      `application.yml`, which is public in this repo
- [ ] Different database passwords for beta and production
- [ ] Backup timers enabled, and a restore actually tested
- [ ] `RCLONE_REMOTE` configured so backups leave the server
- [ ] Firewall: only 22, 80, 443
- [ ] Postgres has no published port on the server
- [ ] `seed.enabled` false in prod (the default in `application-prod.yml`)
- [ ] Swagger disabled in prod (the default)
- [ ] HTTPS working on both domains
- [ ] An uptime check pointed at `app.yourdomain.com`
- [ ] `.env` files and `backend/.h2-data/` never committed
