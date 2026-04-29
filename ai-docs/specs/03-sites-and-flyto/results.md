# 03-sites-and-flyto — Results

T341 record of what shipped, what's user-confirmed, and what's still pending hardware measurement. The spec is feature-complete on `main`; on-device UX confirmation is the only outstanding work.

## Status by phase

| Phase | Code complete | User-verified | Commit |
|---|---|---|---|
| Phase 1 — projection math | ✓ | n/a (pure functions) | `f9c68d0` |
| Phase 2 — marker overlay | ✓ | (pending hardware) | `6a6b75a` |
| Phase 3 — animated fly-to | ✓ | (pending hardware) | `7c90c39` |
| Phase 4 — interaction polish | ✓ delivered in Phase 2 | (pending hardware) | `6a6b75a` |
| Phase Final — tests + docs | ✓ | n/a | (this commit) |

## Acceptance criteria

| | Status | Notes |
|---|---|---|
| **SC-001** Markers visible on the near-side hemisphere | ✓ code-complete | `MarkerOverlay` projects via `projectSiteToScreen` (T301) and renders one Compose `Box` per visible site. 11 `ProjectionTest` cases verify the math. Visual confirmation pending hardware. |
| **SC-002** Animated fly-to over ~1.5 s | ✓ code + unit-test | `MoonExplorerActionsImplTest.flyToMoonLocation_animated_*` cases (T322) verify the `TimeSource.Monotonic` + `delay(16)` + `easeInOutCubic` loop reaches the target exactly and progresses monotonically through the eased curve. Visual smoothness pending hardware. |
| **SC-003** Tap a marker → info sheet | ✓ code-complete | `MarkerOverlay.onMarkerTap` → `MoonExplorerScreen.infoSheetSite = catalog.byId(id)` (T311). Same flow as a search-result tap; reuses `LocationInfoSheet`. Tap-vs-pan disambiguation on hardware is the open question. |
| **SC-004** Highlighted marker has distinct visual | ✓ code-complete | `MarkerDot` in T310 picks 22-dp + `MaterialTheme.colorScheme.primary` fill when `site.id == highlightedSiteId`, vs 14-dp + white for default. Visual confirmation pending hardware (or a debug menu calling `actions.highlightLocation(...)`). |
| **SC-005** 60 FPS sustained on Pixel 6 + iPhone 12 during fly-to | TBD | Hardware measurement needed. The 16-marker projection runs in commonMain at < 100 µs total per frame, well below the budget; Compose recomposition cost is the actual ceiling. |
| **SC-006** `:shared:allTests` passes | ✓ green | 79 tests across 10 suites green on `testAndroidHostTest` + `iosSimulatorArm64Test`. New since 01-shell: `ProjectionTest` (11), `MoonMathTest` extensions (+8), `MoonExplorerActionsImplTest` extensions (+4 animation cases). |

## Tests

`./gradlew :shared:testAndroidHostTest :shared:iosSimulatorArm64Test` runs:

| Suite | Tests | Notes |
|---|---|---|
| `AssetCacheTest` | 6 | from 02-mvp |
| `AssetManifestTest` | 3 | from 02-mvp |
| `Sha256Test` | 3 | from 02-mvp |
| `MoonMathTest` | **13** | 5 carryover + 8 new (T320): zero / quarter-turn / wraparound short-path / +2π modular / ease endpoints / accel-decel / S-curve symmetry / out-of-range clamping |
| `MoonViewModelTest` | 10 | from spike |
| `MoonExplorerActionsImplTest` | **13** | 9 carryover + 4 new (T322): `animated_reachesTargetExactly` / `animated_progressesMonotonically` / `cancelMidAnimation_leavesPartialState` / `yawWrap_takesShortPath` |
| `ProjectionTest` | **11** | T302 — centre / far-side / limb / off-axis east+west / north+south / aspect / limb-alpha-monotonic / zero-viewport / camera-aimed-at-site |
| `SiteCatalogTest` | 11 | from 01-shell |
| `UvSphereTest` | 8 | from spike |
| `SharedCommonTest` | 1 | placeholder |
| **Total** | **79** | All green on Android JVM + iOS simulator-arm64 |

## Deviations / deferrals (per spec.md § Out of Scope)

- **Custom user-pinned markers** (long-press to drop a pin) — deferred to a future spec.
- **Marker labels with collision avoidance** — info sheet carries the name; uniform dots only on the overlay.
- **3D billboards via Filament** — 2D Compose overlay was deliberately picked for cross-platform-free simplicity. A future spec can add depth-sorted Filament `Renderable` markers if shadows / Z-sorting matter.
- **Cancel fly-to on gesture** — animation continues if the user starts dragging mid-fly. Documented as accepted-for-v1; one-line wire-up if hardware UX shows it's jarring (`currentFlyJob?.cancel()` inside the `pointerInput` callback).
- **Marker icons / emoji per site type** — uniform dot for v1.
- **Drag-from-marker pan-through** — `Modifier.clickable` may claim the initial press on a marker; if hardware UX shows starting-drag-on-marker fails to pan, swap to `pointerInput { detectTapGestures }`. Filed in spec.md § Edge Cases as accepted-for-v1.

## Pending hardware measurements

The spec is code-complete; everything below needs an on-device session to confirm UX:

- **Markers anchor correctly during pan/pinch** — drag the camera around; markers should stay glued to their lat/lon points without lag or jitter.
- **Far-side markers cull cleanly** — rotate to the back of the Moon; markers near the limb fade out smoothly (no popping).
- **Tap-vs-pan disambiguation** — tapping a marker fires its `onTap` (info sheet opens); dragging from a marker… probably gets caught by `clickable` and won't pan. Confirm + decide if this needs `detectTapGestures` swap.
- **Tap-vs-pan on bare overlay area** — Compose pointer dispatch should send these to `MoonViewport`'s `pointerInput { detectTransformGestures }`. Confirm.
- **Animated fly-to feels right** — 1.5 s default duration with `easeInOutCubic` should feel like Material's standard motion. Hardware test for jank.
- **Interruption hand-off** — tap a different "Center on this site" mid-animation. The `currentFlyJob?.cancel()` should make the new fly start from wherever the camera is now, not from the prior animation's start.
- **Highlighted marker** — call `actions.highlightLocation("tycho")` from a debug menu (or wire a temporary toggle); confirm the larger primary-coloured dot is visibly distinct.
- **Marker visibility under different camera distances** — at MIN_DIST = 1.5 the Moon almost fills the view; at MAX_DIST = 20 it's a small dot. Markers should remain visible and tappable across the range.
- **60 FPS sustained** during fly-to (SC-005) — Android Studio Profiler / Xcode Frame Capture.

## References

- [`spec.md`](spec.md) — user stories + acceptance criteria
- [`plan.md`](plan.md) — architecture flow + components
- [`tasks.md`](tasks.md) — task list (T301–T342)
- [`../../decisions/0003-renderer-host-pattern.md`](../../decisions/0003-renderer-host-pattern.md) — pull-not-push state
- [`../../decisions/0005-koog-adoption-timing.md`](../../decisions/0005-koog-adoption-timing.md) — `MoonExplorerActions` shape (animated `flyToMoonLocation` is the locked signature implemented here)
- [`../../decisions/0006-selenographic-coordinate-convention.md`](../../decisions/0006-selenographic-coordinate-convention.md) — lat/lon → camera math
- [`../../research/selenographic-math-camera.md`](../../research/selenographic-math-camera.md) §1, §5, §7 — projection + animation + culling math
- [`../01-app-shell/results.md`](../01-app-shell/results.md) — predecessor results (search + info sheet + actions surface; snap-to fly-to placeholder)
- [`../02-moon-renderer-mvp/results.md`](../02-moon-renderer-mvp/results.md) — renderer baseline (textures, FOV)
- [`../04-sun-control/results.md`](../04-sun-control/results.md) — successor spec — reuses 03's TimeSource + cancellable-delay + Mutex animation pattern for `setLightingPreset`
- Hand-off branch: `main` (Phase Final at this commit).
