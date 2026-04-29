# 01-app-shell — Results

T231 record of what shipped, what's user-confirmed, and what's still pending hardware measurement. The spec is feature-complete on `main`; visual UX confirmation on real devices is the only outstanding work.

## Status by phase

| Phase | Code complete | User-verified | Commit |
|---|---|---|---|
| Phase 1 — site catalog data | ✓ | n/a (no UI yet) | `b92d35d` |
| Phase 2 — actions surface (ADR-0005) | ✓ | n/a | `528b63c` |
| Phase 3 — UI shell (search + info sheet + settings) | ✓ | (pending hardware) | `fb72bc7` |
| Phase Final — tests + docs | ✓ | n/a | (this commit) |

## Acceptance criteria

| | Status | Notes |
|---|---|---|
| **SC-001** Search "tycho" finds Tycho | ✓ code + unit-test | `MoonExplorerActionsImplTest.searchMoonLocations_findsBySubstring`. Visual confirmation pending hardware. |
| **SC-002** Tap result → info sheet | ✓ code-complete | `MoonExplorerScreen` `onResultTap` sets `infoSheetSite`; `LocationInfoSheet` opens. Visual UX pending hardware. |
| **SC-003** "Center on this site" snaps camera | ✓ code + unit-test | `MoonExplorerActionsImplTest.flyToMoonLocation_advancesCameraToSiteCoords` confirms `cameraYawRad/Pitch` advance to the expected values within 1e-4 rad. Visual confirmation pending hardware. |
| **SC-004** Settings opens placeholder | ✓ code-complete | About → Settings → SettingsSheet sequential flow wired in `MoonExplorerScreen`. Visual UX pending hardware. |
| **SC-005** `MoonExplorerActions` matches ADR-0005 | ✓ verified | All eight methods present with the locked signatures. The only deviation is `MoonSite`'s shape (subtitle/type/description vs ADR-0005's `tags: List<String>`) — documented in `plan.md` § "MoonSite" and called out in `MoonSite.kt`'s class doc. |
| **SC-006** `:shared:allTests` passes | ✓ green | `testAndroidHostTest` + `iosSimulatorArm64Test` — 56 tests across 9 suites, including 11 new SiteCatalogTest cases + 9 new MoonExplorerActionsImplTest cases. |

## Tests

`./gradlew :shared:testAndroidHostTest :shared:iosSimulatorArm64Test` runs:

| Suite | Tests | Notes |
|---|---|---|
| `AssetCacheTest` | 6 | from 02-mvp |
| `AssetManifestTest` | 3 | from 02-mvp |
| `Sha256Test` | 3 | from 02-mvp |
| `MoonMathTest` | 5 | from spike — `latLonToYawPitch` / `yawPitchToLatLon` / `greatCircleDistKm` add no new test cases here (they're exercised through `MoonExplorerActionsImplTest` instead — integration > pure-function tests for trig that's a literal degree-to-radian rescale) |
| `MoonViewModelTest` | 10 | from spike + `setCameraTarget` use is implicit through the actions impl |
| `SiteCatalogTest` | **11** | T204 — name / subtitle search, case-insensitivity, empty + whitespace, sort order, limit incl. zero/negative, byId hit + miss, all-returns-bundled-order |
| `MoonExplorerActionsImplTest` | **9** | T212 — search, flyTo (success + unknown id), `getCurrentView` round-trip, `setSunDirection` unit-vector, `highlightLocation` toggle, deferred `setLightingPreset` stub, `compareLocations` distance plausibility, concurrent `flyTo` Mutex serialization |
| `UvSphereTest` | 8 | from spike |
| `SharedCommonTest` | 1 | placeholder |
| **Total** | **56** | All green on Android JVM + iOS simulator-arm64 |

## Deviations / deferrals

- **`MoonSite` shape** extended from ADR-0005's `tags: List<String>` to `subtitle? + type + description` for the bundled catalog. Rationale and forward-compat notes in `plan.md` § "MoonSite". Future Koog tools (Phase 3) read whichever fields they need via `Json { ignoreUnknownKeys = true }`.
- **`SiteCatalog.loadBundled()` unit test** deferred — same Compose-Resources-in-commonTest gap that deferred `MoonAssetLoaderTest` (T143) in `02-moon-renderer-mvp`. The bundled JSON is exercised end-to-end at app startup; the runtime parse failing would be a hard crash the user would notice immediately.
- **Animated fly-to** stays out of scope — `flyToMoonLocation` snaps. `03-sites-and-flyto` adds the lerp. The `durationMs` parameter is parsed but unused.
- **`setLightingPreset`** returns `ActionAck(ok = false, …)`. Real lighting presets are a future-spec polish task.
- **`compareLocations`** computes geodesic distance only; richer comparison notes deferred.

## Pending hardware measurements

The spec is code-complete; everything below needs an on-device session to confirm UX:

- **SearchBar** — collapsed icon hit-target clear of the iOS notch / Android status bar (no overlap with the existing About icon at TopStart). `statusBarsPadding()` is in place but eyes-on-glass is the test.
- **Soft keyboard** rises when the search field gets focus, doesn't shove the dropdown off-screen on a small device.
- **Result tap → info sheet** transition feels instant.
- **"Center on this site"** snap is visible but not jarring; the moon visibly re-orients.
- **About → Settings sequential** flow (close-About + open-Settings) doesn't flicker.
- **Sheet drag-down dismiss** + **scrim-tap dismiss** both work for `LocationInfoSheet` and `SettingsSheet` on iOS specifically (matching `AboutSheet`'s already-confirmed behaviour from 02-mvp).
- **Search performance** — the `LaunchedEffect(searchQuery, actions)` cancels + restarts on every keystroke. The `MoonExplorerActions.searchMoonLocations` call is sub-millisecond against 16 entries; no jank expected, but worth confirming with a fast typist.

## References

- [`spec.md`](spec.md) — user stories + acceptance criteria
- [`plan.md`](plan.md) — architecture flow + components
- [`tasks.md`](tasks.md) — task list (T201–T232)
- [`../../decisions/0005-koog-adoption-timing.md`](../../decisions/0005-koog-adoption-timing.md) — `MoonExplorerActions` shape lock
- [`../../decisions/0006-selenographic-coordinate-convention.md`](../../decisions/0006-selenographic-coordinate-convention.md) — lat/lon → camera math
- [`../02-moon-renderer-mvp/results.md`](../02-moon-renderer-mvp/results.md) — predecessor results (textures + HD streaming)
- Hand-off branch: `main` (Phase Final at this commit).
