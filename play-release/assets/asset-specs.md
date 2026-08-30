# Google Play asset specifications

These specifications are for the **phone/tablet Android v1 only**. Do not select TV, Wear OS, Automotive, or XR form factors unless a real tested build exists for them.

## Required listing assets

| Asset | Play requirement | Deliverable/status |
|---|---|---|
| Store icon | 512 × 512 px, 32-bit PNG with alpha, maximum 1,024 KB | `icon-512.png`: dimensions/format/size/full-square-mask review PASS; publisher brand approval still required; not uploaded |
| Feature graphic | 1,024 × 500 px, JPEG or 24-bit PNG without alpha | `feature-graphic-1024x500.png`: dimensions/format PASS; visually inspected; publisher brand approval still required; not uploaded |
| Phone screenshots | At least 2; JPEG or 24-bit PNG without alpha; 320–3,840 px; longest side no more than 2× shortest side | Capture at least 4 actual 1,080 × 1,920 portrait screenshots after release-candidate verification |
| 7-inch tablet screenshots | Optional unless tablet distribution/quality claims are made; recommended 4 at 1,080 px or higher, 9:16 or 16:9 | Capture only if the tablet layout passes the large-screen checklist |
| 10-inch tablet screenshots | Same caution as above | Capture only if the tablet layout passes |

Google recommends at least four 1,080 px screenshots in 9:16 portrait or 16:9 landscape for promotional eligibility. Screenshots must show the actual current app; never place an unbuilt screen in a store asset.

## Delivered raster verification

| File | Verified metadata | SHA-256 | Truthful content claim |
|---|---|---|---|
| `icon-512.png` | 512 × 512, 8-bit RGBA (fully opaque), 83,956 bytes | `225643a58c4993637b3abb27509bfe8928e8795ba94f8212ae188d676fef62ff` | White abstract O/assistant orbit with an aqua arc and spark on a full-square indigo-to-midnight field; no text, third-party logo, rank, price, or feature claim |
| `feature-graphic-1024x500.png` | 1,024 × 500, 8-bit RGB with no alpha, 106,456 bytes | `05fd1f386357f710a0759803af1d7c62426b59afd0ef4e8c9021811d82423496` | Matching white/aqua O mark, “Omar AI,” and “Your AI for life and business”; no price, award, rank, install prompt, third-party logo, or unbuilt-screen claim |

Recalculate hashes after any edit. The feature graphic passed the no-alpha requirement only after conversion to RGB. The replacement icon has a fully opaque full-square background, no baked rounded-square mask, and keeps its focal mark away from the outer edge so Google Play can apply dynamic masks.

Icon safe-zone source check: the visible orbit/mark/star foreground is bounded by approximately `x=104…432` and `y=104…409` within the 512 × 512 canvas, leaving at least 80 px to every outer edge. The background alone reaches the square edge; no corner transparency or pre-rounded plate is baked in.

## Editable vector masters

`icon-source.svg` and `feature-graphic-source.svg` are the editable vector masters used to export the verified raster files above. Keep source and raster branding together; after any source edit, export again, visually inspect, and recalculate metadata/hashes.

The vector system uses:

- Brand palette: midnight `#07111F`, indigo `#5137D7`, violet `#8E6CFF`, aqua `#56D4F2`, off-white `#F7F8FC`.
- Icon: an abstract “O”/assistant orbit. It does not imitate Google, OpenAI, Android, or another third-party mark.
- Feature graphic: a centered assistant-orbit motif and the restrained message “Your AI for life and business.” This is a product-positioning line, not a claim that every master-plan module is already shipped.
- Keep key content in the center safe area. Do not add ratings, awards, price claims, “Free,” “Best,” “#1,” install calls to action, Google Play badges, or third-party logos.

## Vector-concept export commands

Use a vetted vector exporter and visually inspect the result. Example with Inkscape:

```bash
inkscape icon-source.svg --export-type=png --export-filename=icon-512.png -w 512 -h 512
inkscape feature-graphic-source.svg --export-type=png --export-filename=feature-graphic-rgba.png -w 1024 -h 500 --export-background=#07111F --export-background-opacity=255
convert feature-graphic-rgba.png -alpha off -type TrueColor PNG24:feature-graphic-1024x500.png
```

For the feature graphic, verify the final export is 24-bit RGB PNG (PNG color type 2) with no alpha channel. The explicit flattening step is required because some SVG exporters write an alpha channel even when every pixel is opaque. If ImageMagick is unavailable, use another vetted encoder that produces PNG24/RGB, then inspect the file. Do not upload the SVG source itself to Play Console.

## Accessibility text

- Icon alt text: `White Omar AI orbit with an aqua spark on an indigo background`
- Feature graphic alt text: `Omar AI ring mark with the words Your AI for life and business`
- Screenshot alt text: use the screen-specific text in `../screenshots/screenshot-plan.md`; keep each entry under 140 characters.
