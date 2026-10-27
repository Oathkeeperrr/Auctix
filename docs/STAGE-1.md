# Stage 1 — product images and a 24–30 lot catalogue

What changed, why, and how to check it. Everything here is backend plus frontend; the bidding
engine, auction lifecycle, authentication and WebSocket layer were not touched.

## The root cause of the missing images

Nothing was broken. The fallback was working exactly as designed and the photographs had never
been added to the repository.

1. `DataSeeder.photoFor(title)` mapped six lot titles to `/images/<name>.jpg` and returned
   `null` for everything else.
2. Those paths resolved against `frontend/public/images/`, which held one file: `README.md`.
3. `Item.imageUrl` therefore held either a path that 404s, or `null`.
4. `ItemThumbnail` rendered an `<img>`, the request failed, `onError` fired, and the component
   fell back to a category-tinted glyph — `◴ ◰ ◧ ◩ ◆`.

A fifth problem would have appeared even with the files in place: nginx had no `/images/`
location, so those requests fell through to `location /` and the React app answered with
`index.html` instead of a photograph.

## What was added

**A gallery model.** `Item` held one `imageUrl`. It now also owns an ordered `List<ItemImage>`
with a URL, sort order and caption, cascaded so saving a lot saves its photographs. `imageUrl`
survives as the single cover used by catalogue tiles, set from the first frame so the two can
never disagree.

**Images served from the backend.** Photographs live in `static/images/lots` and reach the
browser through a new nginx `location /images/` that proxies to the backend, with a seven-day
cache header. `SecurityConfig` permits `/images/**` without authentication.

**A catalogue of 28 lots.** 13 live, 7 scheduled, 4 ended and 4 awaiting approval, across all
eight categories. Bid ladders are replayed as real `Bid` rows, so `currentPrice` and `bidCount`
are derived from the database rather than typed in. One ended lot closes below its reserve, so
the unsold path is represented.

**Seeding that can run twice.** The old guard was `if (userRepository.count() > 0) return;` —
all or nothing, which meant a lot added later never appeared without wiping the volume. Each
lot is now keyed on its title and users are matched on e-mail, so the catalogue can grow across
restarts.

**A gallery on the detail page.** Large frame, thumbnail strip, click to zoom, arrow-key
navigation. Photographs and the 3D view are now two tabs rather than alternatives; a lot with no
photographs opens on the 3D view and shows no chooser, so no seller is obliged to supply images.

**Card polish.** A shimmer while a photograph loads, a fixed 4:3 box, and an explicit action
reading "Place a bid", "View lot" or "View result" according to status.

## Files changed

| File | Change |
|---|---|
| `domain/ItemImage.java` | new — one photograph of a lot |
| `domain/Item.java` | owns the ordered gallery; `addImage` sets the cover |
| `repository/ItemImageRepository.java` | new |
| `repository/ItemRepository.java` | `existsByTitleIgnoreCase` for seed idempotency |
| `dto/auction/ItemImageDto.java` | new |
| `dto/auction/AuctionDetailDto.java` | carries `images` |
| `service/AuctionService.java` | populates the gallery per detail view |
| `config/SecurityConfig.java` | `/images/**` is public |
| `config/DataSeeder.java` | rewritten — 28 lots, per-lot idempotency |
| `infra/nginx/nginx.conf` | `/images/` proxies to the backend |
| `static/images/lots/*.jpg` | 36 files, 12 lots × 3 frames |
| `static/images/lots/README.md` | the image manifest |
| `types/index.ts` | `ItemImage`, and `images` on `AuctionDetail` |
| `components/auction/ItemThumbnail.tsx` | loading, ready and failed states |
| `components/auction/ProductGallery.tsx` | new — strip, zoom, keyboard |
| `components/auction/LotMedia.tsx` | new — photographs / 3D tabs |
| `components/auction/AuctionCard.tsx` | explicit view action |
| `pages/AuctionDetailPage.tsx` | gallery panel and related lots |

## Verifying

```powershell
docker compose down -v
docker compose up -d --build

# a photograph, not the React app
Invoke-WebRequest "http://localhost:8088/images/lots/submariner-1.jpg" |
  Select-Object StatusCode, @{n='Type';e={$_.Headers.'Content-Type'}}

# 24 publicly visible lots across 8 categories
(Invoke-RestMethod "http://localhost:8088/api/auctions?size=50").totalElements
(Invoke-RestMethod "http://localhost:8088/api/auctions?size=50").content.category | Sort-Object -Unique

# the gallery reaches the API
$id = (Invoke-RestMethod "http://localhost:8088/api/auctions").content[0].id
(Invoke-RestMethod "http://localhost:8088/api/auctions/$id").images

# seeding twice creates nothing
docker compose restart backend
docker compose logs --tail=5 backend     # "Demo catalogue already complete: 28 lots present."

# the bidding engine still passes
docker compose exec backend ./mvnw test -Dtest=BiddingConcurrencyTest
```

## Known gaps

- 16 of the 28 lots have no photographs. They show the category glyph. The manifest in
  `static/images/lots/README.md` lists the exact filenames wanted.
- The 12 images present are flat vector illustrations, not photographs. Replacing them is a
  drop-in change: same names, same folder.
- Sellers still cannot upload images. That is Stage 2.
- Musical instruments were requested as a category but `Category` has no such value. Adding one
  is a small change, deferred because no lot or image exists for it yet.
