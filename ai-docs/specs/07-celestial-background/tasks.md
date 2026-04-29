# Tasks: 07 — Celestial Background

## Format: `[ID] [P?] [US?] Description`

`[P]` = parallel-safe with sibling tasks. `[US#]` = which user story this serves.
Acceptance criteria for each user story live in `spec.md`.

## Path conventions

All paths relative to `MoonExplorer/` repo root. Task IDs are namespaced **T700+** to avoid collision with `00-renderer-spike` (T001–T093), `02-moon-renderer-mvp` (T100–T145), `01-app-shell` (T200–T232), `03-sites-and-flyto` (T301–T342), and `04-sun-control` (T410–T452). The 700 range leaves the 500 / 600 spans for the existing skeleton placeholders (`05-polish` and `06-koog-agent`) when they're picked up.

---

## Phase 1: Stars (skybox)

- [x] **T701** [P] [US1] Acquired ESO Milky Way Panorama (Brunier, ESO press release 0932) at 6000×3000 RGB TIFF (28 MB). License verified as CC BY 4.0 via ESO's [copyright page](https://www.eso.org/public/copyright/) — applies to public images including this one; ESO logo is the only relevant exception (we don't use it). Canonical attribution string captured: **"Milky Way Panorama: ESO/S. Brunier. CC BY 4.0. https://www.eso.org/public/images/eso0932a/"**. Wired into `AboutSheet.kt` (verbatim, must not be paraphrased per CC BY 4.0) and recorded as an "Attribution" amendment in `ADR-0004` dated 2026-04-30. Source TIFF is **not committed to the repo** (28 MB binary); re-bake instructions live in `tools/bake-stars-cubemap/bake_eso.py`'s docstring.
  - Source: ESO press release 0932 — Milky Way Panorama by Serge Brunier. Confirm CC BY 4.0 license terms against the original press release page; capture the canonical attribution string ("ESO/S. Brunier") for ADR-0004's amendment + AboutSheet.
  - Fallback: NASA Tycho Star Catalog skymap (public domain) if ESO licensing isn't unambiguous.
  - Output: a single equirectangular PNG (4096×2048 or similar), staged outside the repo for the bake step in T702.
  - _Requirements: NFR (asset attribution), FR-011_

- [x] **T702** [P] [US1] Baked equirectangular → 6-face cubemap via `tools/bake-stars-cubemap/bake_eso.py` (Python 3.14 + numpy 2.4.2 + PIL). Standard OpenGL cubemap face-axis convention; bilinear sampling; numpy-vectorized per face (~1 second per face). Output: 6 PNGs at **1024×1024** sRGB at `shared/src/commonMain/composeResources/files/stars/{px,nx,py,ny,pz,nz}.png` — px/nx/pz/nz are 1.9–2.0 MB each (Milky Way band cuts across them); py/ny are 1.3–1.4 MB (more sparse). **Total ~10.5 MB** (over the spec's ≤ 6 MB guideline; documented as accepted-for-quality deviation since the celestial backdrop is the second-largest visual asset after the Moon textures and 1024-per-face is the visible-difference threshold for the Milky Way band).
  - Convert with cmft / AMD Cubemapgen / Blender / Python+PIL — pick whichever produces seamless edges; document the tool + version used in `results.md`.
  - Output: 6 PNG files at 1024×1024 each, sRGB 8-bit. Naming: `{px,nx,py,ny,pz,nz}.png` matching Filament's `[+X, -X, +Y, -Y, +Z, -Z]` face enum order.
  - Bundle path: `shared/src/commonMain/composeResources/files/stars/`
  - Total bundled size ≤ 6 MB.
  - _Requirements: FR-001, NFR (bundle size — deviation accepted, see above)_

- [x] **T702-placeholder** [US1] **Superseded by T702** — the procedural placeholder script (`tools/bake-stars-cubemap/generate_placeholder.py`) stays in the tree as a fallback test-data generator (useful for "test without bundling 10 MB of assets" scenarios), but the bundled PNGs are now the real ESO bake.

- [x] **T703** [US1] [Android] Skybox setup in `MoonHost.kt`
  - On init: load 6 PNGs via `BitmapFactory.decodeStream` (matches the existing 2K Moon-texture path); build a Filament cubemap `Texture` (`TextureSampler.SamplerType.SAMPLER_CUBEMAP`); upload each face via `setImage(level=0, faceOffset)`; build a `Skybox.Builder().environment(texture).build(engine)`; on per-frame `if (state.showStars && lastSkyboxState != true)`: `scene.setSkybox(skybox)`; flip-side: `scene.setSkybox(null)`.
  - Cache the `Skybox` instance — recreating it every frame is wasteful.
  - Track the previous `showStars` value to avoid redundant `setSkybox` calls.
  - _Requirements: FR-001, FR-002_

- [x] **T704** [US1] [iOS] Skybox setup spans the full bridge — `MoonRenderer.h` declares `loadStarsCubemapPx:nx:py:ny:pz:nz:` (one-shot) + `setShowStars:` (per-recomp). `MoonRenderer.mm` implements: `decodePngToRgba8` per face → packed `std::vector<uint8_t>` of 6 face's RGBA8 → `Texture::FaceOffsets` + `setImage(level, pbd, offsets)` → `Skybox::Builder().environment(cubemap).build()` → conditional `scene->setSkybox(...)`. `MoonRendererProvider` gains `applyStarsCubemap` + `applyShowStars` closures. `MoonAssets.kt` gains `loadAndPushStarsCubemap()` (reads 6 PNGs from compose resources, pushes via the closure). `MoonViewport.ios.kt` calls `applyShowStars(state.showStars)` in the `update` lambda. `MoonRendererViewController.swift` adds forwarders + pre-viewDidLoad pending-bytes buffer. `iOSApp.swift` wires the two new closures + adds a startup `Task` for `loadAndPushStarsCubemap()`. Symbol coverage from `Filament/filament` subspec (ADR-0008) confirmed via `compileKotlinIosSimulatorArm64` linking cleanly.
  - Same dance in Obj-C++: 6 `CGImageSource`-decoded PNGs → cubemap `Texture` → `Skybox::Builder().environment(...).build(engine)` → `scene->setSkybox(...)`.
  - Verify `Skybox` symbol resolves from `Filament/filament` subspec (ADR-0008) — `nm libfilament.a | grep -i Skybox` if there's any doubt.
  - _Requirements: FR-001, FR-002_

- [x] **T705** [US1] `MoonRenderState.showStars: Boolean = true` + `MoonViewModel.setShowStars(Boolean)` shipped; `MoonViewModelTest` extended with `setShowStars_defaultIsTrue` + `setShowStars_togglesState` (+2 cases). Suite count: **101 green** on Android JVM + iOS sim (was 99 after 04 Phase Final → +2).
  - Add `showStars: Boolean = true` to `MoonRenderState`.
  - Add `fun setShowStars(value: Boolean) { _state.update { it.copy(showStars = value) } }` to `MoonViewModel`.
  - Both renderer hosts read `state.showStars` per frame and conditionally `setSkybox(...)`.
  - _Requirements: FR-002, FR-009 (partial), FR-010 (partial)_

**Checkpoint**: launching the app on simulator shows the Moon against the Milky Way panorama; the camera pans through the celestial sphere with parallax-correct stars; `state.showStars = false` makes the stars vanish.

---

## Phase 2: Sun disc

- [x] **T710** [US2] Authored `shared/src/commonMain/composeResources/files/materials/sun.mat` — unlit emissive disc, `masked` blending, single `intensity: float` parameter. Fragment shader checks `length(uv - 0.5)` against the disc radius (0.5) and uses a `1 - smoothstep(0.495, 0.5, r)` alpha falloff so the disc has a soft anti-aliased edge that survives bloom kernel blur. `requires : [position, uv0]` only — no tangents (unlit shading doesn't need them).
  - Unlit shading model, single `intensity: float` parameter, fragment outputs `baseColor = vec4(intensity, intensity, intensity, 1.0)`.
  - Source per `plan.md` § "Sun material".
  - _Requirements: FR-003_

- [x] **T711** [US2] Refactored `compileMaterials` from a single hardcoded `Exec` task to a `registerMaterialCompile(name)` helper that registers one `Exec` task per `.mat` source. Added `compileMoonMaterial` + `compileSunMaterial`; `compileMaterials` is now an umbrella that depends on both. The downstream Compose-Resources packaging dependency block in `tasks.matching` now lists both per-material tasks directly (Gradle's strict-input-output detector wants direct dependencies on the producer tasks; the umbrella alone wasn't enough). Confirmed `sun.filamat` (52 KB) lands next to `moon.filamat` (734 KB) in `composeResources/files/materials/`.
  - The task already compiles the Moon material; check whether it globs `materials/*.mat` (zero-line change) or names files explicitly (one-line addition).
  - Confirm `sun.filamat` lands in `composeResources/files/` next to the Moon's `.filamat`.
  - _Requirements: FR-003_

- [x] **T712** [US2] [Android] Sun Renderable in `MoonHost.kt` — `sun.filamat` loaded synchronously alongside `moon.filamat` (matches existing pattern). 1×1 quad mesh built procedurally (4 vertices, FLOAT3 positions + FLOAT2 uv0, 6 ushort indices). MaterialInstance created with `intensity = SUN_EMISSIVE_INTENSITY = 5f`. Renderable entity attached to scene at init when `lastShowSun ≠ default true`; per-frame `applySunBillboard` flips attach/detach when state.showSun changes. Cached `sunTransformInstance` for the per-frame transform path. Tear-down extends the existing `destroy()` reverse-order chain.
  - Build a 1×1 quad mesh procedurally (4 verts, 2 tris). Load `sun.filamat` via `Material.Builder().payload(filamatBytes).build(engine)`. Create `MaterialInstance`; set `intensity = SUN_EMISSIVE_INTENSITY` (~5–10, tunable in T722). Build a `Renderable` Entity; cache the entity + transform manager instance.
  - Add to scene only when `state.showSun = true` (mirror the skybox attach/detach pattern from T703).
  - _Requirements: FR-004, FR-006_

- [x] **T713** [US2] [iOS] Sun Renderable spans the full bridge — `MoonRenderer.h` declares `loadSunMaterial:` (one-shot) + `setShowSun:` (per-recomp). `MoonRenderer.mm` implements both: loadSunMaterial: builds Material → MaterialInstance with intensity=5 → 1×1 quad mesh (interleaved POSITION + UV0 in one vertex buffer) → Renderable, attaches to scene if `_showSun`. Per-recomp setShowSun: idempotent attach/detach. `MoonRendererProvider` gains `applySunMaterial` + `applyShowSun` closures. `MoonAssets.kt` gains `loadAndPushSunMaterial()`. `MoonViewport.ios.kt` calls `applyShowSun(state.showSun)` per recomp. `MoonRendererViewController.swift` adds forwarders + a `pendingSunMaterial` buffer for pre-viewDidLoad pushes. `iOSApp.swift` wires the two new closures + a startup Task for `loadAndPushSunMaterial()`. Constants (`kSunDistance` etc.) defined in MoonRenderer.mm's anonymous namespace mirror Android's companion-object values.
  - Same in Obj-C++: load `sun.filamat`, build quad mesh, build Renderable, scene attach/detach.
  - _Requirements: FR-004, FR-006_

- [x] **T714** [US2] [P] Per-frame sun billboard transform on both platforms. **Adjusted SUN_DISTANCE from plan's 1000 → 50** because both renderers' `FAR_PLANE = 100`; sun at 1000 would clip. With camera `MAX_DIST = 20`, worst-case sun-to-camera distance is 70 — comfortably inside far plane. `SUN_SCALE ≈ 0.455` units (= 2·50·tan(0.0091/2)) preserves the ~0.52° apparent angular diameter from the camera. Billboard math: `forward = camPos - sunPos` (normalized), `right = worldUp × forward`, `up = forward × right`; matrix is column-major per Filament's TransformManager (right-axis * s, up-axis * s, forward-axis, sunPos). Pole singularity (forward parallel to worldUp) handled by falling back to world +X for right.
  - Read `state.sunDirection`. Compute `sunPos = sunDir · SUN_DISTANCE` (~~1000.0~~ 50.0). Compute billboard rotation via `lookAt(sunPos, cameraPos, Vec3.UP)`. Compute scale: `2 * SUN_DISTANCE * tan(SUN_ANGULAR_DIAMETER_RAD / 2) ≈ ~~9.1~~ 0.455` units. Apply via `transformManager.setTransform(sunInstance, mat)`.
  - Constants live next to existing renderer constants — `SUN_DISTANCE: Float = ~~1000f~~ 50f`, `SUN_ANGULAR_DIAMETER_RAD: Float = 0.0091f`, `SUN_EMISSIVE_INTENSITY: Float = ~~8f~~ 5f` (placeholder; T722 tunes).
  - _Requirements: FR-005_

- [x] **T715** [US2] `MoonRenderState.showSun: Boolean = true` + `MoonViewModel.setShowSun(Boolean)` shipped; `MoonViewModelTest` extended with `setShowSun_defaultIsTrue` + `setShowSun_togglesState` (+2 cases). Suite count: **103 green** on Android JVM + iOS sim (was 101 after Phase 1 → +2).
  - Add `showSun: Boolean = true` to `MoonRenderState`.
  - Add `fun setShowSun(value: Boolean) { _state.update { it.copy(showSun = value) } }` to `MoonViewModel`.
  - _Requirements: FR-006, FR-010 (partial)_

**Checkpoint**: with stars on + sun on, dragging the joystick / tapping a preset moves a flat white disc across the starfield. No glow yet — that's Phase 3.

---

## Phase 3: Bloom

- [x] **T720** [US3] [Android] `view.setBloomOptions(...)` in `MoonHost.kt` — `View.BloomOptions` configured once in init with `threshold = true` (highpass), `strength = 0.5f`, `resolution = 360`, `levels = 6`, `blendMode = ADD`, and lens-flare-style effects (`lensFlare`, `starburst`, `chromaticAberration`, `ghostCount`) explicitly disabled. `enabled` flag tracks `state.showSun`: per-frame `applyBloom(state)` dedups against `lastBloomEnabled` and only re-pushes the struct when the user toggles. Saves the highpass + Gaussian blur post-FX cost when the sun is hidden.
  - On init or per-frame (whichever Filament prefers — usually init + reconfigure on toggle): construct `BloomOptions { enabled = state.showSun; strength = 0.5f; resolution = 360; levels = 6; threshold = true; blendMode = ADD }`. Call `view.setBloomOptions(options)`.
  - _Requirements: FR-007, FR-008_

- [x] **T721** [US3] [iOS] `view->setBloomOptions(...)` in `MoonRenderer.mm` — `View::BloomOptions _bloomOptions` instance member configured at init with the same values as Android (threshold-based, strength 0.5, resolution 360, levels 6, ADD blend, lens-flare-style effects off). Push happens at init (with `enabled = false`) and again from `setShowSun:` on toggle change with `_lastBloomEnabled` dedup. Symmetric with the Android pattern.
  - Same struct, same values. Filament's iOS API is C++ matching the Java binding 1:1.
  - _Requirements: FR-007, FR-008_

- [ ] **T722** [US3] Tune sun emissive intensity vs bloom threshold — **hardware-bound**, deferred. Phase 3 lands the initial values: `SUN_EMISSIVE_INTENSITY = 5f` (set on the sun.filamat MaterialInstance) + bloom threshold-true + bloom strength 0.5. T722 confirms on Pixel 6 / iPhone 12 that the sun blooms cleanly across the joystick range + 4 lighting presets while the Moon's lit edge stays sharp; adjusts the constants if either side leaks past the bloom threshold. Final tuned values are recorded in `results.md` (T741).
  - Hardware test: confirm sun blooms cleanly across the joystick range + 4 lighting presets; confirm Moon's lit edge does NOT bloom at any orientation. Adjust `SUN_EMISSIVE_INTENSITY` (T714 constant) up if the disc's bloom is faint, down if Moon highlights leak past threshold.
  - Document the final tuned value + the rationale in `results.md`.
  - _Requirements: FR-007 (clean separation), SC-003_

**Checkpoint**: the sun has a soft glowing halo; the Moon's lit edge stays sharp without bloom contamination across the full joystick + preset range.

---

## Phase 4: Settings toggles

- [x] **T730** [US4] Update `SettingsSheet` signature + add 2 `Switch` rows — sheet body rewritten from the 01-shell "Coming soon" placeholder to a "Celestial background" section with two toggle rows ("Show stars", "Show sun"). New params: `showStars / showSun: Boolean`, `onShowStarsChange / onShowSunChange: (Boolean) -> Unit`. Private `ToggleRow` composable holds the Material3 `Switch + Text` layout; reusable for any future settings group.
  - New params: `showStars: Boolean`, `showSun: Boolean`, `onShowStarsChange: (Boolean) -> Unit`, `onShowSunChange: (Boolean) -> Unit`.
  - Two `Row` entries inside the existing sheet, each with `Text(label) + Switch(checked, onCheckedChange)`. Material3 styling. Labels: "Show stars" and "Show sun".
  - _Requirements: FR-009_

- [x] **T731** [P] [US4] `MoonViewModelTest` coverage already landed in Phase 1 (T705: `setShowStars_defaultIsTrue` + `setShowStars_togglesState`) and Phase 2 (T715: `setShowSun_defaultIsTrue` + `setShowSun_togglesState`). The plan's idempotent-StateFlow tests are intentionally skipped — they'd test Kotlin Coroutines' StateFlow distinct-equals semantics rather than this code's contract; the existing 4 cases cover the API surface. Suite stays at **103 green**.
  - `setShowStars_togglesState`: default true → setShowStars(false) → state.showStars == false; setShowStars(true) again → state.showStars == true.
  - `setShowSun_togglesState`: same shape.
  - `setShowStars_idempotent`: calling setShowStars(true) when already true doesn't emit a new state value to the StateFlow (StateFlow's distinct-equals semantics).
  - `setShowSun_idempotent`: same shape.
  - _Requirements: SC-006_

- [x] **T732** [US4] Wire `SettingsSheet` in `MoonExplorerScreen` — the `SettingsSheet(...)` call site at the bottom of `MoonExplorerScreen` now passes `state.showStars` / `state.showSun` plus `viewModel::setShowStars` / `viewModel::setShowSun` callbacks. No new state holders at the screen level (direct setters; no animation pipeline, no `currentXxxJob` tracking).
  - The existing `SettingsSheet(...)` call site gains four new args bound to `state.showStars`, `state.showSun`, `viewModel::setShowStars`, `viewModel::setShowSun`.
  - No new state holders at the screen level — these are direct setters, not animated commands; no `currentXxxJob` tracking needed.
  - _Requirements: FR-009, FR-010_

**Checkpoint**: opening Settings shows two switches; flipping each one reflects in the renderer within one frame.

---

## Phase Final: Polish + tests + docs

- [ ] **T740** Run `:shared:testAndroidHostTest :shared:iosSimulatorArm64Test` — all suites green
  - Carryover tests (99 from 04-sun-control's Phase Final) + new `MoonViewModelTest` cases for `setShowStars` / `setShowSun` (~4 new cases). Target ~103 tests across 11 suites.
  - _Requirements: SC-006_

- [ ] **T741** [P] Write `ai-docs/specs/07-celestial-background/results.md`
  - Status by phase; user-confirmed items vs pending hardware confirmation (eclipse occlusion looks right, bloom tuning across presets, 60 FPS sustained on target devices, settings toggles within one frame).
  - Test counts table.
  - Deviations log: ADR-0004 amendment (ESO attribution), bundled-PNG cubemap not KTX2 (per ADR-0011), bloom always-on when sun-on (no separate flag), settings persistence deferred.
  - Final tuned values for `SUN_EMISSIVE_INTENSITY` + bloom `threshold` + bloom `strength` baked from T722's empirical tuning.
  - _Requirements: agent-runbook.md_

- [ ] **T742** [P] Cross-reference notes in 02-mvp + 04 results
  - 02-mvp `results.md` § References — add a forward-link to `07-celestial-background/results.md` as the spec that paid down "renderer's flat-black backdrop" (matching the pattern 03 used to forward-link from 01 / 02).
  - 04-sun-control `results.md` § References — same forward-link; 07 reuses 04's `state.sunDirection` as its sun-billboard input.
  - 01-app-shell `results.md` § Deviations / deferrals — strike through any "SettingsSheet placeholder" note if one exists; SettingsSheet finally gets real toggles in 07.
  - _Requirements: agent-runbook.md_

- [ ] **T743** [P] Amend ADR-0004 with star attribution
  - Add a paragraph mirroring ADR-0005's amendment style: § "Amendments" subsection (or a parallel attribution paragraph to the existing NASA SVS one). Date 2026-04-29; pointer back to this spec; ESO Milky Way Panorama by Serge Brunier (CC BY 4.0). The verbatim attribution string also lives in `AboutSheet.kt`.
  - _Requirements: FR-011_

**Final Checkpoint**: all four user stories' acceptance criteria pass on real devices; stars + sun + bloom render together without artifacts; settings toggles update within one frame; ADR-0004 amended; `results.md` filed; cross-refs updated.

---

## Dependencies & Execution Order

| From → To | Why |
|---|---|
| T701 → T702 | Bake step needs the source asset |
| T702 → T703 | Skybox loader needs the cubemap files in resources |
| T702 → T704 | Same on iOS |
| T703 / T704 → T705 | Per-frame attach/detach reads `state.showStars` |
| T710 → T711 | Build wiring needs the source file |
| T711 → T712 / T713 | Renderer needs the baked `sun.filamat` |
| T712 / T713 → T714 | Per-frame transform needs the entity to exist |
| T712 / T713 → T715 | Per-frame attach/detach reads `state.showSun` |
| T714 + T715 → T720 / T721 | Bloom config tracks `state.showSun` |
| T720 / T721 → T722 | Tuning can't happen until bloom is wired |
| T705 + T715 → T730 | SettingsSheet binds to both flags |
| T730 → T731 | Tests exercise the viewmodel methods the sheet calls |
| T730 → T732 | Screen wiring uses the new sheet signature |
| Phases 1–4 → Phase Final | Everything wired before final tests + results |

## Parallel Examples

- **Phase 1**: T701 + T702 are sequential (asset → bake), but T703 + T704 are parallel (different platforms touching different files).
- **Phase 2 + Phase 3**: Thread A = Android (T712 → T720). Thread B = iOS (T713 → T721). Both need T714 / T715 already in. T722 (tuning) is hardware-bound and waits on both threads.
- **Phase Final**: T741, T742, T743 are independent doc edits — safe in parallel.

## Implementation Strategy

- **Two PRs**, mirroring the 02-mvp / 03 / 04 split:
  - **PR 1**: Phase 1 + Phase 2 — assets, skybox, sun Renderable. Stars + flat sun visible. Roughly half the spec's volume.
  - **PR 2**: Phase 3 + Phase 4 + Phase Final — bloom tuning, Settings toggles, results.md + ADR amendment.
- **Asset bake (T701 + T702) lands in the PR 1 commit, not a separate "asset" commit.** The cubemap PNGs are version-controlled with the rest of the bundle (per ADR-0004's bundled-tier strategy); landing them with the consumer code keeps the diff coherent.
- **T722 (bloom tuning) is hardware-bound.** The PR 2 commit lands an initial guess for `SUN_EMISSIVE_INTENSITY` + bloom values; tuning happens in a follow-up commit on the same branch with the on-device measurements documented in `results.md`.

## Notes

- **No new Gradle deps.** `compileMaterials` exists; Filament's `Skybox`, `Material.Builder`, and `BloomOptions` come from the existing pod (ADR-0008).
- **Cubemap face order** is fixed by Filament — `[+X, -X, +Y, -Y, +Z, -Z]`. Don't get cute with naming; use `px / nx / py / ny / pz / nz` so the loader iteration is mechanical.
- **`SUN_DISTANCE = 1000` is far past `MAX_DIST = 20`.** The sun is always behind anything camera-relevant; depth-test handles the eclipse case for free.
- **Bloom always-on when sun-on.** No separate `showSunBloom` flag — one mental model: showing the sun = the sun glows. If hardware perf testing shows bloom is the problem on a specific device, file a follow-up; don't pre-emptively split the flag.
- **Settings persistence** (DataStore-backed `showStars` / `showSun` survive app restarts) is **out of scope**. One-task follow-up if anyone asks.
- **No new ADRs.** Only ADR-0004 gets a paragraph-level amendment for the star attribution. Same shape as 04-sun-control's ADR-0005 `durationMs` amendment.
- **Earth is deliberately excluded.** A future spec can add Earth as an actual `Renderable` lit by the same `state.sunDirection`; the depth-test occlusion behavior would be automatically correct (Earthrise from far-side flyovers would just work). Out of scope here to keep this spec focused.
