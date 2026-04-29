# 07-celestial-background — Results

T741 record of what shipped, what's user-confirmed, and what's still pending hardware measurement. The spec is feature-complete on `main`; on-device UX confirmation + the bloom tuning pass (T722) are the only outstanding work.

## Status by phase

| Phase | Code complete | User-verified | Commit |
|---|---|---|---|
| Phase 1 — skybox + state plumbing | ✓ | (pending hardware) | `30f4212` |
| ESO Milky Way bake (T701 + T702) | ✓ | n/a (license verified) | `510bc94` |
| Phase 2 — sun billboard | ✓ | (pending hardware) | `41f38f0` |
| Phase 3 — bloom on the sun | ✓ | (pending hardware; T722 tunes) | `cc7a9f2` |
| Phase 4 — SettingsSheet toggles | ✓ | (pending hardware) | `8bf4bab` |
| Phase Final — tests + docs | ✓ | n/a | (this commit) |

## Acceptance criteria

| | Status | Notes |
|---|---|---|
| **SC-001** Stars visible in the background — Milky Way band recognizable | ✓ code-complete | Filament `Skybox` cubemap loaded from 6 bundled PNG faces (real ESO Brunier panorama at 1024×1024 each, baked offline by `tools/bake-stars-cubemap/bake_eso.py`). Both renderer hosts attach/detach via `state.showStars`. Visual confirmation pending hardware. |
| **SC-002** Sun visibly moves when joystick / preset changes the sun direction | ✓ code-complete | Sun billboard at `state.sunDirection · 50`, billboard transform updated per frame from camera position. Preset animation (04-sun-control) and joystick drag drive `state.sunDirection`; the renderer reads it per frame. Visual confirmation pending hardware. |
| **SC-003** Sun blooms; Moon's reflective highlights do NOT bloom | ✓ code-complete (initial values) | Filament `BloomOptions` configured with `threshold = true` (highpass), `strength = 0.5`, `resolution = 360`. Sun emissive `intensity = 5` is well above the threshold; Moon's reflective max (~1.0) sits at the cusp. **T722 hardware-tunes** the values if either side leaks past the threshold; final tuned constants will land in a follow-up commit on this branch. |
| **SC-004** Stars and sun individually toggleable from `SettingsSheet` | ✓ code-complete | `SettingsSheet` rewritten to expose two `Switch` rows under a "Celestial background" section. `MoonViewModel.setShowStars` / `setShowSun` direct setters; renderer hosts read the flags per frame. Bloom config `enabled` flag tracks `state.showSun` so toggling the sun off saves the post-FX cost. Visual confirmation pending hardware. |
| **SC-005** 60 FPS sustained on Pixel 6 + iPhone 12 with stars + sun + bloom on | TBD | Hardware measurement needed. Bloom adds one post-FX pass; expected ≤ 2 ms on target hardware. The cubemap is a single full-screen draw (sub-ms). T722 captures the on-device numbers. |
| **SC-006** `:shared:allTests` passes | ✓ green | 103 tests across 11 suites green on `testAndroidHostTest` + `iosSimulatorArm64Test`. New since 04-sun-control's 99: `MoonViewModelTest` extensions (+4: `setShowStars` / `setShowSun` × default + togglesState). The bulk of the spec's work is per-platform Filament native code (Skybox setup, sun Renderable, bloom config) which has no commonTest coverage — gap noted, not a regression. |

## Tests

`./gradlew :shared:testAndroidHostTest :shared:iosSimulatorArm64Test` runs:

| Suite | Tests | Notes |
|---|---|---|
| `AssetCacheTest` | 6 | from 02-mvp |
| `AssetManifestTest` | 3 | from 02-mvp |
| `Sha256Test` | 3 | from 02-mvp |
| `MoonMathTest` | 23 | from spike + 03-flyto + 04-sun-control |
| `MoonViewModelTest` | **14** | 10 carryover + 4 new (T705 + T715): `setShowStars_defaultIsTrue` / `_togglesState`, `setShowSun_defaultIsTrue` / `_togglesState` |
| `MoonExplorerActionsImplTest` | 18 | from 04-sun-control |
| `ProjectionTest` | 11 | from 03-flyto |
| `SiteCatalogTest` | 11 | from 01-shell |
| `UvSphereTest` | 8 | from spike |
| `LightingPresetsTest` | 5 | from 04-sun-control |
| `SharedCommonTest` | 1 | placeholder |
| **Total** | **103** | All green on Android JVM + iOS simulator-arm64 |

## Deviations / deferrals

- **`SUN_DISTANCE` adjusted from plan's 1000 → 50.** Both renderers' `FAR_PLANE = 100`; sun at distance 1000 would clip. With camera `MAX_DIST = 20`, worst-case sun-to-camera distance is 70 — comfortably inside far plane. `SUN_SCALE = 2 · 50 · tan(0.0091/2) ≈ 0.455` units preserves the ~0.52° apparent angular diameter from the camera that the plan specified. Documented in `tasks.md` T714.
- **Cubemap bundle ~10.5 MB** vs the plan's ≤ 6 MB guideline. Six PNGs at 1024×1024: equatorial faces (px/nx/pz/nz) carry the Milky Way band at ~1.9–2.0 MB each; polar faces (py/ny) ~1.3–1.4 MB. **Accepted-for-quality**: 1024-per-face is the threshold below which the Milky Way band loses recognizability, and the celestial backdrop is the second-largest visual asset after the Moon textures. Total install bump is well within the 50 MB Play Store warning.
- **`compileMaterials` Gradle refactor** — restructured from a single hardcoded `Exec` task to a `registerMaterialCompile(name)` helper that registers one task per `.mat` source (`compileMoonMaterial` + `compileSunMaterial`); `compileMaterials` is now an umbrella aggregator. The downstream `tasks.matching` block lists per-material tasks directly because Gradle's strict-input-output detector wants explicit dependencies on the producer outputs.
- **Filament `setImage(Engine, Int, PixelBufferDescriptor, IntArray)` cubemap variant is deprecated.** Used in T703's Android cubemap upload. Compile emits a non-fatal deprecation warning. The modern API is a per-face overload — clean follow-up refactor if the deprecated form ever fully removes; behaviour is identical.
- **Settings persistence (DataStore-backed `showStars` / `showSun` survival across app restarts)** — out of scope for v1; flags reset to defaults each session. One-task follow-up if anyone asks.
- **T722 (bloom-threshold tuning) deferred** — hardware-bound; initial `SUN_EMISSIVE_INTENSITY = 5` + bloom `threshold = true` / `strength = 0.5` / `resolution = 360` should give clean separation between sun-bloom and Moon-non-bloom on Pixel 6 + iPhone 12. Final tuned values land in a follow-up commit on this branch with on-device measurements.

## Pending hardware measurements

The spec is code-complete; everything below needs an on-device session to confirm UX:

- **Milky Way band recognizability** — the equatorial faces should show the bright Galactic-Centre band cutting across the disc. Bundled bake at 1024 per face; verify on-device that the band reads as continuous (cubemap edges should be seam-correct from the equirect-to-cubemap projection in `bake_eso.py`).
- **Sun bloom vs Moon highlight separation** (T722) — confirm sun blooms cleanly across the joystick range + 4 lighting presets; confirm Moon's lit edge does NOT bloom at any orientation. Adjust `SUN_EMISSIVE_INTENSITY` (in `MoonHost.kt` companion + `MoonRenderer.mm`'s anonymous namespace) if either side leaks past the threshold.
- **Eclipse occlusion** — set the camera + sun so the Moon is between them. Moon should occlude the sun via depth-test; bloom may leak slightly around the Moon's silhouette depending on the kernel radius. Charming, not a bug. If the bleed is distracting, raise the threshold further.
- **Settings toggles update within one frame** — open About → Settings, toggle "Show stars" off; stars vanish on the next render. Same for "Show sun" + the bloom that follows it.
- **60 FPS sustained on Pixel 6 + iPhone 12 with stars + sun + bloom on** (SC-005) — Android Studio Profiler / Xcode Frame Capture. Bloom is the suspect for any drop on low-end hardware.
- **Joystick + preset interactions with the visible sun** — drag the joystick: the sun disc should slide across the starfield in real time, bloom following. Tap a preset: 500 ms cubic-eased glide. Mid-animation tap-redirect should hand off cleanly.
- **App backgrounded + foregrounded** — Skybox + sun Renderable should restore correctly after lifecycle pause/resume; confirm no Filament resource leak (Engine teardown order is exercised by the existing `dispose()` tests on iOS sim, but real lifecycle pressure is hardware-only).

## References

- [`spec.md`](spec.md) — user stories + acceptance criteria
- [`plan.md`](plan.md) — architecture flow + components
- [`tasks.md`](tasks.md) — task list (T701–T743)
- [`../../decisions/0001-filament-as-renderer.md`](../../decisions/0001-filament-as-renderer.md) — Filament as the renderer
- [`../../decisions/0003-renderer-host-pattern.md`](../../decisions/0003-renderer-host-pattern.md) — pull-not-push state delivery
- [`../../decisions/0004-asset-strategy.md`](../../decisions/0004-asset-strategy.md) — § "Attribution" amended (2026-04-30) with the ESO Brunier paragraph + ESO logo carve-out
- [`../../decisions/0006-selenographic-coordinate-convention.md`](../../decisions/0006-selenographic-coordinate-convention.md) — sun direction frame
- [`../../decisions/0008-filament-pod-via-raw-url.md`](../../decisions/0008-filament-pod-via-raw-url.md) — `Skybox` / `Material` / `BloomOptions` symbols come from this pod's subspecs
- [`../../decisions/0011-android-hd-ktx2-deferred.md`](../../decisions/0011-android-hd-ktx2-deferred.md) — PNG bundling rule applied to the cubemap
- [ESO press release 0932](https://www.eso.org/public/images/eso0932a/) — Milky Way Panorama by Serge Brunier (CC BY 4.0)
- [`../02-moon-renderer-mvp/results.md`](../02-moon-renderer-mvp/results.md) — predecessor renderer baseline (textures, FOV)
- [`../04-sun-control/results.md`](../04-sun-control/results.md) — `state.sunDirection` is the input the sun billboard reads each frame
- Hand-off branch: `main` (Phase Final at this commit).
