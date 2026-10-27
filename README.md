# AuctiX

**A Containerized Real-Time Live Bidding and Auction Management System.**

Java 17 · Spring Boot · PostgreSQL · Redis · WebSocket/STOMP · React · Three.js · Docker

AuctiX is a working full-stack auction platform, not a mockup. Bids are validated, ordered and
timestamped by the backend inside a database transaction that holds a row lock on the auction, so
simultaneous bidders get a correct and explainable result. Every price change is published to Redis
and fanned out over WebSockets, so other bidders see it without refreshing. Auctions open and close
on the **server clock** — the browser only renders a countdown.

---

## Table of contents

1. [Running it](#1-running-it)
2. [Demo accounts](#2-demo-accounts)
3. [The end-to-end demonstration](#3-the-end-to-end-demonstration)
4. [Architecture](#4-architecture)
5. [How concurrency is handled](#5-how-concurrency-is-handled)
6. [Auction lifecycle](#6-auction-lifecycle)
7. [Real-time layer](#7-real-time-layer)
8. [Data model](#8-data-model)
9. [API reference](#9-api-reference)
10. [Security](#10-security)
11. [Testing](#11-testing)
11b. [Running it in VS Code](#11b-running-it-in-vs-code)
12. [Local development without Docker](#12-local-development-without-docker)
13. [Configuration reference](#13-configuration-reference)
14. [Project layout](#14-project-layout)
15. [Troubleshooting](#15-troubleshooting)

---

## 1. Running it

### Requirements

- Docker Desktop (or Docker Engine) with Compose v2 — `docker compose version` should print v2.x
- About 3 GB of free disk space for the images
- Ports `80`, `8080`, `5432` and `6379` free (all configurable in `.env`)

### Steps

```bash
# 1. Unzip and enter the project
cd auctix

# 2. Create your environment file
cp .env.example .env

# 3. Put a real secret in it (at least 32 characters)
#    macOS / Linux:
#      openssl rand -base64 48
#    Windows PowerShell:
#      [Convert]::ToBase64String((1..48 | % {Get-Random -Max 256}))
#    Paste the result as JWT_SECRET= in .env

# 4. Build and start everything
docker compose up --build
```

The first build takes a few minutes while Maven and npm download their dependencies. When it
settles you will see `Started AuctixApplication`.

Then open:

| URL | What it is |
|---|---|
| <http://localhost> | The AuctiX application |
| <http://localhost:8080/swagger-ui.html> | Interactive API documentation |
| <http://localhost:8080/actuator/health> | Backend health |

To stop: `Ctrl-C`, then `docker compose down`.
To wipe the database and start clean: `docker compose down -v`.

> **The backend refuses to start without `JWT_SECRET`.** That is deliberate — no secret is
> hard-coded anywhere in the source. The error message tells you exactly what to set.

---

## 2. Demo accounts

On an empty database the platform seeds a realistic catalogue (`AUCTIX_SEED_ENABLED=true`).

| Role | E-mail | Password |
|---|---|---|
| Administrator | `admin@auctix.io` | `Password123!` |
| Seller | `seller@auctix.io` | `Password123!` |
| Seller | `motors@auctix.io` | `Password123!` |
| Bidder | `kalpesh@auctix.io` | `Password123!` |
| Bidder | `riya@auctix.io` | `Password123!` |
| Bidder | `daniel@auctix.io` | `Password123!` |

Seeded lots include a live Rolex Submariner with bid history, a live Bugatti Chiron, two upcoming
lots, one listing **waiting for admin approval**, and a **10-minute "sprint" lot** so you can watch
anti-sniping and automatic closing happen inside a single sitting.

---

## 3. The end-to-end demonstration

This is the scenario the system is built to prove. It takes about five minutes.

**Setup.** Open two browsers (or one normal and one private window) side by side so you can be two
people at once.

| # | Step | What to watch for |
|---|---|---|
| 1 | Sign in as `seller@auctix.io`, go to **Seller → Create auction**. Set the start time 2 minutes out and the end time ~10 minutes out. Submit. | The lot is created with status **In review** — it is not live yet. |
| 2 | Sign in as `admin@auctix.io` in the other browser. Open **Admin → Pending approvals** and click **Approve**. | Status becomes **Upcoming**. The seller receives a notification instantly. |
| 3 | Wait for the start time. | The scheduler flips it to **Live** on its own and announces it on the WebSocket topic. |
| 4 | Open the lot in both browsers, signed in as two different bidders. | Both see the same price and the same countdown. |
| 5 | Bidder A places a bid. | **Bidder B's screen updates without a refresh** — price pulses, the bid appears in the live ledger. |
| 6 | Bidder B places a higher bid. | **Bidder A gets a red "You've been outbid" toast** pushed to their private queue. |
| 7 | Try to bid below the minimum, or bid twice in a row. | The server rejects it with `BID_TOO_LOW` / `DUPLICATE_BID` and a readable message. |
| 8 | Bid in the last 30 seconds. | **Anti-sniping** extends the auction by 30 seconds and tells everyone why. |
| 9 | Let the clock run out. | The auction **closes itself** on server time, picks the highest valid bid, and settles. |
| 10 | Check both bidders' notifications. | The winner is told they won; the seller is told it sold; the others are told it closed. |
| 11 | Look at **Admin → Audit trail**. | Every action — including the bids that were *rejected* — is recorded with a server timestamp. |

**To prove the concurrency claim** without clicking fast enough, run the automated test instead:

```bash
cd backend
mvn test -Dtest=BiddingConcurrencyTest
```

It fires 20 bidders at one auction simultaneously and asserts the final database state is
consistent: one winner, no lost bids, and a strictly increasing ladder that honours the increment.

---

## 4. Architecture

```
                         ┌──────────────┐
                         │   Browser    │
                         └──────┬───────┘
                                │ HTTP + WebSocket
                         ┌──────▼───────┐
                         │    Nginx     │  :80
                         │ reverse proxy│
                         └──┬────────┬──┘
              /  (SPA)      │        │   /api, /ws
                ┌───────────▼┐     ┌─▼──────────────┐
                │  React SPA │     │  Spring Boot   │  :8080
                │ Vite+R3F   │     │  REST + STOMP  │
                └────────────┘     └──┬──────────┬──┘
                                      │          │
                            ┌─────────▼──┐   ┌───▼──────────┐
                            │   Redis    │   │  PostgreSQL  │
                            │  pub/sub   │   │   durable    │
                            │ rate limit │   │    store     │
                            └─────┬──────┘   └──────────────┘
                                  │
                    events fan out to every backend instance
                                  │
                         ┌────────▼─────────┐
                         │ WebSocket / STOMP │
                         └───────────────────┘
```

Each box is a container in `docker-compose.yml`. Redis is what makes the design horizontally
scalable: a bid accepted by instance A is published to Redis, and instance B relays it to the
WebSocket sessions *it* is holding. Adding `docker compose up --scale backend=3` behind the proxy
needs no application changes.

**PostgreSQL is the system of record.** Redis holds transient state only (pub/sub messages, rate
limit counters). Nothing is lost if Redis restarts.

---

## 5. How concurrency is handled

This is the part that matters most, so it is worth being precise about.

### The problem

Users A, B and C submit \$13,000, \$13,500 and \$14,000 within a few milliseconds of each other.
A naive implementation reads the current price, compares, and writes — and two requests that read
the same value will both think they are winning. One bid silently overwrites the other.

### The solution

`BidTransactionService.execute()` runs inside a single transaction that **begins by taking a write
lock on the auction row**:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select a from Auction a where a.id = :id")
Optional<Auction> findByIdForUpdate(@Param("id") Long id);
```

On PostgreSQL that is `SELECT ... FOR UPDATE`. The consequences:

- Exactly one of the three transactions gets the lock. The other two **block** until it commits.
- When they resume they re-read the **committed** price and are validated against it.
- A bid that was valid when it was composed but is stale by the time it is processed is rejected
  with `BID_TOO_LOW` — it can never overwrite a higher committed bid.
- **Ordering is decided by the database**, not by which HTTP request happened to arrive first.

A `@Version` column on `Auction` adds optimistic-locking defence in depth for any path that updates
an auction without the pessimistic lock. `BiddingService` retries a bounded number of times on
`ConcurrencyFailureException` before surfacing `CONCURRENT_UPDATE`.

Everything that does not need the lock is deliberately kept **outside** the critical section — rate
limiting before it, and event publishing, notifications and audit writes after the commit — so the
lock is held for the shortest possible time.

### Server time

Auction expiry is evaluated against `Instant.now()` on the server, inside the locked transaction.
The frontend calls `GET /api/system/time` at start-up, computes
`offset = serverTime - clientTime`, and renders every countdown against the corrected clock. A user
with a wrong device clock sees the correct countdown and cannot bid a millisecond past the close.

### Anti-sniping

Configurable, on by default. A valid bid inside the final 30 seconds extends the auction by 30
seconds (`ANTI_SNIPE_*` in `.env`), capped at 20 extensions so an auction cannot run forever. The
extension happens inside the same locked transaction as the bid, and an `AUCTION_EXTENDED` event
tells every watcher why the clock moved.

### Proxy bidding

A bidder can set a maximum. When someone outbids them, the engine automatically counter-bids the
smallest winning amount on their behalf, up to that ceiling, inside the same transaction. The loop
is bounded and terminates because every iteration raises the price by at least the increment.

---

## 6. Auction lifecycle

```
PENDING_APPROVAL ──approve──► SCHEDULED ──start time──► LIVE ──near end──► ENDING
       │                          │                       │                  │
       └──reject──► REJECTED      └────────cancel─────────┴───end time───────┘
                                                                  │
                                                                  ▼
                                              CLOSED ──► SETTLED ──► ARCHIVED
```

Transitions are owned by `AuctionLifecycleService` and validated by
`AuctionStatus.canTransitionTo(...)`. The time-driven ones are triggered by `AuctionScheduler`,
which runs every second: it selects candidates cheaply without a lock, then hands each one to the
lifecycle service, which **re-checks the condition while holding the row lock**. That is what stops
a close from racing with an anti-sniping extension that just moved the end time.

Settlement happens immediately on close: the winner is determined (highest bid, reserve met), bid
rows are marked `WON`/`LOST`, standing proxy bids are deactivated, and the winner, the seller and
the losing bidders are all notified.

---

## 7. Real-time layer

**Endpoint:** `/ws` (STOMP over native WebSocket). The JWT travels in the `Authorization` header of
the CONNECT frame and is validated by `StompAuthChannelInterceptor`.

| Destination | Who can subscribe | Carries |
|---|---|---|
| `/topics/auction/{id}` | anyone, including signed-out visitors | bids, extensions, status changes for one lot |
| `/topics/auctions` | anyone | the global feed that keeps catalogue prices fresh |
| `/user/queue/notifications` | the authenticated owner only | outbid alerts, wins, approvals |
| `/app/auctions/{id}/bids` | authenticated (client → server) | optional STOMP route for placing a bid |

**Event types:** `BID_PLACED`, `BID_REJECTED`, `OUTBID`, `AUCTION_STARTED`, `AUCTION_EXTENDED`,
`AUCTION_ENDING`, `AUCTION_CLOSED`, `AUCTION_SETTLED`, `AUCTION_WON`, `AUCTION_APPROVED`,
`AUCTION_REJECTED`, `NOTIFICATION`.

Events are published **after the transaction commits** — lifecycle events go through Spring's
`@TransactionalEventListener(AFTER_COMMIT)` — so no client can observe a state change that later
rolls back. The frontend keeps **one** connection for the whole app and multiplexes subscriptions
through a React context, reconnecting and re-subscribing automatically.

---

## 8. Data model

| Table | Purpose | Notable columns |
|---|---|---|
| `users` | accounts and roles | `email` (unique), `password_hash` (BCrypt), `role`, `status` |
| `items` | the thing being sold | `seller_id`, `category`, `model_kind` |
| `auctions` | one sale of one item | `version` (optimistic lock), `current_price`, `highest_bidder_id`, `start_time`, `end_time`, `original_end_time`, `status`, `extension_count`, `winner_id` |
| `bids` | accepted bids | `amount`, `server_timestamp`, `status`, `automatic` |
| `auto_bids` | proxy-bidding ceilings | unique on (`auction_id`, `bidder_id`) |
| `notifications` | per-user alerts | `type`, `read_flag` |
| `watchlist_entries` | follows | unique on (`user_id`, `auction_id`) |
| `audit_events` | append-only trail | includes **rejected** bids, written in their own transaction so they survive a rollback |

Relationships: a user has many items, bids, notifications and watchlist entries; an item has
auctions; an auction has bids, auto-bids and watchers, and points at a seller and (once closed) a
winner.

Schema is managed by Hibernate (`ddl-auto=update`), which keeps the project to one command. For a
production deployment you would switch this to Flyway migrations.

---

## 9. API reference

Full interactive documentation at **<http://localhost:8080/swagger-ui.html>**.

### Public

| Method | Path | Notes |
|---|---|---|
| `POST` | `/api/auth/register` | returns a JWT; `ADMIN` cannot be self-registered |
| `POST` | `/api/auth/login` | returns a JWT |
| `GET` | `/api/auctions` | filters: `search`, `category`, `status`, `minPrice`, `maxPrice`, `sort`, `page`, `size` |
| `GET` | `/api/auctions/featured` | landing-page carousel |
| `GET` | `/api/auctions/{id}` | full detail |
| `GET` | `/api/auctions/{id}/bids` | masked bid history |
| `GET` | `/api/system/time` | **authoritative clock** used for countdown sync |
| `GET` | `/api/categories` | category list |

### Authenticated

| Method | Path | Notes |
|---|---|---|
| `POST` | `/api/auctions/{id}/bids` | place a bid; optional `maxAmount` enables proxy bidding |
| `POST` | `/api/auctions/{id}/watchlist` | toggle watch |
| `GET` | `/api/users/me`, `/dashboard`, `/bids`, `/active`, `/wins`, `/losses`, `/watchlist` | bidder dashboard |
| `PUT` | `/api/users/me` · `POST` `/api/users/me/password` | profile |
| `GET` | `/api/notifications` · `/unread-count` · `POST` `/{id}/read` · `/read-all` | alerts |

### Seller (`ROLE_SELLER`)

`POST|PUT|DELETE /api/auctions[/{id}]` · `GET /api/seller/auctions` · `GET /api/seller/stats` ·
`GET /api/seller/auctions/{id}/analytics`

### Admin (`ROLE_ADMIN`)

`GET /api/admin/stats` · `/users` · `/auctions` · `/auctions/pending` · `/audit` ·
`/reports/activity` · `POST /api/admin/auctions/{id}/approve|reject|cancel` ·
`PATCH /api/admin/users/{id}/status|role`

### Error format

Every failure returns the same body with a stable code:

```json
{
  "code": "BID_TOO_LOW",
  "message": "Your bid must be at least 12700.00.",
  "status": 422,
  "path": "/api/auctions/1/bids",
  "timestamp": "2026-10-11T13:42:13.512Z",
  "details": { "minimumBid": 12700.00, "currentPrice": 12450.00, "minIncrement": 250.00 }
}
```

Codes: `AUCTION_NOT_FOUND`, `AUCTION_NOT_ACTIVE`, `AUCTION_ENDED`, `AUCTION_NOT_APPROVED`,
`AUCTION_ALREADY_STARTED`, `BID_TOO_LOW`, `DUPLICATE_BID`, `SELF_BID_FORBIDDEN`,
`CONCURRENT_UPDATE`, `RATE_LIMITED`, `USER_NOT_AUTHORIZED`, `ACCOUNT_SUSPENDED`,
`INVALID_CREDENTIALS`, `EMAIL_ALREADY_USED`, `VALIDATION_ERROR`, `INVALID_SCHEDULE`,
`INVALID_STATE_TRANSITION`, `INTERNAL_ERROR`.

---

## 10. Security

- **Passwords** — BCrypt, strength 12. Plaintext is never stored, logged or returned.
- **JWT** — HS256, signed with `JWT_SECRET` from the environment. The app refuses to boot if it is
  missing or shorter than 32 characters. Nothing is hard-coded.
- **Authorisation** — role-based at the URL level and with `@PreAuthorize`, re-checked in the
  service layer (a seller can only edit their own lots; only an admin can moderate).
- **WebSocket** — the CONNECT frame is authenticated; private `/user/**` destinations require a
  principal; `/app/**` sends require one too.
- **Rate limiting** — bids are capped per user per window in Redis (so the limit holds across
  instances), with an in-process fallback, plus a coarse limit at the Nginx edge.
- **Injection** — all data access goes through JPA/Criteria; no string-concatenated SQL.
- **Privacy** — bid history exposes a masked label (`Kal*** S.`), never a full name or e-mail.
- **Validation** — Bean Validation on every request DTO, and the business rules re-checked
  server-side. The frontend never decides whether a bid is valid.

---

## 11. Testing

```bash
cd backend
mvn test
```

The suite runs against H2 in PostgreSQL mode with Redis disabled, so **no Docker is needed**.

| Test | What it proves |
|---|---|
| `BiddingConcurrencyTest` | 20 simultaneous bidders leave a consistent, strictly increasing ladder; 12 identical simultaneous bids produce exactly **one** winner; bids below the minimum, self-bids and double-bids are rejected |
| `AuctionLifecycleTest` | approval and rejection; closing on server time and declaring the winner; an unmet reserve means nobody wins; anti-sniping extends the end time; proxy bidding counter-bids to the ceiling and stops above it |
| `AuthApiTest` | registration issues a JWT and never echoes the password; duplicate e-mail and weak password rejected; protected endpoints 401; public endpoints stay open |
| `AuctionApiTest` | role enforcement on listing creation and the admin API; bid history is masked; anonymous users cannot bid |
| `WebSocketBiddingTest` | a real STOMP client receives `BID_PLACED` on the auction topic without polling, and the previous leader receives a private `OUTBID` |
| `AuctionRepositoryTest` | the scheduler's queries, the write-locked read, and distinct-bidder counting |
| `MaskingAndStateMachineTest` | identity masking and every allowed/forbidden lifecycle transition |

Run one of them: `mvn test -Dtest=BiddingConcurrencyTest`

---

## 11b. Running it in VS Code

Open the `auctix` folder in VS Code (**File → Open Folder**, pick the folder that contains
`docker-compose.yml` — not `backend` or `frontend` on their own). VS Code will offer the
recommended extensions; accept, or install **Extension Pack for Java** and **Docker** manually.

The project ships with `.vscode/tasks.json` and `.vscode/launch.json`, so you do not need to type
commands. Press **Ctrl/Cmd + Shift + P → Run Task** and pick one:

| Task | What it does |
|---|---|
| **AuctiX: start everything (Docker)** | the whole stack, then open <http://localhost> |
| **AuctiX: dev mode (infra + backend + frontend)** | postgres/redis in Docker, backend and Vite with hot reload |
| **AuctiX: start infrastructure only** | just postgres and redis |
| **AuctiX: run all backend tests** | `mvn test` |
| **AuctiX: run the concurrency test** | the 20-simultaneous-bidders proof |
| **AuctiX: stop everything** | `docker compose down` |

To debug the backend with breakpoints, press **F5** and choose **Debug AuctiX backend** — it starts
postgres and redis first, then launches Spring Boot with the debugger attached. Set a breakpoint in
`BidTransactionService.execute()` and place a bid to step through the locking logic.

Remember to `cp .env.example .env` and set `JWT_SECRET` before the Docker task. The dev tasks
supply a development secret for you.

## 12. Local development without Docker

Useful when you want hot reload. You still need PostgreSQL and (optionally) Redis.

**Backend**

```bash
cd backend

# Point at your own database, or start just the infrastructure containers:
#   docker compose up postgres redis

export JWT_SECRET="a-local-development-secret-at-least-32-characters"
export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/auctix"
export SPRING_DATASOURCE_USERNAME=auctix
export SPRING_DATASOURCE_PASSWORD=auctix

mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

The `dev` profile generates a random JWT key if you forget to export one (tokens then reset on
every restart) and turns Redis off so the app runs standalone.

**Frontend**

```bash
cd frontend
npm install
npm run dev     # http://localhost:5173
```

Vite proxies `/api` and `/ws` to `http://localhost:8080`, so there is no CORS setup to do. Override
the target with `VITE_PROXY_TARGET` if your backend is elsewhere.

**Useful commands**

```bash
npm run typecheck                 # TypeScript, no emit
npm run build                     # production bundle
docker compose logs -f backend    # follow backend logs
docker compose exec postgres psql -U auctix -d auctix
docker compose exec redis redis-cli monitor      # watch events fan out live
```

---

## 13. Configuration reference

Everything is environment-driven. Full list in `.env.example`.

| Variable | Default | Meaning |
|---|---|---|
| `JWT_SECRET` | *(required)* | HS256 signing key, 32+ characters |
| `JWT_EXPIRATION_MINUTES` | `720` | token lifetime |
| `POSTGRES_DB` / `_USER` / `_PASSWORD` | `auctix` | database credentials |
| `REDIS_ENABLED` | `true` | `false` runs single-instance with in-process events |
| `ANTI_SNIPE_ENABLED` | `true` | extend on late bids |
| `ANTI_SNIPE_WINDOW_SECONDS` | `30` | what counts as "late" |
| `ANTI_SNIPE_EXTENSION_SECONDS` | `30` | how much time a late bid buys |
| `BID_RATE_LIMIT_MAX` | `10` | bids allowed per user per window |
| `AUCTION_SCHEDULER_INTERVAL_MS` | `1000` | how often lifecycle transitions are evaluated |
| `AUCTION_ENDING_WINDOW_SECONDS` | `60` | when a lot is flagged `ENDING` |
| `AUCTIX_SEED_ENABLED` | `true` | load demo data into an empty database |
| `HTTP_PORT` | `80` | the port Nginx publishes |

---

## 14. Project layout

```
auctix/
├── docker-compose.yml          # the whole stack
├── .env.example                # copy to .env
├── infra/nginx/nginx.conf      # reverse proxy: /api, /ws, SPA
├── backend/
│   ├── Dockerfile              # multi-stage Maven → JRE
│   ├── pom.xml
│   └── src/main/java/com/auctix/
│       ├── config/             # security, WebSocket, Redis, scheduling, seed data
│       ├── domain/             # JPA entities + enums (the state machine lives here)
│       ├── repository/         # Spring Data, including the write-locked read
│       ├── dto/                # request/response records, grouped by area
│       ├── service/            # BidTransactionService, BiddingService, lifecycle, scheduler
│       ├── realtime/           # Redis pub/sub bus + STOMP broadcaster
│       ├── security/           # JWT, principal, filter
│       ├── web/                # REST controllers + global error handling
│       └── exception/          # ErrorCode, ApiException, ApiError
└── frontend/
    ├── Dockerfile              # multi-stage npm → nginx
    └── src/
        ├── components/three/   # the 3D gallery (procedural models, pedestal, particles)
        ├── components/auction/ # bid form, countdown, live ledger, cards
        ├── components/ui/      # design-system primitives
        ├── realtime/           # one STOMP connection, shared via context
        ├── lib/                # API client, server-clock sync, formatting
        └── pages/              # landing, browser, detail, dashboards, admin
```

**On the 3D:** every model — the watch, the car, the sneaker, the artwork, the collectible — is
built procedurally from Three.js primitives. Nothing is downloaded, so the gallery loads instantly
and works offline. Quality adapts to the device (reduced geometry and effects on phones), and if
WebGL is unavailable or the visitor has `prefers-reduced-motion` set, a styled 2D presentation is
rendered instead. The 3D is confined to the hero and the product viewer; admin tables and dashboards
are deliberately plain.

---

## 15. Troubleshooting

**`JWT_SECRET must be set`** — you skipped `cp .env.example .env`, or left the placeholder in place.
It must be at least 32 characters.

**Port 80 already in use** — set `HTTP_PORT=8088` in `.env` and open <http://localhost:8088>.
Same for `POSTGRES_PORT`, `BACKEND_PORT`.

**Backend keeps restarting** — check `docker compose logs backend`. Almost always the database is
not ready yet (Compose waits on the health check, but a slow machine can still time out) or
`JWT_SECRET` is too short.

**The page loads but prices never move** — the WebSocket is not connecting. The dot next to the
search box in the header shows the connection state. Check that `/ws` is reaching the backend
through Nginx, and look for the upgrade headers in `infra/nginx/nginx.conf`.

**"Another bid landed first"** — that is the concurrency control working as designed. Someone else's
bid committed while yours was waiting for the lock. Re-read the price and bid again.

**Login fails with a message naming an HTTP status** — the status tells you where it broke:

| What you see | Cause |
|---|---|
| `E-mail or password is incorrect.` | genuinely wrong credentials |
| `The API is not responding (HTTP 502/503)` | nginx cannot reach the backend: it is still starting, or it crashed. Check `docker compose logs backend` |
| `The API endpoint was not found (HTTP 404)` | the `/api` proxy rule is not matching |
| `The server returned an error (HTTP 500)` | a real backend exception; the message and `docker compose logs backend` will name it |
| `Cannot reach the AuctiX server` | nothing answered at all — the stack is down, or you are on the wrong port |

Confirm the API directly, bypassing the browser entirely:

```bash
curl -i -X POST http://localhost/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"kalpesh@auctix.io","password":"Password123!"}'
```

PowerShell:

```powershell
try {
  Invoke-RestMethod -Uri "http://localhost/api/auth/login" -Method Post `
    -ContentType "application/json" `
    -Body '{"email":"kalpesh@auctix.io","password":"Password123!"}'
} catch { $_.Exception.Response.StatusCode; $_.ErrorDetails.Message }
```

A healthy response contains a `token` and the user object.

**`port is already allocated`** — only Nginx publishes a port now, so this can only be `HTTP_PORT`.
Set `HTTP_PORT=8088` in `.env` and use <http://localhost:8088>. Postgres, Redis and the backend are
reachable inside the Docker network and deliberately not published; use
`docker compose exec postgres psql -U auctix -d auctix` or
`docker compose exec redis redis-cli` when you need them.

**Rebuild from scratch**

```bash
docker compose down -v
docker compose build --no-cache
docker compose up
```

---

## A note on this build

This project was written end to end as source. The sandbox it was authored in had no access to
Maven Central or npm, so the dependency-resolving build (`mvn package`, `npm install`) runs for the
first time on your machine — that is what `docker compose up --build` does. The Java sources were
parse-verified with `javac` and the TypeScript with `tsc`; the full type-check and compile happen in
the Docker build.
