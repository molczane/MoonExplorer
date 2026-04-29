# build-ktx2

Encodes the Moon Explorer renderer's KTX2 / Basis Universal asset tier and produces the runtime `manifest.json`. Owned by `02-moon-renderer-mvp` (T106, T107).

## Pipeline

```
                          tools/build-ktx2/
                          ├── install-toktx.sh ──► bin/<os>-<arch>/{toktx, libktx*.dylib}
                          ├── tif2png.py
                          ├── build.sh ──┐
                          └── manifest.py│
                                         │
   tools/build-ktx2/.cache/  (gitignored)│
   ├── lroc_color_poles_{2k,8k}.tif      │
   ├── ldem_16_uint.tif                  │
   ├── moon_normal_{2k,8k}.png ──────────┘  (← bake.py output)
   │
   └── (build.sh writes here):
       moon_albedo_{2k,8k}.png      (TIFF -> PNG via tif2png.py)
       moon_albedo_{2k,8k}.ktx2     (Basis ETC1S, sRGB)
       moon_normal_{2k,8k}.ktx2     (Basis UASTC, linear, zstd L18)
       manifest.json                (← manifest.py, consumed at runtime)
```

## Prerequisites

- macOS (Apple Silicon or Intel) — Linux/Windows install paths are not implemented in `install-toktx.sh` yet.
- Python 3.10+ with **Pillow** (`python3 -m pip install pillow`) — used by `tif2png.py` and the bake script.
- `curl` (Mac default) for downloading the KTX-Software .pkg.

## Why a vendored toktx instead of `brew install`

KTX-Software is **not** published to Homebrew core (as of 2026-04-29). Khronos's official Mac artifact is a distribution `.pkg` that requires admin to install globally. `install-toktx.sh` extracts the `toktx` binary plus the `libktx` dylibs from the `.pkg` payload using `pkgutil --expand` + `cpio` (no admin required) and vendors them into `tools/build-ktx2/bin/<os>-<arch>/`. Same pattern as `tools/matc/` for Filament's `matc`.

To pin a different KTX-Software version: `KTX_VERSION=4.4.2 ./install-toktx.sh` (default is the version this repo is verified against).

## End-to-end run

From repo root, after `tools/build-ktx2/.cache/` is populated by T104 (source TIFFs) + T105 (`bake.py`):

```bash
cd tools/build-ktx2
./install-toktx.sh                 # one-time per machine
./build.sh                          # produces 4 KTX2 files
python3 manifest.py \
  --in-dir .cache \
  --out .cache/manifest.json \
  --owner <github-owner> \
  --release-tag assets-v1
```

Then bundle the 2 K outputs and the manifest into `:shared`:

```bash
cp .cache/moon_albedo_2k.ktx2 ../../shared/src/commonMain/composeResources/files/textures/
cp .cache/moon_normal_2k.ktx2 ../../shared/src/commonMain/composeResources/files/textures/
cp .cache/manifest.json       ../../shared/src/commonMain/composeResources/files/
```

## Encoding choices

| Asset | Compression | Transfer fn | toktx flags |
|---|---|---|---|
| Albedo (2 K + 8 K) | Basis ETC1S | sRGB | `--t2 --bcmp --genmipmap --assign_oetf srgb` |
| Normal (2 K + 8 K) | UASTC L2 + RDO + zstd L18 | linear | `--t2 --uastc 2 --uastc_rdo_l 1.0 --zcmp 18 --genmipmap --assign_oetf linear` |

ETC1S keeps albedo small (≈ 0.5 bpp on natural images). UASTC + RDO + zstd is the right pivot for normal maps — ETC1S compression artifacts show as banding on shading derivatives. Both targets generate a mip chain at encode time so Filament can use `LINEAR_MIPMAP_LINEAR` filtering at runtime (FR-008).

## Output sizes (verified 2026-04-29)

| File | Size |
|---|---|
| `moon_albedo_2k.ktx2` | ~300 KB |
| `moon_normal_2k.ktx2` | ~2.0 MB |
| `moon_albedo_8k.ktx2` | ~4.7 MB |
| `moon_normal_8k.ktx2` | ~23.3 MB |
| **2 K bundle total** | **~2.3 MB** (≤ 5 MB per spec NFR) |
| **8 K HD total** | **~28 MB** (≤ 30 MB per spec NFR) |

## Manifest schema

`manifest.py` writes `manifest.json` matching the Kotlin `AssetManifest` shape (T110):

```json
{
  "version": "2026-04-29-1",
  "albedo": { "url", "sha256", "sizeBytes", "width", "height" },
  "normal": { "url", "sha256", "sizeBytes", "width", "height" }
}
```

`version` is date-based (`YYYY-MM-DD-N`); bumps every regeneration so cache invalidation (FR-009) is deterministic. URLs follow ADR-0010's pattern; the bundled manifest ships with `<owner>/MoonExplorer` placeholders that get search-replaced before T109 (the GH Releases upload).

## References

- ADR-0004 — asset strategy (KTX2/Basis, mip chain, OETF tags)
- ADR-0010 — HD assets on GitHub Releases
- `ai-docs/research/moon-assets.md` §4 — rationale for Basis / UASTC choices
- `ai-docs/specs/02-moon-renderer-mvp/spec.md` — FR-001, FR-008, NFR bundle/HD sizes
- `ai-docs/specs/02-moon-renderer-mvp/tasks.md` — T106, T107, T108
