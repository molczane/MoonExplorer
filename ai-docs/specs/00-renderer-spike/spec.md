# Feature Specification: 00 — Renderer Spike

**Branch:** `00-renderer-spike`
**Created:** 2026-04-28
**Status:** Draft (pending user ratification of dependent ADRs)

## Goal (1-line)
Validate that we can render a lit Moon sphere on both Android and iOS using Filament, with the shared-state-driven renderer-host pattern (ADR-0003), before committing to MVP work.

## User Scenarios

### User Story 1 — See a 3D Moon on launch (Priority: P1)

**Why this priority:** The single most important thing to prove before any other work. If this doesn't work end-to-end on both platforms, the rest of the plan is wrong.

**Independent test:** Build and launch the app on a Pixel 6 and an iPhone 12. Verify a textured 3D sphere is visible on both.

**Acceptance Scenarios** (EARS-style):
- WHEN the app is launched THEN the system SHALL display a textured 3D sphere within 2 seconds.
- WHEN no input is given THEN the system SHALL render a stable image at the device's native refresh rate (60 FPS minimum).

### User Story 2 — Direct manipulation gestures (Priority: P1)

**Why this priority:** The "tactile lunar globe" is the core experience. Without working gestures the spike has not validated the renderer-host pattern.

**Independent test:** Drag and pinch on the Moon viewport on both platforms. Confirm camera responds.

**Acceptance Scenarios:**
- WHEN the user drags horizontally THEN the system SHALL rotate the camera yaw and the Moon SHALL visibly rotate, smoothly at 60 FPS, in the opposite direction of the drag (per `ai-docs/research/selenographic-math-camera.md` §4).
- WHEN the user drags vertically THEN the system SHALL pitch the camera within ±89° clamped (per §2).
- WHEN the user pinches outward THEN the system SHALL decrease camera distance (zoom in).
- WHEN the user pinches beyond `MIN_DIST` THEN the system SHALL clamp camera distance — the camera SHALL NOT enter the sphere.

### User Story 3 — Adjust sun direction (Priority: P2)

**Why this priority:** Validates that lighting is actually being driven by shared state, not a hardcoded shader uniform.

**Independent test:** Drag a UI control. Confirm the terminator (lit/unlit boundary) on the Moon visibly shifts.

**Acceptance Scenarios:**
- WHEN the user moves the sun control THEN the system SHALL update the lighting on the Moon visibly within one frame.
- WHEN the sun control is at center THEN the lit hemisphere SHALL be the one facing the camera (full-Moon-like).

### User Story 4 — Asset swap test (Priority: P3)

**Why this priority:** Confirms the asset pipeline works end-to-end before the real NASA texture is brought in.

**Independent test:** Toggle between two bundled placeholder textures (e.g., a colored grid and a checkerboard). Both render.

**Acceptance Scenarios:**
- WHEN the asset toggle is activated THEN the system SHALL switch the displayed Moon texture without restarting the renderer.

### Edge Cases

- Configuration change (rotate device): renderer SHALL reinitialize cleanly without leaking Filament resources.
- App backgrounded then foregrounded: renderer SHALL pause frame submission, then resume without crash.
- Renderer host disposed (e.g., navigation): all Filament resources SHALL be released.
- Asset load failure: app SHALL display a fallback "renderer unavailable" UI rather than crashing.

## Requirements

### Functional Requirements

- **FR-001**: WHEN the app is launched THEN the system SHALL display a 3D textured sphere within 2 seconds on both Android and iOS.
- **FR-002**: WHEN the user drags THEN the system SHALL rotate the camera with zoom-aware sensitivity (per `selenographic-math-camera.md` §4).
- **FR-003**: WHEN the user pinches THEN the system SHALL zoom the camera distance with exponential mapping (per §3).
- **FR-004**: WHEN the user adjusts the sun control THEN the system SHALL update lighting within one frame.
- **FR-005**: The system SHALL use the same compiled material (`.filamat`) on both platforms (per ADR-0001).
- **FR-006**: WHEN the renderer host is disposed THEN the system SHALL release all Filament resources without leaks (per ADR-0003).
- **FR-007**: WHEN the app is backgrounded THEN the renderer SHALL stop submitting frames; WHEN it is foregrounded THEN the renderer SHALL resume.
- **FR-008**: The system SHALL load all renderer assets (material, albedo texture, normal map) from the bundled `composeResources/files/` location (per ADR-0004 fallback path).

### Key Entities

- `MoonRenderState` (data class) — see ADR-0003.
- `MoonViewport` (expect Composable) — see ADR-0003.
- `MoonViewModel` — owns the StateFlow.
- `UvSphere` (procedural mesh) — see ADR-0006 texture mapping.
- A single Filament material at `composeResources/files/materials/moon.filamat`.
- Bundled fallback albedo + normal textures (2K KTX2/Basis Universal) — placeholders for the spike, not the real NASA assets.

## Non-Functional Requirements

- **Performance**: 60 FPS on a Pixel 6 and an iPhone 12 baseline. No frame drops in the spike scenario (one sphere, one light).
- **Memory**: No Filament resource leaks across configuration change / pause / resume cycle.
- **Build**: Both Android and iOS builds complete from a clean checkout (modulo `pod install` on iOS).
- **Battery**: Spike screen idle (no input) consumes <5% battery per 10 minutes on a Pixel 6 baseline. (Indicative; not a strict gate.)

## Success Criteria

- **SC-001**: The app launches to a visible 3D Moon on both Android and iOS without manual intervention.
- **SC-002**: All four user stories' acceptance criteria are met on a real device (not emulator only).
- **SC-003**: No Filament-related warnings or errors in `adb logcat` / Xcode console under steady-state.
- **SC-004**: ADR-0002's open verification (`Filament.podspec` simulator-arm64) is resolved and documented.
- **SC-005**: `./gradlew :shared:allTests` passes (math + state-mutation tests, no rendering tests).

## Assumptions & Out of Scope

**Out of scope for this spike:**
- Real NASA SVS textures (placeholder only — see ADR-0004 for the real asset spec, deferred to `02-moon-renderer-mvp`).
- Site markers (Apollo 11, craters, maria) — Phase 1 (`01-app-shell` + `03-sites-and-flyto`).
- Search by name — Phase 1 (`03-sites-and-flyto`).
- Fly-to animation — Phase 1 (`03-sites-and-flyto`).
- Sun control beyond a single slider (full joystick, presets, scientific mode) — Phase 1 (`04-sun-control`).
- Koog AI guide — Phase 3 (`06-koog-agent`).
- Polish: cinematic camera, label fades, themes, onboarding — Phase 2 (`05-polish`).

**Assumptions:**
- Android development happens on a Mac with Android Studio + AGP 9-compatible toolchain.
- iOS development happens on a Mac with Xcode 15+ and CocoaPods installed.
- Test device for Android: Pixel 6 (or comparable, arm64, Android 13+).
- Test device for iOS: iPhone 12 (or comparable, A14, iOS 16+).
- Filament 1.71.x is current and stable enough for both platforms.

## References

- ADR-0001 (Filament as renderer)
- ADR-0002 (Filament-on-iOS via Swift)
- ADR-0003 (Renderer host pattern)
- ADR-0004 (Asset strategy — fallback path used here)
- ADR-0006 (Selenographic convention)
- `ai-docs/research/filament-cmp-integration.md`
- `ai-docs/research/agp9-kmp-native-deps.md`
- `ai-docs/research/selenographic-math-camera.md`
- `./plan.md`
- `./tasks.md`
