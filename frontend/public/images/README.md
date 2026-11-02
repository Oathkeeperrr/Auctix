# Lot photographs

Drop image files in this folder and they appear on the auction cards and detail pages.
Anything here is served from the site root, so `images/rolex-submariner.jpg` is reachable at
`/images/rolex-submariner.jpg`.

## For the seeded demo lots

The seed data already points at these exact filenames. Add any of them and that lot gets a real
photo; leave one out and that lot keeps its generated placeholder. Nothing breaks either way.

| Filename | Lot |
|---|---|
| `rolex-submariner.jpg` | Rolex Submariner Date 126610LN |
| `bugatti-chiron.jpg` | Bugatti Chiron Pur Sport |
| `air-jordan-1.jpg` | Air Jordan 1 Retro High OG 'Chicago' |
| `starry-night.jpg` | The Starry Night - Museum Grade Replica |
| `patek-nautilus.jpg` | Patek Philippe Nautilus 5711 - Sprint Lot |
| `omega-speedmaster.jpg` | Omega Speedmaster Professional Moonwatch |

Landscape crops around 1200x900 look best; the cards use a 4:3 box with `object-cover`.

After adding files, rebuild the frontend so they are copied into the image:

```bash
docker compose up --build frontend
```

## For your own listings

The **Create auction** form has an *Image URL* field. Paste either a path into this folder
(`/images/my-item.jpg`) or any public URL. If the image fails to load, the card falls back to the
category placeholder rather than showing a broken icon.

## A note on sources

No images are bundled with this project, to keep it free of third-party licensing. Use your own
photographs, or something explicitly licensed for reuse.
