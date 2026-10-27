# Lot photographs

Served at `/images/lots/<name>` — nginx proxies `/images/` to this service. Each lot has three
frames, `<slug>-1.jpg` to `<slug>-3.jpg`; `-1` is the catalogue cover, `-2` a detail crop, `-3`
an alternate view.

**All 28 seeded lots have artwork.** 84 files, 28 slugs.

## How artwork reaches a lot

The slug is the first argument of that lot's `new Lot(...)` row in `config/DataSeeder.java`.
Two paths put images on a lot:

1. **New lot** — the seeder creates it and attaches three frames.
2. **Existing lot** — `backfillMissingGalleries()` runs on every start and attaches frames to
   any lot in the database that has none. This is what lets artwork be added to a catalogue
   that is already seeded, without `docker compose down -v`.

A lot that already has images is never touched, so both paths are safe to re-run.

## Replacing artwork with photography

Drop in files of the same name and rebuild the backend image:

```
docker compose up -d --build backend
```

Landscape, around 1200x900. Cards use a 4:3 box with `object-cover`.

Note that the backfill only fills *empty* galleries — it will not replace existing rows. To
swap artwork on a lot that already has images, either keep the same filenames (no database
change needed, the URLs are unchanged) or clear that lot's `item_images` rows first.

## The slugs

| Slug | Lot | Category |
|---|---|---|
| `submariner` | Rolex Submariner Date 126610LN | Luxury watches |
| `daytona` | Rolex Cosmograph Daytona 116500LN | Luxury watches |
| `nautilus` | Patek Philippe Nautilus 5711/1A | Luxury watches |
| `speedmaster` | Omega Speedmaster Professional | Luxury watches |
| `tank` | Cartier Tank Louis Cartier 18k | Luxury watches |
| `reverso` | Jaeger-LeCoultre Reverso Tribute | Luxury watches |
| `chiron` | Bugatti Chiron Pur Sport | Cars |
| `carrera` | Porsche 911 Carrera 3.2 | Cars |
| `gullwing` | Mercedes-Benz 300 SL Gullwing | Cars |
| `integrale` | Lancia Delta HF Integrale | Cars |
| `jordan` | Air Jordan 1 'Chicago' | Sneakers |
| `royal` | Air Jordan 1 'Black Gold' | Sneakers |
| `dunkparis` | Nike Dunk Low SB 'Paris' | Sneakers |
| `yeezyzebra` | Adidas Yeezy Boost 350 V2 | Sneakers |
| `starrynight` | The Starry Night replica | Art |
| `composition` | Composition No. 7 | Art |
| `greatwave` | The Great Wave woodblock | Art |
| `cobalt` | Study in Cobalt | Art |
| `macintosh` | Apple Macintosh 128K | Electronics |
| `rangefinder` | Leica M6 Classic | Electronics |
| `gameboy` | Nintendo Game Boy DMG-01 | Electronics |
| `birkin` | Hermes Birkin 30 Togo | Fashion |
| `chanelflap` | Chanel Classic Flap Medium | Fashion |
| `levis501` | Levi's 501XX Big E | Fashion |
| `mantle` | 1952 Topps Mickey Mantle #311 | Sports memorabilia |
| `worldcupshirt` | Match-Worn 1986 World Cup Final Shirt | Sports memorabilia |
| `steiff` | Steiff Teddy Bear c.1905 | Collectibles |
| `hobbit` | The Hobbit, first edition | Collectibles |

## Provenance

All 84 files are flat vector illustrations, not photographs, and none depicts a real
manufacturer's design — each is a generic representative of its category. The 48 files for the
sixteen lots listed from `tank` onward were generated for this project as SVG and rasterised;
the generator is not part of the build. The original 36 came with the project.

For an academic submission, record this note: the artwork is original to the project and
carries no third-party licence.
