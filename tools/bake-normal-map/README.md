# bake-normal-map

Bakes a tangent-space normal map from the NASA SVS LDEM (lunar elevation) for the Moon Explorer renderer. Owned by `02-moon-renderer-mvp` (T103, T105).

## Inputs

- **`ldem_16_uint.tif`** (5760x2880, 16-bit unsigned, 0.5 m / integer encoding)
  Source: NASA SVS CGI Moon Kit — <https://svs.gsfc.nasa.gov/vis/a000000/a004700/a004720/ldem_16_uint.tif>
  Public domain.

## Outputs

- `moon_normal_2k.png` — 2048x1024 tangent-space normal (RGB)
- `moon_normal_8k.png` — 8192x4096 tangent-space normal (RGB)

Both tiers are bicubic-resampled from the 5760x2880 normal baked at the LDEM's native resolution.

## Tangent-space convention

Matches the renderer's `UvSphere.kt` tangent frame:

| Axis | Direction |
|---|---|
| T | east (`dP/du`) |
| B | south (`dP/dv`, since v=0 is the north pole) |
| N | radial outward |

Channel encoding (OpenGL / Filament default):

| Channel | Meaning |
|---|---|
| R | `(nx + 1) / 2` — east tilt |
| G | `(ny + 1) / 2` — south tilt (G > 0.5 means surface leans south) |
| B | `(nz + 1) / 2` — always > 0.5 (z = 1 in tangent space) |

## Equirectangular distortion correction

A u-pixel spans `(2*pi*R*cos(phi))/W` meters; a v-pixel spans `(pi*R)/H` meters. At the poles `cos(phi)` -> 0, so `bake.py` clamps `cos(phi) >= 1e-3` (`POLE_CLAMP`). Polar normals are not meaningful at LDEM resolution — the clamp keeps the slope finite without affecting mid-latitude pixels.

## Prerequisites

- Python 3.10+
- NumPy >= 2.0
- Pillow >= 10.0

```bash
python3 -m pip install numpy pillow
```

## Usage

From repo root, after the LDEM TIFF is downloaded into the build cache:

```bash
python3 tools/bake-normal-map/bake.py \
  --input tools/build-ktx2/.cache/ldem_16_uint.tif \
  --out-dir tools/build-ktx2/.cache
```

Output PNGs are consumed by `tools/build-ktx2/` (T106) for KTX2 / Basis encoding.

## References

- [`ai-docs/research/moon-assets.md`](../../ai-docs/research/moon-assets.md) §5 — normal-map generation rationale
- [`ai-docs/specs/02-moon-renderer-mvp/spec.md`](../../ai-docs/specs/02-moon-renderer-mvp/spec.md) — FR-001, FR-008
- [`ai-docs/specs/02-moon-renderer-mvp/tasks.md`](../../ai-docs/specs/02-moon-renderer-mvp/tasks.md) — T103, T105
