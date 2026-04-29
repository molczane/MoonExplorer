# Implementation Plan: 03 — Sites and Fly-to

**Branch:** `03-sites-and-flyto`
**Created:** 2026-04-29
**Status:** Draft (pending user ratification)

## Architecture flow

```
┌─────────────────────────────────────────────────────────────────────┐
│  UI (Compose, commonMain)                                           │
│  • MoonViewport renders the Moon (existing — 02-mvp)                │
│  • MarkerOverlay (NEW) — sits above MoonViewport in the same Box    │
│      reads state.cameraYaw/Pitch/Distance + state.highlightedSiteId │
│      projects each catalog site to screen px, culls far-side,       │
│      renders a Compose Surface per visible marker (with click)      │
│  • SearchBar / LocationInfoSheet / SettingsSheet (existing — 01)    │
└─────────────────────────────────────────────────────────────────────┘
                          │
                          │ Marker tap → onMarkerTap(id)
                          │ → infoSheetSite = catalog.byId(id)
                          ▼
┌─────────────────────────────────────────────────────────────────────┐
│  Actions (MoonExplorerActions — locked per ADR-0005)                │
│  • flyToMoonLocation(id, durationMs) — IMPLEMENTATION CHANGES:      │
│      durationMs == 0 → snap (existing path; preserves test compat)  │
│      durationMs > 0  → coroutine that interpolates yaw + pitch      │
│                        with shortestYawDelta + cubic ease-in-out,   │
│                        sampling every ~16 ms via delay()            │
│      Cancellation: the loop's delay() is cancellable; the screen's  │
│      caller cancels the prior Job before launching the next         │
└─────────────────────────────────────────────────────────────────────┘
                          │
                          │ viewModel.setCameraTarget(yaw, pitch)
                          ▼
┌─────────────────────────────────────────────────────────────────────┐
│  State (existing)                                                   │
│  • MoonViewModel.setCameraTarget — already in place from 01-shell   │
│  • Per-frame state pull by both renderers (Filament) AND the        │
│    MarkerOverlay (Compose recomposition on state change)            │
└─────────────────────────────────────────────────────────────────────┘
```

## Components

### `Projection.kt` (commonMain/domain/)

The world-to-screen pipeline as a pure function. No Compose, no Filament — testable in isolation.

```kotlin
data class ScreenPos(
    val xPx: Float,
    val yPx: Float,
    /** 0.0 (limb / culled-but-just-visible) → 1.0 (squarely facing the camera). */
    val limbAlpha: Float,
)

/**
 * Projects a selenographic (lat°, lon°) point on the unit Moon to screen pixels.
 * Returns null if the point is on the far side of the Moon (camera-facing dot product ≤ 0)
 * or behind the camera. The projection matches Filament's setProjection(FOV, aspect,
 * NEAR, FAR) — see `selenographic-math-camera.md` §5 + §7.
 */
fun projectSiteToScreen(
    latDeg: Double,
    lonDeg: Double,
    cameraYawRad: Float,
    cameraPitchRad: Float,
    cameraDistance: Float,
    viewportWidthPx: Float,
    viewportHeightPx: Float,
    fovYRad: Float = DEFAULT_FOV_Y_RAD,
): ScreenPos?
```

Math:

1. **Site cartesian** — reuse `latLonToCartesian`. For unit Moon: `site = (cos(lat)·sin(lon), sin(lat), cos(lat)·cos(lon))`.
2. **Camera position** — reuse `cameraPosition(yaw, pitch, distance)`. Camera direction from origin = `cam.normalize()` = `(cos(p)·sin(y), sin(p), cos(p)·cos(y))`.
3. **Far-side cull** — site is on the camera-facing hemisphere iff `dot(site, camDir) > 0`. Limb fade: `limbAlpha = smoothstep(0.0, 0.3, dot)`.
4. **Camera frame** — forward (toward origin) = `−cam.normalized`; up = `cameraUpVector(pitch)` (handles polar singularity); right = `forward × up` then re-orthogonalised.
5. **Camera-space coords** — decompose `(site − cam)` into `(right, up, forward)` basis: `(xCam, yCam, depthCam)`. depth > 0 means in front of camera.
6. **Perspective divide** — `xNdc = xCam / (depth · tan(fovY/2) · aspect); yNdc = yCam / (depth · tan(fovY/2))`.
7. **NDC → screen px** — `xPx = (xNdc + 1) · 0.5 · viewportW; yPx = (1 − yNdc) · 0.5 · viewportH` (Y flipped).

Aspect = `viewportW / viewportH`. The renderer's near/far planes don't matter for marker projection — markers are points, not depth-tested.

### `MoonMath` additions

Two short pure functions:

```kotlin
/** Wraps to − from into (−π, π] so animations take the shorter angular path. */
fun shortestYawDelta(fromRad: Float, toRad: Float): Float

/** Cubic ease-in-out, t ∈ [0, 1] → eased ∈ [0, 1]. Matches Material's FastOutSlowIn shape. */
fun easeInOutCubic(t: Float): Float
```

### `MoonExplorerActionsImpl.flyToMoonLocation` rework

```kotlin
override suspend fun flyToMoonLocation(id: String, durationMs: Long): ActionAck = mutex.withLock {
    val site = catalog.byId(id)
        ?: return@withLock ActionAck(ok = false, message = "no such site: $id")
    val (targetYaw, targetPitch) = latLonToYawPitch(site.lat, site.lon)

    if (durationMs <= 0L) {
        viewModel.setCameraTarget(targetYaw, targetPitch)
        return@withLock ActionAck(ok = true, message = "centered on ${site.name}")
    }

    val start = viewModel.state.value
    val startYaw = start.cameraYawRad
    val startPitch = start.cameraPitchRad
    val yawDelta = shortestYawDelta(startYaw, targetYaw)
    val pitchDelta = targetPitch - startPitch

    val source = TimeSource.Monotonic.markNow()
    while (true) {
        val elapsed = source.elapsedNow().inWholeMilliseconds
        val t = (elapsed.toFloat() / durationMs).coerceIn(0f, 1f)
        val eased = easeInOutCubic(t)
        viewModel.setCameraTarget(startYaw + yawDelta * eased, startPitch + pitchDelta * eased)
        if (t >= 1f) break
        delay(FRAME_MS)  // ~16 ms; cancellable
    }
    ActionAck(ok = true, message = "centered on ${site.name}")
}

private const val FRAME_MS: Long = 16L
```

Cancellation: `delay()` is cancellable. If the caller's `Job` is cancelled mid-animation, `delay` throws `CancellationException`, the loop unwinds, `withLock` releases, and the new `flyToMoonLocation` proceeds from wherever the camera ended up — which yields the "fluid hand-off" feel from US2.

### `MarkerOverlay.kt` (commonMain/ui/)

```kotlin
@Composable
fun MarkerOverlay(
    sites: List<MoonSite>,
    cameraYawRad: Float,
    cameraPitchRad: Float,
    cameraDistance: Float,
    highlightedSiteId: String?,
    onMarkerTap: (siteId: String) -> Unit,
    modifier: Modifier = Modifier,
)
```

Layout: a `Box(modifier.fillMaxSize())`. Captures viewport size via `onSizeChanged`. Each frame (recomposition) projects every site. Visible markers render as `Surface(shape = CircleShape)` with `Modifier.offset { IntOffset(xPx, yPx) }` and `Modifier.size(if (highlighted) 24.dp else 14.dp)`. Tap via `Modifier.clickable { onMarkerTap(site.id) }`.

The viewport-size capture inside the same composable means the overlay is self-contained — `MoonExplorerScreen` just drops it in alongside the existing viewport.

Recomposition cost: 16 markers × ~10 trig ops × per-frame state read. Wrapped in `derivedStateOf` so we only re-project when the camera state actually changes (the StateFlow already de-duplicates equal values). Recomposition cost dominated by the 16 `Surface` placements; well under 16 ms even on slow phones.

### `MoonExplorerScreen` rewire

Two changes:

1. Add `MarkerOverlay` to the existing `Box` after `MoonViewport` and before the search bar / icon buttons. Tap detection lives on the overlay's child Surfaces — they sit above the gesture detector but each one only consumes taps within its tiny radius, so pan/pinch on the rest of the viewport falls through.

2. Track the in-flight fly-to job and cancel it on new fly-to:

```kotlin
var currentFlyJob: Job? by remember { mutableStateOf(null) }
// in onCenterClick:
actions?.let { a ->
    currentFlyJob?.cancel()
    currentFlyJob = scope.launch { a.flyToMoonLocation(site.id) }
}
```

Same pattern for marker taps that snap-to (or for highlight-then-fly).

## Data models

No new data classes beyond `ScreenPos` (above). `MoonSite`, `SiteCatalog`, `MoonRenderState`, `MoonExplorerActions` all stay as-is.

## Project structure delta

```
shared/src/commonMain/kotlin/org/jetbrains/moonexplorer/
├── domain/
│   ├── Projection.kt                   (NEW — projectSiteToScreen, ScreenPos)
│   └── MoonMath.kt                     (+ shortestYawDelta, easeInOutCubic)
├── actions/
│   └── MoonExplorerActionsImpl.kt      (flyToMoonLocation rework — animated)
└── ui/
    ├── MarkerOverlay.kt                (NEW)
    └── MoonExplorerScreen.kt           (drop in MarkerOverlay; track currentFlyJob)

shared/src/commonTest/kotlin/org/jetbrains/moonexplorer/
├── domain/
│   ├── ProjectionTest.kt               (NEW)
│   └── MoonMathTest.kt                 (+ shortestYawDelta + easeInOutCubic cases)
└── actions/
    └── MoonExplorerActionsImplTest.kt  (+ animated fly-to progression test)
```

No platform-specific files. No new Gradle deps. No changes to `androidApp/`, `iosApp/`, native build infra.

## Error handling

| Scenario | Handling |
|---|---|
| Viewport size = 0 (pre-`onSizeChanged`) | `MarkerOverlay` skips projection, renders nothing |
| Site projects to NDC outside [−1, 1] (off-screen but on near-side) | Compose's offset clips naturally; no error needed |
| `flyToMoonLocation` cancelled mid-animation | `delay` throws CancellationException; the `withLock` releases; the new fly-to acquires and animates from the current state. Mutex is fair — FIFO order |
| `flyToMoonLocation(id, durationMs = 0)` | Snap path (preserves `MoonExplorerActionsImplTest.flyToMoonLocation_advancesCameraToSiteCoords`'s expectations) |
| Marker tap when same site is already highlighted | Open the info sheet anyway — taps on markers are always meaningful |

## Testing strategy

### `commonTest`

- **`ProjectionTest`** (T302):
  - Site at (lat=0, lon=0), camera at (yaw=0, pitch=0, distance=2): projects to viewport centre.
  - Site at (lat=0, lon=180°): far-side, returns null.
  - Site at (lat=0, lon=90° east), camera straight ahead: at the limb (dot ≤ 0), returns null or near-zero alpha.
  - Site at (lat=0, lon=45° east): projects to the right of centre with limbAlpha ≈ 1 (squarely on the lit hemisphere).
  - Site at (lat=45° north, lon=0°): projects above centre.
  - Aspect ratio: changing viewport from 1:1 to 16:9 narrows the horizontal span correspondingly.

- **`MoonMathTest`** (extensions, T321):
  - `shortestYawDelta(0, 0)` = 0
  - `shortestYawDelta(0, π/2)` = π/2 (no wrap needed)
  - `shortestYawDelta(170°, −170°)` = +20° (wraps; takes shorter path)
  - `shortestYawDelta(−170°, 170°)` = −20°
  - `easeInOutCubic(0)` = 0; `easeInOutCubic(1)` = 1; `easeInOutCubic(0.5)` = 0.5; `easeInOutCubic(0.25)` < 0.25 (acceleration).

- **`MoonExplorerActionsImplTest` extensions** (T322):
  - `flyToMoonLocation("tycho", durationMs = 0)` — snap path still works (existing case; reaffirm).
  - `flyToMoonLocation("tycho", durationMs = 50)` — animated: collect the StateFlow with `state.take(N).toList()`, assert intermediate values progress monotonically toward the target.
  - Cancellation: launch a fly-to with `durationMs = 1000` in a job, cancel it after 100 ms, verify the state is between start and target (not at target).

### Out of scope for `03-sites-and-flyto` tests

- UI screenshot tests of the marker overlay — same Compose-UI-test gap noted in 01 + 02 specs.
- Frame-rate measurement — hardware concern, captured in `results.md`.

## Complexity tracking

| Decision | Why this complexity is in scope |
|---|---|
| 2D Compose overlay (not 3D Filament markers) | Cross-platform free, no per-platform native work, plenty fast for 16 markers. 3D billboards can be a future polish spec. |
| Per-frame projection in commonMain | The math is ~10 µs / marker; recomposition is the actual cost ceiling and Compose's `derivedStateOf` keeps that under control. |
| Animated fly-to in `flyToMoonLocation` (not in a separate animator) | The existing Mutex protects the animation against concurrent state mutation; nothing extra to build. Cancellation comes for free via cancellable `delay`. |
| `currentFlyJob` tracked at the call site (not inside the impl) | The impl shouldn't know about UI lifetime. The screen owns the coroutine scope, so it owns the job. Smaller blast radius. |

## Risks / open questions

1. **Marker tap detection vs gesture detector ordering.** Compose pointer dispatch goes outer→inner, so marker `clickable` receives taps before the parent's `pointerInput { detectTransformGestures }`. A click on a marker shouldn't *also* register as a pan. Verify on hardware; if it does, add `consume = true` semantics. Low risk.
2. **Recomposition rate of `MarkerOverlay`.** Reading `state.cameraYawRad` directly causes recomposition on every gesture frame (~60 Hz). 16-marker projection plus 16 `Surface` placements per frame. Should be fine on modern phones; if not, throttle via `produceState` + a fixed-frame ticker.
3. **Animation while Filament is also re-rendering**: not actually a concern — they read the same `MoonRenderState` snapshot, so they stay in sync. The marker overlay will look "behind" only by one frame in the worst case, which is below perceptual threshold.
4. **Aspect ratio drift between Filament's projection and the overlay's**. Both use `DEFAULT_FOV_Y_RAD` and the actual viewport pixel dimensions; the only place they could disagree is if Filament's near-plane clip happens before Compose has the size. Edge case — handled by the "viewport size = 0 → skip" rule.
5. **Two markers tapped at once** (rare, but two finger taps): Compose dispatches both as separate clickable events; whichever fires first wins. Acceptable.

## References

- ADR-0003 (Renderer host pattern — pull-not-push)
- ADR-0005 (`MoonExplorerActions` shape)
- ADR-0006 (Selenographic coordinate convention)
- `ai-docs/research/selenographic-math-camera.md` §1 (lat/lon ↔ cartesian), §5 (camera animation), §7 (limb culling)
- `ai-docs/specs/01-app-shell/plan.md` — `MoonExplorerActionsImpl` shape; `MoonExplorerScreen` wiring
- `ai-docs/specs/02-moon-renderer-mvp/plan.md` — renderer FOV / projection conventions
- `./spec.md` — acceptance criteria
- `./tasks.md` — execution plan
