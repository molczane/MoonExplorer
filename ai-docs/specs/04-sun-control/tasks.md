# Tasks: 04 — Sun Control

## Format: `[ID] [P?] [US?] Description`

`[P]` = parallel-safe with sibling tasks. `[US#]` = which user story this serves.
Acceptance criteria for each user story live in `spec.md`.

## Path conventions

All paths relative to `MoonExplorer/` repo root. Task IDs are namespaced **T400+** to avoid collision with `00-renderer-spike` (T001–T093), `02-moon-renderer-mvp` (T100–T145), `01-app-shell` (T200–T232), and `03-sites-and-flyto` (T301–T342).

---

## Phase 1: Math + preset table (pure commonMain)

- [x] **T410** [P] [US2] Implement `domain/LightingPresets.kt` — exhaustive `when` over the 4 `LightingPreset` enum values returning the spec'd Vec3. Day = `(0,0,1)`, Terminator = `(1,0,0)`, HighContrast = `(0.8660254,0,0.5)`, Night = `(0,0,-1)`.
  - `fun lightingPresetSunDir(preset: LightingPreset): Vec3` — exhaustive `when` over the 4 enum values, returning the spec'd `Vec3` per `plan.md` § "Lighting preset table".
  - Compile-time constants: Day = `(0, 0, 1)`, Terminator = `(1, 0, 0)`, HighContrast = `(0.8660254f, 0, 0.5f)`, Night = `(0, 0, -1)`.
  - _Requirements: FR-005, US2_

- [x] **T411** [P] [US1, US3] Add `MoonMath.joystickToSunDir` + `MoonMath.lerpSunDirection` — joystick clamps outside-disk inputs onto the boundary with `z = 0`; lerp uses lat/lon decomposition via `asin(y) + atan2(x, z)` with `shortestYawDelta` (reuses 03 T320's primitive) and `latLonToCartesian` reconstruction.
  - `fun joystickToSunDir(x: Float, y: Float): Vec3` — clamps `(x, y)` to the unit disk; `z = sqrt(max(0, 1 - x² - y²))`. Replaces the 1-axis `joystickToHemisphereDir(x)` currently in `ui/SunControl.kt`.
  - `fun lerpSunDirection(from: Vec3, to: Vec3, t: Float): Vec3` — lat lerps linearly via `asin(y)`; lon takes `shortestYawDelta` (already in MoonMath from 03 T320); reconstructs unit Vec3 via `latLonToCartesian` math (`cos(lat)·sin(lon), sin(lat), cos(lat)·cos(lon)`). `t` clamped to `[0, 1]`.
  - _Requirements: FR-002, FR-004, FR-006, FR-008_

- [x] **T412** [P] [US1, US2, US3] `commonTest` — added `LightingPresetsTest` (5 cases) + extended `MoonMathTest` (+10 cases): joystick centre / east-boundary / north-boundary / outside-clamp / off-diagonal-boundary / interior-unit-length, plus lerp endpoints / Day→Night-through-Terminator / Half→Apollo-mid-arc-at-+75° / unit-length-across-t. **94 tests green** on Android JVM + iOS sim (was 79 after 03 Phase Final → +15).
  - `LightingPresetsTest` (4 cases): each preset's Vec3 magnitude == 1.0 within 1e-6; concrete value sanity-check.
  - `MoonMathTest` extensions (~9 cases per the plan):
    - `joystickToSunDir(0, 0)` → `(0, 0, 1)`; magnitude 1.
    - `joystickToSunDir(1, 0)` → `(1, 0, 0)` (disk boundary, east).
    - `joystickToSunDir(0, 1)` → `(0, 1, 0)` (disk boundary, north).
    - `joystickToSunDir(2, 0)` → `(1, 0, 0)` (clamped).
    - `joystickToSunDir(0.6, 0.8)` → magnitude 1, `z` ≈ 0.
    - `joystickToSunDir(0.5, 0.5)` → magnitude 1.
    - `lerpSunDirection(Day, Night, 0.5)` → `(1, 0, 0)` within 1e-4 (passes through Half).
    - `lerpSunDirection(Day, Night, 0.0)` and `(_, _, 1.0)` → endpoints exactly.
    - `lerpSunDirection(Half, Apollo, 0.5)` → mid-arc; lat ≈ 0; lon ≈ 75°.
    - Magnitude-of-output ≈ 1 across `t ∈ [0, 1]`.
  - _Requirements: SC-007_

**Checkpoint**: `:shared:testAndroidHostTest` includes a green `LightingPresetsTest` and the extended `MoonMathTest`.

---

## Phase 2: SunPanel UI

- [x] **T420** [US1] Implement `ui/SunJoystick.kt` — 120-dp circular pad with a 28-dp draggable knob; gesture loop uses `awaitEachGesture` + `awaitFirstDown` so the knob jumps to the first touch position immediately (no drag-threshold lag from `detectDragGestures`). Knob centre travels in a disk of radius `(diskR − knobR)` so the knob's *edge* meets the outer ring at `|sunDirection| = 1`. Y-flipped to translate Compose's screen-down Y to our world-up Y. Emits raw disk-relative `(x, y)`; the screen wraps with `joystickToSunDir` for clamping.
  - `@Composable fun SunJoystick(sunDirection: Vec3, onDrag: (Float, Float) -> Unit, modifier: Modifier = Modifier)`.
  - 120-dp `Box` with `Modifier.size(120.dp)`. Render the disk as a `Canvas` (concentric circle for boundary + filled circle for the knob at `state-derived` position).
  - Knob render position: `IntOffset((sunDirection.x * radiusPx).toInt(), (-sunDirection.y * radiusPx).toInt())` — Y-flipped because Compose Y grows downward.
  - Drag via `Modifier.pointerInput(Unit) { detectDragGestures(onDrag = { change, _ -> ... }) }`. Convert pointer position → disk-relative `(x, y) ∈ [-1, 1]²` (normalize by `radiusPx`); clamp to disk; emit via `onDrag`.
  - _Requirements: FR-001, FR-002, FR-003_

- [x] **T421** [US2] Implement `ui/LightingPresetRow.kt` — 2x2 grid of `FilledTonalButton`s (88×40 dp each) with labels "Full" / "Half" / "Apollo" / "New" mapped to `LightingPreset.Day / Terminator / HighContrast / Night`. Picked `FilledTonalButton` over `Button` for a softer dark-background-friendly look that matches the existing About / Settings sheet aesthetic.
  - `@Composable fun LightingPresetRow(onPresetTap: (LightingPreset) -> Unit, modifier: Modifier = Modifier)`.
  - 4 `Button`s in a `Column` (or `Row` — final orientation decided based on the layout in T422). Labels: "Full" / "Half" / "Apollo" / "New" → enum mapping per `plan.md` § "ui/LightingPresetRow.kt" `PRESET_LABELS`.
  - Each button: `Button(onClick = { onPresetTap(preset) }) { Text(label) }` with `Modifier.fillMaxWidth()` if vertical, `Modifier.padding(...)` for spacing.
  - _Requirements: FR-005_

- [x] **T422** [US1, US2] Implement `ui/SunPanel.kt` — composes `SunJoystick` + `LightingPresetRow` in a `Row` with `Arrangement.spacedBy(16.dp)`, vertically centred. Joystick on the left, 2x2 preset grid on the right. ~120 dp tall × ~300 dp wide; fits comfortably at BottomCenter without consuming the full screen width.
  - `@Composable fun SunPanel(sunDirection: Vec3, onJoystickDrag: (Float, Float) -> Unit, onPresetTap: (LightingPreset) -> Unit, modifier: Modifier = Modifier)`.
  - Layout: `Row` with `SunJoystick` on the left and `LightingPresetRow` on the right. Total height ~140 dp.
  - Pass `sunDirection`, `onJoystickDrag` through to `SunJoystick`. Pass `onPresetTap` through to `LightingPresetRow`.
  - _Requirements: FR-001_

**Checkpoint**: `SunPanel` previewable in isolation (Compose `@Preview` if available; or manual launch). Joystick reflects an arbitrary `sunDirection` input and emits drag callbacks. Presets fire taps. No state-flow integration yet.

---

## Phase 3: Animated `setLightingPreset`

- [x] **T430** [US3, US4] Amend ADR-0005 + interface signature — `setLightingPreset(preset: LightingPreset, durationMs: Long = 500): ActionAck`. ADR-0005 updated in place (interface code block + new "Amendments" section dated 2026-04-29). Interface kdoc updated to list the amendment alongside 03-flyto's animation graduation note.
  - Edit `actions/MoonExplorerActions.kt` to add the default arg. Non-breaking for existing callers.
  - Edit `ai-docs/decisions/0005-koog-adoption-timing.md` § "Action surface (commonMain, today)" code block — change the `setLightingPreset` line to match. Add a one-paragraph "Amendments" section noting the change with date 2026-04-29 and the spec link.
  - _Requirements: FR-010_

- [x] **T431** [US3, US4] Rework `MoonExplorerActionsImpl.setLightingPreset` — graduated from 01-shell's `ok = false` deferred stub to a real animated impl. Identical control-flow shape to `flyToMoonLocation` (T321): `mutex.withLock` + `TimeSource.Monotonic.markNow()` + cancellable `delay(FRAME_MS)` + `easeInOutCubic` + `lerpSunDirection`. Class kdoc rewritten to remove the deferred-methods note and document `setSunDirection` as snap-only.
  - `durationMs <= 0L` → snap path: `viewModel.setSunDirection(lightingPresetSunDir(preset))`; return `ActionAck(ok = true, message = "lighting set to ${preset.name}")`.
  - `durationMs > 0L` → animation loop, identical control-flow shape to `flyToMoonLocation` (T321):
    ```
    val target = lightingPresetSunDir(preset)
    val start = viewModel.state.value.sunDirection
    val source = TimeSource.Monotonic.markNow()
    while (true) {
        val t = (source.elapsedNow().inWholeMilliseconds.toFloat() / durationMs).coerceIn(0f, 1f)
        val eased = easeInOutCubic(t)
        viewModel.setSunDirection(lerpSunDirection(start, target, eased))
        if (t >= 1f) break
        delay(FRAME_MS)
    }
    ```
  - The existing `mutex.withLock` keeps two concurrent `setLightingPreset` calls serialized. The loop's `delay` is cancellable so the screen-level `currentLightingJob.cancel()` interrupts cleanly.
  - Update the class kdoc — remove `setLightingPreset` from the deferred-methods list; note that `setSunDirection(lat, lon)` stays snap-only.
  - _Requirements: FR-005, FR-006, FR-007, FR-008, FR-009_

- [x] **T432** [P] [US3, US4] Extend `MoonExplorerActionsImplTest` — removed `setLightingPreset_returnsDeferredStub` (01-shell's `ok = false` assertion); added 6 new cases: `setLightingPreset_returnsOk` / `_durationZero_snaps` / `_animated_reachesTargetExactly` / `_animated_progressesMonotonically` / `_cancelMidAnimation_leavesPartialState` / `_concurrentSerializesViaMutex`. Suite count: **99 green** on Android JVM + iOS sim (94 → +5 net).
  - Replace `setLightingPreset_returnsDeferredAck` (the `ok = false` stub test) with `setLightingPreset_returnsOk` (asserts `ok = true`, message contains preset name).
  - Add `setLightingPreset_durationZero_snaps`: `setLightingPreset(LightingPreset.Day, 0)` from a non-default initial state → `state.sunDirection == (0, 0, 1)` exactly.
  - Add `setLightingPreset_animated_reachesTargetExactly`: `setLightingPreset(LightingPreset.HighContrast, 50)`; await; assert `state.sunDirection ≈ (0.866, 0, 0.5)` within 1e-4.
  - Add `setLightingPreset_animated_progressesMonotonically`: from Day, `setLightingPreset(Terminator, 50)`; collect StateFlow; assert intermediate `sunDirection.x` strictly between 0 and 1, `sunDirection.z` strictly between 1 and 0, magnitude ≈ 1 throughout.
  - Add `setLightingPreset_cancelMidAnimation_leavesPartialState`: launch `setLightingPreset(Night, 1000)` in a job; cancel after 120 ms; assert `sunDirection.z` is between 1 and -1 exclusive (not at Night).
  - Add `setLightingPreset_concurrent_serializes`: launch two `setLightingPreset` calls in parallel (durationMs = 30 each); both targets different; assert final state equals the second target.
  - Existing `setSunDirection_unitVector` test stays as-is (snap-only path; ADR-0005 contract for that method is unchanged).
  - _Requirements: SC-007_

- [x] **T433** [US3, US4] Wire `currentLightingJob` in `MoonExplorerScreen` — declaration added alongside `currentFlyJob` with a `@Suppress("unused")` annotation; the read/write is wired by T440 (Phase 4 SunPanel swap-in). Splitting the declaration from the wire-up keeps Phase 3's commit code-complete (impl + tests + interface change) without dragging the SunControl→SunPanel swap forward.
  - `var currentLightingJob: Job? by remember { mutableStateOf<Job?>(null) }` alongside `currentFlyJob`.
  - In `onPresetTap`: `currentLightingJob?.cancel(); currentLightingJob = scope.launch { actions?.setLightingPreset(preset) }`.
  - Same pattern as `currentFlyJob` from T323; lives at screen level so the impl doesn't know about UI lifetime.
  - _Requirements: FR-009_

**Checkpoint**: tapping a preset smoothly animates the sun over ~500 ms. Tapping a different preset mid-animation visibly redirects without a snap. `MoonExplorerActionsImplTest` is fully green with the new animation cases.

---

## Phase 4: MoonExplorerScreen swap-in

- [ ] **T440** [US1, US2, US3, US4] Replace `SunControl` with `SunPanel` in `MoonExplorerScreen`
  - Delete the current `SunControl(value = state.sunDirection.x, onValueChange = { … }, …)` block at BottomCenter.
  - Replace with `SunPanel(sunDirection = state.sunDirection, onJoystickDrag = { x, y -> viewModel.setSunDirection(joystickToSunDir(x, y)) }, onPresetTap = { preset -> currentLightingJob?.cancel(); currentLightingJob = scope.launch { actions?.setLightingPreset(preset) } }, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp))`.
  - Confirm the screen still compiles + previews; sun direction visibly responds to both joystick drag and preset taps when launched.
  - _Requirements: FR-001, FR-003, FR-005, FR-009_

- [ ] **T441** Delete `ui/SunControl.kt`
  - The whole file is superseded. `joystickToHemisphereDir(x)` is replaced by `joystickToSunDir(x, y)` in MoonMath; `SunControl` is replaced by `SunPanel`. Removing the file in the same commit prevents stale imports.
  - Confirm no remaining imports of `SunControl` or `joystickToHemisphereDir` anywhere in the tree (`grep -r SunControl shared/ androidApp/ iosApp/`).
  - _Requirements: cleanliness; no FR_

**Checkpoint**: launching the app on simulator shows `SunPanel` at BottomCenter with the new joystick + presets; dragging moves the sun; tapping presets animates. No references to the deleted `SunControl` remain.

---

## Phase Final: Polish + tests + docs

- [ ] **T450** Run `:shared:testAndroidHostTest :shared:iosSimulatorArm64Test` — all suites green
  - All carryover tests + new `LightingPresetsTest` + extended `MoonMathTest` + extended `MoonExplorerActionsImplTest` pass on Android JVM + iOS simulator-arm64.
  - Expected counts: `LightingPresetsTest` (+4), `MoonMathTest` (+9), `MoonExplorerActionsImplTest` (+5 net: -1 stub, +6 animation cases). Roughly **+18 cases over 03-sites-and-flyto's 79**, target ~97 tests across 11 suites.
  - _Requirements: SC-007_

- [ ] **T451** [P] Write `ai-docs/specs/04-sun-control/results.md`
  - Status by phase; user-confirmed items vs pending hardware confirmation (60 FPS during animation; joystick gesture-vs-pan disambiguation; preset transition feel; mid-animation interruption snappiness).
  - Test counts table (mirrors 03's results.md format).
  - Deviations log: ADR-0005 amendment (durationMs default arg); world-space joystick (not camera-space) for v1; lat/lon lerp instead of slerp.
  - _Requirements: agent-runbook.md_

- [ ] **T452** [P] Cross-reference notes in 01 + 03 results
  - 01-shell `results.md` § Deviations / deferrals — strike through the `setLightingPreset` deferral note and point at `04-sun-control/results.md` (mirrors 03's pattern when it resolved 01's snap-to placeholder).
  - 03-flyto `results.md` § References — add `../04-sun-control/results.md` as the next-spec link.
  - _Requirements: agent-runbook.md_

**Final Checkpoint**: all four user stories' acceptance criteria pass on real devices; joystick drag is responsive; preset transitions feel smooth; mid-animation tap redirects cleanly; `results.md` filed; cross-refs updated; ADR-0005 amendment committed.

---

## Dependencies & Execution Order

| From → To | Why |
|---|---|
| T410 → T431 | Animated impl reads `lightingPresetSunDir` |
| T411 → T431 | Animated impl reads `lerpSunDirection` |
| T411 → T420 | Joystick gesture handler emits `(x, y)`; the screen wraps it with `joystickToSunDir`. (T420 doesn't depend on T411 directly — `joystickToSunDir` is called at the screen level — but T440's wiring depends on T411.) |
| T411 → T412 | Tests need the function to exist |
| T410 → T412 | Tests need the function to exist |
| T420 + T421 → T422 | `SunPanel` composes both subcomposables |
| T430 → T431 | Impl needs the new signature |
| T431 → T432 | Tests exercise the new animation path |
| T431 → T433 | Screen tracks the job that wraps the (now-suspending-with-delay) impl |
| T422 + T431 + T433 → T440 | Screen wiring needs SunPanel + animated impl + job tracker |
| T440 → T441 | Delete the old file only after the new one is wired in |
| Phases 1–4 → Phase Final | Everything wired before final tests |

## Parallel Examples

- **Phase 1**: T410, T411 are on different files (`LightingPresets.kt` vs `MoonMath.kt`); T412 lands tests for both. All three can be tackled in one focused session.
- **Phase 2 + Phase 3 in parallel**: Thread A = T420 → T421 → T422 (UI). Thread B = T430 → T431 → T432 → T433 (impl + tests + screen job track). Files don't overlap.
- **Phase Final**: T451 + T452 touch different `results.md` files; safe in parallel.

## Implementation Strategy

- **Two PRs**. Land Phase 1 + Phase 3 (math + preset table + animated impl) as a single PR titled `04-sun-control: lighting preset table + animated setLightingPreset`. Land Phase 2 + Phase 4 + Phase Final as a second PR titled `04-sun-control: SunPanel UI + screen wire-in + results`. Splitting math/animation away from UI mirrors 03's two-PR strategy and keeps review chunks small.
- Phase 3's T430 amends ADR-0005 — bundle that edit with the impl rework so the spec, plan, ADR, and code all advance together. The ADR-0005 amendment is a default-arg addition (non-breaking); existing tests in 01-shell and 03-flyto stay green.
- Phase 4's T441 (delete `SunControl.kt`) lands in the same commit as T440 (wire `SunPanel`). Avoids a momentary state where `SunControl` is unreferenced but still in the tree.

## Notes

- **No new Gradle deps.** `kotlin.time.TimeSource.Monotonic`, `kotlinx.coroutines.delay`, Compose `Canvas` / `pointerInput { detectDragGestures }` / `Button` are all on classpath from prior specs.
- **Joystick gesture math** lives in `SunJoystick.kt` — pixel offsets to disk-relative `(x, y)` coords. The pure helper `joystickToSunDir(x, y)` (in MoonMath) only does the `(x, y) → Vec3` mapping; the gesture-coord conversion is a UI concern.
- **Preset Vec3 values** are tuned by eye in QA — adjusting `(0°, +60°)` for HighContrast to e.g. `(+50°)` or `(+70°)` is a one-line edit in `LightingPresets.kt`. The animation, UI, and tests don't care about the exact numbers as long as they're unit-length.
- **Camera-space joystick mapping** is documented as deferred; a future polish spec (or a one-task addition) can wire `inverse(viewMatrix)` into `joystickToSunDir`. The tests stay valid because they exercise the pure function with explicit `(x, y)` inputs.
- **Animation easing alternatives** — if QA finds 500 ms cubic ease-in-out too slow / too fast, the constant lives in `LightingPresetRow.kt`'s callback (or could be a `companion object` constant in the impl). Tuning is one edit.
- **`setSunDirection(lat, lon)` snap-only** is a deliberate ADR-0005 contract (no default-arg amendment for that signature). If a future spec adds animation to `setSunDirection`, that's a separate ADR amendment with its own justification.
- **`MoonExplorerActions` deferred-stub backlog from 01-shell** is closed when this spec lands. With `setLightingPreset` graduated, the only `ok = false`-returning action is gone; `compareLocations`'s "richer comparison deferred" note remains as a non-blocking polish opportunity.
