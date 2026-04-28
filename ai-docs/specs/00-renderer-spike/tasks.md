# Tasks: 00 — Renderer Spike

## Format: `[ID] [P?] [US?] Description`

`[P]` = parallel-safe with sibling tasks. `[US#]` = which user story this serves.
Acceptance criteria for each user story live in `spec.md`.

## Path conventions

All paths relative to `MoonExplorer/` repo root.

---

## Phase 1: Setup

- [x] **T001** [P] Add Filament version + library aliases to `gradle/libs.versions.toml`
  - `filament = "1.71.1"` under `[versions]`
  - `filament-android` and `filament-utils-android` under `[libraries]`
  - _Requirements: ADR-0001, ADR-0002, tech-stack.md_

- [x] **T002** Add Filament deps to `:shared/androidMain.dependencies` in `shared/build.gradle.kts`
  - `implementation(libs.filament.android)`
  - `implementation(libs.filament.utils.android)`
  - Verified: `./gradlew :shared:dependencies --configuration androidCompileClasspath` resolves Filament + transitive `gltfio-android`. `./gradlew :androidApp:assembleDebug` packages `libfilament-jni.so`, `libfilament-utils-jni.so`, `libgltfio-jni.so`.
  - _Requirements: ADR-0001, FR-005_

- [x] **T003** [P] ~~Vendor `matc` binary under `tools/matc/`~~ → **Download on demand** (revised 2026-04-28 with user approval)
  - **Deviation from original spec**: vendoring ~60 MB of matc binaries to git was deemed too costly. Replaced with a `downloadFilamentTools` Gradle task that fetches matc for the host OS (Mac/Linux) into `tools/matc/<version>/<os>/matc` on first build. The cache directory is gitignored (`tools/matc/`).
  - Filament version is pinned in `gradle/libs.versions.toml`; that single source drives both the Maven Android deps and the matc download URL.
  - Implementation: `:shared/build.gradle.kts` `downloadFilamentTools` task (group `filament`).
  - Tarball is downloaded from `https://github.com/google/filament/releases/download/v<version>/filament-v<version>-<os>.tgz`, only `filament/bin/matc` is extracted, the rest is discarded. Idempotent via `onlyIf { !matc.exists() }`.
  - Verified: `./gradlew :shared:downloadFilamentTools` produces `tools/matc/1.71.1/mac/matc` (~11 MB), executable, `--help` works.
  - _Requirements: ADR-0001, FR-005_

- [x] **T004** Add `compileMaterials` Gradle `Exec` task to `shared/build.gradle.kts`
  - Reads: `shared/src/commonMain/composeResources/files/materials/moon.mat`
  - Outputs: `shared/build/generated/filamat/moon.filamat`, then copies into `composeResources/files/materials/`
  - Wired as a dependency of `processAndroidMainResources*`, `syncComposeResourcesForIos*`, and `generateComposeResClass`. `onlyIf { moon.mat exists }` makes it a no-op until T030 creates the source file.
  - Depends on `downloadFilamentTools` so matc is fetched before first run.
  - _Requirements: FR-005_

- [ ] **T005** ~~Verify `Filament.podspec` simulator-arm64 status~~ ✅ Resolved 2026-04-28
  - **Outcome**: arm64 simulator is excluded by the podspec (`EXCLUDED_ARCHS[sdk=iphonesimulator*] = arm64`). Mitigation = Rosetta on Apple Silicon Macs. See ADR-0002 §"Verification".
  - No further action; this slot remains in the task list as a checkpoint history.

- [x] **T006** [P] Initialize `iosApp/Podfile` with the locked Filament subspecs
  - [x] `pod 'Filament/filament'` and `pod 'Filament/ktxreader'` — authored
  - [x] `iosApp/Pods/` already gitignored via `**/Pods/`
  - [x] **Discovery 2026-04-28**: `pod install` failed because Filament's CocoaPods trunk publishing stops at 1.69.3 (1.70.x+ is on GitHub but never `pod trunk push`ed). Switched to `:podspec => '<raw github URL at v1.71.1 tag>'` form keeping the Android+iOS version pinned at 1.71.1. See **ADR-0008**.
  - [x] **`pod install` verified by user 2026-04-28**: "Fetching podspec for `Filament` from `https://raw.githubusercontent.com/google/filament/v1.71.1/ios/CocoaPods/Filament.podspec` ... Installing Filament (1.71.1) ... Pod installation complete! There are 2 dependencies from the Podfile and 1 total pod installed." `iosApp.xcworkspace` generated.
  - _Requirements: ADR-0002, ADR-0008_

- [x] **T007** [P] Pre-Phase-0 smoke test of `Shared.framework` inside the new CocoaPods workspace
  - [x] **CocoaPods integration verified 2026-04-28**: `pod install` integrated `Shared.framework`'s existing `embedAndSignAppleFrameworkForXcode` build phase into the new `iosApp.xcworkspace` cleanly. CocoaPods reports: "Please close any current Xcode sessions and use `iosApp.xcworkspace` for this project from now on."
  - [ ] **Optional follow-up** (user discretion, not blocking Phase 2): open `iosApp.xcworkspace` in Xcode and confirm the existing wizard "Click me!" CMP shell still launches on simulator (Rosetta) / device. Phase 3 iOS work (T037–T040) will exercise this code path natively, so a separate visual confirmation here is belt-and-braces, not load-bearing.
  - _Requirements: ADR-0002_

**Checkpoint**: `./gradlew :androidApp:assembleDebug` builds with Filament on classpath; `pod install` succeeds in `iosApp/`; existing CMP shell still runs on iOS via the workspace.

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

### Implementation for User Story 1 (iOS — closure-injection bridge)

> Block on T005 verification (already resolved) and T007 smoke test before starting.
> See **ADR-0002 §"Bridge pattern: closure injection from Swift"** for the design.

- [ ] **T034** [US1] Implement `iosMain/render/MoonRendererProvider.kt`
  - Kotlin `object` (singleton) in `:shared/iosMain` with mutable closure properties:
    - `var factory: () -> UIViewController = { UIViewController() }`
    - `var applyCamera: (yawRad: Float, pitchRad: Float, distance: Float) -> Unit`
    - `var applySunDirection: (x: Float, y: Float, z: Float) -> Unit`
    - `var applyMoonRotation: (rotationRad: Float) -> Unit`
    - `var applyAssets: (albedo: ByteArray, normal: ByteArray, material: ByteArray) -> Unit`
    - `var dispose: () -> Unit`
  - All defaults are no-ops or `{ UIViewController() }` so commonMain works without iOS-app wiring (e.g., Compose Previews, tests)
  - _Requirements: ADR-0002 §"Bridge pattern", ADR-0003_

- [ ] **T035** [US1] Implement `iosMain/render/MoonViewport.ios.kt`
  - `actual @Composable fun MoonViewport(state: MoonRenderState, modifier: Modifier)`
  - `val vc = remember { MoonRendererProvider.factory() }`
  - `UIKitViewController(factory = { vc }, update = { ... }, onRelease = { MoonRendererProvider.dispose() }, modifier = modifier)`
  - In `update`: forward `state.cameraYawRad/pitchRad/distance` to `applyCamera`; `state.sunDirection.{x,y,z}` to `applySunDirection`; `state.moonRotationRad` to `applyMoonRotation`
  - _Requirements: FR-001, FR-002, FR-003, FR-004, FR-006, FR-007, ADR-0002, ADR-0003_

- [ ] **T036** [US1] Implement `iosMain/render/MoonAssets.kt`
  - `suspend fun loadAndPushBundledAssets()` reads:
    - `Res.readBytes("files/materials/moon.filamat")`
    - `Res.readBytes("files/textures/moon_albedo_2k.ktx2")`
    - `Res.readBytes("files/textures/moon_normal_2k.ktx2")`
  - Calls `MoonRendererProvider.applyAssets(albedo, normal, material)`
  - Top-level function so Swift sees it as `MoonAssetsKt.loadAndPushBundledAssets()`
  - _Requirements: FR-001, FR-008, ADR-0004_

- [ ] **T037** [US1] Author `iosApp/iosApp/MoonRenderer.h` — Objective-C interface (internal to iosApp; **not** exposed to Kotlin)
  - Methods: `init`, `setCameraYaw:pitch:distance:`, `setSunDirectionX:y:z:`, `setMoonRotation:`, `loadAssetsAlbedo:normal:material:`, `pause`, `resume`, `dispose`
  - All types ObjC-compatible (`NSData`, `float`, `void`)
  - _Requirements: ADR-0002_

- [ ] **T038** [US1] Implement `iosApp/iosApp/MoonRenderer.mm` — Objective-C++ wrapping Filament's C++ API
  - `<filament/Engine.h>` etc. hidden behind ObjC interface so Swift sees only ObjC types
  - Mirrors the Filament object graph from `ai-docs/research/filament-cmp-integration.md` §1
  - Engine creation, scene setup, render loop coupling, resource teardown
  - _Requirements: ADR-0001, ADR-0002, FR-006_

- [ ] **T039** [US1] Implement `iosApp/iosApp/MoonRendererView.swift` and `MoonRendererViewController.swift`
  - **`MoonRendererView`**: `UIView` subclass with `+ (Class)layerClass = CAMetalLayer`. `initializeMetalLayer()` sets pixel format `MTLPixelFormatBGRA8Unorm` and drawable size from bounds × contentScaleFactor.
  - **`MoonRendererViewController`**: `UIViewController` hosting a `MoonRendererView`, owns a `MoonRenderer` instance. On `viewDidLoad`: call `renderer.init` with `view.layer`. Drives a `CADisplayLink` render loop calling `renderer` methods. Public methods exposed to Swift callers (used by `iOSApp.swift`'s closure wiring): `setCamera(yaw:pitch:distance:)`, `setSunDirection(x:y:z:)`, `setMoonRotation(_:)`, `loadAssets(albedo:normal:material:)`, `tearDown()`. On `viewWillDisappear`: pause CADisplayLink. On `dealloc`: `renderer.dispose()`.
  - _Requirements: FR-001, FR-002, FR-003, FR-004, FR-006, FR-007, ADR-0002_

- [ ] **T040** [US1] Wire `MoonRendererProvider` closures from `iosApp/iosApp/iOSApp.swift`
  - Add `KotlinByteArray+Data.swift` extension converting `KotlinByteArray` → `Data`.
  - In `iOSApp.init()`:
    - Create one `MoonRendererViewController` instance.
    - Set `MoonRendererProvider.shared.factory = { renderer }` — captures the instance.
    - Set `applyCamera`/`applySunDirection`/`applyMoonRotation` to forward floats to the corresponding renderer methods.
    - Set `applyAssets = { albedo, normal, material in renderer.loadAssets(albedo: albedo.toData(), normal: normal.toData(), material: material.toData()) }`.
    - Set `dispose = { renderer.tearDown() }`.
    - Kick off `Task { await MoonAssetsKt.loadAndPushBundledAssets() }` to push bundled assets at startup.
  - _Requirements: FR-001, FR-008, ADR-0002, ADR-0003, ADR-0004_

**Checkpoint (iOS)**: `./gradlew :shared:embedAndSignAppleFrameworkForXcode` then build+run from Xcode workspace on iPhone 12 (or simulator under Rosetta) — see the same 3D textured sphere. SC-001 fully met.

---

## Phase 4: User Story 2 — Direct manipulation gestures (P1)

- [ ] **T041** [US2] Add `Modifier.pointerInput { detectTransformGestures { ... } }` in `MoonExplorerScreen` driving `viewModel.onDrag(dx, dy, ...)` and `viewModel.onPinch(zoom)`
  - Read viewport size via `Modifier.onSizeChanged` to provide `viewportH` to `onDrag`
  - _Requirements: FR-002, FR-003_

- [ ] **T042** [US2] Verify on Pixel 6 + iPhone 12: drag rotates Moon smoothly; pinch zooms; clamps work; 60 FPS sustained
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
  - On Android: re-bind texture in MoonHost. On iOS: extend `MoonRendererProvider` with `applyAlbedoSwap: (ByteArray) -> Unit` and call from the toggle
  - _Requirements: ADR-0004_

- [ ] **T061** [US4] Verify both textures render correctly on both platforms without renderer restart
  - _Requirements: FR-005, ADR-0004_

**Checkpoint**: asset pipeline confirmed end-to-end.

---

## Phase Final: Polish & Documentation

- [ ] **T090** Document any deviations from ADRs in either a follow-up ADR or an update to `architecture.md` / `tech-stack.md`
  - _Requirements: agent-runbook.md_

- [ ] **T091** Add a debug-build assertion in `MoonViewport.ios.kt` that warns at first composition if `MoonRendererProvider.factory` is still the default no-op (forgot to wire from Swift)
  - _Requirements: ADR-0002_

- [ ] **T092** Add `iosApp/README.md` with `pod install` + Rosetta + Xcode workspace build instructions
  - Include the Rosetta toggle steps for Apple Silicon Mac developers
  - _Requirements: ADR-0002, agent-runbook.md_

- [ ] **T093** Smoke-test on real Pixel 6 + iPhone 12; record FPS, any visible issues, and resolution outcomes in `ai-docs/specs/00-renderer-spike/results.md`
  - _Requirements: SC-002, SC-003_

**Final Checkpoint**: all four user stories' acceptance criteria pass on real devices. `results.md` filed.

---

## Dependencies & Execution Order

| From → To | Why |
|---|---|
| T001 → T002, T006 | Versions defined first |
| T003 → T004 | matc binary present before the Gradle task that runs it |
| T006 → T007 | Podfile installed before workspace smoke test |
| T007 → T034..T040 | iOS workspace verified working before Filament-on-iOS work begins |
| Phase 1 → Phase 2 | Build infrastructure before code |
| Phase 2 → Phase 3 | Interfaces defined before implementations |
| Phase 3 (Android) ⊥ Phase 3 (iOS) | Once T030+T031 land, two engineers can work in parallel |
| Phase 3 → Phase 4, 5, 6 | US1 (MVP) ships before subsequent stories |
| Phase 6 → Phase Final | All work done before polish |

## Parallel Example: Phase 3 (after T030 + T031 land)

Two agents in parallel:
- **Agent A (Android)**: T032 → T033 → Checkpoint (Android)
- **Agent B (iOS)**: T034 → T035 → T036 → T037 → T038 → T039 → T040 → Checkpoint (iOS)

The shared state contract (`MoonRenderState`, `MoonViewport`) is locked by Phase 2. The closure-injection bridge contract (`MoonRendererProvider`'s closure properties) is locked by T034 — Swift side (T037–T040) and iOS Compose side (T035–T036) can thereafter work concurrently against that interface.

## Implementation Strategy

- Land Phase 1 + 2 in a single PR titled "00-renderer-spike: setup + foundational".
- Land Phase 3 (Android) + Phase 3 (iOS) in a second PR titled "00-renderer-spike: US1 MVP".
- Land Phase 4, 5, 6 as one PR titled "00-renderer-spike: gestures + sun + asset swap".
- Land Phase Final as one final PR titled "00-renderer-spike: polish + docs".
- Final squash-merge to `main` with an ADR-summary commit.

## Notes

- This is a **spike**. We pay down debt later: real NASA textures (`02-moon-renderer-mvp`), real sun joystick (`04-sun-control`), site markers + fly-to (`01-app-shell` + `03-sites-and-flyto`), polish (`05-polish`), Koog (`06-koog-agent`).
- If anything in Phase 3 takes more than 2 days per platform, **stop and reconsider the route**. ADR-0002's "Alternatives rejected" section lists fallbacks (route a/b/c/d).
- Don't relax acceptance criteria silently. If FR-001 (2-second startup) is missed, raise it — don't quietly stretch the budget.
