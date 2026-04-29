# Feature Specification: 03 — Sites and Fly-to

**Branch:** `03-sites-and-flyto`
**Created:** 2026-04-29
**Status:** Draft (pending user ratification)

## Goal (1-line)

Render the curated catalog as **visible markers on the Moon's surface** and replace `01-app-shell`'s snap-to camera centering with a **smoothly animated fly-to** — closing the visual loop between "I can search a site" and "I can see where it is and feel the camera travel there."

## User Scenarios

### User Story 1 — See where the sites are (Priority: P1)

**Why this priority:** Until users can see markers, they have no idea what's on the Moon. The catalog is a hidden list of strings; markers turn it into a *map*. This is the spec's whole reason to exist alongside animated fly-to.

**Independent test:** Launch the app on a fresh install. Within one frame of the renderer initialising, ~5–8 small dots appear on the visible (near-side) hemisphere of the Moon, anchored to the surface. Tilt or rotate the Moon — the dots stay attached to their lat/lon points, so Tycho stays on Tycho.

**Acceptance Scenarios:**
- WHEN the renderer is alive AND the catalog has loaded THEN the system SHALL render a marker for every site in `SiteCatalog.all` whose lat/lon is on the camera-facing hemisphere.
- WHEN a site's selenographic position is on the far side (camera-facing dot product ≤ 0) THEN the system SHALL NOT render its marker.
- WHEN a site is near the limb (small but positive dot product) THEN the system SHALL fade the marker's alpha smoothly toward zero so it doesn't pop.
- WHEN the user drags / pinches the camera THEN markers SHALL re-project every frame without visible jitter, staying anchored to their lat/lon.

### User Story 2 — Smoothly fly the camera to a chosen site (Priority: P1)

**Why this priority:** `01-app-shell` shipped a deliberate snap. The animated lerp is what makes the search → "Center on this site" loop *feel* like exploration instead of teleportation. It's also the integration test for the projection math — if the math is wrong, animation makes the error obvious.

**Independent test:** Tap "Center on this site" for Tycho from the info sheet. The camera animates over ~1.5 s with ease-in-out so Tycho rolls into the centre of the screen.

**Acceptance Scenarios:**
- WHEN `MoonExplorerActions.flyToMoonLocation(id, durationMs)` is invoked AND `durationMs > 0` THEN the camera's yaw + pitch SHALL interpolate from the current state to the target over `durationMs` with cubic ease-in-out, sampling every frame (~16 ms cadence).
- WHEN `durationMs == 0` THEN the camera SHALL snap to the target in a single state update (preserves the snap-only path for tests + immediate-positioning use cases).
- WHEN the yaw delta crosses the ±π wrap THEN the animation SHALL take the shorter path (e.g. flying from lon = +170° to lon = −170° travels 20°, not 340°).
- WHEN a fly-to is in flight AND the user triggers a new fly-to THEN the in-flight animation SHALL cancel cleanly and the new one SHALL start from the current (interrupted) state.

### User Story 3 — Tap a marker to learn about a site (Priority: P2)

**Why this priority:** Search is the keyboard path; tap-on-marker is the pointer path. Both lead to the same `LocationInfoSheet` (from `01-app-shell` T221). Without it, markers are pretty dots that don't *do* anything — a missed UX opportunity for two days' marker work.

**Independent test:** Tap a marker on the visible hemisphere. The `LocationInfoSheet` opens for that site, identical to the search-result-tap flow.

**Acceptance Scenarios:**
- WHEN the user taps a marker AND the marker corresponds to site `s` THEN the system SHALL open `LocationInfoSheet(site = s)`.
- WHEN multiple markers overlap within the tap-target radius THEN the front-most (largest dot-product / closest to camera centre) SHALL win.
- WHEN the user taps an empty area of the viewport THEN the gesture SHALL fall through to the existing `pointerInput` drag handler — markers don't steal pan/pinch when there's no marker under the finger.

### User Story 4 — Highlighted marker visual (Priority: P2)

**Why this priority:** `MoonRenderState.highlightedSiteId` and `MoonExplorerActions.highlightLocation` already exist (per ADR-0005 + `01-app-shell` T211). 03 makes them visible. Without this, the highlight flag is dead state.

**Independent test:** Call `actions.highlightLocation("tycho", on = true)` from a test or debug menu. Tycho's marker visually distinguishes itself from the others (larger, thicker outline, accent colour).

**Acceptance Scenarios:**
- WHEN `state.highlightedSiteId` matches a marker's site id THEN the marker SHALL render with a visually distinct treatment (larger radius + accent fill or outline).
- WHEN `state.highlightedSiteId` is null OR doesn't match any catalog site THEN no marker SHALL render the highlighted treatment.

### Edge Cases

- **Pole sites**: max latitude in the catalog is South Pole–Aitken at −53°, well under the camera pitch clamp (±89.4°). No special handling needed.
- **Site exactly at the limb (dot product = 0)**: cull it (treat as far-side). Limb fade smoothstep starts at 0+epsilon to avoid divide-by-near-zero.
- **Viewport size = 0** (first frame after composition before `onSizeChanged` fires): skip projection; markers don't render until the viewport has dimensions.
- **Camera distance very close to the surface**: marker may project off-screen because the angular size of the moon exceeds the FOV. Acceptable — the user is "inside" the orbit limit; clamping to MIN_DIST = 1.5 keeps them at arm's length. If a marker's NDC coords are outside [−1, 1], the marker simply isn't drawn (Compose offsets clip naturally).
- **Animation interruption mid-flight**: the in-flight coroutine is cancelled; the next fly-to starts from the *current* (partial) state, not the prior animation's start. Yields a fluid feel.
- **Animation while user is dragging**: gestures continue to mutate state directly. The animation's per-frame `setCameraTarget` overwrites whatever yaw/pitch the gesture produced. Interaction during a fly-to is "the camera fights you" — not great UX, but documented as accepted; cancelling on gesture is a polish task.
- **Two markers overlap on screen** (e.g., Apollo 11 and Mare Tranquillitatis): tap target picks the front-most by camera-direction dot product. Visually they may be a few pixels apart — acceptable for v1.

## Requirements

### Functional Requirements

- **FR-001**: WHEN the renderer initialises AND the catalog is loaded THEN the system SHALL maintain a `MarkerOverlay` Compose layer above the renderer that renders one marker per `SiteCatalog.all` entry whose camera-facing dot product is positive.
- **FR-002**: WHEN a marker's camera-facing dot product is in `(0, 0.3]` THEN the marker's alpha SHALL be `smoothstep(0, 0.3, dot)` — fading toward the limb.
- **FR-003**: Marker projection SHALL use the perspective camera with `DEFAULT_FOV_Y_RAD` and the actual viewport dimensions (in px) — i.e., screen positions match what Filament rendered.
- **FR-004**: WHEN `flyToMoonLocation(id, durationMs > 0)` is invoked THEN the implementation SHALL animate yaw + pitch from the current state to the target over `durationMs` ms with cubic ease-in-out, sampling every ~16 ms via `kotlinx.coroutines.delay`.
- **FR-005**: Yaw interpolation SHALL take the shorter angular path (delta wrapped into `(-π, π]`).
- **FR-006**: WHEN a new `flyToMoonLocation` call arrives mid-animation THEN the in-flight coroutine SHALL be cancellation-safe (uses cancellable `delay`); the screen's caller is responsible for cancelling the prior `Job` before launching the next.
- **FR-007**: WHEN the user taps a marker on the overlay THEN the system SHALL open `LocationInfoSheet` for that site (same flow as search-result tap).
- **FR-008**: WHEN `state.highlightedSiteId` matches a rendered marker's id THEN the marker SHALL render with a distinct visual treatment (larger radius + accent fill).
- **FR-009**: `flyToMoonLocation(id, durationMs = 0)` SHALL behave identically to `01-app-shell`'s snap path (no animation; single state update). Preserves test compatibility for `MoonExplorerActionsImplTest.flyToMoonLocation_advancesCameraToSiteCoords`.

### Key Entities

- **`ScreenPos`** — `(xPx, yPx, limbAlpha)`, all `Float`. Output of the world-to-screen projection.
- **`MarkerOverlay`** — Compose composable in `commonMain/ui/`. Reads `state` (camera + highlightedSiteId) and the catalog, projects each site, renders or culls.
- **`projectSiteToScreen`** — pure function in `commonMain/domain/Projection.kt`. Inputs: lat/lon, camera state, viewport size, FOV. Output: `ScreenPos?` (null if culled).
- **`shortestYawDelta`** — pure helper in `MoonMath`. Wraps `to − from` into `(−π, π]`.
- **`easeInOutCubic`** — pure helper. `t ∈ [0, 1]`.

## Non-Functional Requirements

- **Performance**: 60 FPS sustained during fly-to. 16 markers × per-frame projection (~10 trig ops each) is ~10 µs of CPU; nowhere near a frame budget. Compose recomposition is the actual ceiling — `MarkerOverlay` reads `state` via `collectAsState` and re-runs the projection in a `derivedStateOf` so we recompose only when state actually advances.
- **Animation feel**: cubic ease-in-out matches Material's standard "FastOutSlowIn" curve; animations under 1500 ms feel responsive.
- **Cross-platform**: pure Compose overlay (no per-platform Filament work). Markers + tap detection live entirely in commonMain.
- **Layered architecture**: continuous gestures still go through `viewModel.onDrag/onPinch`; discrete fly-to commands still go through `MoonExplorerActions`. 03 only changes the implementation of `flyToMoonLocation`.
- **Testability**: projection math + yaw-delta + easing are pure functions with deterministic inputs/outputs.

## Success Criteria

- **SC-001**: Launching the app shows real visible markers on the Moon (not just NASA-textured colours). At least 4 markers visible at the default camera angle.
- **SC-002**: Tapping "Center on this site" for Tycho from the info sheet animates the camera over ~1.5 s — visibly smooth, no snap.
- **SC-003**: Tapping a marker directly opens its info sheet, equivalent to tapping the search result.
- **SC-004**: Calling `actions.highlightLocation("tycho")` produces a visually distinct marker for Tycho.
- **SC-005**: 60 FPS sustained on Pixel 6 + iPhone 12 during a fly-to (carryover hardware target from `02-moon-renderer-mvp`).
- **SC-006**: `:shared:allTests` passes including new `ProjectionTest`, `MoonMathTest` extensions for `shortestYawDelta` + `easeInOutCubic`, and an animation-progression test in `MoonExplorerActionsImplTest`.

## Assumptions & Out of Scope

**Out of scope:**
- **Custom user-pinned markers** (long-press to drop a pin) — defer to a future spec.
- **Marker labels with collision avoidance** — render the dot only; the info sheet carries the name.
- **3D billboards / Filament-side markers** — the 2D Compose overlay is simpler and good enough for 16 markers at this scale. If we later want depth-sorted 3D billboards, that's a renderer-side polish spec.
- **Cancel fly-to on gesture** — animation continues if the user starts dragging mid-fly. Documented in edge cases; accepted for v1.
- **Marker icons / emoji per site type** — uniform dot for all v1; future polish can differentiate `MARE` / `CRATER` / `LANDING_SITE`.
- **Accessibility (TalkBack / VoiceOver)** — markers are decorative; the search bar remains the keyboard / screen-reader path.
- **Animated lighting changes** during fly-to — sun direction stays put; only camera animates.

**Assumptions:**
- ADR-0006 selenographic convention is the source of truth for lat/lon → cartesian mapping.
- ADR-0005's `MoonExplorerActions.flyToMoonLocation(id, durationMs)` signature stays unchanged. Only the impl swaps from snap to animated.
- The 16-site catalog from `01-app-shell` is the marker source. No new sites added here.
- The renderer's projection (Filament's view + perspective matrix with `DEFAULT_FOV_Y_RAD`, near = 0.1, far = 100) is what the overlay must match. Any drift means markers float relative to features.
- Compose Multiplatform's gesture detector composes with overlay tap handlers — markers get first crack at taps; uncaught taps fall through to `pointerInput { detectTransformGestures }`.

## References

- ADR-0003 (Renderer host pattern — pull-not-push state)
- ADR-0005 (`MoonExplorerActions` shape — `flyToMoonLocation` signature)
- ADR-0006 (Selenographic coordinate convention)
- `ai-docs/research/selenographic-math-camera.md` §1 (lat/lon ↔ cartesian), §5 (camera animation), §7 (limb culling)
- `ai-docs/specs/01-app-shell/spec.md` — `LocationInfoSheet` + `MoonExplorerActions` consumer
- `ai-docs/specs/02-moon-renderer-mvp/spec.md` — renderer FOV / projection conventions
- `./plan.md`
- `./tasks.md`
