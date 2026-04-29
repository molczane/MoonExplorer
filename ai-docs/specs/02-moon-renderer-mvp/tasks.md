# Tasks: 02 — Moon Renderer MVP

## Format: `[ID] [P?] [US?] Description`

`[P]` = parallel-safe with sibling tasks. `[US#]` = which user story this serves.
Acceptance criteria for each user story live in `spec.md`.

## Path conventions

All paths relative to `MoonExplorer/` repo root. Task IDs are namespaced **T100+** to avoid collision with `00-renderer-spike` (T001–T093).

---

## Phase 1: Setup + asset prep (one-off, dev-side)

- [x] **T101** [P] Add Ktor + kotlinx-serialization to `gradle/libs.versions.toml` and `shared/build.gradle.kts`
  - Versions: Ktor `3.2.x` (latest stable for Kotlin 2.3.x), kotlinx-serialization `1.10.x`.
  - Modules: `ktor-client-core`, `ktor-client-content-negotiation`, `ktor-serialization-kotlinx-json` in `commonMain`; `ktor-client-okhttp` in `androidMain`; `ktor-client-darwin` in `iosMain`.
  - Apply `kotlin("plugin.serialization")` to `:shared`.
  - _Requirements: ADR-0004, FR-002_

- [ ] **T102** File **ADR-0010 — CDN host choice**
  - Compare Cloudflare R2 / GitHub Releases / AWS S3 for our scale (≤ 50 MB / version, low traffic).
  - Recommend GitHub Releases for simplicity (free, unlimited bandwidth, immutable per release, no auth required for static assets).
  - Document the URL pattern (e.g. `https://github.com/<user>/<repo>/releases/download/assets-vN/moon_albedo_8k.ktx2`) and the version-bump procedure.
  - **Block T109 / T120 on this ADR landing.**
  - _Requirements: ADR-0004, FR-002_

- [x] **T103** [P] Author `tools/bake-normal-map/bake.py` (Python + NumPy + PIL)
  - Reads `ldem_16_uint.tif` (5760×2880, 16-bit unsigned half-meters per `moon-assets.md`).
  - Central-difference of elevation in spherical coordinates + `cos(latitude)` correction for equirectangular distortion at the poles.
  - Normalize, pack `(nx, ny, nz)` → `(R, G, B)` via `(x*0.5+0.5)`.
  - Outputs 2 K + 8 K bicubic-resampled PNGs.
  - `tools/bake-normal-map/README.md` documents inputs / outputs / commands.
  - _Requirements: FR-001, FR-008_

- [x] **T104** [P] Download NASA SVS source TIFFs
  - `lroc_color_poles_8k.tif` (8192×4096, 48 MB) — albedo source.
  - `ldem_16_uint.tif` (5760×2880, 31.7 MB) — elevation source for normal-map bake.
  - Direct URLs in `ai-docs/research/moon-assets.md` §2.
  - **Don't commit these** — they're upstream sources that the bake / build steps consume; document their cache location under `tools/build-ktx2/.cache/` (gitignored).
  - _Requirements: ADR-0004_

- [x] **T105** Run `tools/bake-normal-map/bake.py` against the LDEM TIFF
  - Produces `moon_normal_2k.png` and `moon_normal_8k.png` under `tools/build-ktx2/.cache/`.
  - _Requirements: FR-001, FR-008_

- [ ] **T106** Author `tools/build-ktx2/build.sh` + run it
  - For albedo (`lroc_color_poles_2k.tif` and `_8k.tif` resampled): `toktx --t2 --bcmp --genmipmap --assign_oetf srgb`.
  - For normals: `toktx --t2 --uastc 2 --uastc_rdo_l 1.0 --zcmp 18 --genmipmap --assign_oetf linear`.
  - Produces `moon_albedo_{2k,8k}.ktx2` + `moon_normal_{2k,8k}.ktx2`.
  - `tools/build-ktx2/README.md` covers `brew install ktx` prerequisite + commands.
  - _Requirements: FR-001, FR-008, ADR-0004_

- [ ] **T107** Author `tools/build-ktx2/manifest.py` + run it
  - Reads the four KTX2 files, computes SHA-256, writes `manifest.json` matching `AssetManifest` schema.
  - Bumps `version` field on every regeneration (use the date — `2026-04-28-1` etc. — so cache invalidation is deterministic).
  - _Requirements: FR-009_

- [ ] **T108** Bundle 2 K KTX2 + manifest into `composeResources/files/`
  - Copy `moon_albedo_2k.ktx2` and `moon_normal_2k.ktx2` to `shared/src/commonMain/composeResources/files/textures/`.
  - Copy `manifest.json` to `shared/src/commonMain/composeResources/files/`.
  - **Delete** the Phase 0 placeholder PNGs (`moon_albedo_2k.png`, `moon_albedo_2k_alt.png`, `moon_normal_2k.png`).
  - _Requirements: FR-001, NFR bundle-size_

- [ ] **T109** Upload 8 K KTX2 files to the chosen CDN (per ADR-0010)
  - For GitHub Releases: `gh release create assets-v<n> tools/build-ktx2/.cache/moon_albedo_8k.ktx2 tools/build-ktx2/.cache/moon_normal_8k.ktx2 --notes "..."`.
  - Update `manifest.json` URLs + bundle the manifest.
  - _Requirements: FR-002, ADR-0010_

**Checkpoint**: `tools/` scripts run end-to-end on the dev machine and produce the four KTX2 files; bundled 2 K + manifest live under `composeResources/files/`; HD lives at the CDN URL with hashes matching the manifest. App still builds with the old PNG path code (transition to KTX2 in Phase 2).

---

## Phase 2: KTX2 loading (replaces PNG path)

- [ ] **T110** [P] [US1] Implement `assets/AssetManifest.kt` in commonMain
  - `@Serializable data class AssetManifest(version: String, albedo: AssetEntry, normal: AssetEntry)` + `AssetEntry(url, sha256, sizeBytes, width, height)`.
  - Companion `parse(json: String): AssetManifest` using `Json { ignoreUnknownKeys = true }`.
  - _Requirements: FR-001, FR-009_

- [ ] **T111** [P] [US1] Implement `assets/StorageDir.kt` (expect) + actuals
  - `expect class StorageDir { suspend fun read(name: String): ByteArray? ; suspend fun writeAtomically(name: String, bytes: ByteArray) ; fun exists(name: String): Boolean ; fun delete(name: String) }`.
  - Android actual backed by `Context.filesDir`; iOS actual backed by `NSFileManager` `documentDirectory`.
  - _Requirements: FR-002, FR-004_

- [ ] **T112** [P] [US1] Implement `assets/Sha256.kt` (expect) + actuals
  - `expect fun sha256(bytes: ByteArray): String`.
  - Android: `MessageDigest.getInstance("SHA-256")`.
  - iOS: `CC_SHA256` from `<CommonCrypto/CommonDigest.h>` via cinterop.
  - _Requirements: FR-004_

- [ ] **T113** [US1] Implement `assets/AssetCache.kt`
  - `class AssetCache(private val storage: StorageDir, private val http: HttpClient)`.
  - `suspend fun lookupOrFetch(entry: AssetEntry): ByteArray`: cache hit + sha256 valid → return; mismatch → discard + fetch; cache miss → fetch → atomic write → verify.
  - `suspend fun invalidate(version: String)`: delete cached files when manifest version changes.
  - _Requirements: FR-002, FR-004, FR-009_

- [ ] **T114** [US1] Modify `state/MoonRenderState.kt`
  - Replace `albedoVariant: Int` with `textureSet: TextureSet = TextureSet.Placeholder`.
  - Define `sealed class TextureSet { Placeholder; Bundled2K(...); Hd8K(...) }`.
  - Update `MoonViewModel` to drop `toggleAlbedoVariant` / `setAlbedoVariant`, add `setTextureSet(TextureSet)`.
  - _Requirements: FR-001, FR-003_

- [ ] **T115** [US1] Replace `MoonHost`'s PNG decode with KTX2 loading (Android)
  - Use `KTXLoader.createTextureFromBuffer(engine, ByteBuffer.wrap(bytes), KTXLoader.Options())` from `filament-utils-android`.
  - New `applyTextureSet(state)` rebinds material samplers when `state.textureSet` changes (state-driven swap pattern from Phase 0 T060).
  - `LINEAR_MIPMAP_LINEAR` minfilter to use the KTX2 mip chain.
  - Drop `BitmapFactory.decodeByteArray` + `uploadTexture(...)` paths.
  - _Requirements: FR-001, FR-003, FR-008_

- [ ] **T116** [US1] Replace `MoonRenderer.mm`'s PNG decode with KTX2 loading (iOS)
  - Use `Ktx2Reader::Async::doTranscoding` + `uploadImages` per `ai-docs/research/filament-cmp-integration.md` §5.
  - `_albedoTex` + `_normalTex` rebuilt from KTX2 byte arrays.
  - Drop `decodePngToRgba8` + `_albedoTexAlt` + `_currentAlbedoVariant`. Replace `loadAssetsAlbedo:normal:material:` + `loadAltAlbedo:` + `setAlbedoVariant:` with a single `loadTextureSetAlbedo:normal:material:variant:`.
  - _Requirements: FR-001, FR-003, FR-008_

- [ ] **T117** [US3] Implement `assets/MoonAssetLoader.kt` + drop `runBlocking` from `MoonHost.init`
  - `class MoonAssetLoader(scope: CoroutineScope, storage: StorageDir, http: HttpClient, viewModel: MoonViewModel)`.
  - `suspend fun loadInto()`: read bundled 2 K → `viewModel.setTextureSet(Bundled2K(...))` → read manifest → `cache.lookupOrFetch` for albedo + normal → `viewModel.setTextureSet(Hd8K(...))`.
  - `MoonHost.init` no longer reads assets directly; it just observes `state.textureSet` and applies on change.
  - `MoonExplorerScreen` constructs `MoonAssetLoader` once via `remember` and `LaunchedEffect(Unit) { loader.loadInto() }`.
  - _Requirements: FR-007, SC-007_

**Checkpoint**: bundled 2 K KTX2 textures load on both platforms; tap-launch-and-look shows the real Moon (not colored quadrants); `:shared:allTests` still green; no `runBlocking` left in `MoonHost.kt`.

---

## Phase 3: CDN streaming

- [ ] **T120** [US2] Wire HTTP fetch in `AssetCache` via Ktor
  - Use `HttpClient(...) { install(ContentNegotiation) { json() } }`.
  - `cache.lookupOrFetch` calls `http.get(entry.url) { onDownload { ... } }` for cache misses.
  - Stream to `storage.writeAtomically(...)` rather than buffering in memory if size > 4 MB.
  - Cancel mid-flight on `Job.cancel()` (called when `MoonAssetLoader`'s scope is cancelled).
  - _Requirements: FR-002, edge-case "App backgrounded mid-fetch"_

- [ ] **T121** [US2] First-launch HD download flow in `MoonAssetLoader`
  - After `Bundled2K` is pushed, kick off HD fetch on `Dispatchers.IO`.
  - Single in-flight job per `MoonAssetLoader` instance (deduplicate concurrent triggers).
  - On success, push `Hd8K` via `viewModel.setTextureSet(...)`. Renderer detects state change and rebinds.
  - _Requirements: FR-002, FR-003_

- [ ] **T122** [US2] HD swap-in without Engine teardown
  - `MoonHost.applyTextureSet(state)` already handles the rebind path from T115 — verify the `Hd8K` branch destroys the old (2 K) Filament `Texture` objects after binding the new ones (no leak).
  - iOS `MoonRenderer` does the same in `loadTextureSetAlbedo:normal:material:variant:` (variant goes 0=2K → 1=HD).
  - _Requirements: FR-003, NFR memory_

- [ ] **T123** [US2] Manifest version invalidation
  - `MoonAssetLoader` compares the local manifest version to a remote-or-bundled version it fetched.
  - If different → `cache.invalidate(oldVersion)` deletes the prior HD files before fetching the new ones.
  - _Requirements: FR-009_

- [ ] **T124** [US2] Offline graceful fallback
  - `MoonAssetLoader` catches `HttpRequestTimeoutException` / `IOException` from Ktor and logs without crashing.
  - State stays at `Bundled2K`; user-visible behavior is "real Moon, just not the HD version yet".
  - On next launch, the loader retries.
  - _Requirements: FR-006_

**Checkpoint**: with Wi-Fi available, HD textures land within ~5 s of launch and the Moon visibly sharpens. With airplane mode on a fresh install, the 2 K bundled textures load and the app is fully usable. Offline → online → restart → HD downloads.

---

## Phase 4: Attribution UI

- [ ] **T130** [US4] Implement `ui/AboutSheet.kt`
  - `ModalBottomSheet` (Material3) with the verbatim attribution string, build version (`BuildKonfig` or hand-rolled `expect val`), Filament version, and short pointers to the ADR docs.
  - Dismissable by drag or scrim tap.
  - _Requirements: FR-005_

- [ ] **T131** [US4] Wire ⓘ button in `MoonExplorerScreen`
  - `IconButton(Icons.Outlined.Info, ...)` aligned `TopStart` with `statusBarsPadding()` (mirror the Phase 6 toggle's safe-area treatment).
  - Tap: open `AboutSheet`.
  - The Phase 6 "Texture A / B" toggle is removed (its variant state went away with `albedoVariant`).
  - _Requirements: FR-005_

**Checkpoint**: tap ⓘ → attribution sheet appears with verbatim NASA SVS string; dismiss returns to the viewport with camera/sun preserved.

---

## Phase Final: Polish + tests + docs

- [ ] **T140** [P] `commonTest`: `AssetManifestTest.parse`
  - Round-trip serialize / deserialize a known-good manifest JSON.
  - Verify `version`, `albedo.url`, `albedo.sha256`, `normal.url`, `normal.sha256` all extracted correctly.
  - _Requirements: SC-006_

- [ ] **T141** [P] `commonTest`: `AssetCacheTest`
  - Use `MockEngine` from `ktor-client-mock` to drive HTTP responses.
  - Tests: cacheHit, cacheMiss, hashMismatch (corrupt cache → re-fetch), atomicWrite (no torn read on concurrent access).
  - _Requirements: SC-006, FR-002, FR-004_

- [ ] **T142** [P] `commonTest`: `Sha256Test`
  - Known-good vector: `sha256("abc".encodeToByteArray()) == "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"` (FIPS 180-4 sample).
  - Per platform via `expect`/`actual` — runs on both `androidHostTest` + `iosSimulatorArm64Test`.
  - _Requirements: SC-006_

- [ ] **T143** [P] `commonTest`: `MoonAssetLoaderTest.placeholderToBundledToHd`
  - Use `MockEngine` for HTTP. Drive a fake `MoonViewModel`.
  - Assert `state.textureSet` advances `Placeholder` → `Bundled2K` → `Hd8K`.
  - _Requirements: SC-006_

- [ ] **T144** [P] Update `ai-docs/specs/00-renderer-spike/results.md`
  - Add a closing "Handed off to 02-moon-renderer-mvp at commit `<hash>`" pointer.
  - _Requirements: agent-runbook.md_

- [ ] **T145** Smoke test on real Pixel 6 + iPhone 12; record FPS, any visible issues, and resolution outcomes in `ai-docs/specs/02-moon-renderer-mvp/results.md`
  - Confirm SC-001 through SC-005 on hardware.
  - Measure HD swap-in latency.
  - Verify offline mode (airplane mode + fresh install).
  - _Requirements: SC-001, SC-002, SC-003, SC-004, SC-005_

**Final Checkpoint**: all four user stories' acceptance criteria pass on real devices; `results.md` filed with FPS measurements and any deferred items; old PNG-path code fully removed.

---

## Dependencies & Execution Order

| From → To | Why |
|---|---|
| T101 → T117, T120 | Ktor + serialization deps must be on classpath |
| T102 (ADR-0010) → T109, T120 | CDN host decision blocks upload + fetch |
| T103, T104 → T105 | Bake script + LDEM TIFF in place before bake |
| T105, T104 → T106 | PNG inputs for KTX2 build |
| T106 → T107 → T108 | Build outputs → manifest → bundle |
| T108 → T115, T116 | Bundled KTX2 must exist for renderer to load |
| T109 → T121 | HD assets at CDN before fetch wiring |
| T110, T111, T112 → T113 | Building blocks for AssetCache |
| T113, T114 → T117 | Cache + state contract before loader orchestration |
| T117 → T120, T121 | MoonAssetLoader exists before HD fetch wiring |
| Phase 2 → Phase 3 | Bundled-2 K path must work before HD path |
| Phase 3 → Phase Final | All flows wired before polish + tests |

## Parallel Example: Phase 2 (after T108 + T101 land)

Three threads of work in parallel:
- **Thread A (data + cache)**: T110 → T111 → T112 → T113
- **Thread B (Android renderer)**: T115 (depends on T108)
- **Thread C (iOS renderer)**: T116 (depends on T108)

T114 (state) gates T115 + T116, so do T114 first or do it on Thread A right after T113.

T117 (loader) requires T113 (cache) + T114 (state) + T115 + T116 (renderers consume the new state).

## Implementation Strategy

- Land Phase 1 in a single PR titled "02-moon-renderer-mvp: assets + tooling".
- Land Phase 2 in a second PR titled "02-moon-renderer-mvp: KTX2 path + bundled 2 K".
- Land Phase 3 in a third PR titled "02-moon-renderer-mvp: CDN streaming".
- Land Phase 4 + Phase Final as a fourth PR titled "02-moon-renderer-mvp: attribution + polish + docs".
- Final squash-merge to `main` with an ADR-summary commit.

## Notes

- This spec **does not** introduce tile streaming or elevation displacement — explicitly out of scope per `spec.md` § "Out of scope". A future spec (`07-moon-renderer-tiled` or similar) handles those.
- The Phase 6 `albedoVariant` debug toggle goes away with this spec — its purpose was to prove the runtime swap path works, and the same path now serves the 2K-to-HD swap. No regression.
- ADR-0009 captures the Phase 0 deviations this spec pays down (PNG → KTX2, runBlocking → async). When all Phase Final tasks land, ADR-0009's items 1 + 6 (PNG and "spike runBlocking") are resolved.
- If the iOS lighting looks wrong with the real LDEM-baked normal map (Phase 3 review #4 noted the tangent-quat handling was untested under a real normal map), debug via the bitangent-sign check in `ai-docs/research/filament-cmp-integration.md` and `MoonRenderer.mm`'s `packTangentFrame`. The Android packed-quat path was unified with iOS in commit `e96a0c8`, so both should behave identically.
