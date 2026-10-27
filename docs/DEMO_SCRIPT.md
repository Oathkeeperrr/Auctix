# AuctiX — live demonstration script

A 6–8 minute walkthrough for a presentation or viva. It proves the three claims that matter:
**real-time**, **race-free**, and **server-authoritative**.

---

## Before you start

```bash
cp .env.example .env     # put a real 32+ char JWT_SECRET in it
docker compose up --build
```

Wait for `Started AuctixApplication`, then open <http://localhost>.

**Window setup** — have these ready before you present:

| Window | Signed in as | Showing |
|---|---|---|
| A (main screen) | `kalpesh@auctix.io` | the auction detail page |
| B (private window, side by side) | `riya@auctix.io` | the same auction detail page |
| C (background tab) | `admin@auctix.io` | Admin dashboard |
| D (terminal) | — | `docker compose logs -f backend` |

Password for all: `Password123!`

---

## Act 1 — The platform (60 seconds)

1. Land on <http://localhost>. The hero renders a 3D watch on a lit pedestal; the panel beside it
   shows a live countdown and current bid.
   > "Everything in this panel comes from the backend. The countdown is driven by the server's
   > clock, not the browser's."
2. Click the thumbnails under the panel to swap the featured lot. Each is a different procedural
   3D model — nothing is downloaded.
3. Scroll to **Live & Upcoming**, then go to **Auctions** and filter by category and status.

---

## Act 2 — The lifecycle (90 seconds)

4. In window C (admin), open **Admin → Pending approvals**. There is one listing waiting.
   > "A seller cannot publish straight to the floor. Every listing is moderated."
5. Click **Approve**. Status becomes *Upcoming*.
6. Switch to the seller's view and point out the notification that arrived instantly.
7. Back in admin, open the **Audit trail** tab.
   > "Approve, reject, every bid — including the rejected ones — are appended here with a server
   > timestamp."

---

## Act 3 — Real-time bidding (2 minutes) ★ the core demo

Use the seeded **sprint lot** (closes ~10 minutes after start-up) or the Rolex.

8. Open the same auction in windows A and B, side by side.
9. **In A**, place a bid.
   > "Watch B — I am not going to refresh it."
   - B's price pulses and updates.
   - The bid appears in B's live ledger with a masked identity (`Kal*** S.`).
10. **In B**, bid higher.
    - **A gets a red "You've been outbid" toast**, pushed to that user's private queue.
11. Point at window D (logs): the bid, the Redis publish and the broadcast are all visible.

**What to say:**
> "The bid went to Spring Boot over REST. It was validated and written inside a transaction that
> held a row lock on the auction. After it committed, the event was published to Redis, and every
> backend instance relayed it over STOMP — to the public topic for the price, and to one private
> queue for the outbid alert."

---

## Act 4 — The server is in charge (90 seconds)

12. Try to bid **below** the minimum → `BID_TOO_LOW` with the exact amount required.
13. Try to bid **twice in a row** while leading → `DUPLICATE_BID`.
14. Sign in as the seller and try to bid on your own lot → `SELF_BID_FORBIDDEN`.
    > "The frontend never decides whether a bid is valid. Every one of these is a server decision."
15. Tick **"Bid automatically for me up to a maximum"**, set a ceiling, and have the other window
    bid under it.
    - The engine counter-bids automatically, marked **auto** in the ledger.

---

## Act 5 — Closing on server time (90 seconds)

16. On the sprint lot, wait until under a minute. The status flips to **Ending** and the countdown
    turns red.
17. Place a bid **inside the last 30 seconds**.
    - **Anti-sniping** fires: the auction extends by 30 seconds and everyone is told why.
18. Let it run out.
    - The auction closes **itself** — nobody clicked anything.
    - The result block shows the winner and the final price.
    - The winner's bell shows "You won the auction"; the seller's shows "Your item sold".
19. Reload the page.
    > "This is read back from PostgreSQL. Redis only carried the messages; the record is durable."

---

## Act 6 — Concurrency, proved properly (60 seconds)

Clicking fast cannot demonstrate a race condition. The test can.

```bash
cd backend
mvn test -Dtest=BiddingConcurrencyTest
```

> "Twenty threads bid on the same auction at the same instant. The test asserts the final state is
> consistent: no bid was lost, exactly one winner, and the accepted bids form a strictly increasing
> ladder that honours the minimum increment. A second test fires twelve *identical* bids
> simultaneously and asserts that exactly one is accepted."

Then show the code that makes it true — `AuctionRepository.findByIdForUpdate`:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select a from Auction a where a.id = :id")
Optional<Auction> findByIdForUpdate(@Param("id") Long id);
```

> "On PostgreSQL that is `SELECT ... FOR UPDATE`. Concurrent bidders queue on the lock, and each
> one is validated against the committed price. The database decides the order, not the network."

---

## Questions you should expect

**"What if two bids arrive at exactly the same microsecond?"**
One transaction gets the row lock; the other blocks until the first commits, then re-reads the
committed price. If it no longer clears the minimum it is rejected with `BID_TOO_LOW`. A lower bid
can never overwrite a higher committed one.

**"What if the user changes their computer's clock?"**
Nothing changes. Expiry is evaluated against `Instant.now()` on the server inside the locked
transaction. The frontend only *renders* a countdown, and even that is corrected by an offset
measured against `GET /api/system/time`.

**"Why Redis if PostgreSQL already stores everything?"**
Redis is not storage here. It is the pub/sub bus that lets a bid accepted by one backend instance
reach WebSocket sessions held by another, plus the shared rate-limit counter. Lose Redis and no
data is lost.

**"How would you scale this?"**
`docker compose up --scale backend=3` behind the Nginx proxy. No application change is needed —
that is exactly what the Redis fan-out is for. Sticky sessions are not required because any
instance can serve any socket.

**"Why not just poll every second?"**
Polling is a round trip per client per second whether or not anything happened, and it still shows
stale prices between polls. The push model sends one message only when state actually changes.

**"What stops someone scripting thousands of bids?"**
Per-user rate limiting in Redis, a coarser limit at the Nginx edge, JWT authentication on every
request including the WebSocket handshake, and the row lock, which serialises per auction anyway.
