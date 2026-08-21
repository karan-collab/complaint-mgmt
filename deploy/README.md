# SocietyCare deployment

Deployed to an Ubuntu VPS by GitHub Actions, with Caddy for HTTPS. Production
is required; beta is optional.

| Environment | Domain | Host port | Compose project | Deployed |
|---|---|---|---|---|
| **beta** *(optional)* | `beta.complaintsmgmt.com` | 8081 | `societycare-beta` | on every push to `main`, when `BETA_ENABLED=true` |
| **production** | `complaintsmgmt.com` | 8080 | `societycare-prod` | after you approve — and after beta, if beta is enabled |

**Beta is opt-in.** Leave `BETA_ENABLED` unset and production deploys on its own;
the beta job is skipped. Turn it on once a beta host exists and nothing else
changes. Running production alone is a reasonable choice for a small
deployment — the trade-off is that database migrations reach real data on their
first run, so rehearse them with the local stack in section 1 and take a backup
before deploying one.

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

The images build and run natively on both `amd64` and `arm64`, so an Apple
Silicon Mac needs no special flags. That is deliberate: the API's runtime base
is `eclipse-temurin:11-jre-jammy` rather than the alpine variant, because
Temurin publishes no arm64 build of its Alpine JRE images. Being tied to one CPU
architecture would rule out ARM servers (Oracle Ampere, Hetzner CAX, AWS
Graviton), which are the cheapest hosting available.

The deploy workflow builds both architectures and pushes them under one tag, so
the server pulls whichever matches it without anyone choosing.

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
BETA_ENABLED=true
  push to main ──► build-and-push ──► deploy-beta ──► deploy-production
                   tests, images       automatic      waits for approval

BETA_ENABLED unset (production only)
  push to main ──► build-and-push ──► deploy-production
                   tests, images       waits for approval
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

| Variable | Value | Effect |
|---|---|---|
| `DEPLOY_ENABLED` | `true` | Enables deployment at all. Until set, pushes build and test only |
| `BETA_ENABLED` | `true` | Adds the beta stage. Leave unset to deploy production alone |

Both live under Settings → Secrets and variables → Actions → **Variables**.

### Secrets

**Repository-level** (shared by both environments):

| Secret | Description |
|---|---|
| `DEPLOY_HOST` | VPS IP or hostname. **Define this per environment instead** if beta and production live on different machines — an environment secret overrides the repository one |
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

- Ubuntu 24.04 LTS. **How much RAM depends on how many environments you run:**

  | Setup | Needs | Fits on |
  |---|---|---|
  | Production only | ~260 MB + OS | **1 GB** (an Oracle free micro) |
  | Production + beta, one box | ~490 MB + OS, plus a deploy spike | 2 GB |
  | One environment per box | ~260 MB each | **two 1 GB boxes** |

  Those figures are measured, not estimated, and assume the memory tuning in
  section 7. Untuned, one stack costs ~555 MB and none of the 1 GB options work.
- **Either CPU architecture works** — the images are built for amd64 and arm64,
  so ARM hosts are in play, and they are consistently the cheapest:

  | Host | Arch | Cost | Note |
  |---|---|---|---|
  | Oracle Ampere A1 | arm64 | free | capacity is often unavailable; no support |
  | Hetzner CAX11 | arm64 | ~€3.79 | EU only, ~150 ms from India |
  | Vultr Mumbai | amd64 | ~$10 | low latency |
  | DigitalOcean Bangalore | amd64 | ~$12 | low latency |

- Firewall: open **22**, **80**, **443** and nothing else

> On **Oracle Cloud** specifically, opening a port in the VCN Security List is
> only half the job — their Ubuntu images ship their own `iptables` rules that
> block traffic independently. If ports look open and still time out, that is
> why.

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

Three **A records**, all pointing at the server's IP (`203.0.113.5` below is a
stand-in for your actual IP):

| Type | Name | Value |
|---|---|---|
| A | `@` (the bare domain) | `203.0.113.5` |
| A | `www` | `203.0.113.5` |
| A | `beta` | `203.0.113.5` |

Do this **before** reloading Caddy — certificate issuance requires each name to
already resolve here, and a failed issuance has a cool-off period.

Check with:

```bash
dig +short complaintsmgmt.com beta.complaintsmgmt.com
```

Both should print the server's IP. DNS can take a few minutes to propagate.

> **If you use Cloudflare DNS**, set each record to **DNS only** (the grey cloud,
> not the orange one). With Cloudflare's proxy on, it intercepts ports 80 and 443
> and answers with its own certificate — Caddy never sees the challenge request
> and certificate issuance fails. Grey cloud means Cloudflare just answers the
> DNS lookup and gets out of the way, which is what this setup needs.

### 4.5 Caddy

```bash
sudo apt install -y debian-keyring debian-archive-keyring apt-transport-https
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | sudo gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' | sudo tee /etc/apt/sources.list.d/caddy-stable.list
sudo apt update && sudo apt install -y caddy
```

The domain is already set to `complaintsmgmt.com` in the Caddyfile. The one edit
still needed is the beta password — generate a hash and paste it in:

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

## 7. Memory

The stack is deliberately tuned to fit a small box. Measured per environment:

| Container | Untuned | Tuned |
|---|---|---|
| api (Spring Boot) | 477 MB | **262 MB** |
| postgres | 69 MB | **18 MB** |
| web (nginx) | 9 MB | 9 MB |
| **total** | **555 MB** | **289 MB** |

Constrained to a 420 MB container limit the API starts healthy in ~15 seconds
and settles at ~204 MB, which is what makes a 1 GB host viable at all.

Where the savings come from:

- **`JAVA_TOOL_OPTIONS` in backend/Dockerfile.** The JVM sizes its heap from the
  *container* limit (`MaxRAMPercentage=60`), so it adapts to whatever box it
  lands on. `UseSerialGC` drops G1's per-region metadata and GC threads, which
  buy nothing on one core. `-Xss512k` matters more than it looks: stacks default
  to ~2 MB each and the app runs 45+ threads.
- **Hikari pool 10 → 5** and **Tomcat threads 200 → 25** in
  `application-prod.yml`. Every pooled connection is a backend process on the
  Postgres side, and 200 worker threads reserve stack space for concurrency this
  app will never see.
- **Postgres told to be modest** in the compose file: `shared_buffers=64MB`,
  `max_connections=25`. It otherwise assumes it owns the machine.

The figure that actually sizes your host is not the steady state but the
**deploy spike**: `compose up -d` starts the new container before stopping the
old, so briefly two APIs run at once. Budget for it.

---

## 8. Before you tell a single resident the address

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
- [ ] An uptime check pointed at `complaintsmgmt.com`
- [ ] `.env` files and `backend/.h2-data/` never committed
