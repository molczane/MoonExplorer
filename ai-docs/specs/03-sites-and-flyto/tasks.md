# Tasks: 03 — Sites and Fly-to

## Format: `[ID] [P?] [US?] Description`

`[P]` = parallel-safe with sibling tasks. `[US#]` = which user story this serves.
Acceptance criteria for each user story live in `spec.md`.

## Path conventions

All paths relative to `MoonExplorer/` repo root. Task IDs are namespaced **T300+** to avoid collision with `00-renderer-spike` (T001–T093), `02-moon-renderer-mvp` (T100–T145), and `01-app-shell` (T200–T232).

---

## Phase 1: Projection math (pure commonMain functions)

- [x] **T301** [P] [US1] Implement `domain/Projection.kt`
  - `data class ScreenPos(xPx, yPx, limbAlpha: Float)`.
  - `fun projectSiteToScreen(lat, lon, yaw, pitch, distance, viewportW, viewportH, fovY): ScreenPos?`.
  - Math per `plan.md` § "Projection.kt": `latLonToCartesian` → camera-facing cull (dot product) → camera-space basis (forward / up / right) → perspective divide → NDC → screen px.
  - Limb fade via `smoothstep(0.0, 0.3, dot)`.
  - _Requirements: FR-001, FR-002, FR-003_

- [x] **T302** [P] [US1] `commonTest`: `ProjectionTest` — 11 cases: centre / far-side cull / limb cull / off-axis east + west / northern + southern / aspect-narrows-horizontal / limb-alpha-monotonic / zero-viewport-returns-null / camera-aimed-at-site-centres-it.
  - Centre (lat=0, lon=0, camera yaw=0/pitch=0/distance=2) → `(viewportW/2, viewportH/2)`.
  - Far side (lon=180°) → null.
  - Limb (lon=90° east, camera straight) → null (dot = 0 cull).
  - Off-axis (lon=45° east) → x > centre, limbAlpha ≈ 1.
  - Northern (lat=45°) → y < centre.
  - Aspect change (viewport 1:1 vs 16:9) narrows horizontal span as expected.
  - Pole (lat=85°) projects above centre with limbAlpha ≈ 1 when camera looks straight at it.
  - _Requirements: SC-006_

**Checkpoint**: `:shared:testAndroidHostTest` includes a passing `ProjectionTest`.

---

## Phase 2: Marker overlay (UI)

- [x] **T310** [US1, US3, US4] Implement `ui/MarkerOverlay.kt` in commonMain — Box overlay captures viewport via `onSizeChanged`; per-site `projectSiteToScreen` filters far-side markers; visible markers render as 14-dp white dots (22 dp accent fill when highlighted) with a 48-dp invisible tap target around each, alpha = limbAlpha. Bare overlay area has no pointer modifier so taps fall through to the underlying viewport gesture detector.
  - `@Composable fun MarkerOverlay(sites, cameraYawRad, cameraPitchRad, cameraDistance, highlightedSiteId, onMarkerTap, modifier)`.
  - Captures viewport size via `Modifier.onSizeChanged`. While viewport is 0×0, render nothing.
  - For each site: call `projectSiteToScreen`. Visible (non-null) markers render as `Surface(shape = CircleShape)` with `Modifier.offset { IntOffset(xPx.toInt(), yPx.toInt()) }` and `Modifier.size(if (highlighted) 24.dp else 14.dp)`. Highlighted variant uses an accent fill.
  - `Modifier.clickable { onMarkerTap(site.id) }` on each Surface — taps on markers fire onMarkerTap; taps on bare overlay area fall through to the underlying gesture detector (Compose pointer dispatch handles the ordering for free).
  - `derivedStateOf` wrap around the projection computation so we don't recompose every keystroke.
  - _Requirements: FR-001, FR-002, FR-007, FR-008_

- [x] **T311** [US1] Wire `MarkerOverlay` into `MoonExplorerScreen` — hoisted `catalog: SiteCatalog?` alongside `actions` so the overlay reads `catalog.all` directly without round-tripping through MoonExplorerActions. Overlay sits in the Box between `MoonViewport` and `SunControl`; `onMarkerTap` sets `infoSheetSite = catalog.byId(id)`. Conditional render via `catalog?.let { ... }` so the overlay is invisible until the JSON parse completes.
  - Drop in the overlay between `MoonViewport` and `SunControl` in the existing `Box`. Pass `state.cameraYaw/Pitch/Distance`, `state.highlightedSiteId`, and a `onMarkerTap` callback that sets `infoSheetSite = catalog.byId(id)`.
  - Catalog source: re-use the `actions` instance's catalog. Stash the catalog at `MoonExplorerScreen` level so the overlay doesn't have to round-trip through `MoonExplorerActions`. (Or expose `actions.allSites()` — out of ADR-0005 scope; cleaner to thread the catalog directly.)
  - Verify the overlay's tap handlers don't break the existing pan/pinch on the empty Moon — Compose's pointer dispatch should handle it; on-device confirmation is in T350.
  - _Requirements: FR-001, FR-007_

**Checkpoint**: launching the app shows ~5–8 markers on the visible hemisphere, anchored to the surface as the user drags.

---

## Phase 3: Animated fly-to

- [x] **T320** [US2] [P] Add `MoonMath.shortestYawDelta` + `easeInOutCubic` — extended `MoonMathTest` with 8 new cases (zero / quarter-turn / wraparound shorter-path / modular inputs / ease endpoints / accel-decel / symmetry / clamping).
  - `fun shortestYawDelta(fromRad: Float, toRad: Float): Float` — wraps `to − from` into `(−π, π]`.
  - `fun easeInOutCubic(t: Float): Float` — `t < 0.5 ? 4·t³ : 1 − ((-2·t+2)³)/2`. Clamp `t` to `[0, 1]`.
  - _Requirements: FR-004, FR-005_

- [x] **T321** [US2] Rework `MoonExplorerActionsImpl.flyToMoonLocation` — `durationMs ≤ 0` keeps the snap path (FR-009 escape hatch); `durationMs > 0` runs a `TimeSource.Monotonic` + `delay(16)` loop using `shortestYawDelta` + `easeInOutCubic`. Mutex withLock + cancellable delay → screen-level `currentFlyJob.cancel()` interrupts cleanly.
  - `durationMs <= 0` → snap (existing path; preserves T212 expectations).
  - `durationMs > 0` → coroutine loop:
    ```
    val source = TimeSource.Monotonic.markNow()
    while (true) {
        val t = (source.elapsedNow().inWholeMilliseconds / durationMs.toFloat()).coerceIn(0f, 1f)
        val eased = easeInOutCubic(t)
        viewModel.setCameraTarget(startYaw + yawDelta·eased, startPitch + pitchDelta·eased)
        if (t >= 1f) break
        delay(16L)  // cancellable
    }
    ```
  - Yaw delta uses `shortestYawDelta`. The existing `mutex.withLock` keeps two concurrent fly-tos serialized — the loop's `delay` is cancellable so the screen-level `currentFlyJob.cancel()` interrupts cleanly.
  - _Requirements: FR-004, FR-005, FR-006, FR-009_

- [x] **T322** [US2] [P] Extend `MoonExplorerActionsImplTest` — renamed snap test to `flyToMoonLocation_durationZero_snaps`; added `animated_reachesTargetExactly` (50 ms run, eased(1) = 1 → state at target), `animated_progressesMonotonically` (mid-state strictly between 0 and target, final = target), `cancelMidAnimation_leavesPartialState` (1000 ms run, cancel after 120 ms, state strictly between), `yawWrap_takesShortPath` (custom initial state at +170°, target at -170°, asserts mid yaw stays on the +20° short path not the -340° long path). Updated concurrent test to use 30 ms animations to keep the suite fast.
  - `flyToMoonLocation("tycho", durationMs = 0)` — same expectations as 01-shell's existing test; reaffirms snap path still works.
  - `flyToMoonLocation("tycho", durationMs = 50)` — collect StateFlow values with `state.take(K).toList()`; assert intermediate yaws/pitches strictly between start and target, monotonic toward target.
  - Cancellation: in a `launch { actions.flyToMoonLocation("apollo_11", durationMs = 1000) }`, cancel the job after ~100 ms; assert state is between start and Apollo 11 target (not at target).
  - Yaw wrap: from yaw = 170° to a target with lon = −170° (or vice versa) — interpolated state should stay within ±10° of the start initially (shorter path), not hurtle through the 0° meridian.
  - _Requirements: SC-006_

- [x] **T323** [US2] Track `currentFlyJob` in `MoonExplorerScreen` — `var currentFlyJob: Job?` remembered at screen level; `onCenterClick` now does `currentFlyJob?.cancel(); currentFlyJob = scope.launch { ... }` so a tap-mid-animation interrupts the prior fly cleanly.
  - `var currentFlyJob: Job? by remember { mutableStateOf(null) }`.
  - In `onCenterClick`: `currentFlyJob?.cancel(); currentFlyJob = scope.launch { actions?.flyToMoonLocation(site.id) }`.
  - Same pattern wherever else a fly-to fires (e.g., marker tap if the click handler also triggers a fly).
  - _Requirements: FR-006_

**Checkpoint**: tapping "Center on this site" smoothly animates the camera over ~1.5 s; tapping a different site mid-animation visibly redirects without a jarring reset.

---

## Phase 4: Marker interaction polish

- [ ] **T330** [US3] Wire marker tap → info sheet
  - Already covered by T310 + T311's `onMarkerTap` callback that sets `infoSheetSite`. Verify on-device that taps on markers fire and don't double-fire as gestures.
  - _Requirements: FR-007_

- [ ] **T331** [US4] Highlighted marker visual
  - In `MarkerOverlay`, when `site.id == highlightedSiteId`: render with size = 24.dp (vs 14.dp default), `MaterialTheme.colorScheme.primary` fill (vs `onSurface` for default), and a subtle border.
  - _Requirements: FR-008_

**Checkpoint**: tapping a marker opens the info sheet; calling `actions.highlightLocation("tycho")` from a debug menu (or via `MoonExplorerActionsImplTest`) produces a visibly distinct Tycho marker.

---

## Phase Final: Polish + tests + docs

- [ ] **T340** Run `:shared:testAndroidHostTest :shared:iosSimulatorArm64Test` and confirm green
  - All carryover tests + new `ProjectionTest` + extended `MoonMathTest` + extended `MoonExplorerActionsImplTest` pass on both platforms.
  - _Requirements: SC-006_

- [ ] **T341** [P] Write `ai-docs/specs/03-sites-and-flyto/results.md`
  - Status by phase; user-confirmed items; pending hardware confirmation (60 FPS during fly-to; tap-vs-pan disambiguation; marker visibility under different camera distances).
  - _Requirements: agent-runbook.md_

- [ ] **T342** [P] Cross-reference notes in 01 + 02 results
  - 01-app-shell: note that animated fly-to lands here (snap-to placeholder is no longer the final form).
  - 02-mvp: nothing new to note unless we end up needing a renderer FOV alignment fix during projection-verification.
  - _Requirements: agent-runbook.md_

**Final Checkpoint**: all four user stories' acceptance criteria pass on real devices; markers stay anchored during gestures; fly-to feels smooth; tap-on-marker opens info sheet; `results.md` filed.

---

## Dependencies & Execution Order

| From → To | Why |
|---|---|
| T301 → T302 | Tests need the function to exist |
| T301 → T310 | `MarkerOverlay` calls `projectSiteToScreen` |
| T310 → T311 | Can't drop in what doesn't exist |
| T320 → T321 | Animation uses `shortestYawDelta` + `easeInOutCubic` |
| T321 → T322 | Tests exercise the new animation path |
| T321 → T323 | Screen tracks the job that wraps the (now-suspending-with-delay) impl |
| Phase 2 → Phase 4 | Marker UI must exist before tap polish |
| Phase 1 + Phase 2 + Phase 3 → Phase Final | Everything wired before final tests |

## Parallel Examples

- **Phase 1**: T301 + T302 are sequential (test depends on the function), but Phase 1 runs in parallel with Phase 3's T320 — they touch different files.
- **Phase 2 + Phase 3 in parallel**: T310 (MarkerOverlay file) and T321 (ActionsImpl edit) touch different files. Two threads: Thread A = T301 → T310 → T311; Thread B = T320 → T321 → T323. Tests in T302 + T322 are parallel-safe with each other.

## Implementation Strategy

- **Two PRs**. Land Phase 1 + Phase 3 (math + animation) as a single PR titled `03-sites-and-flyto: projection math + animated fly-to`. Land Phase 2 + Phase 4 + Phase Final as a second PR titled `03-sites-and-flyto: marker overlay + tap interaction + results`. Splitting math/animation away from the UI overlay makes review chunks smaller.
- Phase 3's T321 introduces a `delay()` inside `withLock`. Verify on-device that gesture responsiveness isn't degraded by the per-frame coroutine (it shouldn't — gestures don't acquire the action mutex).

## Notes

- **No new Gradle deps.** `kotlin.time.TimeSource.Monotonic`, `kotlinx.coroutines.delay`, Compose `Surface` / `clickable` / `derivedStateOf` are all already on classpath from the 01-shell + 02-mvp work.
- **Marker fidelity is intentionally simple** — uniform 14.dp dots, accent for highlighted. Site-type icons (different glyphs for crater / mare / landing site) is a polish spec.
- **3D billboards via Filament** are deliberately out of scope. If we ever want depth-sorted markers that also cast shadows, a future spec adds them as Filament `Renderable`s. The 2D Compose overlay buys us the same visual at zero cross-platform native cost.
- **Cancel-on-gesture for fly-to** is documented as accepted-but-not-implemented in `spec.md` § Edge Cases. If hardware testing reveals this is jarring, file a follow-up; it's a 5-line change to wire `currentFlyJob?.cancel()` into the `pointerInput` callback.
- **ADR-0009 items 7 + 8** (camera fly-to + markers) are resolved when this spec lands.
