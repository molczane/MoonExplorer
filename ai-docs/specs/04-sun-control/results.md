# 04-sun-control — Results

T451 record of what shipped, what's user-confirmed, and what's still pending hardware measurement. The spec is feature-complete on `main`; on-device UX confirmation is the only outstanding work.

## Status by phase

| Phase | Code complete | User-verified | Commit |
|---|---|---|---|
| Phase 1 — math + preset table | ✓ | n/a (pure functions) | `6383729` |
| Phase 2 — SunPanel UI | ✓ | (pending hardware) | `1f9fb85` |
| Phase 3 — animated `setLightingPreset` + ADR-0005 amendment | ✓ | n/a (action surface) | `ee86cfe` |
| Phase 4 — SunControl → SunPanel swap-in | ✓ | (pending hardware) | `602ecac` |
| Phase Final — tests + docs | ✓ | n/a | (this commit) |

## Acceptance criteria

| | Status | Notes |
|---|---|---|
| **SC-001** `SunPanel` (joystick + 4 preset buttons) replaces `SunControl` at BottomCenter | ✓ code-complete | `MoonExplorerScreen` now hosts `SunPanel`; `SunControl.kt` deleted (T441). Visual confirmation pending hardware. |
| **SC-002** Joystick drag produces visible terminator movement | ✓ code-complete | `SunJoystick` uses `awaitEachGesture` + `awaitFirstDown` so the knob jumps to the first touch position with no drag-threshold lag; `onJoystickDrag` calls `viewModel.setSunDirection(joystickToSunDir(x, y))` direct. Per-frame Filament shader read picks up the new direction immediately. Visual confirmation pending hardware. |
| **SC-003** Each preset produces a visually distinct lit Moon | ✓ code + unit-test | `LightingPresetsTest` verifies the four sub-solar Vec3s are unit-length and at the right (lat°, lon°). Visual identity (Full / Half / Apollo / New) pending hardware. |
| **SC-004** Preset transitions are animated ~500 ms | ✓ code + unit-test | `MoonExplorerActionsImplTest.setLightingPreset_animated_reachesTargetExactly` confirms the eased loop lands at the target sub-solar Vec3 within 1e-4. Visual smoothness pending hardware. |
| **SC-005** Mid-animation interruption | ✓ code + unit-test | `setLightingPreset_cancelMidAnimation_leavesPartialState` cancels a 1000 ms Day → Night animation at 150 ms; asserts `sun.x` advanced past 0 and `sun.z > 0.5` (nowhere near Night). The screen-level `currentLightingJob.cancel()` interrupts cleanly via the cancellable `delay` inside `mutex.withLock`. |
| **SC-006** 60 FPS sustained on Pixel 6 + iPhone 12 during preset transition | TBD | Hardware measurement needed. Per-frame work is `lerpSunDirection` (~6 trig ops) + one `viewModel.setSunDirection` mutation; well below the 16 ms budget. Compose recomposition cost is the actual ceiling. |
| **SC-007** `:shared:allTests` passes | ✓ green | 99 tests across 11 suites green on `testAndroidHostTest` + `iosSimulatorArm64Test`. New since 03-flyto: `LightingPresetsTest` (5), `MoonMathTest` extensions (+10), `MoonExplorerActionsImplTest` extensions (+5 net: -1 stub, +6 animation cases). |

## Tests

`./gradlew :shared:testAndroidHostTest :shared:iosSimulatorArm64Test` runs:

| Suite | Tests | Notes |
|---|---|---|
| `AssetCacheTest` | 6 | from 02-mvp |
| `AssetManifestTest` | 3 | from 02-mvp |
| `Sha256Test` | 3 | from 02-mvp |
| `MoonMathTest` | **23** | 13 carryover + 10 new (T411): joystick centre / east-boundary / north-boundary / outside-clamp / off-diagonal-boundary / interior-unit-length, plus lerp endpoints / Day→Night-through-Terminator / Half→Apollo-mid-arc-at-+75° / unit-length-across-t |
| `MoonViewModelTest` | 10 | from spike |
| `MoonExplorerActionsImplTest` | **18** | 13 carryover + 5 net new (T432): `setLightingPreset_returnsOk` / `_durationZero_snaps` / `_animated_reachesTargetExactly` / `_animated_progressesMonotonically` / `_cancelMidAnimation_leavesPartialState` / `_concurrentSerializesViaMutex`. The 01-shell `setLightingPreset_returnsDeferredStub` test is gone — its `ok = false` assertion no longer matches the graduated impl |
| `ProjectionTest` | 11 | from 03-flyto |
| `SiteCatalogTest` | 11 | from 01-shell |
| `UvSphereTest` | 8 | from spike |
| `LightingPresetsTest` | **5** | T410 — Day = `(0,0,1)`, Terminator = `(1,0,0)`, HighContrast = `(0.866,0,0.5)`, Night = `(0,0,-1)`, plus the unit-length sweep across `LightingPreset.entries` |
| `SharedCommonTest` | 1 | placeholder |
| **Total** | **99** | All green on Android JVM + iOS simulator-arm64 |

## Deviations / deferrals (per spec.md § Out of Scope)

- **ADR-0005 amendment** — `setLightingPreset` gained a non-breaking `durationMs: Long = 500` default arg. Mirrors the precedent set by `flyToMoonLocation(id, durationMs = 1500)`. Recorded in `0005-koog-adoption-timing.md` § "Amendments" so the change is discoverable from the ADR's own history. The Phase-3 Koog tool binding inherits the same expressiveness — natural-language prompts can request slow / instant transitions ("show me Apollo lighting slowly" → tool passes a larger `durationMs`).
- **Lat/lon lerp instead of slerp** — the equatorial preset table makes lat/lon lerp on the equator equivalent to slerp on the equatorial great circle; for joystick → preset transitions where the start has non-zero lat, lat/lon lerp differs subtly from slerp but stays smooth and singularity-free. Slerp is a polish replacement if the visual difference matters; for v1, lat/lon lerp is correct enough and reuses `shortestYawDelta` from 03-flyto verbatim.
- **World-space joystick mapping** — the joystick's `(x, y)` maps directly to world-space `(sunX, sunY)`. When the camera rotates, the joystick's "right" stops meaning "world +X" and the user has to re-learn the mapping. Camera-space mapping (multiply by `inverse(viewMatrix)` upper-3×3) is a polish task; documented in `spec.md` § Out of Scope.
- **Scientific mode** — entering selenographic sun lat/lon directly via numeric input. Defer to a future spec; the AI guide can drive `setSunDirection(lat, lon)` from natural language without needing a numeric-input UI.
- **Light intensity slider** / **time-of-day animation** / **phase-by-date selector** / **sub-solar latitude variation** — all out of scope per Constitution V (tactile, not scientific). Defer.
- **Reset button** / **Preset highlight when joystick is near a preset** / **camera-space joystick mapping** — UI polish; defer.
- **`setSunDirection(lat, lon)` snap-only** — animation is opt-in via `setLightingPreset`. Preserves 01-shell's `setSunDirection_unitVector` snap-path expectations and matches the locked ADR-0005 contract for that signature (no `durationMs` default-arg amendment for `setSunDirection` — only `setLightingPreset`).
- **`MoonExplorerActions` deferred-stub backlog from 01-shell** — closed by this spec. With `setLightingPreset` graduated, the only `ok = false`-returning action is gone. `compareLocations`'s "richer comparison deferred" note remains as a non-blocking polish opportunity.

## Pending hardware measurements

The spec is code-complete; everything below needs an on-device session to confirm UX:

- **Joystick gesture vs viewport pan disambiguation** — the joystick's `awaitEachGesture` consumes pointer events inside the 120-dp pad; events outside the pad fall through to `MoonViewport`'s `pointerInput { detectTransformGestures }`. Same Compose pointer-dispatch pattern that `MarkerOverlay` relies on (03 T310, confirmed working there). Verify on hardware.
- **Joystick latency** — finger movement → terminator movement should feel ≤ 1-frame. Direct `viewModel.setSunDirection` hop, no animation, no Mutex. Confirm responsiveness on a slow phone.
- **Joystick knob saturation** — drag to the disk corner; knob should clamp to the disk boundary (not the bounding-rect corner). `joystickToSunDir` clamps `r² > 1` to `z = 0`; the knob's render position uses `(sunDirection.x, sunDirection.y)` which is unit-bounded.
- **Preset transition feel** — 500 ms cubic ease-in-out should match Material's standard motion curve. Hardware test for jank.
- **Mid-animation tap redirect** — tap a preset; mid-animation, tap another preset. The `currentLightingJob.cancel()` should make the new transition start from wherever the sun ended up, not from the prior animation's start.
- **Day → Night and Night → Day visuals** — both go through 90° equatorial sweeps via opposite limbs (Day→Night through the +90° meridian, Night→Day through the −90° meridian). Confirm both look like 180° great-circle rotations on hardware.
- **Concurrent fly-to + sun preset** — tap "Center on Tycho" + a preset button quickly. Both animations should run in parallel (independent jobs); Mutex inside the impl serialises against other concurrent action calls but doesn't gate the camera vs sun tracks against each other (they hit different state fields, not the same Mutex).
- **`setSunDirection` from the future Koog tool** — call `actions.setSunDirection(lat, lon)` from a debug menu; the joystick's knob position should track the new sun direction within one frame (knob is `state.sunDirection`-derived, not local UI state).

## References

- [`spec.md`](spec.md) — user stories + acceptance criteria
- [`plan.md`](plan.md) — architecture flow + components
- [`tasks.md`](tasks.md) — task list (T410–T452)
- [`../../decisions/0005-koog-adoption-timing.md`](../../decisions/0005-koog-adoption-timing.md) — `MoonExplorerActions` shape; this spec amends `setLightingPreset` (see § "Amendments")
- [`../../decisions/0006-selenographic-coordinate-convention.md`](../../decisions/0006-selenographic-coordinate-convention.md) — sun direction lat/lon → cartesian
- [`../../research/selenographic-math-camera.md`](../../research/selenographic-math-camera.md) §6 — sun direction modes (a) joystick + (b) selenographic; preset table
- [`../01-app-shell/results.md`](../01-app-shell/results.md) — predecessor results (`setLightingPreset` deferred-stub origin)
- [`../03-sites-and-flyto/results.md`](../03-sites-and-flyto/results.md) — animation pattern (cubic ease-in-out + cancellable mid-animation) reused here
- Hand-off branch: `main` (Phase Final at this commit).
