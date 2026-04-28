# Moon Surface Assets for Filament Mobile App — Research Brief

> Research output. Source: agent run 2026-04-28. Filament v1.71.x supports `KHR_texture_basisu` (KTX2 with BasisU) and ships `Ktx1Reader.h` and `Ktx2Reader.h`.

## 1. Catalog of Sources

### NASA SVS — CGI Moon Kit (primary)

URL: [`https://svs.gsfc.nasa.gov/4720`](https://svs.gsfc.nasa.gov/4720)

The single most useful page for this project. Provides ready-to-render equirectangular textures derived from LRO data. License: NASA public domain; credit "NASA's Scientific Visualization Studio" (Ernie Wright / Noah Petro). All assets are equirectangular, 2:1 aspect.

Color/albedo (2025 LROC re-release):
- `lroc_color_16bit_srgb.tif` — full-res (~54k×27k implied), 16-bit TIFF, **2.4 GB**
- `lroc_color_16bit_srgb_16k.tif` — 16384×8192, **909.4 MB**
- `lroc_color_16bit_srgb_8k.tif` — 8192×4096, **232.0 MB**
- `lroc_color_16bit_srgb_4k.tif` — 4096×2048, **59.0 MB**
- `lroc_color.exr` — full-res float16, 942.8 MB

Color/albedo (2019 "with poles" version, 8-bit, smaller):
- `lroc_color_poles.tif` — 27360×13680, **494.1 MB**
- `lroc_color_poles_16k.tif` — 16384×8192, **178.3 MB**
- `lroc_color_poles_8k.tif` — 8192×4096, **48.3 MB**
- `lroc_color_poles_4k.tif` — 4096×2048, **12.5 MB**
- `lroc_color_poles_2k.tif` — 2048×1024, **3.2 MB**
- `lroc_color_poles_1k.jpg` — 1024×512, **135.8 KB**

Displacement (LDEM, derived from LOLA):
- `ldem_64.tif` — 23040×11520, float km, **1012.6 MB** (native ~64 ppd)
- `ldem_64_uint.tif` — same dims, 16-bit unsigned half-meters, **506.3 MB**
- `ldem_16.tif` — 5760×2880, **63.3 MB**
- `ldem_4_uint.tif` — 1440×720, **2.0 MB**

No normal map, specular, or roughness is shipped — these must be derived. All files served under `/vis/a000000/a004700/a004720/` on `svs.gsfc.nasa.gov`.

### NASA SVS — Moon Phase and Libration

Pages: [2024](https://svs.gsfc.nasa.gov/5187/), [2025](https://svs.gsfc.nasa.gov/5415/), [2026](https://svs.gsfc.nasa.gov/5587), gallery [`/gallery/moonphase/`](https://svs.gsfc.nasa.gov/gallery/moonphase/).

**Not a texture source.** These are 8760 pre-rendered hourly frames (730×730 or 1920×1080) showing the lit Moon for that year. They are useful as ground-truth for validating your Filament lighting against NASA's own renderer, but are not equirectangular maps — the underlying Moon textures used to make them come from the CGI Moon Kit above.

### USGS Astrogeology — LROC WAC Global Mosaic

URL: [`https://astrogeology.usgs.gov/search/map/Moon/LRO/LROC_WAC/Lunar_LRO_LROC-WAC_Mosaic_global_100m_June2013`](https://astrogeology.usgs.gov/search/map/Moon/LRO/LROC_WAC/Lunar_LRO_LROC-WAC_Mosaic_global_100m_June2013)

- Native resolution: **100 m/pixel**
- Dimensions: **109,164 × 54,582 pixels**
- File size: **~5.5 GB** (single GeoTIFF) at [`https://planetarymaps.usgs.gov/mosaic/Lunar_LRO_LROC-WAC_Mosaic_global_100m_June2013.tif`](https://planetarymaps.usgs.gov/mosaic/Lunar_LRO_LROC-WAC_Mosaic_global_100m_June2013.tif)
- Projection: Simple Cylindrical (= equirectangular), planetocentric lat / +E lon
- License: U.S. public domain; "please cite authors"

This is the *raw* science product behind the SVS color map. Use SVS for v1; come back to USGS only if you need scientific accuracy or to re-mosaic.

### USGS Astrogeology — LOLA Global DEM 118 m

URL: [`https://astrogeology.usgs.gov/search/map/Moon/LRO/LOLA/Lunar_LRO_LOLA_Global_LDEM_118m_Mar2014`](https://astrogeology.usgs.gov/search/map/Moon/LRO/LOLA/Lunar_LRO_LOLA_Global_LDEM_118m_Mar2014)

LOLA-derived global elevation, 118 m/pixel, equirectangular, public domain. SVS's `ldem_*` files are downsampled from this product.

### Solar System Scope (third-party, CC-BY 4.0)

URL: [`https://www.solarsystemscope.com/textures/`](https://www.solarsystemscope.com/textures/)

Pre-cleaned 2K / 4K / 8K equirectangular Moon JPEGs (8K = 8192×4096, ~14.3 MB JPEG). License is **CC-BY 4.0**, which is permissive for commercial use but requires attribution to Solar System Scope. Convenient as a backup or stand-in but you should prefer NASA's first-party data and avoid the extra attribution string.

---

## 2. Recommended Starting Set for v1

Pick from the SVS CGI Moon Kit:

| Role | Asset | Resolution | Source size |
|---|---|---|---|
| Albedo (color) | `lroc_color_poles_8k.tif` | 8192×4096 | 48.3 MB |
| Elevation (for normal-map gen + future displacement) | `ldem_16.tif` or `ldem_16_uint.tif` | 5760×2880 | 63.3 / 31.7 MB |
| Bundled fallback (in-app) | `lroc_color_poles_2k.tif` + derived 2K normal | 2048×1024 | ~3.2 MB |

Direct URLs:
- `https://svs.gsfc.nasa.gov/vis/a000000/a004700/a004720/lroc_color_poles_8k.tif`
- `https://svs.gsfc.nasa.gov/vis/a000000/a004700/a004720/ldem_16_uint.tif`
- `https://svs.gsfc.nasa.gov/vis/a000000/a004700/a004720/lroc_color_poles_2k.tif`

I recommend the `_poles` 2019 8-bit set over the 2025 16-bit set for v1: 8-bit RGB is plenty for a handheld Moon, and the 2025 release is 4× heavier per tier with no perceptual gain on a phone screen.

**Bundle / download size estimates** (8K equirect, RGBA8 source):

| Format | Albedo (8K) | Normal (8K, RG channels OK) | Total |
|---|---|---|---|
| Raw RGBA8 | 134 MB | 67 MB (RG) | 201 MB |
| PNG (zlib) | ~50–80 MB | ~25–40 MB | ~75–120 MB |
| KTX2 + Basis ETC1S | ~6–10 MB | ~4–6 MB | **~10–16 MB** |
| KTX2 + Basis UASTC | ~14–20 MB | ~10–14 MB | ~25–35 MB |
| ASTC 6×6 (in KTX2) | ~25 MB | ~12 MB (RG) | ~37 MB |
| ASTC 8×8 | ~14 MB | ~7 MB | ~21 MB |

ETC1S sizes are estimated from the Basis Universal docs (typical 0.5–1.5 bpp on natural images). Treat them as ballpark; verify with `basisu` once you have the files.

For the in-app **fallback** (2K albedo + 2K normal): KTX2/ETC1S lands at ~1.5–2.5 MB, comfortably inside your 5–7 MB budget.

---

## 3. Resolution Tiers

| Tier | Use case | Equirect dims | Compressed target | Source asset |
|---|---|---|---|---|
| Fallback | Cold start, bundled | 2048×1024 | 1.5–3 MB (ETC1S) | `lroc_color_poles_2k.tif` |
| MVP | First CDN download | 4096×2048 | 8–15 MB (ETC1S) | `lroc_color_poles_4k.tif` |
| Polish | "HD" toggle on Wi-Fi | 8192×4096 | 25–45 MB (ETC1S) | `lroc_color_poles_8k.tif` |
| High-end | Tablets + heavy zoom | 16384×8192 | 100–180 MB (ETC1S/UASTC) | `lroc_color_poles_16k.tif` |

**When does a single texture stop working?**

- iOS: Metal Feature Set Tables: **GPU Family 3+ (A9 / iPhone 6s, 2015 and later) supports 16384×16384**. Family 1–2 caps at 8192. ([Apple Metal Feature Set Tables](https://developer.apple.com/metal/Metal-Feature-Set-Tables.pdf)) Anything you ship today is comfortably 16K-capable.
- Android: GLES 3.0 spec mandates ≥2048; Adreno 4xx and Mali-T7xx onward report 16384 in practice, but Adreno 3xx (Snapdragon 800-era, still in budget devices) caps at 8192. Query `GL_MAX_TEXTURE_SIZE` at runtime and fall back.

Practical rule: **8K equirect (8192×4096) is the safe single-texture ceiling for a global Android+iOS install base in 2026.** 16K works on >95% of devices but you should still tile or downscale-on-demand for the long tail. Above 16K the source must be tiled (cubemap faces or a quadtree of equirect tiles); the SVS 27k×13k and USGS 109k×54k assets are tile-streaming territory.

The crossover from "ship one texture" to "stream tiles" is therefore at the 16K → 32K boundary, which only matters if you let users zoom in past ~50% of the screen showing one mare.

---

## 4. Texture Format and Packaging

PNG decompresses to full-size RGBA on the GPU — useless for runtime, only fine as a delivery container before conversion. ASTC (any block size) is GPU-native on every modern Android (GLES 3.2 / Vulkan) and iOS (A8+) device but requires per-platform encoding. **KTX2 + Basis Universal is the right pivot:** one container, transcode at load time to the GPU's native format (ETC2 on older Android, ASTC on new Android + iOS, BC7 on macOS).

Filament directly supports both: the [`libs/ktxreader`](https://github.com/google/filament/tree/main/libs/ktxreader) module ships [`Ktx1Reader.h`](https://github.com/google/filament/blob/main/libs/ktxreader/include/ktxreader/Ktx1Reader.h) and [`Ktx2Reader.h`](https://github.com/google/filament/blob/main/libs/ktxreader/include/ktxreader/Ktx2Reader.h). The `Ktx2Reader` API exposes `requestFormat()` (you pre-register the compressed formats your hardware supports), `load()` (synchronous: zstd-decompress then transcode), and `Async::doTranscoding()` / `uploadImages()` for off-thread loads. Inline doc: *"a basis-encoded KTX2 can be quickly transcoded to any number of formats, so you need to tell it what formats your hw supports."* It also recognises the glTF `KHR_texture_basisu` extension.

Conversion workflow for the project:

```bash
# 1. SVS .tif -> 8-bit PNG (ImageMagick)
magick lroc_color_poles_8k.tif -depth 8 lroc_color_8k.png

# 2. PNG -> KTX2 + Basis ETC1S (UASTC for normal map)
toktx --t2 --bcmp --genmipmap --assign_oetf srgb \
      lroc_color_8k.ktx2 lroc_color_8k.png            # albedo (sRGB, ETC1S)

toktx --t2 --uastc 2 --uastc_rdo_l 1.0 --zcmp 18 --genmipmap \
      --assign_oetf linear \
      moon_normal_8k.ktx2 moon_normal_8k.png          # normal map (linear, UASTC)
```

`toktx` (KTX-Software) and `basisu` are the two converters; `toktx --t2 --bcmp` produces the ETC1S variant Filament's `Ktx2Reader` consumes. Use UASTC for normal maps where ETC1S compression artifacts are visible as banding on shading derivatives. Always set `--assign_oetf srgb` on albedo and `linear` on normal/elevation — Filament respects the transfer function tag.

---

## 5. Normal Map Generation

SVS gives you an LDEM but no normal map. Three viable workflows:

**A. Bake offline from LDEM (recommended).**
Use the 5760×2880 `ldem_16_uint.tif` (or downsample LDEM 64 to your target size), then compute normals via central-difference of elevation in spherical coordinates. A short Python script with NumPy/PIL is ~30 lines: read heightmap, compute `dz/du` and `dz/dv` weighted by `cos(latitude)` to correct the equirectangular distortion at the poles, normalize, pack `(nx, ny, nz)` → `(R, G, B)` with the standard `(x*0.5+0.5)` mapping. Up-sample the resulting normal to match the 8K albedo with bilinear or bicubic. ImageMagick alone can't do the cosine correction; use Python.

**B. Standalone tools.** xNormal and Crazybump both convert heightmap → normal but neither corrects equirectangular distortion at the poles, so you'll get pinched normals at ±90° latitude. Acceptable if you mask the poles with the camera or only allow ±60° tilt.

**C. Compute in shader.** Skip the normal map entirely and sample the elevation map in the Filament fragment shader, computing normals from screen-space derivatives or finite-difference taps. This trades GPU cost for zero authoring time but requires a custom Filament material; not worth it for v1.

Recommendation: bake a 4K and 8K normal map offline from LDEM 16 once, ship them as KTX2/UASTC. Total authoring time: an afternoon.

---

## 6. Licensing Pitfalls

**NASA SVS.** Public domain in the U.S.; credit "NASA's Scientific Visualization Studio." Per [NASA media usage guidelines](https://www.nasa.gov/nasa-brand-center/images-and-media/), commercial use is allowed but **must not imply NASA endorsement**. The NASA insignia, logotype, and worm are *not* public domain — do not put them on your app icon, splash screen, or marketing. Don't use NASA imagery in NFTs or attribute AI-generated content to NASA.

Ship this attribution string in the About / Credits screen:

> Lunar surface imagery: NASA's Scientific Visualization Studio, "CGI Moon Kit" (Ernie Wright / Noah Petro), derived from LRO LROC and LOLA data. Public domain. https://svs.gsfc.nasa.gov/4720

**USGS Astrogeology.** Public domain; "please cite authors." If you use the LROC WAC mosaic or LOLA DEM directly:

> Moon LRO LROC-WAC Global Morphology Mosaic 100 m, USGS Astrogeology Science Center / NASA / GSFC / Arizona State University, June 2013.

> Moon LRO LOLA Global LDEM 118 m, USGS Astrogeology / NASA GSFC, March 2014.

**Solar System Scope** (only if used). CC-BY 4.0 — requires "Textures by Solar System Scope (https://www.solarsystemscope.com/textures), CC BY 4.0." A non-trivial requirement; prefer NASA-only.

**Pitfalls to avoid.** Don't use the NASA "meatball" or worm logo. Don't write "Endorsed by NASA," "Official NASA app," or similar. Don't use SVS-rendered Moon Phase frames as your in-app texture and call them "NASA imagery" without noting they're Ernie Wright's renders, not raw orbiter photos. Anything labeled "courtesy of [Person]" on an SVS page is third-party copyrighted material — check before use.

---

## Sources

- [NASA SVS — CGI Moon Kit (4720)](https://svs.gsfc.nasa.gov/4720)
- [NASA SVS — Moon Phase and Libration gallery](https://svs.gsfc.nasa.gov/gallery/moonphase/)
- [USGS Astrogeology — LROC WAC Global Mosaic 100m v3](https://astrogeology.usgs.gov/search/map/Moon/LRO/LROC_WAC/Lunar_LRO_LROC-WAC_Mosaic_global_100m_June2013)
- [USGS Astrogeology — LOLA Global LDEM 118m](https://astrogeology.usgs.gov/search/map/Moon/LRO/LOLA/Lunar_LRO_LOLA_Global_LDEM_118m_Mar2014)
- [Filament `libs/ktxreader`](https://github.com/google/filament/tree/main/libs/ktxreader) — `Ktx1Reader.h`, `Ktx2Reader.h`
- [Khronos KTX 2.0 spec](https://github.khronos.org/KTX-Specification/ktxspec.v2.html)
- [Basis Universal — KTX2 support details](https://github.com/BinomialLLC/basis_universal/wiki/KTX2-File-Format-Support-Technical-Details)
- [Apple Metal Feature Set Tables](https://developer.apple.com/metal/Metal-Feature-Set-Tables.pdf)
- [NASA media usage guidelines](https://www.nasa.gov/nasa-brand-center/images-and-media/)
- [Solar System Scope textures (CC-BY 4.0)](https://www.solarsystemscope.com/textures/)
