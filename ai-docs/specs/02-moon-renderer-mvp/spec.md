# Feature Specification: 02 — Moon Renderer MVP

**Branch:** `02-moon-renderer-mvp`
**Created:** 2026-04-28
**Status:** Draft (pending user ratification)

## Goal (1-line)

Pay down the Phase 0 spike's asset deviations: real NASA SVS Moon textures shipped as KTX2 + Basis Universal (per ADR-0004) — a 2 K bundled fallback plus an 8 K HD tier streamed from CDN on first launch — with the legally required attribution surface and the spike's `runBlocking`-on-UI-thread asset reads eliminated.

## User Scenarios

### User Story 1 — Real Moon textures (Priority: P1)

**Why this priority:** The whole reason this spec exists. Without it, users keep seeing the colored test-pattern quadrants the spike shipped (per ADR-0009 §1).

**Independent test:** Launch the app on a fresh install. The Moon should look like the Moon — recognizable Mare Tranquillitatis, Tycho crater, Mare Imbrium — not colored quadrants.

**Acceptance Scenarios:**
- WHEN the app launches on a fresh install THEN the system SHALL display the Moon using NASA SVS LROC albedo and an LDEM-derived normal map within 2 s.
- WHEN the user rotates the Moon to the near-side THEN Mare Tranquillitatis, Mare Serenitatis, Tycho, and Copernicus SHALL be visually recognizable.
- WHEN the renderer is up THEN textures SHALL be uploaded as KTX2 + Basis Universal and transcoded by Filament's `Ktx2Reader` to the GPU's native compressed format (per ADR-0004).

### User Story 2 — HD tier streams from CDN (Priority: P1)

**Why this priority:** ADR-0004 commits to keeping the install size under the Play Store 50 MB warning threshold. The 2 K bundle is "good enough on small phone screens"; the 8 K tier delivers real visual fidelity. Shipping only 2 K would defeat the visual upgrade the spec exists to deliver.

**Independent test:** Launch on Wi-Fi, watch the network. After ~5 s of background download, the Moon visibly sharpens into 8 K detail without an app restart.

**Acceptance Scenarios:**
- WHEN the user first launches the app on Wi-Fi AND the HD cache is empty THEN the system SHALL fetch the 8 K albedo + normal from the CDN URLs in the asset manifest in the background.
- WHEN the HD assets land in the cache AND the renderer is alive THEN the system SHALL transcode and rebind them in place of the 2 K bundles within one frame (no Engine teardown).
- WHEN the user is offline AND no HD cache exists THEN the system SHALL display the bundled 2 K fallback indefinitely without errors.
- WHEN the HD download fails partway THEN the system SHALL discard the partial file and retry on next app launch; the 2 K fallback SHALL stay bound in the meantime.

### User Story 3 — Async asset loading (Priority: P1)

**Why this priority:** Phase 3 review #9 documented `runBlocking { Res.readBytes(...) }` calls in `MoonHost.init` as accepted-for-spike-only. Real assets are larger (2 K KTX2 ~2 MB, 8 K KTX2 ~10 MB) and shipping a noticeable composition jank into v1 would be a regression in user-perceived performance.

**Independent test:** Launch on a low-end device. The first frame appears within ~1 s with whatever asset is ready (placeholder or 2 K), and the rest streams in without freezing the UI thread.

**Acceptance Scenarios:**
- WHEN the renderer host first composes THEN initial composition SHALL NOT call `runBlocking` to read assets — assets load via a coroutine on `Dispatchers.Default` and trigger the renderer's texture upload through the existing pull-not-push state path (ADR-0003).
- WHEN assets are still loading at first composition THEN the system SHALL show the Moon's geometric silhouette using the renderer's clear color, without animation jank.

### User Story 4 — Attribution credit (Priority: P2)

**Why this priority:** ADR-0004 mandates the NASA SVS attribution string for legal compliance. Not P1 because failing to ship it is a legal risk, not a visual one — engineering cost is small and the surface is small.

**Independent test:** Open the About / Credits sheet from the main UI. Confirm the attribution string appears verbatim.

**Acceptance Scenarios:**
- WHEN the user taps the ⓘ "About" affordance THEN the system SHALL display a credits surface containing the verbatim attribution string from ADR-0004:
  > Lunar surface imagery: NASA's Scientific Visualization Studio, "CGI Moon Kit" (Ernie Wright / Noah Petro), derived from LRO LROC and LOLA data. Public domain. https://svs.gsfc.nasa.gov/4720
- WHEN the user dismisses the About surface THEN the system SHALL return to the main Moon viewport without state loss (camera / sun / variant preserved).

### Edge Cases

- Phone with insufficient storage for HD download: gracefully fall back to 2 K, log to console; do not retry the download every launch (use exponential backoff or only retry on a manifest-version bump).
- HD download succeeds but fails KTX2 validation (corrupt file, hash mismatch): discard the cached file, keep 2 K bound, retry on next launch.
- Multiple app launches while HD download is in progress: deduplicate via a single in-flight job per session.
- 2 K bundled assets are corrupt: hard failure with clear error UI. This is a release-pipeline mistake, never a user fault.
- App backgrounded during HD download: download cancels gracefully (Ktor's `HttpResponse` cancellation), resumes on next foreground.
- Asset manifest version bumps server-side: app re-fetches HD on next launch (cache invalidation by version).
- iOS simulator on Apple Silicon (Rosetta): textures download + decode correctly under x86_64 translation.

## Requirements

### Functional Requirements

- **FR-001**: WHEN the app starts THEN the system SHALL load the 2 K bundled albedo + normal as KTX2 + Basis Universal via Filament's `Ktx2Reader` (Android: `filament-utils-android` `KTXLoader`; iOS: `Filament/ktxreader` subspec — already in the Phase 0 Podfile).
- **FR-002**: WHEN the app starts AND the HD cache is empty AND the device has free storage THEN the system SHALL fetch the 8 K albedo + normal from the CDN URLs listed in the manifest and persist them to platform Files dir.
- **FR-003**: WHEN the HD assets land in the cache AND the renderer is alive THEN the system SHALL transcode and rebind them in place of the 2 K bundles within one frame (no Engine teardown). The variant-swap pattern from `00-renderer-spike` T060 is reused.
- **FR-004**: WHEN cached HD assets are loaded THEN the system SHALL verify SHA-256 against the manifest BEFORE binding to Filament; on mismatch SHALL discard the cache file and re-download on next launch.
- **FR-005**: WHEN the user opens the About surface THEN the system SHALL display the verbatim NASA SVS attribution string from ADR-0004.
- **FR-006**: WHEN the app is offline AND the HD cache is empty THEN the system SHALL render the Moon with the 2 K bundled fallback indefinitely.
- **FR-007**: WHEN initial composition runs THEN the system SHALL NOT block on synchronous asset reads — the asset loader runs on a background coroutine and pushes texture-ready bytes through the existing renderer state path.
- **FR-008**: WHEN textures are uploaded to Filament THEN the system SHALL use the KTX2's mip chain. The `LINEAR_MIPMAP_LINEAR` minfilter SHALL be applied.
- **FR-009**: WHEN the asset manifest version changes between app launches THEN the system SHALL invalidate the HD cache and re-download.

### Key Entities

- **`AssetManifest`** — versioned descriptor (URL + SHA-256 + size + dimensions) for the HD tier. Bootstrap copy committed at `composeResources/files/manifest.json`; remote copy fetched from CDN if cache stale.
- **`AssetCache`** — platform Files-dir-backed cache; atomic write via temp + rename, SHA-256 verify, version-aware invalidation.
- **`MoonAssetLoader`** — orchestrates: read bundled 2 K → check cache → CDN fetch → SHA-256 verify → push to renderer via the existing texture-set state path.
- **`MoonRenderState.textureSet`** (new field) — `sealed class TextureSet` with `Placeholder`, `Bundled2K`, `Hd8K` variants; renderer rebinds material samplers when this changes.

## Non-Functional Requirements

- **Bundle size**: 2 K KTX2/ETC1S albedo + 2 K UASTC normal ≤ 5 MB total. Total install (with Compose runtime + Filament native libs) ≤ 50 MB so the Play Store warning isn't tripped.
- **HD download size**: 8 K KTX2/ETC1S albedo + 8 K UASTC normal ≤ 30 MB combined. Download time ≤ 5 s on broadband (50+ Mbps).
- **Performance**: 60 FPS sustained throughout HD swap-in (no frame drops).
- **Memory**: Both 2 K and 8 K Texture handles resident during the swap. Release the 2 K Texture once HD is bound.
- **Offline**: Fully functional with the bundled 2 K only — no network calls required to render.
- **Attribution**: Compliant with NASA media usage guidelines. NASA logo / insignia not used (per ADR-0004 § "Pitfalls to avoid").

## Success Criteria

- **SC-001**: Moon visibly resembles NASA imagery — Mare Tranquillitatis, Tycho, Copernicus all recognizable on the near-side.
- **SC-002**: HD download completes within 10 s on broadband; user-perceptible swap-in within ~5 s of first launch.
- **SC-003**: 60 FPS sustained on Pixel 6 + iPhone 12 (the same baseline as `00-renderer-spike`).
- **SC-004**: App functions offline using only the bundled 2 K fallback.
- **SC-005**: Attribution string visible in About surface verbatim.
- **SC-006**: `:shared:allTests` passes including new tests for `AssetManifest` parsing + `AssetCache` cache-hit / cache-miss / hash-mismatch / atomic-write.
- **SC-007**: No `runBlocking` calls remain on the UI thread in `MoonHost.kt` after the migration.

## Assumptions & Out of Scope

**Out of scope:**
- **Tile streaming for resolutions > 16 K** — would require an equirectangular tile pyramid + region-of-interest fetching. Defer to a future spec (e.g. `07-moon-renderer-tiled`).
- **Elevation displacement rendering** — real LDEM-driven geometric displacement (not just normal mapping). Defer to a polish-grade spec.
- **Time-of-day Earth-shadow simulation** — separate concern from texturing.
- **Color-managed pipeline** — we accept Filament's default sRGB → linear → sRGB tone-mapping.
- **Phase 0 alt-texture toggle (`moon_albedo_2k_alt.png`)** — its purpose was to validate the runtime swap path; the same path now carries the 2K-to-HD swap. Toggle removed in this spec; alt PNG deleted.

**Assumptions:**
- ADR-0004 (asset strategy) is the source of truth.
- ADR-0010 chose GitHub Releases as the HD CDN; T109 / T120 are unblocked once the repo is pushed to a known GitHub owner.
- NASA SVS CGI Moon Kit assets remain hosted at `https://svs.gsfc.nasa.gov/4720` for the duration of this spec's work.
- The chosen CDN host supports HTTPS, range requests for resumable downloads, and ETag-based cacheability.
- Bake script + `toktx` run on a Mac dev machine; the resulting KTX2 binaries are committed for the 2 K tier and uploaded once for the 8 K tier.

## References

- ADR-0001 (Filament renderer)
- ADR-0003 (renderer host pattern — pull-not-push)
- ADR-0004 (asset strategy — what we're now implementing)
- ADR-0009 (spike deviations log — what we're paying down)
- ADR-0010 (CDN host — GitHub Releases)
- `ai-docs/research/moon-assets.md` — full asset catalog + bake / convert workflow
- `ai-docs/specs/00-renderer-spike/results.md` — handoff state
- `./plan.md`
- `./tasks.md`
