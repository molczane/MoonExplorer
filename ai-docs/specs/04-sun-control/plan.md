# Implementation Plan: 04 — Sun Control

**Branch:** `04-sun-control`
**Created:** 2026-04-29
**Status:** Draft (pending user ratification)

## Architecture flow

```
┌─────────────────────────────────────────────────────────────────────┐
│  UI (Compose, commonMain)                                           │
│  • MoonViewport renders the Moon (existing — 02-mvp)                │
│  • MarkerOverlay renders site markers (existing — 03-flyto)         │
│  • SunPanel (NEW) — replaces SunControl at BottomCenter             │
│      • SunJoystick: 120-dp circular pad, knob position derived      │
│        from state.sunDirection; drag → joystickToSunDir(x, y)       │
│        → viewModel.setSunDirection(unitVec)                         │
│      • LightingPresetRow: 4 buttons (Full / Half / Apollo / New)    │
│        → onPresetTap(preset) → currentLightingJob.cancel();         │
│        currentLightingJob = scope.launch {                          │
│            actions.setLightingPreset(preset)                        │
│        }                                                            │
│  • SearchBar / About / Settings (existing — 01-shell)               │
└─────────────────────────────────────────────────────────────────────┘
                          │
                ┌─────────┴───────────┐
                │ joystick drag       │ preset tap
                ▼                     ▼
┌───────────────────────┐   ┌─────────────────────────────────────────┐
│ MoonViewModel         │   │ MoonExplorerActions (locked, ADR-0005)  │
│ .setSunDirection(vec) │   │ .setLightingPreset(preset,              │
│   — direct, no Mutex  │   │     durationMs = 500): ActionAck        │
│   — gesture path      │   │                                         │
│                       │   │ IMPL CHANGES (was deferred stub):       │
│                       │   │  durationMs == 0 → snap path            │
│                       │   │  durationMs > 0  → coroutine that       │
│                       │   │    interpolates sunDirection via        │
│                       │   │    lerpSunDirection (lat/lon lerp +     │
│                       │   │    shortestYawDelta + easeInOutCubic),  │
│                       │   │    sampling every ~16 ms via            │
│                       │   │    cancellable delay()                  │
│                       │   │  Cancellation: loop's delay is          │
│                       │   │    cancellable; screen's caller         │
│                       │   │    cancels the prior Job before         │
│                       │   │    launching the next                   │
└───────────────────────┘   └─────────────────────────────────────────┘
                          │
                          │ viewModel.setSunDirection(currentVec)
                          ▼
┌─────────────────────────────────────────────────────────────────────┐
│  State (existing)                                                   │
│  • MoonViewModel.setSunDirection — already in place from spike      │
│  • MoonRenderState.sunDirection: Vec3 — read by Filament shader     │
│    every frame (ADR-0003 pull-not-push)                             │
└─────────────────────────────────────────────────────────────────────┘
```

The two side-effecting paths (joystick gesture; preset tap) are *deliberately* separate — gestures must not acquire the action mutex (would block on a 500 ms preset animation), and preset taps benefit from the mutex (serialise concurrent Phase-3 Koog tool dispatches). 03-sites-and-flyto established this pattern; 04 follows it.

## ADR-0005 amendment

ADR-0005 locks the interface; this spec amends one signature non-breakingly:

```kotlin
// Before (current commonMain code, matching ADR-0005 v1):
suspend fun setLightingPreset(preset: LightingPreset): ActionAck

// After (commonMain code; ADR-0005 amended):
suspend fun setLightingPreset(preset: LightingPreset, durationMs: Long = 500): ActionAck
```

The amendment matches the precedent set by `flyToMoonLocation(id: String, durationMs: Long = 1500)`, which already shipped with the duration parameter. Default arg keeps existing callers source-compatible. The interface change is bundled with the impl rework; ADR-0005 is updated in the same commit (or a follow-on amendment note inside ADR-0005 §"Action surface").

The ADR-0005 amendment is the *only* deviation from "as-locked". `MoonExplorerActions` as a whole stays Koog-agnostic; the Phase-3 Koog tool will pass `durationMs` like a normal Kotlin arg.

## Lighting preset table

All four presets sit on the equator (sub-solar lat = 0°). Sub-solar lon picks a distinct mood per `selenographic-math-camera.md` §6:

| `LightingPreset` | UI label | Sub-solar (lat°, lon°) | Vec3 (x, y, z) | Visual |
|---|---|---|---|---|
| `Day` | "Full" | (0°, 0°) | (0.000, 0.000, 1.000) | Full disk lit; no terminator visible from default camera. |
| `Terminator` | "Half" | (0°, +90°) | (1.000, 0.000, 0.000) | Vertical terminator on the +Z meridian; waxing-half-Moon look. |
| `HighContrast` | "Apollo" | (0°, +60°) | (0.866, 0.000, 0.500) | Long crater shadows from the upper-right; iconic Apollo morning lighting. |
| `Night` | "New" | (0°, +180°) | (0.000, 0.000, −1.000) | Near-side dark; only silhouette + ambient. New-Moon look. |

`Vec3` values come from `latLonToCartesian(latDeg, lonDeg)` evaluated for each preset and frozen as compile-time constants in `LightingPresets.kt`.

The 4 sub-solar lons are deliberately spaced (0°, 60°, 90°, 180°) so no two presets are antipodal in lon-space (the only true antipode pair is Day↔Night, which lat/lon lerp handles cleanly via the +90° intermediate). Tuning the exact lons is a QA polish task — the constants are easy to nudge.

## Components

### `domain/MoonMath.kt` additions

Two pure functions:

```kotlin
/**
 * 2D joystick → unit hemisphere (selenographic-math-camera.md §6 mode (a)).
 * (x, y) is clamped to the unit disk; z = sqrt(max(0, 1 − x² − y²)).
 * Replaces the 1-axis joystickToHemisphereDir(x) currently in ui/SunControl.kt.
 */
fun joystickToSunDir(x: Float, y: Float): Vec3

/**
 * Interpolates two unit-length sun directions on the lat/lon surface — lat lerps
 * linearly, lon takes the shorter arc via shortestYawDelta. Reconstructs the
 * unit vector via latLonToCartesian. t is clamped to [0, 1].
 */
fun lerpSunDirection(from: Vec3, to: Vec3, t: Float): Vec3
```

`shortestYawDelta` and `easeInOutCubic` already exist (from 03-sites-and-flyto T320). `lerpSunDirection` reuses both:

```kotlin
fun lerpSunDirection(from: Vec3, to: Vec3, t: Float): Vec3 {
    val tt = t.coerceIn(0f, 1f)
    val fromLat = asin(from.y.coerceIn(-1f, 1f))
    val toLat   = asin(to.y.coerceIn(-1f, 1f))
    val fromLon = atan2(from.x, from.z)
    val toLon   = atan2(to.x, to.z)
    val lonDelta = shortestYawDelta(fromLon, toLon)
    val newLat = fromLat + (toLat - fromLat) * tt
    val newLon = fromLon + lonDelta * tt
    val cl = cos(newLat)
    return Vec3(cl * sin(newLon), sin(newLat), cl * cos(newLon))
}
```

The animated `setLightingPreset` does its own `easeInOutCubic(t)` before passing to `lerpSunDirection` — `lerpSunDirection` itself is linear (so the impl can compose linear lerp with whichever easing it likes, or pass `easedT` directly).

### `domain/LightingPresets.kt` (NEW)

```kotlin
package org.jetbrains.moonexplorer.domain

import org.jetbrains.moonexplorer.actions.LightingPreset

/**
 * Sub-solar Vec3 for each LightingPreset, derived from the (lat, lon) table
 * in spec.md / plan.md. Frozen as compile-time constants — adjusting these is
 * a tuning task, not a structural change.
 */
fun lightingPresetSunDir(preset: LightingPreset): Vec3 = when (preset) {
    LightingPreset.Day          -> Vec3(0f, 0f, 1f)              // (0°,    0°)
    LightingPreset.Terminator   -> Vec3(1f, 0f, 0f)              // (0°,  +90°)
    LightingPreset.HighContrast -> Vec3(0.8660254f, 0f, 0.5f)    // (0°,  +60°)
    LightingPreset.Night        -> Vec3(0f, 0f, -1f)             // (0°, +180°)
}
```

UI labels live in the UI layer (`SunPanel.kt`), not the domain — keeps the domain free of localisation concerns.

### `MoonExplorerActionsImpl.setLightingPreset` rework

```kotlin
override suspend fun setLightingPreset(preset: LightingPreset, durationMs: Long): ActionAck = mutex.withLock {
    val target = lightingPresetSunDir(preset)

    if (durationMs <= 0L) {
        viewModel.setSunDirection(target)
        return@withLock ActionAck(ok = true, message = "lighting set to ${preset.name}")
    }

    val start = viewModel.state.value.sunDirection
    val source = TimeSource.Monotonic.markNow()
    while (true) {
        val elapsedMs = source.elapsedNow().inWholeMilliseconds
        val t = (elapsedMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        val eased = easeInOutCubic(t)
        viewModel.setSunDirection(lerpSunDirection(start, target, eased))
        if (t >= 1f) break
        delay(FRAME_MS)
    }
    ActionAck(ok = true, message = "lighting set to ${preset.name}")
}
```

Identical control-flow shape to `flyToMoonLocation` (T321) — only the per-frame mutator differs (`setSunDirection` instead of `setCameraTarget`). `FRAME_MS = 16L` already exists in the impl's companion object.

Cancellation: `delay()` is cancellable. If the screen's caller's `Job` is cancelled mid-animation, `delay` throws `CancellationException`, the loop unwinds, `withLock` releases, and the next `setLightingPreset` proceeds from wherever the sun ended up — yielding the "fluid hand-off" feel of US3.

### `ui/SunPanel.kt` (NEW — replaces `SunControl`)

```kotlin
@Composable
fun SunPanel(
    sunDirection: Vec3,
    onJoystickDrag: (x: Float, y: Float) -> Unit,
    onPresetTap: (LightingPreset) -> Unit,
    modifier: Modifier = Modifier,
)
```

Layout: Row at BottomCenter, ~140-dp tall.
- **Left**: `SunJoystick` — 120-dp circular pad. Knob position = `IntOffset((sunDirection.x * radius).toInt(), (-sunDirection.y * radius).toInt())` (Y-flip for screen coords). Drag via `Modifier.pointerInput { detectDragGestures }`; convert pixel offset to disk-relative `(x, y) ∈ [-1, 1]²`; clamp to disk; emit via `onJoystickDrag`.
- **Right**: `LightingPresetRow` — `Column` of 4 buttons stacked vertically (or `LazyRow` of 4 buttons horizontally below the joystick — final layout decided in T412). Each `Button { onPresetTap(preset) }` with the user-facing label.

The knob *render* position is a function of `sunDirection` (state-driven), so external sun-direction changes (preset tap, future Koog tool) move the knob automatically. The drag *consumes* pointer events — no recomposition while drag is in flight; on each `onDrag`, the state is updated via `onJoystickDrag` and the knob re-positions on the next frame.

Pointer-input scope: only inside the 120-dp disk. The rest of `SunPanel`'s footprint (the preset row) uses `Button { … }` which has its own ripple/click handling. Bare panel area falls through to the underlying `MoonViewport` gesture detector — same Compose pointer-dispatch rule the marker overlay relies on (03 T310).

### `ui/SunJoystick.kt` (NEW — extracted from `SunPanel` for testability)

```kotlin
@Composable
fun SunJoystick(
    sunDirection: Vec3,
    onDrag: (x: Float, y: Float) -> Unit,
    modifier: Modifier = Modifier,
)
```

Single-purpose composable. Renders the disk + knob; handles drag math. Lives in its own file so the gesture math is easy to find when iterating; `SunPanel` imports it.

### `ui/LightingPresetRow.kt` (NEW)

```kotlin
@Composable
fun LightingPresetRow(
    onPresetTap: (LightingPreset) -> Unit,
    modifier: Modifier = Modifier,
)
```

UI labels for the 4 enum values are stored as a private `val PRESET_LABELS: Map<LightingPreset, String>` inside this file:

```kotlin
private val PRESET_LABELS = mapOf(
    LightingPreset.Day          to "Full",
    LightingPreset.Terminator   to "Half",
    LightingPreset.HighContrast to "Apollo",
    LightingPreset.Night        to "New",
)
```

Future localisation puts this in a Compose-Resources `strings.xml`-equivalent.

### `MoonExplorerScreen` rewire

Three changes:

1. Delete the `SunControl(...)` call. Add a `SunPanel(...)` in the same BottomCenter slot.
2. Track an in-flight lighting job:

```kotlin
var currentLightingJob: Job? by remember { mutableStateOf<Job?>(null) }
```

3. Wire callbacks:

```kotlin
SunPanel(
    sunDirection = state.sunDirection,
    onJoystickDrag = { x, y -> viewModel.setSunDirection(joystickToSunDir(x, y)) },
    onPresetTap = { preset ->
        actions?.let { a ->
            currentLightingJob?.cancel()
            currentLightingJob = scope.launch { a.setLightingPreset(preset) }
        }
    },
    modifier = Modifier
        .align(Alignment.BottomCenter)
        .navigationBarsPadding()
        .padding(16.dp),
)
```

`currentFlyJob` (for `flyToMoonLocation`) and `currentLightingJob` (for `setLightingPreset`) are independent — flying to a site doesn't cancel a sun preset animation, and vice versa. Two parallel side-effect tracks; both serialise inside the action impl's Mutex.

Delete `joystickToHemisphereDir(x)` from `SunControl.kt` (or delete the whole file — superseded). Delete `SunControl.kt` once `SunPanel.kt` is wired in and tests are green.

## Data models

No new data classes. `Vec3`, `LightingPreset`, `MoonRenderState`, `MoonExplorerActions`, `ActionAck` all stay as-is. `LightingPresets.kt` is a constants table, not a data model.

## Project structure delta

```
shared/src/commonMain/kotlin/org/jetbrains/moonexplorer/
├── domain/
│   ├── LightingPresets.kt              (NEW — lightingPresetSunDir(preset))
│   └── MoonMath.kt                     (+ joystickToSunDir, lerpSunDirection)
├── actions/
│   └── MoonExplorerActions.kt          (durationMs default arg added to setLightingPreset)
│   └── MoonExplorerActionsImpl.kt      (setLightingPreset rework — animated)
├── ui/
│   ├── SunControl.kt                   (DELETED — superseded by SunPanel)
│   ├── SunPanel.kt                     (NEW — joystick + preset row)
│   ├── SunJoystick.kt                  (NEW — extracted from SunPanel)
│   ├── LightingPresetRow.kt            (NEW)
│   └── MoonExplorerScreen.kt           (SunControl → SunPanel; track currentLightingJob)

shared/src/commonTest/kotlin/org/jetbrains/moonexplorer/
├── domain/
│   ├── LightingPresetsTest.kt          (NEW — preset Vec3 sanity)
│   └── MoonMathTest.kt                 (+ joystickToSunDir + lerpSunDirection cases)
└── actions/
    └── MoonExplorerActionsImplTest.kt  (+ animated setLightingPreset cases;
                                          flip stub-test to real-impl test)
```

No platform-specific files. No new Gradle deps. No changes to `androidApp/`, `iosApp/`, native build infra.

## Error handling

| Scenario | Handling |
|---|---|
| Joystick drag outside the disk | Clamp `(x, y)` to the disk boundary before calling `joystickToSunDir`; result has `z = 0` (sun on the limb). |
| `lerpSunDirection` with `from ≈ to` (same preset re-tapped) | `shortestYawDelta` returns ~0; lat lerp is ~0; result ≈ `to` for any t. Loop completes in one frame at t = 1. |
| `lerpSunDirection` with from = +Y (lat = +90°) | Edge case: `atan2(0, 0)` returns 0 by convention; lon is undefined but lat = π/2 dominates. The lerp moves through valid intermediate states. The joystick can't produce lat = ±90° (would require `(x, y) = (0, ±1)` exactly, which is the disk boundary; clamped to z = 0 in practice means y = ±1 → lat = arcsin(1) = +90°, indeed possible). Acceptable; users near the pole rarely tap presets. |
| `setLightingPreset(preset, durationMs = 0)` | Snap path; preserves test escape hatch. |
| `setLightingPreset` cancelled mid-animation | `delay` throws `CancellationException`; `withLock` releases; the new `setLightingPreset` acquires and animates from the current sun direction. Mutex is FIFO. |
| Joystick gesture during a `setLightingPreset` animation | The animation's `viewModel.setSunDirection` overwrites the joystick's. Documented as accepted-for-v1; cancel-on-gesture is a polish task (mirrors 03's identical situation with fly-to). |
| `LightingPreset` enum value with no preset Vec3 (impossible at runtime, but the compiler can't prove it) | The `when` is exhaustive over the sealed enum; Kotlin enforces this. |

## Testing strategy

### `commonTest`

- **`MoonMathTest`** extensions (T411):
  - `joystickToSunDir(0, 0)` → `Vec3(0, 0, 1)`. Magnitude 1.
  - `joystickToSunDir(1, 0)` → `Vec3(1, 0, 0)` (disk boundary, east).
  - `joystickToSunDir(0, 1)` → `Vec3(0, 1, 0)` (disk boundary, north pole).
  - `joystickToSunDir(2, 0)` (outside disk) → `Vec3(1, 0, 0)` (clamped to boundary).
  - `joystickToSunDir(0.6, 0.8)` → magnitude 1, z = 0 (on the disk boundary, x² + y² = 1 exactly).
  - `joystickToSunDir(0.5, 0.5)` → `Vec3(0.5, 0.5, sqrt(0.5))`. Magnitude 1.
  - `lerpSunDirection(Day, Night, 0.5)` → `(1, 0, 0)` (the Half preset's vec; the path passes through Terminator).
  - `lerpSunDirection(Day, Night, 0.0)` → Day exactly.
  - `lerpSunDirection(Day, Night, 1.0)` → Night exactly.
  - `lerpSunDirection(Half, Apollo, 0.5)` → mid-arc; lat = 0; lon ≈ 75° → Vec3 ≈ `(sin75°, 0, cos75°)`.
  - `lerpSunDirection` magnitude ≈ 1 for any t (the cartesian reconstruction is unit-length by construction).

- **`LightingPresetsTest`** (T410):
  - `lightingPresetSunDir(Day)` → `Vec3(0, 0, 1)`. Magnitude 1.
  - `lightingPresetSunDir(Terminator)` → `Vec3(1, 0, 0)`. Magnitude 1.
  - `lightingPresetSunDir(HighContrast)` → `(0.866, 0, 0.5)`. Magnitude 1 within 1e-6.
  - `lightingPresetSunDir(Night)` → `Vec3(0, 0, -1)`. Magnitude 1.

- **`MoonExplorerActionsImplTest`** extensions (T431) — **and an existing test flips**:
  - `setLightingPreset_durationZero_snaps`: `setLightingPreset(Day, 0)` from current state → `state.sunDirection == Day vec` exactly. Replaces the prior `setLightingPreset_returnsDeferredAck` test (the stub is gone).
  - `setLightingPreset_animated_reachesTargetExactly`: `setLightingPreset(Apollo, 50)`; collect state; assert final `sunDirection == Apollo vec` within 1e-4.
  - `setLightingPreset_animated_progressesMonotonically`: `setLightingPreset(Half, 50)` from Day; assert intermediate `sunDirection` values strictly between Day and Half along the equatorial arc.
  - `setLightingPreset_cancelMidAnimation_leavesPartialState`: launch `setLightingPreset(Night, 1000)` in a job, cancel after 120 ms, assert `sunDirection` is between Day and Night (not at Night).
  - `setLightingPreset_lonWrap_takesShortPath`: setup state with `sunDirection` corresponding to lat = 0, lon = +170°; `setLightingPreset(Day, 50)` (target lon = 0°). The shortest arc is +190° via lon = +180° → -180° → 0° (i.e., back through the wrap). Assert mid-state lon is close to ±180°, not crossing through 0° via -170° → -90° → 0°.
    - Actually `shortestYawDelta(170°, 0°) = -170°` (within (-π, π]). Lerp goes 170° → 85° → 0°. So the path takes the *shorter* arc directly through 0°. The "wrap" check is really: if the user starts at lon = +170° and the target is at lon = -170°, shortestYawDelta returns +20° (not -340°). Test that case.
  - `setLightingPreset_returnsOk`: `ActionAck.ok == true`. Differs from the prior 01-shell test that asserted `ok == false` for the deferred stub.
  - `setLightingPreset_concurrent_serializes`: launch two `setLightingPreset` calls in parallel (durationMs = 30 each); assert final state is at the second target, not interleaved.

### Out of scope for `04-sun-control` tests

- UI screenshot tests of `SunPanel` — same Compose-UI-test gap noted in 01 + 02 + 03 specs.
- Frame-rate measurement during animation — hardware concern, captured in `results.md`.
- Joystick gesture-detection-vs-viewport-pan disambiguation — hardware concern, same as 03's marker tap-vs-pan question.

## Complexity tracking

| Decision | Why this complexity is in scope |
|---|---|
| Lat/lon lerp instead of slerp | Equatorial preset table makes lat/lon lerp == slerp on the equator; off-equator joystick → preset transitions are slightly different from slerp but visually indistinguishable for 500 ms. Avoids slerp's antipodal edge case (Day↔Night) entirely. |
| Animation always-on (default 500 ms; `durationMs = 0` for snap) | Mirrors 03's `flyToMoonLocation` precedent; the default is what users see, the override is what tests + power callers need. |
| Two parallel job trackers (`currentFlyJob` + `currentLightingJob`) at the screen level | Camera and sun are independent state. Both can animate concurrently (e.g., the user taps "Center on Tycho" + "Apollo lighting" at the same time). One job per side-effect track; cancel only the relevant prior animation. |
| `setSunDirection` stays snap-only; `setLightingPreset` is the animated path | Keeps the locked `setSunDirection` signature snap-compatible (preserves 01-shell's `setSunDirection_unitVector` test); animation is opt-in via the discrete preset path. |
| ADR-0005 amendment via default-arg addition (not a new method) | Default args are non-breaking in Kotlin; matches the precedent already set by `flyToMoonLocation(id, durationMs = 1500)`. Avoids interface-method proliferation. |
| Joystick in world-space coords (not camera-space) for v1 | Camera-space mapping requires multiplying by `inverse(viewMatrix)` upper-3×3, which means the joystick depends on `state.cameraYawRad`/`Pitch`. World-space is simpler: the joystick's "right" is always world-+X. Acceptable for v1; documented as a polish task. |

## Risks / open questions

1. **Joystick gesture vs `MoonViewport`'s `detectTransformGestures`.** Compose pointer dispatch sends events to the deepest hit target first. The joystick's `pointerInput { detectDragGestures }` should claim drags inside the disk; outside the disk (panel area without a child) the bare panel doesn't have a pointer modifier so events should fall through. This is the same dispatch pattern that 03's `MarkerOverlay` relies on — confirmed working there. Risk: low.
2. **Knob recompose rate during drag.** `SunPanel` reads `state.sunDirection` for the knob position; recomposing on every frame the user drags is fine (~60 Hz). The drag pipeline mutates state via `onJoystickDrag` → `viewModel.setSunDirection` → state flow updates → recompose. One state hop per pointer event; no animation, no frame loop.
3. **Lat/lon lerp through near-pole intermediates.** If the joystick puts the sun at `lat = 89°` and the user taps "Full" (lat = 0°), the lerp lat-axis is 89° → 0° linearly while lon does shortestYawDelta. Near `lat = ±90°`, lon is mathematically undefined but `atan2(x, z)` returns a deterministic value; the lerp produces a smooth path. No singularity in practice.
4. **Mid-animation `setSunDirection` from joystick wrestles the animation.** Documented as accepted; user can stop fighting by lifting the finger. Cancel-on-gesture wire-up is a 5-line change (`pointerInput` callback calls `currentLightingJob?.cancel()`); deferred to polish.
5. **Preset button labels in cramped layouts.** "Apollo" is 6 chars; "Half" is 4. Buttons sized for the longest label. Localisation may break the row layout; future polish.
6. **Concurrent fly-to + sun preset animation.** Both run; both serialize inside the impl's Mutex. If a fly-to is in flight and `setLightingPreset` is invoked, it waits ≤1.5 s before its own animation starts. Acceptable; the mutex is the contract from ADR-0005. If hardware UX shows the wait is jarring, splitting the side-effects into two mutexes (camera mutex + sun mutex) is an option — but the ADR-0005 model is "actions are sequential", so we'd need an ADR amendment first.

## References

- ADR-0005 (`MoonExplorerActions` shape — amended by FR-010 to add `durationMs` default arg to `setLightingPreset`)
- ADR-0006 (Selenographic coordinate convention — sun direction lat/lon → cartesian)
- `ai-docs/research/selenographic-math-camera.md` §6 — sun direction modes (a) joystick + (b) selenographic
- `ai-docs/initial-idea.md` "Sun placement tool" — v1 joystick + presets scope
- `ai-docs/specs/01-app-shell/plan.md` — `MoonExplorerActionsImpl` shape; `MoonExplorerScreen` wiring
- `ai-docs/specs/01-app-shell/results.md` — § Deviations / deferrals — the `setLightingPreset` stub originated here
- `ai-docs/specs/03-sites-and-flyto/plan.md` — § "MoonExplorerActionsImpl.flyToMoonLocation rework" — animation pattern reused
- `ai-docs/specs/03-sites-and-flyto/results.md` — animation pattern's test cases — model for 04's tests
- `./spec.md` — acceptance criteria
- `./tasks.md` — execution plan
