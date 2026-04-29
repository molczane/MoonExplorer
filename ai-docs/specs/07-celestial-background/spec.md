# Feature Specification: 07 — Celestial Background

**Branch:** `07-celestial-background`
**Created:** 2026-04-29
**Status:** Draft (pending user ratification)

## Goal (1-line)

Replace the renderer's flat-black backdrop with a real celestial backdrop — a Milky Way starfield + a visible blooming sun that respects `state.sunDirection` — so the Moon stops floating in a void and starts feeling like it's *somewhere*. Earth deferred to a future spec.

## Numbering note

This spec slots in as **07** rather than 05 to leave the existing skeleton placeholders (`05-polish`, `06-koog-agent`) where they are. Time-of-implementation order is `04 → 07 → 05 → 06`; numbering is semantic-group order, not chronological.

## User Scenarios

### User Story 1 — Stars behind the Moon (Priority: P1)

**Why this priority:** The black void is the single biggest "something's missing" cue in screenshots. A starfield turns the same Moon into "the Moon in space" — same model, totally different feel. It's also the highest-ROI per line of code in the spec.

**Independent test:** Launch the app on a fresh install. The Moon hangs in a star-rich Milky Way panorama. Pan the camera; stars stay parallax-correct (fixed in world space, camera moves through the celestial sphere).

**Acceptance Scenarios:**
- WHEN the renderer is initialised AND `state.showStars = true` THEN the system SHALL render a Filament `Skybox` cubemap loaded from the bundled 6-face Milky Way Panorama PNGs.
- WHEN the user rotates the camera (yaw / pitch / pinch) THEN the stars SHALL stay fixed in world space — the camera moves through the celestial sphere, no skybox rotation.
- WHEN `state.showStars = false` THEN the system SHALL detach the skybox and the renderer's clear color (black) SHALL show through.

### User Story 2 — Sun visible in the scene (Priority: P1)

**Why this priority:** The directional light has lit the Moon since the spike, but the sun itself hasn't been visible — there was no source object. Making the sun visible closes the loop: when the user moves the joystick or picks a preset, they see the sun *move*, not just the lighting change.

**Independent test:** From the default camera, drag the sun-joystick or tap the Half preset. The sun's bright disc visibly slides across the starfield as the lighting on the Moon updates.

**Acceptance Scenarios:**
- WHEN the renderer is initialised AND `state.showSun = true` THEN the system SHALL render a camera-facing emissive billboard quad at `state.sunDirection · SUN_DISTANCE`, scaled to subtend ~0.52° angular diameter at the world origin (the sun's actual apparent size from the Moon).
- WHEN `state.sunDirection` changes (joystick drag, preset animation, `setSunDirection` action) THEN the sun's transform SHALL update on the next frame.
- WHEN the Moon is between the camera and the sun (camera + Moon + sun roughly colinear) THEN the Moon SHALL occlude the sun via Filament's depth-test — a free-of-charge solar eclipse from a third-party vantage point.
- WHEN `state.showSun = false` THEN the system SHALL remove the sun billboard from the scene.

### User Story 3 — Bloom on the sun (Priority: P1)

**Why this priority:** A 0.52° disc is geometrically tiny. Without bloom it's a flat sticker; with bloom it glows convincingly and "feels like a sun." Bloom also handles the camera-zoom case: at MIN_DIST = 1.5 the disc may be a couple of pixels, but its bloom keeps it visible and dramatic.

**Independent test:** With the sun visible, observe a soft glowing halo around its disc. Pan the camera so the Moon's lit edge is in frame; the Moon's highlight does NOT bloom — only the sun does.

**Acceptance Scenarios:**
- WHEN `state.showSun = true` THEN the renderer SHALL enable Filament's bloom with a threshold tuned so the sun's emissive HDR pixels qualify and the Moon's reflective pixels do not (sun emissive intensity ~5–10× the Moon's max reflective).
- WHEN `state.showSun = false` THEN bloom SHALL be disabled — no bright pixels to bloom anyway, but disabling saves the post-FX pass cost on low-end hardware.
- WHEN the camera is at MIN_DIST or MAX_DIST THEN bloom SHALL render correctly at both extremes — the sun stays visible without aliasing artifacts.

### User Story 4 — Settings toggles for stars + sun (Priority: P2)

**Why this priority:** The placeholder `SettingsSheet` has been a UI promise unkept since 01-app-shell. This is its natural first job. Also gives users on low-end devices an escape hatch (turn stars + bloom off if it's chuggy).

**Independent test:** Open About → Settings. Toggle "Show stars" off; the stars vanish. Toggle "Show sun" off; the sun and its bloom vanish. Re-toggle both; they come back.

**Acceptance Scenarios:**
- WHEN the user opens `SettingsSheet` THEN the system SHALL display two `Switch` controls labelled "Show stars" and "Show sun" bound to `state.showStars` and `state.showSun`.
- WHEN the user toggles a switch THEN the renderer SHALL reflect the change within one frame.
- WHEN the app launches THEN both flags SHALL default to `true`.
- Persistence across app launches is **out of scope** for v1; flags reset to defaults each session. DataStore-backed persistence is a polish task.

### Edge Cases

- **Sun directly behind camera**: not visible, no bloom; viewport shows stars + Moon's dark side. Acceptable — astronomically correct.
- **Sun directly behind Moon (alignment)**: Moon occludes sun via depth-test; bloom may leak slightly around the Moon's silhouette depending on the kernel radius. Charming, not a bug.
- **All toggles off**: viewport shows just the Moon on a flat-black background — essentially the pre-spec state. Useful as a rendering baseline.
- **Sun direction at the +Y or −Y poles**: emissive billboard renders normally; the lit Moon-hemisphere is the polar cap. Astronomically rare (real sun-Moon system has axial tilt < 2°) but the math doesn't care.
- **Joystick drag with bloom**: per-frame transform update + bloom redraw. No frame stutter expected on target hardware; if observed, document in `results.md`.
- **Preset transition with bloom**: sun lerps via `lerpSunDirection`; bloom tracks every frame. The visually striking result — a smooth glowing arc across the starfield — is the spec's headline visual moment.
- **Day → Night preset routes the sun behind the Moon at t = 0.5**: the bloom briefly leaks through the Moon's silhouette as the sun crosses. Accepted v1 artifact.

## Requirements

### Functional Requirements

- **FR-001**: WHEN the renderer is initialised AND `state.showStars = true` THEN the system SHALL load the 6-face PNG cubemap from `composeResources/files/stars/{px,nx,py,ny,pz,nz}.png`, build a Filament cubemap `Texture`, attach it to a `Skybox`, and call `scene.setSkybox(skybox)`. The cubemap face order matches Filament's expected `[+X, -X, +Y, -Y, +Z, -Z]` enumeration.
- **FR-002**: WHEN `state.showStars` flips false → true THEN the renderer SHALL re-attach the skybox; flipping true → false SHALL detach it (`scene.setSkybox(null)`).
- **FR-003**: A `sun.mat` source SHALL ship in `materials/`; the existing `compileMaterials` Gradle task SHALL produce `sun.filamat` and bundle it in resources. The material is unlit + emissive with a single uniform `intensity` (≥ 5.0 in linear HDR so it qualifies for bloom thresholding).
- **FR-004**: WHEN the renderer is initialised AND `state.showSun = true` THEN the system SHALL build a 1×1 quad mesh, instantiate the `sun.filamat` material, create a Renderable Entity, and add it to the scene.
- **FR-005**: Per-frame, the renderer SHALL update the sun Entity's transform: position = `state.sunDirection · SUN_DISTANCE` (where `SUN_DISTANCE = 1000.0f` — well past `MAX_DIST = 20`), rotation = camera-facing (billboard), scale to subtend `SUN_ANGULAR_DIAMETER_RAD ≈ 0.0091` (~0.52°) at world origin.
- **FR-006**: WHEN `state.showSun` flips false → true THEN the renderer SHALL re-attach the sun Renderable; flipping true → false SHALL remove it from the scene.
- **FR-007**: WHEN `state.showSun = true` THEN the renderer SHALL enable Filament bloom via `View.setBloomOptions(BloomOptions { enabled = true; threshold = ~5–10; strength = ~0.5 })`. Threshold tuned empirically during T722 so only the sun's emissive pixels qualify.
- **FR-008**: WHEN `state.showSun = false` THEN bloom SHALL be disabled (`enabled = false`).
- **FR-009**: `SettingsSheet` SHALL render two `Switch` controls — "Show stars" and "Show sun" — bound to `state.showStars` and `state.showSun` via `viewModel.setShowStars(Boolean)` / `setShowSun(Boolean)`.
- **FR-010**: `MoonViewModel.setShowStars(Boolean)` and `setShowSun(Boolean)` SHALL update `MoonRenderState` via the existing `_state.update { ... }` pattern. Both methods are testable via `MoonViewModelTest`.
- **FR-011**: ADR-0004 (asset strategy) SHALL be amended with a star-asset attribution string for the ESO Milky Way Panorama, mirroring the NASA SVS attribution string already in place.

### Key Entities

- **`MoonRenderState.showStars: Boolean = true`** (NEW field).
- **`MoonRenderState.showSun: Boolean = true`** (NEW field).
- **`MoonViewModel.setShowStars(Boolean)` / `setShowSun(Boolean)`** (NEW methods).
- **Star cubemap** — 6 PNG files in `composeResources/files/stars/{px,nx,py,ny,pz,nz}.png`.
- **`sun.mat`** (NEW source) → **`sun.filamat`** (baked artifact).
- **Per-platform native**: Filament `Skybox` + sun `Entity` + sun `MaterialInstance` + `BloomOptions`. Setup duplicated on Android (Kotlin/JNI) and iOS (Obj-C++).

## Non-Functional Requirements

- **Performance**: 60 FPS sustained on Pixel 6 + iPhone 12 with stars + sun + bloom enabled. Bloom adds one post-FX pass; expected ≤ 2 ms on target hardware. Skybox is a single full-screen draw (sub-ms). Hardware target carryover from `02-moon-renderer-mvp`.
- **Bundle size**: cubemap ≤ 6 MB total (6 × ≤ 1 MB at 1024×1024 PNG). `sun.filamat` < 50 KB. Total install impact small relative to the Moon textures.
- **GPU memory**: cubemap = ~24 MB at 1024 RGBA8 × 6 faces. Acceptable on top of the 8 K Moon textures.
- **Cross-platform parity**: same look on Android + iOS. Filament's deterministic pipeline guarantees this; visual diffs would indicate a setup bug.
- **Asset attribution**: ESO Milky Way Panorama (Serge Brunier) credited verbatim per CC BY 4.0 in About.
- **No new ADRs**: covered by ADR-0001 (Filament), ADR-0003 (host pattern), ADR-0004 (asset strategy + amendment), ADR-0006 (selenographic — sun direction frame), ADR-0008 (Filament pod — Skybox / Material / bloom symbols). The ADR-0004 amendment is the only doc change in the decisions tree.

## Success Criteria

- **SC-001**: Stars visible in the background at app launch — recognisable Milky Way band.
- **SC-002**: Sun visible as a glowing disc; visibly moves when the joystick or preset changes the sun direction.
- **SC-003**: Sun blooms; Moon's reflective highlights do NOT bloom (verified across the 4 lighting presets + joystick range).
- **SC-004**: Stars and sun individually toggleable from `SettingsSheet`; renderer reflects toggle within one frame.
- **SC-005**: 60 FPS sustained on Pixel 6 + iPhone 12 with stars + sun + bloom on.
- **SC-006**: `:shared:allTests` passes including new `MoonViewModelTest` cases for `setShowStars` / `setShowSun` state mutations. The bulk of the spec's work is per-platform Filament native setup which has no commonTest coverage — gap noted in `plan.md` § Testing strategy, not a regression.

## Assumptions & Out of Scope

**Out of scope:**
- **Earth** — deferred. May land in a future `08-earth` or similar spec.
- **Skybox rotation animation** — stars stay fixed in world space.
- **Lens flares / corona / ghost / starburst effects** on the sun — bloom only.
- **Constellation overlays / labels** — decorative starfield, not an astronomy app.
- **HDR sky / IBL contribution** — skybox is purely visual; the directional sun is the only meaningful light source.
- **Settings persistence across launches** — flags reset to defaults each session.
- **Atmospheric scattering** — none. The Moon has no atmosphere; this is correct.
- **Sun colour tuning** — pure white emissive for v1; 5778 K blackbody approximation is a polish task.
- **Sun corona during eclipse** — when the Moon occludes the sun, no corona is drawn. Pure depth-test occlusion.
- **Ephemeris-based sun position** — sun direction is driven by `state.sunDirection` (the joystick / preset path), not by a real-world clock. Ephemeris is documented as out-of-scope all the way back in `04-sun-control/spec.md`.

**Assumptions:**
- ADR-0006 selenographic convention pins the world-space frame; sun direction is in that frame.
- ADR-0011 (PNG bundled, KTX2 only iOS HD) applies to the cubemap — bundle as PNG. HD KTX2 streaming is unnecessary for the skybox (small assets, no resolution tier).
- Filament's `Skybox`, `Material.Builder().payload(filamatBytes)`, and `View.setBloomOptions` are all available on both platforms via the existing pod (Filament/filament + Filament/uberz subspecs per ADR-0008).
- The existing Gradle `compileMaterials` task can be extended to produce a second `.filamat` (sun.filamat) by adding `sun.mat` to the materials source set.
- ESO Milky Way Panorama (Serge Brunier, ESO press release 0932) is licensed CC BY 4.0; T701 verifies the attribution wording before bundle.
- `SUN_DISTANCE = 1000.0f` keeps the sun behind anything camera-relevant; `SUN_ANGULAR_DIAMETER_RAD = 0.0091` (~0.52°) matches the sun's true apparent size from the Moon.

## References

- ADR-0001 (Filament as the renderer)
- ADR-0003 (Renderer host pattern — pull-not-push)
- ADR-0004 (Asset strategy — amended in this spec to add star attribution)
- ADR-0006 (Selenographic coordinate convention — sun direction frame)
- ADR-0008 (Filament pod via raw URL — Skybox / Material / bloom symbols come from here on iOS)
- ADR-0011 (Android HD KTX2 deferred — PNG bundling rule applies to the cubemap)
- ESO press release 0932 — Milky Way Panorama by Serge Brunier (CC BY 4.0)
- `ai-docs/specs/02-moon-renderer-mvp/spec.md` — established the bundled-PNG asset pattern this spec follows
- `ai-docs/specs/04-sun-control/spec.md` — `state.sunDirection` is the input the sun billboard reads each frame
- `./plan.md`
- `./tasks.md`
