# ADR-0004: Asset strategy — NASA SVS + KTX2/Basis Universal + bundled fallback + CDN

**Status**: Accepted
**Date**: 2026-04-28
**Supersedes**: —

## Context

The Moon needs an albedo (color) texture and a normal map. Optionally an elevation map for later phases. Constraints:

- Mobile install size budget: keep under Google Play's 50 MB warning threshold.
- Want to revisit/iterate textures without forcing app store releases.
- Must be free / public-domain / permissive license.
- Equirectangular projection (UV sphere maps lat/lon directly to UV).
- Filament supports KTX2 + Basis Universal natively via `Ktx2Reader`, transcoding at load time to the GPU's native format.

## Decision

### Source

**NASA SVS CGI Moon Kit** — public domain, derived from LRO/LROC/LOLA data ([https://svs.gsfc.nasa.gov/4720](https://svs.gsfc.nasa.gov/4720)).

- v1 albedo: `lroc_color_poles_8k.tif` (8192×4096, 48.3 MB source, 8-bit sRGB)
- v1 elevation (for normal-map derivation, not displacement v1): `ldem_16_uint.tif` (5760×2880, 31.7 MB source, 16-bit unsigned half-meters)
- Bundled fallback: 2K downsamples of both

Use the 2019 8-bit `_poles` set, **not** the 2025 16-bit `srgb` set. 8-bit is plenty for a handheld Moon, and the 16-bit set is 4× heavier per tier with no perceptual gain on a phone screen.

### Format

**KTX2 + Basis Universal**, transcoded at load time to GPU-native format (ASTC on iOS + new Android, ETC2 on older Android, BC7 on macOS).

- Albedo: ETC1S compression, sRGB transfer function (`toktx --t2 --bcmp --assign_oetf srgb --genmipmap`)
- Normal map: UASTC compression, linear transfer function (`toktx --t2 --uastc 2 --uastc_rdo_l 1.0 --zcmp 18 --assign_oetf linear --genmipmap`)
- Mipmaps generated at conversion time.

### Packaging

**Bundled in `:shared/src/commonMain/composeResources/files/textures/`** — a 2K fallback set (2K albedo + 2K normal as KTX2/ETC1S, target ~2–3 MB total). Loaded via `Res.readBytes("files/textures/moon_albedo_2k.ktx2")`.

**Streamed from CDN on first launch** — an 8K HD set (8K albedo + 8K normal as KTX2/ETC1S, target ~10–16 MB total). Cached to platform Files dir; validated with content-hash; falls back to bundled 2K if download fails or offline.

CDN host TBD in a later ADR (Cloudflare R2 / GitHub Releases / S3 are all viable).

### Normal-map generation

**Bake offline** with a Python script (NumPy + PIL):
1. Read `ldem_16_uint.tif`.
2. Compute central-difference of elevation in spherical coordinates with `cos(latitude)` correction for equirectangular distortion at the poles.
3. Normalize, pack `(nx, ny, nz)` → `(R, G, B)` via `(x*0.5+0.5)`.
4. Up-sample to match albedo resolution (4K/8K) with bicubic.

Commit the bake script under `tools/bake-normal-map/`. Commit baked outputs under `assets/textures/`. Re-bake when source changes; document the source LDEM version in the bake output's metadata sidecar.

### Attribution

Ship in About / Credits screen:

> Lunar surface imagery: NASA's Scientific Visualization Studio, "CGI Moon Kit" (Ernie Wright / Noah Petro), derived from LRO LROC and LOLA data. Public domain. https://svs.gsfc.nasa.gov/4720

Per [NASA media usage guidelines](https://www.nasa.gov/nasa-brand-center/images-and-media/), commercial use is allowed but the app must not imply NASA endorsement and must not use the NASA insignia/logotype/worm.

**Star imagery (added 2026-04-30 via 07-celestial-background, T701):**

> Milky Way Panorama: ESO/S. Brunier. CC BY 4.0. https://www.eso.org/public/images/eso0932a/

The 6000×3000 equirectangular original (ESO press release 0932) is offline-baked
into 6 cubemap faces (1024×1024 each, ~10 MB total) by `tools/bake-stars-cubemap/
bake_eso.py`. CC BY 4.0 requires the credit string verbatim and unaltered; per
ESO's [copyright page](https://www.eso.org/public/copyright/) the wording above
is the canonical form. The ESO logo is **not** used (ESO retains rights to it
separate from the CC BY 4.0 image release).

## Alternatives rejected

- **Bundle full HD set in app**: 30+ MB above the Play Store warning threshold; texture iteration requires app updates.
- **CDN-only, no bundled fallback**: app is unusable on first launch without network.
- **Solar System Scope textures (CC-BY 4.0)**: requires extra attribution string; less authoritative than NASA-first-party source.
- **PNG / JPEG runtime**: decompresses to full-size RGBA on the GPU at load — ~134 MB for 8K albedo. Useless for runtime.
- **Per-platform ASTC**: requires per-platform encoding pipelines; KTX2/Basis transcodes to ASTC at load with one source artifact.
- **Procedural normal map (in-shader)**: requires custom Filament material with derivative computation; not worth it for v1.

## Consequences

- Bundled install ~30 MB total (Compose MP runtime + 2K fallback + everything else). Comfortably under 50 MB.
- High-res tier downloads on first launch: ~10–16 MB; ~3 sec on Wi-Fi.
- First-run UX must include a "downloading high-res Moon" indicator with cancel/skip.
- Offline behavior: 2K fallback always works.
- Texture iteration during development: re-bake → upload to CDN → app picks up on next launch (no app update).
- Conversion pipeline requires: ImageMagick (TIFF → PNG), Python+NumPy+PIL (normal-map bake), `toktx` from KTX-Software (PNG → KTX2). All checked into `tools/`.

## References

- `ai-docs/research/moon-assets.md`
- `ai-docs/research/agp9-kmp-native-deps.md` §4
- [NASA SVS CGI Moon Kit](https://svs.gsfc.nasa.gov/4720)
- [Filament `libs/ktxreader`](https://github.com/google/filament/tree/main/libs/ktxreader)
- [KTX 2.0 spec](https://github.khronos.org/KTX-Specification/ktxspec.v2.html)
- [NASA media usage guidelines](https://www.nasa.gov/nasa-brand-center/images-and-media/)
