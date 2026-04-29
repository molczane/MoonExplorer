# Tasks: 07 — Celestial Background

## Format: `[ID] [P?] [US?] Description`

`[P]` = parallel-safe with sibling tasks. `[US#]` = which user story this serves.
Acceptance criteria for each user story live in `spec.md`.

## Path conventions

All paths relative to `MoonExplorer/` repo root. Task IDs are namespaced **T700+** to avoid collision with `00-renderer-spike` (T001–T093), `02-moon-renderer-mvp` (T100–T145), `01-app-shell` (T200–T232), `03-sites-and-flyto` (T301–T342), and `04-sun-control` (T410–T452). The 700 range leaves the 500 / 600 spans for the existing skeleton placeholders (`05-polish` and `06-koog-agent`) when they're picked up.

---

## Phase 1: Stars (skybox)

- [ ] **T701** [P] [US1] Acquire + verify Milky Way Panorama asset
  - Source: ESO press release 0932 — Milky Way Panorama by Serge Brunier. Confirm CC BY 4.0 license terms against the original press release page; capture the canonical attribution string ("ESO/S. Brunier") for ADR-0004's amendment + AboutSheet.
  - Fallback: NASA Tycho Star Catalog skymap (public domain) if ESO licensing isn't unambiguous.
  - Output: a single equirectangular PNG (4096×2048 or similar), staged outside the repo for the bake step in T702.
  - _Requirements: NFR (asset attribution), FR-011_

- [ ] **T702** [P] [US1] Bake equirectangular → 6-face cubemap
  - Convert with cmft / AMD Cubemapgen / Blender / Python+PIL — pick whichever produces seamless edges; document the tool + version used in `results.md`.
  - Output: 6 PNG files at 1024×1024 each, sRGB 8-bit. Naming: `{px,nx,py,ny,pz,nz}.png` matching Filament's `[+X, -X, +Y, -Y, +Z, -Z]` face enum order.
  - Bundle path: `shared/src/commonMain/composeResources/files/stars/`
  - Total bundled size ≤ 6 MB.
  - _Requirements: FR-001, NFR (bundle size)_

- [ ] **T703** [US1] [Android] Skybox setup in `MoonHost.kt`
  - On init: load 6 PNGs via `BitmapFactory.decodeStream` (matches the existing 2K Moon-texture path); build a Filament cubemap `Texture` (`TextureSampler.SamplerType.SAMPLER_CUBEMAP`); upload each face via `setImage(level=0, faceOffset)`; build a `Skybox.Builder().environment(texture).build(engine)`; on per-frame `if (state.showStars && lastSkyboxState != true)`: `scene.setSkybox(skybox)`; flip-side: `scene.setSkybox(null)`.
  - Cache the `Skybox` instance — recreating it every frame is wasteful.
  - Track the previous `showStars` value to avoid redundant `setSkybox` calls.
  - _Requirements: FR-001, FR-002_

- [ ] **T704** [US1] [iOS] Skybox setup in `MoonRenderer.mm`
  - Same dance in Obj-C++: 6 `CGImageSource`-decoded PNGs → cubemap `Texture` → `Skybox::Builder().environment(...).build(engine)` → `scene->setSkybox(...)`.
  - Verify `Skybox` symbol resolves from `Filament/filament` subspec (ADR-0008) — `nm libfilament.a | grep -i Skybox` if there's any doubt.
  - _Requirements: FR-001, FR-002_

- [ ] **T705** [US1] `MoonRenderState.showStars` + `MoonViewModel.setShowStars`
  - Add `showStars: Boolean = true` to `MoonRenderState`.
  - Add `fun setShowStars(value: Boolean) { _state.update { it.copy(showStars = value) } }` to `MoonViewModel`.
  - Both renderer hosts read `state.showStars` per frame and conditionally `setSkybox(...)`.
  - _Requirements: FR-002, FR-009 (partial), FR-010 (partial)_

**Checkpoint**: launching the app on simulator shows the Moon against the Milky Way panorama; the camera pans through the celestial sphere with parallax-correct stars; `state.showStars = false` makes the stars vanish.

---

## Phase 2: Sun disc

- [ ] **T710** [US2] Author `materials/sun.mat`
  - Unlit shading model, single `intensity: float` parameter, fragment outputs `baseColor = vec4(intensity, intensity, intensity, 1.0)`.
  - Source per `plan.md` § "Sun material".
  - _Requirements: FR-003_

- [ ] **T711** [US2] Wire `sun.mat` into `compileMaterials` Gradle task
  - The task already compiles the Moon material; check whether it globs `materials/*.mat` (zero-line change) or names files explicitly (one-line addition).
  - Confirm `sun.filamat` lands in `composeResources/files/` next to the Moon's `.filamat`.
  - _Requirements: FR-003_

- [ ] **T712** [US2] [Android] Sun Renderable in `MoonHost.kt`
  - Build a 1×1 quad mesh procedurally (4 verts, 2 tris). Load `sun.filamat` via `Material.Builder().payload(filamatBytes).build(engine)`. Create `MaterialInstance`; set `intensity = SUN_EMISSIVE_INTENSITY` (~5–10, tunable in T722). Build a `Renderable` Entity; cache the entity + transform manager instance.
  - Add to scene only when `state.showSun = true` (mirror the skybox attach/detach pattern from T703).
  - _Requirements: FR-004, FR-006_

- [ ] **T713** [US2] [iOS] Sun Renderable in `MoonRenderer.mm`
  - Same in Obj-C++: load `sun.filamat`, build quad mesh, build Renderable, scene attach/detach.
  - _Requirements: FR-004, FR-006_

- [ ] **T714** [US2] [P] Per-frame sun transform — both platforms
  - Read `state.sunDirection`. Compute `sunPos = sunDir · SUN_DISTANCE` (1000.0). Compute billboard rotation via `lookAt(sunPos, cameraPos, Vec3.UP)`. Compute scale: `2 * SUN_DISTANCE * tan(SUN_ANGULAR_DIAMETER_RAD / 2) ≈ 9.1` units. Apply via `transformManager.setTransform(sunInstance, mat)`.
  - Constants live next to existing renderer constants — `SUN_DISTANCE: Float = 1000f`, `SUN_ANGULAR_DIAMETER_RAD: Float = 0.0091f`, `SUN_EMISSIVE_INTENSITY: Float = 8f` (placeholder; T722 tunes).
  - _Requirements: FR-005_

- [ ] **T715** [US2] `MoonRenderState.showSun` + `MoonViewModel.setShowSun`
  - Add `showSun: Boolean = true` to `MoonRenderState`.
  - Add `fun setShowSun(value: Boolean) { _state.update { it.copy(showSun = value) } }` to `MoonViewModel`.
  - _Requirements: FR-006, FR-010 (partial)_

**Checkpoint**: with stars on + sun on, dragging the joystick / tapping a preset moves a flat white disc across the starfield. No glow yet — that's Phase 3.

---

## Phase 3: Bloom

- [ ] **T720** [US3] [Android] `view.setBloomOptions(...)` in `MoonHost.kt`
  - On init or per-frame (whichever Filament prefers — usually init + reconfigure on toggle): construct `BloomOptions { enabled = state.showSun; strength = 0.5f; resolution = 360; levels = 6; threshold = true; blendMode = ADD }`. Call `view.setBloomOptions(options)`.
  - _Requirements: FR-007, FR-008_

- [ ] **T721** [US3] [iOS] `view->setBloomOptions(...)` in `MoonRenderer.mm`
  - Same struct, same values. Filament's iOS API is C++ matching the Java binding 1:1.
  - _Requirements: FR-007, FR-008_

- [ ] **T722** [US3] Tune sun emissive intensity vs bloom threshold — empirical
  - Hardware test: confirm sun blooms cleanly across the joystick range + 4 lighting presets; confirm Moon's lit edge does NOT bloom at any orientation. Adjust `SUN_EMISSIVE_INTENSITY` (T714 constant) up if the disc's bloom is faint, down if Moon highlights leak past threshold.
  - Document the final tuned value + the rationale in `results.md`.
  - _Requirements: FR-007 (clean separation), SC-003_

**Checkpoint**: the sun has a soft glowing halo; the Moon's lit edge stays sharp without bloom contamination across the full joystick + preset range.

---

## Phase 4: Settings toggles

- [ ] **T730** [US4] Update `SettingsSheet` signature + add 2 `Switch` rows
  - New params: `showStars: Boolean`, `showSun: Boolean`, `onShowStarsChange: (Boolean) -> Unit`, `onShowSunChange: (Boolean) -> Unit`.
  - Two `Row` entries inside the existing sheet, each with `Text(label) + Switch(checked, onCheckedChange)`. Material3 styling. Labels: "Show stars" and "Show sun".
  - _Requirements: FR-009_

- [ ] **T731** [P] [US4] `MoonViewModelTest` extensions
  - `setShowStars_togglesState`: default true → setShowStars(false) → state.showStars == false; setShowStars(true) again → state.showStars == true.
  - `setShowSun_togglesState`: same shape.
  - `setShowStars_idempotent`: calling setShowStars(true) when already true doesn't emit a new state value to the StateFlow (StateFlow's distinct-equals semantics).
  - `setShowSun_idempotent`: same shape.
  - _Requirements: SC-006_

- [ ] **T732** [US4] Wire `SettingsSheet` in `MoonExplorerScreen`
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
