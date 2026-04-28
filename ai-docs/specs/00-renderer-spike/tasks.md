# Tasks: 00 — Renderer Spike

## Format: `[ID] [P?] [US?] Description`

`[P]` = parallel-safe with sibling tasks. `[US#]` = which user story this serves.
Acceptance criteria for each user story live in `spec.md`.

## Path conventions

All paths relative to `MoonExplorer/` repo root.

---

## Phase 1: Setup

- [ ] **T001** [P] Add Filament version + library aliases to `gradle/libs.versions.toml`
  - `filament = "1.71.x"` under `[versions]` (pin exact when verified)
  - `filament-android` and `filament-utils-android` under `[libraries]`
  - _Requirements: ADR-0001, ADR-0002, tech-stack.md_

- [ ] **T002** Add Filament deps to `:shared/androidMain.dependencies` in `shared/build.gradle.kts`
  - `implementation(libs.filament.android)`
  - `implementation(libs.filament.utils.android)`
  - _Requirements: ADR-0001, FR-005_

- [ ] **T003** [P] Vendor `matc` binary under `tools/matc/`
  - Download from Filament release tarball matching the pinned version
  - Include `LICENSE` note (Apache 2.0)
  - _Requirements: ADR-0001, FR-005_

- [ ] **T004** Add `compileMaterials` Gradle `Exec` task to `shared/build.gradle.kts`
  - Reads: `shared/src/commonMain/composeResources/files/materials/moon.mat`
  - Outputs: `shared/build/generated/filamat/moon.filamat`, then copies into `composeResources/files/materials/`
  - Wired as a dependency of Compose resource processing
  - _Requirements: FR-005_

- [ ] **T005** Verify `Filament.podspec` simulator-arm64 status
  - Read [`Filament.podspec`](https://github.com/google/filament/blob/main/ios/CocoaPods/Filament.podspec) directly
  - Document outcome in `ai-docs/decisions/0002-filament-ios-distribution.md` under a new "## Verification — YYYY-MM-DD" section
  - If sim-arm64 missing: pick mitigation (build-from-source / Rosetta / device-only); file follow-up ADR if needed
  - _Requirements: ADR-0002, SC-004_

- [ ] **T006** [P] Initialize `iosApp/Podfile` with `pod 'Filament', '~> 1.71.x'` (matching the pinned version from T001)
  - Run `pod install` in `iosApp/`; verify `iosApp.xcworkspace` is generated
  - Add `iosApp/Pods/` to `.gitignore` if not already
  - _Requirements: ADR-0002_

**Checkpoint**: `./gradlew :androidApp:assembleDebug` builds with Filament on classpath; `pod install` succeeds in `iosApp/`.

---

## Phase 2: Foundational (commonMain)

- [ ] **T010** [P] Implement `domain/Vec3.kt` (immutable, no platform types)
  - `data class Vec3(val x: Float, val y: Float, val z: Float)` plus `operator fun plus`, `times`, `dot`, `length`, `normalize`
  - _Requirements: ADR-0003_

- [ ] **T011** [P] Implement `domain/MoonMath.kt` (lat/lon ↔ Cartesian, camera position, lookAt-friendly helpers)
  - Per `ai-docs/research/selenographic-math-camera.md` §1, §2
  - East-positive longitude convention per ADR-0006
  - _Requirements: ADR-0006, FR-002_

- [ ] **T012** [US1] Implement `domain/UvSphere.kt` — procedural UV sphere generator (default 64×32 segments)
  - Outputs: positions, normals, tangents, uvs (Float-encoded `ByteArray`s little-endian); indices (UShort `ByteArray`)
  - UV mapping per ADR-0006 §"Texture mapping": `u = lon/360 + 0.5`, `v = 0.5 - lat/180`
  - _Requirements: FR-001, FR-005_

- [ ] **T013** [US1] Implement `state/MoonRenderState.kt` (data class with defaults)
  - Camera defaults to `(yaw=0, pitch=0, distance=5)` — looking at +Z near-side
  - Sun defaults to `(0, 0, 1)` — full-Moon-like
  - _Requirements: ADR-0003_

- [ ] **T014** [US1] Implement `state/MoonViewModel.kt` with `onDrag`, `onPinch`, `setSunDirection`
  - `onDrag(dxPx, dyPx, viewportH, fovY)` per `selenographic-math-camera.md` §4
  - `onPinch(scale)` per §3 (exponential), clamped `[1.5, 20]`
  - `setSunDirection(Vec3)` simply replaces sun
  - _Requirements: FR-002, FR-003, FR-004_

- [ ] **T015** [US1] Declare `@Composable expect fun MoonViewport(state, modifier)` in `render/MoonViewport.kt`
  - _Requirements: ADR-0003_

- [ ] **T016** [US1] Replace `App()` body with `MoonExplorerScreen` Composable
  - Hosts `MoonViewport(state, Modifier.fillMaxSize().pointerInput { ... })`
  - Holds the `MoonViewModel` via `remember { MoonViewModel() }` for the spike (DI in Phase 1 proper)
  - Removes the wizard "Click me!" `Greeting` UI
  - _Requirements: FR-001_

- [ ] **T017** [US3] Implement `ui/SunControl.kt` — single-axis slider mapping `[-1, 1]` → sun direction X
  - Wires through `viewModel.setSunDirection(Vec3(x, 0f, sqrt(max(0f, 1f - x*x))))` (hemisphere lift; full joystick is `04-sun-control`)
  - _Requirements: FR-004_

### Tests for Phase 2

- [ ] **T020** [P] [US1] `commonTest`: `MoonMathTest.latLonToCartesian`
  - Cases: (0,0)→(0,0,1); (0,90)→(1,0,0); (90,0)→(0,1,0); Apollo 11 (0.674,23.473)→(0.398,0.012,0.917) within 1e-3
  - _Requirements: ADR-0006, FR-001_

- [ ] **T021** [P] [US1] `commonTest`: `UvSphereTest.generate`
  - Vertex count = (segments+1)·(rings+1); `|p|` ≈ 1.0 ±1e-5 for all positions; tangent⊥normal
  - _Requirements: FR-001_

- [ ] **T022** [P] [US1] `commonTest`: `MoonViewModelTest.onPinchClampsDistance`
  - Pinching repeatedly never goes below 1.5 or above 20
  - _Requirements: FR-003_

- [ ] **T023** [P] [US1] `commonTest`: `MoonViewModelTest.onDragUpdatesYawAndPitch`
  - Drag right → yaw decreases; drag down → pitch increases; pitch clamped at ±89°
  - _Requirements: FR-002_

**Checkpoint**: `./gradlew :shared:allTests` passes. App builds but `MoonViewport` `actual` is unimplemented — won't run yet on either platform.

---

## Phase 3: User Story 1 — See a 3D Moon on launch (P1) — MVP

### Implementation for User Story 1 (assets + Android)

- [ ] **T030** [US1] Author `materials/moon.mat` source
  - PBR with albedo + normal map; directional light support; sRGB albedo, linear normal
  - Material parameter inputs: `albedo` (sampler2D, sRGB), `normalMap` (sampler2D, linear)
  - _Requirements: FR-005_

- [ ] **T031** [US1] Generate placeholder `textures/moon_albedo_2k.ktx2` and `textures/moon_normal_2k.ktx2`
  - Use a recognizable test pattern (lat/lon grid + color quadrants) for the spike — easier to verify rotation/lighting than a real Moon
  - `toktx --t2 --bcmp --genmipmap --assign_oetf srgb` for albedo
  - `toktx --t2 --uastc 2 --uastc_rdo_l 1.0 --zcmp 18 --genmipmap --assign_oetf linear` for normal
  - _Requirements: FR-001, FR-008, ADR-0004_

- [ ] **T032** [US1] Implement `androidMain/render/MoonHost.kt`
  - Manages Filament `Engine`, `SwapChain`, `Renderer`, `View`, `Scene`, `Camera`, `UiHelper`, `Material`, `MaterialInstance`, `Texture`s, `VertexBuffer`+`IndexBuffer` from `UvSphere`
  - Loads bytes via `Res.readBytes("files/materials/moon.filamat")` etc.
  - Implements `LifecycleObserver`: `onResume` posts Choreographer frame callback; `onPause` removes it
  - `destroy()` releases all resources in reverse order per `filament-cmp-integration.md` §1
  - _Requirements: FR-006, FR-007, FR-008, ADR-0003_

- [ ] **T033** [US1] Implement `androidMain/render/MoonViewport.android.kt`
  - `AndroidView { SurfaceView(ctx).also { sv -> attach MoonHost; lifecycle.addObserver(host) } }`
  - `onRelease { host.destroy() }`
  - `update = { /* host reads state.value per frame, no push needed */ }`
  - Per Choreographer frame: read `state.value`, push camera + sun + transforms, `renderer.beginFrame/render/endFrame`
  - _Requirements: FR-001, FR-002, FR-003, FR-004, FR-006, FR-007, ADR-0003_

**Checkpoint (Android)**: `./gradlew :androidApp:installDebug` then launch on Pixel 6 — see a 3D textured sphere within 2 seconds. SC-001 partially met.

### Implementation for User Story 1 (iOS)

> Block on T005 verification before starting these tasks.

- [ ] **T034** [US1] Author `iosApp/iosApp/MoonRendererBridge.h` — Objective-C interface
  - Methods: `init`, `setCamera(yaw, pitch, distance)`, `setSunDirection(x, y, z)`, `loadAssets(albedoBytes, normalBytes, materialBytes, sphereVertices, sphereIndices)`, `pause`, `resume`, `dispose`
  - All types are ObjC-compatible (`NSData`, `float`, `void`)
  - _Requirements: ADR-0002_

- [ ] **T035** [US1] Implement `iosApp/iosApp/MoonRendererBridge.mm` — Objective-C++ wrapper around Filament's C++ API
  - C++ `<filament/...>` includes hidden behind ObjC interface so Swift sees only ObjC types
  - Mirrors the Filament object graph from §1 of `filament-cmp-integration.md`
  - _Requirements: ADR-0002_

- [ ] **T036** [US1] Implement `iosApp/iosApp/MoonRendererView.swift` — `UIView` subclass with `+ (Class)layerClass = CAMetalLayer`
  - `initializeMetalLayer()` sets pixel format `MTLPixelFormatBGRA8Unorm` and drawable size
  - _Requirements: FR-001, ADR-0002_

- [ ] **T037** [US1] Implement `iosApp/iosApp/MoonRendererViewController.swift`
  - Hosts `MoonRendererView`, owns the `MoonRendererBridge`
  - On `viewDidLoad`: calls `bridge.init` with `view.layer`, `bridge.loadAssets(...)`
  - Drives a `CADisplayLink` render loop; reads StateFlow snapshots via the Kotlin/Native bridge each frame; calls `bridge.setCamera/setSunDirection`
  - On `viewWillDisappear` / `dealloc`: `bridge.dispose()`
  - _Requirements: FR-001, FR-002, FR-003, FR-004, FR-006, FR-007_

- [ ] **T038** [US1] Implement `iosMain/render/MoonViewport.ios.kt`
  - `UIKitViewController { factory = { MoonRendererViewController() } }`
  - `update = { vc -> /* push state via bridge — but the VC reads StateFlow itself, so this is a no-op or just a `setNeedsDisplay()` */ }`
  - _Requirements: FR-001, ADR-0003_

**Checkpoint (iOS)**: `./gradlew :shared:embedAndSignAppleFrameworkForXcode` then build+run from Xcode workspace on iPhone 12 — see the same 3D textured sphere. SC-001 fully met.

---

## Phase 4: User Story 2 — Direct manipulation gestures (P1)

- [ ] **T040** [US2] Add `Modifier.pointerInput { detectTransformGestures { ... } }` in `MoonExplorerScreen` driving `viewModel.onDrag(dx, dy, ...)` and `viewModel.onPinch(zoom)`
  - Read viewport size via `Modifier.onSizeChanged` to provide `viewportH` to `onDrag`
  - _Requirements: FR-002, FR-003_

- [ ] **T041** [US2] Verify on Pixel 6 + iPhone 12: drag rotates Moon smoothly; pinch zooms; clamps work; 60 FPS sustained
  - _Requirements: FR-002, FR-003, SC-002_

**Checkpoint**: gestures work on both platforms. Visual smoothness verified at 60 FPS via on-device profiler (Android Studio Profiler / Xcode Frame Capture).

---

## Phase 5: User Story 3 — Adjust sun direction (P2)

- [ ] **T050** [US3] Hook `SunControl` slider to `viewModel.setSunDirection`
  - Verify lighting visibly shifts within one frame on both platforms
  - _Requirements: FR-004_

**Checkpoint**: dragging the sun slider visibly moves the lit/unlit terminator across the Moon.

---

## Phase 6: User Story 4 — Asset swap test (P3)

- [ ] **T060** [US4] Add a debug toggle in `MoonExplorerScreen` (e.g., long-press or developer menu) to switch between two bundled placeholder textures
  - Bundle a second placeholder texture: `textures/moon_albedo_2k_alt.ktx2` (different test pattern)
  - _Requirements: ADR-0004_

- [ ] **T061** [US4] Verify both textures render correctly on both platforms without renderer restart
  - _Requirements: FR-005, ADR-0004_

**Checkpoint**: asset pipeline confirmed end-to-end.

---

## Phase Final: Polish & Documentation

- [ ] **T090** Document any deviations from ADRs in either a follow-up ADR or an update to `architecture.md` / `tech-stack.md`
  - _Requirements: agent-runbook.md_

- [ ] **T091** Update `ai-docs/decisions/0002-filament-ios-distribution.md` with the verification outcome from T005
  - Append a "## Verification — YYYY-MM-DD" section
  - _Requirements: ADR-0002, SC-004_

- [ ] **T092** Add `iosApp/README.md` with `pod install` + Xcode build instructions
  - _Requirements: agent-runbook.md_

- [ ] **T093** Smoke-test on real Pixel 6 + iPhone 12; record FPS, any visible issues, and resolution outcomes in `ai-docs/specs/00-renderer-spike/results.md`
  - _Requirements: SC-002, SC-003_

**Final Checkpoint**: all four user stories' acceptance criteria pass on real devices. ADR-0002 verification complete. `results.md` filed.

---

## Dependencies & Execution Order

| From → To | Why |
|---|---|
| T001 → T002, T006 | Versions defined first |
| T003 → T004 | matc binary present before the Gradle task that runs it |
| T005 → T034..T038 | Verify simulator-arm64 before iOS-side work begins |
| Phase 1 → Phase 2 | Build infrastructure before code |
| Phase 2 → Phase 3 | Interfaces defined before implementations |
| Phase 3 (Android) ⊥ Phase 3 (iOS) | Once T030+T031 land, two engineers can work in parallel |
| Phase 3 → Phase 4, 5, 6 | US1 (MVP) ships before subsequent stories |
| Phase 6 → Phase Final | All work done before polish |

## Parallel Example: Phase 3 (after T030 + T031 land)

Two agents in parallel:
- **Agent A (Android)**: T032 → T033 → Checkpoint (Android)
- **Agent B (iOS)**: T034 → T035 → T036 → T037 → T038 → Checkpoint (iOS)

The shared state contract (`MoonRenderState`, `MoonViewport`) is locked by Phase 2.

## Implementation Strategy

- Land Phase 1 + 2 in a single PR titled "00-renderer-spike: setup + foundational".
- Land Phase 3 (Android) + Phase 3 (iOS) in a second PR titled "00-renderer-spike: US1 MVP".
- Land Phase 4, 5, 6 as one PR titled "00-renderer-spike: gestures + sun + asset swap".
- Land Phase Final as one final PR titled "00-renderer-spike: polish + docs".
- Final squash-merge to `main` with an ADR-summary commit.

## Notes

- This is a **spike**. We pay down debt later: real NASA textures (`02-moon-renderer-mvp`), real sun joystick (`04-sun-control`), site markers + fly-to (`01-app-shell` + `03-sites-and-flyto`), polish (`05-polish`), Koog (`06-koog-agent`).
- If anything in Phase 3 takes more than 2 days per platform, **stop and reconsider the route**. ADR-0002's "Risk and Fallback" section lists alternatives (route a/b/c/d).
- Don't relax acceptance criteria silently. If FR-001 (2-second startup) is missed, raise it — don't quietly stretch the budget.
