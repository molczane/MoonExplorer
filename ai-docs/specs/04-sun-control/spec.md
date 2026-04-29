# Feature Specification: 04 — Sun Control

**Branch:** `04-sun-control`
**Created:** 2026-04-29
**Status:** Draft (pending user ratification)

## Goal (1-line)

Replace `01-app-shell`'s 1-axis placeholder slider with a real Sun control surface — a 2D circular joystick for live drag plus four preset buttons (Full / Half / Apollo / New) with ~0.5 s animated transitions — and graduate `MoonExplorerActions.setLightingPreset` from its 01-shell `ok = false` deferred stub to a real animated implementation, completing the lighting commands the future Koog agent will need to call.

## User Scenarios

### User Story 1 — Drag the sun around live with a 2D joystick (Priority: P1)

**Why this priority:** Today's slider is 1-D — sun.x only, y locked to 0 — so the user can't lift the sun above or below the equator. To position the sun anywhere on the camera-facing hemisphere (and feel the terminator sweep across the disk in two axes) we need 2D. This unlocks the "tactile lunar globe" feel for *lighting* the same way 02-mvp's pinch + drag unlocked it for *orientation*.

**Independent test:** Launch the app. Drag the sun-joystick knob in a small circle; the lit hemisphere on the Moon visibly tilts and rotates, no perceptible lag, knob stays under the finger.

**Acceptance Scenarios:**
- WHEN the user drags the knob to position `(x, y)` inside the unit disk THEN the system SHALL set `MoonRenderState.sunDirection = joystickToSunDir(x, y)`, a unit vector on the camera-facing hemisphere per `selenographic-math-camera.md` §6 mode (a).
- WHEN the user drags the knob outside the unit disk (`x² + y² > 1`) THEN the knob SHALL clamp to the disk boundary; `z = 0`; the sun grazes the limb (terminator-on-meridian).
- WHEN the user lifts the finger THEN the sun SHALL stay at the last-set direction (no spring-back).
- WHEN the sun direction changes via any other path (preset tap, `MoonExplorerActions.setSunDirection`, future Koog tool) THEN the knob position SHALL reflect the new direction within one frame — the knob is a function of `state.sunDirection`, not local UI state.

### User Story 2 — Tap a preset to jump to a canonical lighting mood (Priority: P1)

**Why this priority:** Presets are how a first-time user discovers what lunar lighting can look like. Without them, the joystick is "you have to know what you're aiming at" — Full / Half / Apollo / New give one-tap access to four meaningful Moon looks that map 1:1 to ADR-0005's locked `LightingPreset` enum.

**Independent test:** Tap "Full" — the entire near-side disk is lit, no terminator visible. Tap "Half" — terminator runs vertically, half lit / half dark. Tap "Apollo" — long shadows from craters at the eastern third. Tap "New" — near-side dark; only the silhouette is visible.

**Acceptance Scenarios:**
- WHEN the user taps a preset button THEN the system SHALL invoke `MoonExplorerActions.setLightingPreset(preset)` with `preset` mapped per the table in `plan.md` § "Lighting preset table".
- The 4 buttons are labelled "Full" / "Half" / "Apollo" / "New" and map to `LightingPreset.Day` / `Terminator` / `HighContrast` / `Night` respectively. The enum stays at the 4 values ADR-0005 locked.
- WHEN the preset is applied THEN the `sunDirection` Vec3 SHALL match the spec'd sub-solar (lat°, lon°) per the table — Full=(0°,0°), Half=(0°,+90°), Apollo=(0°,+60°), New=(0°,+180°).

### User Story 3 — Preset transitions are animated, not snap (Priority: P1)

**Why this priority:** Snapping the sun-direction across a 90°+ angular jump produces a jarring lighting flash — the visible disk's terminator vanishes and reappears one frame later. A 500 ms cubic-eased transition makes lighting changes feel like a smooth phase change instead of a teleport, matching the 1.5 s animated fly-to from 03 in spirit. This is the closing animation that makes the preset row *feel like* a phase selector.

**Independent test:** From the Full preset, tap "Apollo". The sun visibly slides across ~500 ms; the terminator rotates onto the +60° meridian; long shadows grow. From "Apollo", tap "Full" mid-animation — the prior animation cancels cleanly, a new ~500 ms animation starts from the current intermediate state, no snap.

**Acceptance Scenarios:**
- WHEN `setLightingPreset(preset, durationMs > 0)` is invoked THEN the implementation SHALL interpolate `sunDirection` from the current state to the preset target over `durationMs` ms with cubic ease-in-out, sampling every ~16 ms via cancellable `kotlinx.coroutines.delay`.
- WHEN `setLightingPreset(preset, durationMs == 0)` is invoked THEN the system SHALL snap `sunDirection` to the target in a single state update (preserves a snap escape hatch for tests + future use cases).
- WHEN the start and target sun directions span a longitudinal delta crossing the ±π wrap THEN the interpolation SHALL take the shorter arc (re-using `MoonMath.shortestYawDelta` from 03-sites-and-flyto).
- WHEN a `setLightingPreset` is in flight AND the user taps a different preset THEN the in-flight coroutine SHALL be cancellation-safe (uses cancellable `delay`); the screen-level caller is responsible for cancelling the prior `Job` before launching the next, mirroring 03's `currentFlyJob` pattern.
- Default duration when `setLightingPreset` is called without an explicit `durationMs` is **500 ms**.

### User Story 4 — `setLightingPreset` action wired end-to-end (Priority: P2)

**Why this priority:** Phase 3 of the project plans a Koog AI guide. ADR-0005 locked `setLightingPreset` as part of the agent's tool surface, but `01-app-shell` shipped it as `ActionAck(ok = false, message = "lighting preset … deferred to a future spec")`. Until that stub is replaced, the agent has no way to drive lighting from natural-language prompts ("make it look like Apollo 11 morning"). Resolving the stub here closes the last 01-shell-deferred action.

**Independent test:** Call `actions.setLightingPreset(LightingPreset.HighContrast)` from a unit test. Assert the returned `ActionAck.ok == true` and that `viewModel.state.value.sunDirection` matches the Apollo preset Vec3 within 1e-4.

**Acceptance Scenarios:**
- WHEN any caller (UI button, future Koog tool, test) invokes `MoonExplorerActions.setLightingPreset(preset)` THEN the implementation SHALL animate the sun to the preset target and return `ActionAck(ok = true, message = "<human-readable description>")`. No more `ok = false` deferred stub.
- WHEN concurrent `setLightingPreset` calls arrive (e.g., a Phase-3 `toParallelToolCallsRaw` dispatch) THEN the existing `Mutex` in `MoonExplorerActionsImpl` SHALL serialize them; only one animation runs at a time. Cancelling one and starting the next is the responsibility of the caller, not the impl.

### Edge Cases

- **Joystick drag at exactly the disk boundary** (`x² + y² == 1`): `z = sqrt(0) = 0`; sun is on the limb. No singularity, but the lit hemisphere is reduced to a hairline. Acceptable.
- **Joystick clamped outside the disk**: identical to disk-boundary case (`z = 0`). The clamp happens on the input `(x, y)` before hand-off to `joystickToSunDir`.
- **Joystick gesture vs viewport pan-pinch**: the joystick claims pointer events within its 120-dp circular pad; events outside the pad fall through to the viewport's `pointerInput { detectTransformGestures }`. Compose pointer dispatch handles this for free as long as the joystick's `pointerInput` is on a child Box of the panel.
- **Tapping the same preset twice**: second tap re-runs the animation from the current state (which is the preset target ± a hair). Transition is essentially imperceptible. Acceptable; no special-case.
- **Animation while the user is dragging the joystick**: gestures continue to mutate `sunDirection` directly. The animation's per-frame `setSunDirection` overwrites whatever the joystick produced. "Sun fights you" — same trade-off as 03's "camera fights you", documented as accepted-for-v1; cancel-on-gesture is a polish task.
- **Day → Night transition** (sub-solar (0°, 0°) → (0°, +180°)): lon delta of `+π` in `shortestYawDelta` (since `-π` is excluded from the wrap range); animation passes through `(0°, +90°)` at `t = 0.5` — i.e., the Half preset's sub-solar. Visually the sun does a 180° equatorial sweep through the east limb.
- **Night → Day transition**: lon delta is also `+π` (forward direction; `-π` excluded), so the animation passes through `(0°, +270°)` = `(0°, -90°)` — i.e., the *opposite* limb from Day → Night. Two opposing limbs handle the two reversals. Acceptable; both look like a 180° great-circle sweep.
- **Preset target equals current state** (within float epsilon): animation runs but completes in one frame at `t = 1`. Acceptable; the loop exits via the `t >= 1` break.
- **`setSunDirection(lat, lon)` called concurrently with a `setLightingPreset` animation**: the impl's `Mutex` serializes; whichever lands second waits for the first to complete. If the future Koog agent calls both in parallel, deterministic ordering by Mutex acquisition.

## Requirements

### Functional Requirements

- **FR-001**: `SunPanel` (NEW Compose composable) SHALL replace the current `SunControl` at the BottomCenter alignment of `MoonExplorerScreen`. It SHALL contain a 2D `SunJoystick` and a `LightingPresetRow`.
- **FR-002**: `SunJoystick` SHALL render a circular pad (~120-dp diameter) with a draggable knob. The knob's render position SHALL be a function of `state.sunDirection` projected to `(x, y)`: `knobX = state.sunDirection.x; knobY = state.sunDirection.y` (camera-space approximation; see Assumptions for the off-axis-camera caveat).
- **FR-003**: WHEN the user drags the knob to `(x, y)` THEN the system SHALL clamp `(x, y)` to the unit disk and call `viewModel.setSunDirection(joystickToSunDir(x, y))` directly — bypassing `MoonExplorerActions` because the gesture is continuous, mirroring 03's `onDrag`/`onPinch` pattern.
- **FR-004**: `joystickToSunDir(x, y)` SHALL return a unit-length `Vec3` per `selenographic-math-camera.md` §6 mode (a): `Vec3(x, y, sqrt(max(0, 1 - x² - y²)))`. Outside the disk (`x² + y² > 1`), `z = 0` and the result is clamped to the disk edge.
- **FR-005**: `LightingPresetRow` SHALL render 4 buttons labelled "Full", "Half", "Apollo", "New" mapped to `LightingPreset.Day`, `Terminator`, `HighContrast`, `Night` respectively. Tapping a button SHALL invoke `MoonExplorerActions.setLightingPreset(preset)` (default 500 ms animation).
- **FR-006**: WHEN `setLightingPreset(preset, durationMs > 0)` is invoked THEN the implementation SHALL animate from `state.value.sunDirection` to the preset target Vec3 over `durationMs` ms via lat/lon lerp with cubic ease-in-out, sampling every ~16 ms via cancellable `delay`.
- **FR-007**: `setLightingPreset(preset, durationMs == 0)` SHALL snap to the target in a single state update — preserves a test escape hatch and gives external callers an immediate-positioning option.
- **FR-008**: Lon interpolation inside the animation SHALL use `MoonMath.shortestYawDelta` (added in 03-sites-and-flyto T320) so transitions take the shorter arc.
- **FR-009**: WHEN `setLightingPreset` is in flight AND the screen launches a new `setLightingPreset` THEN the in-flight loop's `delay` SHALL throw `CancellationException` cleanly when the caller cancels its `Job`; `Mutex.withLock` SHALL release; the new call SHALL acquire and animate from the current (interrupted) state. The screen tracks a `currentLightingJob: Job?` and cancels it before launching the next, mirroring `03-sites-and-flyto`'s `currentFlyJob` pattern.
- **FR-010**: ADR-0005 amendment — `setLightingPreset` gains a `durationMs: Long = 500` default-arg in the interface signature. Non-breaking for existing callers; enables tests to pass `durationMs = 0` (snap) and `durationMs = 50` (fast animated). The same amendment was applied to `flyToMoonLocation` already (ADR-0005 ships with `flyToMoonLocation(id, durationMs: Long = 1500)` already), so the precedent is set.

### Key Entities

- **`SunPanel`** — Compose composable in `commonMain/ui/SunPanel.kt`. Replaces `SunControl`. Composed of `SunJoystick` + `LightingPresetRow`. Reads `state.sunDirection`; emits `(x, y) -> Unit` for drag and `(LightingPreset) -> Unit` for taps.
- **`SunJoystick`** — circular pad subcomposable. Captures pointer input via `pointerInput { detectDragGestures }`; clamps to unit disk; emits `(x, y)`.
- **`LightingPresetRow`** — row of 4 buttons; each emits its `LightingPreset` value via `onPresetTap`.
- **`LightingPresets.kt`** (commonMain/domain/) — pure constants. `lightingPresetSunDir(preset: LightingPreset): Vec3` returns the spec'd unit vector for each enum value.
- **`joystickToSunDir(x: Float, y: Float): Vec3`** — pure function in `commonMain/domain/MoonMath.kt`. Replaces the 1-axis `joystickToHemisphereDir(x)` helper currently in `ui/SunControl.kt`.
- **`lerpSunDirection(from: Vec3, to: Vec3, t: Float): Vec3`** — pure function in `commonMain/domain/MoonMath.kt`. Lat/lon lerp using `shortestYawDelta` for lon wrap; converts back to cartesian via `latLonToCartesian`. Reused by the animated `setLightingPreset` impl.

## Non-Functional Requirements

- **Performance**: 60 FPS sustained during preset animation. Per frame: 1 cartesian → lat/lon decomposition (`asin`, `atan2`), 1 lat lerp, 1 lon lerp, 1 lat/lon → cartesian, 1 `viewModel.setSunDirection`. ~6 trig ops total — well below the 16 ms budget.
- **Joystick latency**: ≤ 1 frame from finger move to renderer update. The state hop is `pointerInput → viewModel.setSunDirection → state.value → next frame`. No animation, no buffering, no Mutex (continuous gestures bypass `MoonExplorerActions`).
- **Animation feel**: cubic ease-in-out matches Material's `FastOutSlowIn` curve (same easing as 03's fly-to). 500 ms default keeps the transition snappy without feeling hurried.
- **Cross-platform**: pure Compose UI + commonMain math. No per-platform native work.
- **Layered architecture**: continuous joystick gestures bypass `MoonExplorerActions` and call `viewModel.setSunDirection` directly. Discrete preset taps go through `MoonExplorerActions.setLightingPreset`. Mirrors `03-sites-and-flyto`'s "gestures direct, commands routed" separation.
- **Testability**: `joystickToSunDir`, `lerpSunDirection`, `lightingPresetSunDir` are pure functions with deterministic inputs/outputs. The animated `setLightingPreset` is testable via `runTest` + `delay` virtual time, identical to `flyToMoonLocation` tests from 03.

## Success Criteria

- **SC-001**: `SunPanel` (joystick + 4 preset buttons) replaces `SunControl` at BottomCenter on launch. The 1-axis slider is gone.
- **SC-002**: Dragging the joystick knob produces visible terminator movement on the Moon — the lit hemisphere tilts/rotates in real time without lag.
- **SC-003**: Tapping each of the 4 presets produces a visually distinct lit Moon — Full (no terminator), Half (vertical terminator), Apollo (long crater shadows), New (dark near-side).
- **SC-004**: Tapping a preset animates the sun over ~500 ms — visibly smooth, not snap.
- **SC-005**: Tapping a different preset mid-animation interrupts cleanly; the new animation starts from the current intermediate state, no jarring jump.
- **SC-006**: 60 FPS sustained on Pixel 6 + iPhone 12 during preset transitions (carryover hardware target from `02-moon-renderer-mvp`).
- **SC-007**: `:shared:allTests` passes including new tests for `joystickToSunDir`, `lerpSunDirection`, `lightingPresetSunDir` constants table, and animated `setLightingPreset` (snap path + animated path + cancellation + concurrent serialization).

## Assumptions & Out of Scope

**Out of scope:**
- **Scientific mode** — entering selenographic sun lat/lon directly via numeric input. Defer to a future spec; the AI guide can drive `setSunDirection(lat, lon)` from natural language without needing a numeric-input UI.
- **Light intensity slider** — Filament's directional light intensity stays at the spike's calibrated value. Defer.
- **Time-of-day animation / phase-by-date selector** — a "show me the Moon as it would look on 2026-07-04" feature would need an ephemeris library and date-picker. Out of scope per Constitution V (tactile, not scientific).
- **Sub-solar latitude variation** — all 4 v1 presets sit on the equator (lat = 0°). The Moon's axial tilt vs ecliptic is ~1.54°, imperceptible at our visual scale. Defer.
- **Reset button** — explicit "back to Full" button. The user can tap "Full" instead. Skip.
- **Preset highlight when joystick is near a preset** — visual indicator that the current sun direction is "close to" a preset. Polish; defer.
- **Slerp instead of lat/lon lerp** — the preset table sits on the equator (lat = 0°), so lat/lon lerp on the equator is equivalent to slerp on the equatorial great circle. For joystick → preset transitions where the start has non-zero lat, lat/lon lerp differs from slerp slightly but stays smooth and singularity-free except at the exact poles (which the joystick's clamping never produces). Slerp is a polish replacement if the visual difference matters; for v1, lat/lon lerp is correct enough.
- **Camera-space joystick mapping** — the joystick maps `(x, y)` to world-space `(sunX, sunY)` directly. When the camera rotates, the joystick's "right" stops meaning "world +X" and the user has to re-learn the mapping. v1 documents this trade-off; full camera-space (multiply by `inverse(viewMatrix)` upper-3×3) is a polish task.

**Assumptions:**
- ADR-0006 selenographic convention is the source of truth for sun direction lat/lon → cartesian.
- ADR-0005's `LightingPreset` enum stays at 4 values (Day, Night, Terminator, HighContrast). UI surfaces all 4 with the labels Full / New / Half / Apollo respectively.
- ADR-0005 amends `setLightingPreset` to take an optional `durationMs: Long = 500`. Non-breaking default arg; matches the precedent set by `flyToMoonLocation(id, durationMs = 1500)`. The amendment is recorded in `plan.md` § "ADR-0005 amendment" and re-applied to the locked interface code.
- `setSunDirection(lat, lon)` stays snap-only (no animation). Animation is opt-in via `setLightingPreset`. This preserves `MoonExplorerActionsImplTest.setSunDirection_unitVector`'s snap-path expectations from 01-shell.
- `SunPanel` lives at BottomCenter (where `SunControl` lives today). The About / Search / Settings stack stays untouched; `MoonExplorerScreen.kt`'s only change is `SunControl(...) → SunPanel(...)` plus a new `currentLightingJob` job tracker.
- Joystick disk diameter 120 dp; preset buttons fit beside or below the joystick within ~160 dp of vertical space. Layout details belong to `plan.md`.

## References

- ADR-0005 (`MoonExplorerActions` shape — `setLightingPreset` + `setSunDirection`; amended by FR-010)
- ADR-0006 (Selenographic coordinate convention)
- `ai-docs/research/selenographic-math-camera.md` §6 (sun direction modes (a) joystick + (b) selenographic)
- `ai-docs/initial-idea.md` "Sun placement tool" section — the source of v1's joystick + presets scope
- `ai-docs/specs/01-app-shell/spec.md` — `MoonExplorerActions` consumer; the deferred `setLightingPreset` stub originates here
- `ai-docs/specs/01-app-shell/results.md` — § Deviations / deferrals records the stub's deferral; this spec resolves it
- `ai-docs/specs/03-sites-and-flyto/spec.md` — animation pattern (cubic ease-in-out + cancellable mid-animation) reused here
- `ai-docs/specs/03-sites-and-flyto/plan.md` — § "MoonExplorerActionsImpl.flyToMoonLocation rework" — the template for the animated `setLightingPreset`
- `./plan.md`
- `./tasks.md`
