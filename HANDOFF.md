# SocietyCare — session handoff notes

Context for picking this project up in a fresh session. Covers two sessions:

1. `410d4e1 "update complaint management website"` - admin/resident feature
   work (merged to `main`, 31 files, +2548/-333).
2. "Give delete complaint option to the user" - complaint deletion turned into
   a soft delete with a recorded reason, plus the admin Deleted tab.

---

## 1. What this project is

A complaint-management website for a residential society. Residents raise
complaints for their flat; management (admin) assigns a professional
(plumber, carpenter, electrician, painter) and tracks the ticket to
completion.

**Stack**

| Part | Technology | Notes |
|---|---|---|
| Frontend | Plain HTML + CSS + vanilla JS (ES5-style IIFEs) | **No build step, no npm, no framework.** Hash router. |
| Backend | Spring Boot 2.7, Java 11 target | REST API, JWT auth, Flyway migrations |
| DB (local) | H2 file database | `backend/.h2-data/societycare.mv.db`, PostgreSQL compatibility mode |
| DB (prod) | PostgreSQL 16 | via Docker Compose |
| Deploy | Docker + GitHub Actions → GHCR → VPS, Caddy for HTTPS | See `deploy/README.md` |

The frontend is served as static files; nginx (prod) or a plain static server
(local) hosts it and proxies `/api/` to the backend.

---

## 2. Running it locally

**Backend** — note the `JAVA_HOME`: the machine has no JDK on `PATH`, and
Homebrew's default is Java 26, which Spring Boot 2.7 cannot run on. Use 17:

```bash
cd backend
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  mvn -s settings.xml spring-boot:run "-Dspring-boot.run.profiles=dev,h2"
```

`-s settings.xml` is required for every Maven command in this project (it
forces Maven Central and ignores any corporate mirror).

**Frontend** — any static server on port **5500** (that port is in the
backend's CORS allow-list). Plain `python3 -m http.server 5500` works, but
the browser then caches JS/CSS aggressively and edits appear not to apply.
A no-cache server avoids that:

```python
# dev-server.py — run from the repo root
import http.server

class NoCacheHandler(http.server.SimpleHTTPRequestHandler):
    def end_headers(self):
        self.send_header("Cache-Control", "no-store, no-cache, must-revalidate")
        self.send_header("Pragma", "no-cache")
        super().end_headers()

if __name__ == "__main__":
    http.server.test(HandlerClass=NoCacheHandler, port=5500, bind="127.0.0.1")
```

**URLs**: app <http://localhost:5500> · API <http://localhost:8080/api/v1> ·
Swagger <http://localhost:8080/swagger-ui.html> · H2 console
<http://localhost:8080/h2-console> (JDBC `jdbc:h2:file:./.h2-data/societycare`,
user `sa`, empty password).

**Logins**: admin `admin` / `admin`; residents log in with their flat number
(seeded ones use `pass123`). The demo-credential hints were deliberately
removed from the login screens — see §5.

**Tests**: `cd backend && mvn -s settings.xml test` → **107 tests, all passing.**

---

## 3. Architecture map

**Frontend** (`scripts/`)

| File | Responsibility |
|---|---|
| `app.js` | Hash router, route table, auth guards, global 401 handling |
| `api.js` | fetch wrapper: base URL, Bearer token, `ApiError`, 401 → session-expired event |
| `storage.js` | Domain adapter over the API; maps DTOs to view-shaped objects; `CATEGORIES`, `getDisplayStatus` |
| `session.js` | Session persistence (localStorage) |
| `ui.js` | `escapeHtml`, loading/error blocks, **`openModal`**, **`confirmDialog`** |
| `toast.js` | Toast messages (auto-hides after 2.8s) |
| `views/*.js` | One file per screen; each exports `window.CM.views.<name>` |

Routes: `#/role`, `#/login`, `#/admin/login`, `#/dashboard`, `#/raise`,
`#/complaints`, `#/admin/dashboard`, `#/admin/tickets`, `#/admin/residents`,
`#/admin/professionals`, `#/change-password`.

**Backend** (`backend/src/main/java/com/societycare/`) — packages by feature:
`auth`, `complaint`, `resident`, `professional`, `status`, `admin`, `security`,
`common`, `config`, `seed`.

**API surface**

```
POST   /api/v1/auth/resident/login | /auth/admin/login | /auth/me/password
GET    /api/v1/auth/me
GET    /api/v1/complaints            (admin: all; resident: own flat only)
GET    /api/v1/complaints/{id}
POST   /api/v1/complaints            (resident)
POST   /api/v1/complaints/{id}/delete  (soft delete; body: reason + comments)
GET    /api/v1/complaints/deleted      (admin: withdrawn complaints)
POST   /api/v1/complaints/{id}/assign | /unassign | /complete | /reopen   (admin)
GET/POST/PATCH/DELETE /api/v1/professionals[/{id}]                        (admin)
GET/POST /api/v1/admin/residents · PATCH/DELETE /{id} · POST /{id}/password
GET    /api/v1/meta/categories | /meta/statuses      (public)
```

**Database** — `t_resident`, `t_professional`, `t_complaint`, `t_status`,
`t_admin`. Migrations: `V1__schema.sql`, `V2__seed_status.sql`,
`V3__resident_phone.sql`, `V4__complaint_soft_delete.sql`.

The status invariant is enforced by a CHECK constraint in V1 and drives a lot
of the logic:

| status_id | name | professional | assigned_at | completed_at |
|---|---|---|---|---|
| 1 | Assignment Pending | NULL | NULL | NULL |
| 2 | Pending Work | set | set | NULL |
| 3 | Complete | set | set | set |
| 4 | Deleted (withdrawn) | either | either | NULL, plus deleted_at + deletion_reason set |

That is why unassigning and reopening **must** clear the worker and
timestamps — the database will not allow otherwise. V4 rewrote this constraint
to add the fourth branch; it deliberately says nothing about `professional_id`,
so a withdrawn complaint keeps whatever worker it had, which is how you tell
"withdrawn before assignment" from "withdrawn after a worker was sent".

---

## 4. First session - admin + resident feature work

**Admin — Manage Professionals** (new page `scripts/views/adminProfessionals.js`)
Add / edit / delete workers; mirrors Manage Residents with category in place
of flat. Delete is refused by the API when the professional is referenced by
a complaint (409, message names the person).

**Admin — Manage Residents**
Added Edit and Delete alongside Reset password. Edit opens a prefilled modal
(no confirmation — nothing is destroyed); Delete confirms first and states how
many complaints will be destroyed with the resident.

**Admin — Tickets screen, rebuilt** (`scripts/views/adminTickets.js`)
The old three-level drill-down (status tab → category grid → flat groups) was
replaced by **one screen per tab** with:
- Group by: None / Flat / Category / Professional (Professional hidden on
  Assignment Pending — those tickets have no worker, so it would be one bucket)
- Category filter, flat search (live, keeps focus), sort Newest/Oldest
- Completed tab only: From/To date filter on **completion date**, defaulting
  to 1st of the current month → today
- Groups are collapsible `<details>`, each holding **all** tickets for its key
- Age chip on open tickets ("30 days old"), amber past 7 days
- All state in the URL (`?status=&group=&category=&flat=&sort=&from=&to=`)

**Admin — ticket actions**
Assigned tickets now offer **Remove assignee** and **Reassign** as well as
Mark as Complete; completed tickets offer **Reopen**. Each confirmation is a
sub-state of the same modal driven by `&action=reassign|unassign|reopen`, so
back/refresh behave.

**Admin — dashboard**
Recent tickets are clickable; a click routes to the tickets page on the tab
matching that ticket's status with the modal open.

**Ticket modal** shows "Raised by <resident name> + phone" above the
description (`tel:` link).

**Resident side**
- "+ Raise New Issue" button beside the Registered Complaints heading
- "Delete this issue" at the bottom of each **open** complaint card (red),
  with confirmation; completed complaints cannot be deleted

**Auth**
- Demo credentials removed from both login screens (and the admin username
  placeholder no longer says `admin`)
- **Bug fixed**: `api.js` treated every 401 as an expired session, so a wrong
  password said "Your session has expired". Login endpoints are now exempt;
  they show "Invalid credentials. Please try again." Genuine expiry still
  works (verified by corrupting a token).

**Backend additions**: resident `phone` (V3) exposed on residents *and*
complaints (`residentPhone`); `PATCH`/`DELETE` admin residents; `DELETE`
complaint; complaint `unassign` and `reopen`; `assign` now also reassigns.
Tests grew 66 → 101.

---

## 4b. Second session - complaint soft delete

**The problem with the first version.** Deleting a complaint issued a real
`DELETE FROM t_complaint`. The row vanished, so there was no way to answer
"how many complaints do residents withdraw, and why?".

**What it is now.** `V4__complaint_soft_delete.sql`:

- adds status **4 = Deleted** to `t_status`
- adds `deletion_reason` (VARCHAR 32), `deletion_comments` (TEXT),
  `deleted_at` to `t_complaint`
- **drops and recreates `chk_complaint_status_invariant`** with a fourth
  branch, because the V1 constraint only permitted statuses 1-3 and would
  otherwise reject every deleted row

`ComplaintService.delete(id, request)` sets the status, reason, comments and
timestamp instead of removing the row. It refuses a complaint that is already
Complete (409) or already Deleted (409).

**Reason capture.** `DeletionReason` enum: RESOLVED_ITSELF, RAISED_BY_MISTAKE,
DUPLICATE, HANDLED_PRIVATELY, OTHER. The UI shows a mandatory dropdown; the
free-text box appears **only** when OTHER is chosen, and is then mandatory
(enforced in the browser *and* the service, so the API cannot be bypassed).

**Endpoint changed shape**: `DELETE /complaints/{id}` became
`POST /complaints/{id}/delete` with a JSON body, matching the existing
`/assign`, `/complete`, `/reopen` convention - it is a state transition now,
not a removal.

**Leakage is the main risk of soft delete.** Every listing must exclude
withdrawn rows. `findAll()` and `findByFlat()` use
`findByStatus_StatusIdNot...`, which covers the admin tabs, the resident list,
the dashboard counts and the recent-tickets strip. A test asserts a withdrawn
complaint is absent from both listings but present on the deleted list.

**Admin Deleted tab** - fourth tab beside Completed, red styling, fed by
`GET /complaints/deleted` (the normal listings exclude these). It carries the
same group-by / category / flat search / sort controls. Opening a withdrawn
complaint shows "Withdrawn by the resident" with the reason, comments and
date, and offers no ticket actions.

Tests: 101 -> 107.

---

## 5. Decisions made, and why

These were discussed and settled — don't silently reverse them.

**Completed complaints are kept, never auto-deleted.** They are the society's
record of work done and drive the Completed count. The clutter problem is
solved with the date filter, not deletion. Residents cannot delete completed
complaints (409); admins cannot either.

**Deleting a resident cascades to their complaints.** User chose this over
blocking. The confirmation names the exact count. Complaints have a NOT NULL
FK to resident, so there is no third option without a schema change.

**Reopening a ticket clears the worker and completion date.** Forced by the
CHECK constraint above. If preserving that history matters later, it needs a
separate ticket-history table.

**A professional's category cannot be changed.** Deliberate in the original
code (comment in `UpdateProfessionalRequest`): past tickets reference it. The
edit modal shows it disabled with an explanation.

**Pagination was built, then removed on request.** It paged *rows*, so a flat's
tickets could split across pages and the group heading repeated — which is what
prompted the collapsible-group redesign. All pagination code is gone. When it
returns it should be **server-side** (`?page=&size=`), which also avoids the
split-group problem.

**Deleted complaints use status 4, not a `deleted_at` flag alone.** I
recommended keeping status 1-3 and adding `deleted_at` (no constraint
rewrite, preserves the stage it was withdrawn at); the user chose the explicit
status row for clearer SQL reporting. The constraint rewrite handles it, and
keeping `professional_id` untouched recovers most of the lost information.

**Deletion columns stay on `t_complaint`; no separate `t_deleted_complaints`
table.** Discussed and decided. It is a 1:1 optional relationship, and a CHECK
constraint can only see columns in its own row - moving the reason to another
table would remove the database's guarantee that a Deleted complaint *has* a
reason. If a broader audit trail is ever wanted (resident removals,
professional removals, reassignments), the right shape is a general
`t_audit_log`, not a complaint-specific side table - and that would also fix
the resident-deletion hole below.

**Pagination: decided against, again.** The active tabs are self-limiting
(they drain as work completes) and Completed is already capped by its
month-default date filter, so the visible list never grows unbounded. The real
cost is that `GET /complaints` downloads all history on every tab switch
(~440 bytes per complaint; 18 complaints = 7,950 bytes measured). The cheaper
first fix when it gets slow is **server-side filtering** (`?status=&from=&to=`),
not pagination.

**If pagination ever returns, the filters must move server-side with it.**
Filtering, sorting, grouping and pagination must all live on the same side.
Client filter + client page = correct (browser filters everything, then
slices). Server filter + server page = correct. **Client filter + server page
is silently wrong** - searching flat B-202 would only match rows on the
current page and show "no results" while B-202 sits on page 7.

**Preferred time-slot feature was started and fully reverted** (user asked to
defer). Nothing remains in the codebase. Design was: enum of four 3-hour
windows, optional on create so no existing test breaks, ~1 hour of work.

---

## 6. Conventions — follow these

**Line endings are CRLF.** The repo uses CRLF throughout. Bulk-editing files
with Python/scripts silently rewrites them as LF and makes `git diff` show
every line as changed. If a diff looks absurdly large, check this first:

```bash
git show HEAD:styles/main.css | file -   # ASCII text, with CRLF line terminators
```

**No build step.** Add a new view by creating `scripts/views/x.js`, adding a
`<script>` tag to `index.html`, and registering a route in `app.js`.

**All interpolation goes through `ui.escapeHtml`** — the views build HTML
strings, so this is the XSS defence. Never skip it.

**Modals**: use `ui.openModal(html)` and `ui.confirmDialog({...})` from
`ui.js`. Don't hand-roll another backdrop/escape/close implementation.
Note `.modal > .modal-form` supplies its own padding; `.modal-foot > .modal-form`
does not (the footer already pads).

**View state belongs in the URL** so refresh/back/share work. Use `navigate()`
for real transitions and `replaceParams()` for filter changes that shouldn't
re-render.

**Partial re-render for live filters**: re-render only the list region, never
the input being typed into, or focus is lost on each keystroke.

**Test naming**: `what_condition_expectedResult`, e.g.
`unassign_neverAssigned_returns409`. Tests are MockMvc integration tests
against real Spring + H2; `TestAuth.asAdmin()/asResident(...)` injects the
principal. `@BeforeEach` wipes tables (complaints before residents — FK).

---

## 7. Gotchas hit during this session

- **Stale compiled migration.** Deleting a `V*.sql` from `src/` doesn't remove
  the copy in `backend/target/classes/db/migration/`, and `spring-boot:run`
  applies it. This put a stray column and a V4 history row into the H2 DB;
  the next start would have failed Flyway validation. Fix: delete from
  `target/` too, then clean the DB.
- **Selector collisions.** Adding `data-status` to dashboard recent rows put
  them in range of the stat-card handler. Scope selectors
  (`.stat-card[data-status]`) when reusing attributes.
- **Browser caching** hides frontend edits; use the no-cache dev server, or
  re-fetch scripts with `{cache:'reload'}` before reloading.
- **Bash `cd` persists between tool calls**, which once started the static
  server in `backend/` and served a directory listing.
- **Inspecting or repairing the dev database** (H2 allows a second connection
  thanks to `AUTO_SERVER=TRUE`, so the app can stay running):

  ```bash
  cd backend
  java -cp ~/.m2/repository/com/h2database/h2/2.1.214/h2-2.1.214.jar \
    org.h2.tools.Shell \
    -url "jdbc:h2:file:$(pwd)/.h2-data/societycare;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;AUTO_SERVER=TRUE" \
    -user sa -password "" -sql "SELECT status_id, count(*) FROM t_complaint GROUP BY status_id;"
  ```

- **Don't parse JSON ids with `sed`** — greedy regex grabs the *last* `"id"`
  in the response (e.g. the status id). Use `python3 -c 'json.load(...)'`.

---

## 8. Not done / open items

- **No frontend tests at all.** CI runs backend tests only, so every UI
  behaviour above is verified by manual browser checks. A Vitest+jsdom or
  Playwright suite is the obvious next investment.
- **All filtering/sorting/grouping is client-side** over the full
  `/complaints` list; every tab switch refetches everything (verified in the
  network tab). Correct at this size - see the pagination decision in §5 for
  when and how to change it.
- **Notifications on status change**: scoped but not built. Summary — code is
  easy (~1 day in-app; ~2 more for email); real blockers are missing contact
  data (no email column; some residents have no phone), and for SMS/WhatsApp
  in India the DLT / Meta approval process (weeks). Recommended order:
  in-app → email → SMS/WhatsApp. Decide separately whether one-way
  notification or a two-way comment thread (+3–4 days) is wanted.
- **Preferred visit time slot** — reverted, see §5.
- **Resident-side completed cards** show no completion date or who did the
  work; the data exists.
- **No automated database backups** anywhere. Matters more now that residents
  and admins can delete things permanently.
- Deployment has never actually been run: `DEPLOY_ENABLED` is unset, so
  pushes run CI only. Steps are in `deploy/README.md`; before going live
  change `JWT_SECRET`, the DB passwords, and the bootstrap `admin`/`admin`
  password.

---

## 9. Working style that worked well

- Verify UI changes by driving the real browser and reading back the DOM,
  not by assuming; quote the actual observed values.
- Run the backend suite after any backend change — the user explicitly asks
  that existing tests keep passing.
- **Never mutate the user's real data when testing.** Create a throwaway
  resident/complaint, test against that, then delete it. Their H2 database
  has hand-entered residents (flats 289–292 with names and phones) and
  complaints that must survive.
- Flag mistakes plainly and fix them rather than glossing over.
