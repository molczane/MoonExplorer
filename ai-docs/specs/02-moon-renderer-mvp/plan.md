# Implementation Plan: 02 — Moon Renderer MVP

**Branch:** `02-moon-renderer-mvp` | **Date:** 2026-04-28 | **Spec:** ./spec.md

## Summary

Replace the Phase 0 spike's PNG-via-`BitmapFactory` / `CGImageSource` decode path with KTX2 + Basis Universal loaded by Filament's native KTX2 reader. Ship the 2 K NASA SVS textures bundled in `composeResources/files/textures/`. Add a CDN-backed `AssetCache` that streams the 8 K HD tier on first launch and persists it to platform Files dir with SHA-256 verification. Eliminate the spike's `runBlocking` UI-thread asset reads. Add a small About surface for the legally required attribution.

## Technical Context

| Concern | Value | Source |
|---|---|---|
| KTX2 loader Android | `KTXLoader` from `filament-utils-android` | `tech-stack.md` (already on classpath via Phase 0) |
| KTX2 loader iOS | `Ktx2Reader` from `Filament/ktxreader` subspec | already in `iosApp/Podfile` via Phase 0 |
| HTTP client | Ktor 3.2.x multiplatform — `ktor-client-core` + `ktor-client-okhttp` (Android) + `ktor-client-darwin` (iOS) | new dep |
| JSON | `kotlinx-serialization-json` | new dep |
| Hashing | platform-native SHA-256 via `expect`/`actual`: `MessageDigest` (Android JVM) + `CC_SHA256` (iOS CommonCrypto) | new |
| Storage | `Context.filesDir` (Android) + `NSFileManager` `documentDirectory` (iOS) via `expect`/`actual` | new |
| Asset manifest | static JSON in commonMain resources + remote copy on CDN | new |
| CDN host | TBD — file ADR-0010 before T120 | open |

## Constitution Check

- [x] **I. Mobile-only** — no targets added.
- [x] **II. Tactile lunar globe** — fidelity upgrade serves the brief.
- [x] **III. KMP-shared state, platform-thin renderers** — `MoonAssetLoader` lives in commonMain; per-platform code is just file-system + HTTP plumbing via `expect`/`actual`.
- [x] **IV. Agent-ready** — no Koog dep added.
- [x] **V. Demo-friendly** — real assets justify the spec; tile streaming deferred.
- [x] **VI. Test boundaries** — `AssetManifest` parsing + `AssetCache` logic in `commonTest` with mock HTTP.
- [x] **VII. Specs and ADRs** — ADR-0010 (CDN host) filed before tasks reference it.

## Architecture

```
[App start]
    │
    ▼
MoonAssetLoader.load()  (commonMain, suspend, Dispatchers.Default)
    │
    ├── 1. Read bundled 2 K KTX2 from composeResources/files/textures/
    │      → push to renderer via state.textureSet = Bundled2K
    │
    ├── 2. Read AssetManifest (commonMain bootstrap + CDN if stale)
    │
    ├── 3. AssetCache.lookupOrFetch(manifest.albedo / manifest.normal)
    │      ├── cache hit  + sha256 valid → return path/bytes
    │      ├── cache hit  + sha256 invalid → discard → fetch
    │      └── cache miss → Ktor fetch → atomic temp+rename write → sha256 verify
    │
    └── 4. Push HD bytes to renderer via state.textureSet = Hd8K
           Renderer Ktx2Reader-decodes; rebinds material samplers;
           releases 2 K Texture handle in the same call.
```

The renderer's per-frame `applyTextureSet(state)` detects `state.textureSet` changes the same way `applyAlbedoVariant(state)` does in the Phase 0 spike (reuses the established pattern from T060 / ADR-0003).

## Components and Interfaces

### `commonMain` additions

| File | Purpose |
|---|---|
| `assets/AssetManifest.kt` | `@Serializable data class` with `version: String`, `albedo: AssetEntry`, `normal: AssetEntry`. `AssetEntry` has `url`, `sha256`, `sizeBytes`, `width`, `height`. |
| `assets/AssetCache.kt` | `class AssetCache(storage: StorageDir, http: HttpClient)`. `suspend fun lookupOrFetch(entry: AssetEntry): ByteArray`. Atomic write via temp file + rename. SHA-256 verify before return. |
| `assets/MoonAssetLoader.kt` | Orchestrator. `suspend fun loadInto(viewModel: MoonViewModel)`. Reads bundled 2 K → pushes; reads manifest; fetches HD if needed; pushes HD when ready. |
| `assets/StorageDir.kt` | `expect class StorageDir { fun resolve(name: String): String /* path */ ; suspend fun read(name: String): ByteArray? ; suspend fun writeAtomically(name: String, bytes: ByteArray) }`. |
| `assets/Sha256.kt` | `expect fun sha256(bytes: ByteArray): String` — hex-encoded. |

### `state/MoonRenderState.kt` (modified)

Add a `textureSet: TextureSet = TextureSet.Placeholder` field. Define `sealed class TextureSet` in the same file:
- `Placeholder` — before any asset loaded; renderer shows clear color.
- `Bundled2K(albedoPath: String, normalPath: String)` — KTX2 paths under `files/textures/`. Renderer reads via `Res.readBytes`.
- `Hd8K(albedoBytes: ByteArray, normalBytes: ByteArray)` — KTX2 bytes already in memory (from cache or fresh download).

The Phase 0 `albedoVariant: Int` field is removed (its only consumer, the alt-texture toggle, was a debug aid).

### `androidMain` additions

| File | Purpose |
|---|---|
| `assets/StorageDir.android.kt` | actual: `Context.filesDir`-backed. Construction takes a `Context` (passed via DI, not a global). |
| `assets/Sha256.android.kt` | actual: `MessageDigest.getInstance("SHA-256")`. |
| `render/MoonHost.kt` (modified) | Replace `uploadTexture(BitmapFactory ...)` with `KTXLoader.createTextureFromBuffer(engine, ByteBuffer.wrap(bytes), KTXLoader.Options())`. New `applyTextureSet(state: MoonRenderState)` rebinds material samplers when `textureSet` changes. Drop the `runBlocking` reads — the loader pushes via state. |

### `iosMain` additions

| File | Purpose |
|---|---|
| `assets/StorageDir.ios.kt` | actual: `NSFileManager.defaultManager.URLForDirectory(NSDocumentDirectory, ...)`. |
| `assets/Sha256.ios.kt` | actual: `CC_SHA256` from `<CommonCrypto/CommonDigest.h>` via cinterop platform headers. |
| `render/MoonAssets.kt` (modified) | Replaces the Phase 0 startup-push pattern. Now constructs `MoonAssetLoader` and runs it in a coroutine. |
| `render/MoonRendererProvider.kt` (modified) | Replace `applyAssets` + `applyAltAlbedo` + `applyAlbedoVariant` with a unified `applyTextureSet: (variant: Int, albedoBytes: ByteArray, normalBytes: ByteArray, materialBytes: ByteArray?) -> Unit`. The `materialBytes` arg is null after first call (material's already built). |

### `iosApp/iosApp/` modifications

| File | Purpose |
|---|---|
| `MoonRenderer.h` (modified) | Replace `loadAssetsAlbedo:normal:material:` + `loadAltAlbedo:` + `setAlbedoVariant:` with a single `loadTextureSetAlbedo:normal:material:variant:` method. |
| `MoonRenderer.mm` (modified) | Replace `decodePngToRgba8` path with `Ktx2Reader::Async::doTranscoding` + `uploadImages` per `ai-docs/research/filament-cmp-integration.md` §5. Texture rebind rebuilds + binds in place. |
| `MoonRendererViewController.swift` (modified) | One forwarder for `loadTextureSet`; pending-asset cache reduced to one struct. |
| `iOSApp.swift` (modified) | Wire `applyTextureSet` closure (replaces three from Phase 6). |

### Tools (dev-side, not shipped)

| Path | Purpose |
|---|---|
| `tools/bake-normal-map/bake.py` | Read LDEM `.tif`, central-difference + cosine-latitude correction → packed normal map PNG. |
| `tools/bake-normal-map/README.md` | How to run — input path, output paths. |
| `tools/build-ktx2/build.sh` | PNG → KTX2: `toktx --t2 --bcmp --genmipmap --assign_oetf srgb` (albedo) + `toktx --t2 --uastc 2 --uastc_rdo_l 1.0 --zcmp 18 --genmipmap --assign_oetf linear` (normal). 2 K + 8 K outputs. |
| `tools/build-ktx2/manifest.py` | Generate `manifest.json` with SHA-256 + sizes from the built KTX2 files. |
| `tools/build-ktx2/README.md` | Toolchain prerequisites (`brew install ktx`). |

### Bundled assets (replaces Phase 0 placeholders)

| File | Purpose |
|---|---|
| `composeResources/files/textures/moon_albedo_2k.ktx2` | Real NASA SVS LROC color, ETC1S, mipmapped. ~2 MB. |
| `composeResources/files/textures/moon_normal_2k.ktx2` | LDEM-baked normal, UASTC, mipmapped. ~2 MB. |
| `composeResources/files/manifest.json` | Bootstrap manifest (HD URLs + SHA-256 hashes + dimensions). |

The Phase 0 placeholder PNGs (`moon_albedo_2k.png`, `moon_albedo_2k_alt.png`, `moon_normal_2k.png`) are **deleted** — they served their purpose.

### About surface

| File | Purpose |
|---|---|
| `ui/AboutSheet.kt` | Compose modal bottom sheet with the verbatim attribution string from ADR-0004 + the build version + Filament + ADR pointers. |
| `ui/MoonExplorerScreen.kt` (modified) | Add an ⓘ `IconButton` aligned `TopStart` (mirrors the `TopEnd` placement of the variant toggle that we're removing). Tap opens `AboutSheet`. |

## Data Models

```kotlin
@Serializable
data class AssetManifest(
    val version: String,                 // bump → invalidate caches
    val albedo: AssetEntry,
    val normal: AssetEntry,
)

@Serializable
data class AssetEntry(
    val url: String,                     // e.g. https://cdn.../moon_albedo_8k.ktx2
    val sha256: String,                  // hex
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
)

sealed class TextureSet {
    object Placeholder : TextureSet()
    data class Bundled2K(val albedoPath: String, val normalPath: String) : TextureSet()
    data class Hd8K(val albedoBytes: ByteArray, val normalBytes: ByteArray) : TextureSet() {
        // ByteArray in a data class — equality is identity-based; we don't compare these.
        override fun equals(other: Any?): Boolean = this === other
        override fun hashCode(): Int = albedoBytes.size * 31 + normalBytes.size
    }
}
```

## Error Handling

| Failure mode | Response |
|---|---|
| Network fetch error | Fall back to 2 K, schedule retry on next app launch. Don't crash. |
| SHA-256 mismatch | Discard cache file, fall back to 2 K, retry on next launch. |
| Filesystem out of space | Fall back to 2 K, log warning, no retry until manifest version bumps. |
| KTX2 decode failure | Fall back to 2 K, log warning. |
| Manifest version bump | Cache invalidation: delete cached HD files, re-fetch on next launch. |
| App backgrounded mid-fetch | Coroutine cancellation; partial file deleted, retry on next foreground. |

## Testing Strategy

| Test | Where | Asserts |
|---|---|---|
| `AssetManifestTest.parse` | `commonTest` | Round-trip serialize/deserialize from `manifest.json`. |
| `AssetCacheTest.cacheHit` | `commonTest` (mock HTTP) | 2nd lookup with same entry returns bytes from disk, no HTTP call. |
| `AssetCacheTest.cacheMiss` | `commonTest` (mock HTTP) | 1st lookup fetches via mock HTTP, persists, returns bytes. |
| `AssetCacheTest.hashMismatch` | `commonTest` | Corrupt cache file → discard → re-fetch on next call. |
| `AssetCacheTest.atomicWrite` | `commonTest` | Concurrent writes don't produce torn reads — temp + rename semantics. |
| `MoonAssetLoaderTest.placeholderToBundled` | `commonTest` | Initial state `Placeholder` → after `loadInto` `Bundled2K`. |
| `MoonAssetLoaderTest.bundledToHd` | `commonTest` (mock HTTP) | After CDN fetch + verify, `state.textureSet` advances to `Hd8K`. |
| `Sha256Test` | `commonTest` | Known-good vector matches FIPS 180-4 sample. |
| Manual smoke test | Real Pixel 6 + iPhone 12 | All four user stories' acceptance scenarios pass. |

## Project Structure (delta from `00-renderer-spike` end-state)

```
shared/src/commonMain/kotlin/org/jetbrains/moonexplorer/
├── assets/                                 (new directory)
│   ├── AssetManifest.kt
│   ├── AssetCache.kt
│   ├── MoonAssetLoader.kt
│   ├── StorageDir.kt                       (expect)
│   └── Sha256.kt                           (expect)
├── state/MoonRenderState.kt                (modified — textureSet replaces albedoVariant)
├── state/MoonViewModel.kt                  (modified — drop set/toggleAlbedoVariant; add setTextureSet)
└── ui/AboutSheet.kt                        (new)

shared/src/androidMain/kotlin/org/jetbrains/moonexplorer/
├── assets/
│   ├── StorageDir.android.kt               (actual)
│   └── Sha256.android.kt                   (actual)
└── render/MoonHost.kt                      (modified — KTX2 path + applyTextureSet + drop runBlocking)

shared/src/iosMain/kotlin/org/jetbrains/moonexplorer/
├── assets/
│   ├── StorageDir.ios.kt                   (actual)
│   └── Sha256.ios.kt                       (actual)
├── render/MoonAssets.kt                    (modified — drives MoonAssetLoader)
└── render/MoonRendererProvider.kt          (modified — applyTextureSet replaces three closures)

shared/src/commonMain/composeResources/files/
├── textures/moon_albedo_2k.ktx2            (new — replaces .png)
├── textures/moon_normal_2k.ktx2            (new — replaces .png)
├── textures/moon_albedo_2k.png             (deleted)
├── textures/moon_albedo_2k_alt.png         (deleted)
├── textures/moon_normal_2k.png             (deleted)
└── manifest.json                           (new)

iosApp/iosApp/
├── MoonRenderer.h                          (modified — loadTextureSetAlbedo:normal:material:variant:)
├── MoonRenderer.mm                         (modified — Ktx2Reader path)
├── MoonRendererViewController.swift        (modified — single forwarder + pending struct)
└── iOSApp.swift                            (modified — wire applyTextureSet)

tools/bake-normal-map/                      (new)
├── bake.py
└── README.md

tools/build-ktx2/                           (new)
├── build.sh
├── manifest.py
└── README.md

ai-docs/decisions/
└── 0010-cdn-host.md                        (new — CDN choice)

gradle/libs.versions.toml                   (modified — add ktor + kotlinx-serialization)
shared/build.gradle.kts                     (modified — add ktor + serialization deps + plugin)
```

## Complexity Tracking

| Violation | Why needed | Simpler alternative rejected because |
|---|---|---|
| Adding Ktor as a multiplatform dep | CDN streaming requires HTTPS + cancellation + good iOS support | Hand-rolled `URLSession` + `OkHttp` plumbing would double the per-platform code |
| Adding kotlinx-serialization | `AssetManifest` is JSON | Hand-parsing JSON is ~50 LOC of fragile string handling |
| `expect` / `actual` for SHA-256 | No multiplatform crypto in Kotlin stdlib | Pure-Kotlin SHA-256 is slow on large files; OKio-style hashing not on classpath |
| `expect` / `actual` for `StorageDir` | Filesystem API differs between Android and iOS | Could pull in `okio` for a unified Path abstraction; small dep but not yet justified |

## Open verifications (resolve as part of this spec)

- **ADR-0010 (CDN host)** must land before T120. Candidates: Cloudflare R2 (free egress, simple S3-compatible API), GitHub Releases (free, immutable per release, manual upload), AWS S3 (paid but flexible). Recommend GitHub Releases for the spike-grade simplicity and zero ongoing cost; revisit if asset turnover becomes high.
- **Tangent format on iOS in Phase 0**: was packed-quat FLOAT4 (per Phase 3 review #4). The real LDEM-baked normal map exercises the TBN matrix correctness for the first time — the flat placeholder normal masked any issue. If lighting on iOS looks wrong after T115, debug via the bitangent-sign check noted in the Phase 3 review.

## References

- ADR-0001 (Filament renderer)
- ADR-0003 (renderer host pattern — pull-not-push)
- ADR-0004 (asset strategy — what we're now implementing)
- ADR-0009 (spike deviations log — what we're paying down)
- ADR-0010 (CDN host — TBD)
- `ai-docs/research/moon-assets.md` — asset catalog + bake/convert workflow
- `ai-docs/research/filament-cmp-integration.md` §5 (KTX2 reader on both platforms)
- `ai-docs/specs/00-renderer-spike/results.md` — handoff state
- `./spec.md`
- `./tasks.md`
