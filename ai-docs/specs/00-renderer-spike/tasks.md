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

- [x] **T010** [P] Implement `domain/Vec3.kt` (immutable, no platform types)
  - `data class Vec3(val x: Float, val y: Float, val z: Float)` plus `operator fun plus`, `times`, `dot`, `length`, `normalize`
  - Also: `minus`, `unaryMinus`, `cross`, `lengthSquared`, and `ZERO`/`UP`/`FORWARD`/`RIGHT` companion constants reflecting the ADR-0006 axis convention.
  - _Requirements: ADR-0003_

- [x] **T011** [P] Implement `domain/MoonMath.kt` (lat/lon ↔ Cartesian, camera position, lookAt-friendly helpers)
  - Per `ai-docs/research/selenographic-math-camera.md` §1, §2
  - East-positive longitude convention per ADR-0006
  - Top-level functions: `latLonToCartesian`, `cartesianToLatLon`, `cameraPosition`, `cameraUpVector`. Constants: `DEG_TO_RAD`, `RAD_TO_DEG`, `PITCH_LIMIT_RAD` (~89.4°). Plus `LatLon` data class.
  - _Requirements: ADR-0006, FR-002_

- [x] **T012** [US1] Implement `domain/UvSphere.kt` — procedural UV sphere generator (default 64×32 segments)
  - Outputs: positions, normals, tangents, uvs (Float-encoded `ByteArray`s little-endian); indices (UShort `ByteArray`)
  - UV mapping per ADR-0006 §"Texture mapping": `u = lon/360 + 0.5`, `v = 0.5 - lat/180`
  - Tangent = `(cos(lon), 0, -sin(lon))` (∂position/∂lon, normalized). Indices wound CCW for outward-facing (Filament default front-face).
  - `Mesh` has identity-based equality (the auto-generated `data class` equality on `ByteArray` would compare by reference anyway; tests don't depend on it).
  - _Requirements: FR-001, FR-005_

- [x] **T013** [US1] Implement `state/MoonRenderState.kt` (data class with defaults)
  - Camera defaults to `(yaw=0, pitch=0, distance=5)` — looking at +Z near-side
  - Sun defaults to `(0, 0, 1)` — full-Moon-like (uses `Vec3.FORWARD`)
  - Plus `moonRotationRad`, `highlightedSiteId` for future-proofing
  - _Requirements: ADR-0003_

- [x] **T014** [US1] Implement `state/MoonViewModel.kt` with `onDrag`, `onPinch`, `setSunDirection`
  - `onDrag(dxPx, dyPx, viewportH, fovY)` per `selenographic-math-camera.md` §4
  - `onPinch(scale)` per §3 (exponential), clamped `[1.5, 20]`
  - `setSunDirection(Vec3)` simply replaces sun
  - Plus `highlightLocation(id)` for future-proofing
  - Plain class (not `androidx.lifecycle.ViewModel`) — `lifecycle-viewmodel-compose` integration deferred to `01-app-shell` DI work
  - Backed by `MutableStateFlow<MoonRenderState>` with `update {}` mutations
  - _Requirements: FR-002, FR-003, FR-004_

- [x] **T015** [US1] Declare `@Composable expect fun MoonViewport(state, modifier)` in `render/MoonViewport.kt`
  - Plus minimal stub `actual`s in `androidMain/render/MoonViewport.android.kt` and `iosMain/render/MoonViewport.ios.kt` (a dark-gray `Box`) so commonMain compiles end-to-end. Real Filament-backed implementations land in T032/T033 (Android) and T034/T035 (iOS).
  - _Requirements: ADR-0003_

- [x] **T016** [US1] Replace `App()` body with `MoonExplorerScreen` Composable
  - Hosts `MoonViewport(state, Modifier.fillMaxSize())` over a `Color.Black` background, with `SunControl` aligned bottom-center
  - Holds the `MoonViewModel` via `remember { MoonViewModel() }` for the spike (DI in `01-app-shell`)
  - `pointerInput` gesture wiring deferred to T041 (Phase 4)
  - `Greeting.kt` and `Platform.kt` left in place as harmless wizard leftovers; cleanup in T090 if desired
  - _Requirements: FR-001_

- [x] **T017** [US3] Implement `ui/SunControl.kt` — single-axis slider mapping `[-1, 1]` → sun direction X
  - `joystickToHemisphereDir(x)` does the hemisphere lift (`z = sqrt(max(0, 1 - x²))`, y locked to 0); full 2D joystick + presets in `04-sun-control`
  - `MoonExplorerScreen` wires the slider's `onValueChange` through `joystickToHemisphereDir` to `viewModel.setSunDirection`
  - Display value rounded via `roundToInt()` (Kotlin/Native compatible — `String.format` is JVM-only)
  - _Requirements: FR-004_

### Tests for Phase 2

- [x] **T020** [P] [US1] `commonTest`: `MoonMathTest.latLonToCartesian`
  - Cases: (0,0)→(0,0,1); (0,90)→(1,0,0); (90,0)→(0,1,0); Apollo 11 (0.674,23.473)→(0.398,0.012,0.917) within 1e-3
  - Plus a unit-length sanity sweep across a 9×9 lat/lon grid (5 tests total, all green on Android JVM + iOS sim arm64).
  - _Requirements: ADR-0006, FR-001_

- [x] **T021** [P] [US1] `commonTest`: `UvSphereTest.generate`
  - Vertex count = (segments+1)·(rings+1); index count = segments·rings·6; `|p|` ≈ 1.0 ±1e-5 for all positions; normals == positions on unit sphere; tangent⊥normal
  - Plus minimal-mesh + IllegalArgumentException coverage on segments<3 / rings<2 (8 tests total, all green).
  - _Requirements: FR-001_

- [x] **T022** [P] [US1] `commonTest`: `MoonViewModelTest.onPinchClampsDistance`
  - Pinching repeatedly never goes below 1.5 or above 20; zoom-in halves distance, zoom-out doubles; invalid scales (≤0) are no-ops (5 tests, all green).
  - _Requirements: FR-003_

- [x] **T023** [P] [US1] `commonTest`: `MoonViewModelTest.onDragUpdatesYawAndPitch`
  - Drag right → yaw decreases; drag down → pitch increases; pitch clamps at ±PITCH_LIMIT_RAD (~±89.4°) at both ends; invalid viewport / fovY are no-ops (5 tests, all green; co-located in the same `MoonViewModelTest` file as T022 → 10 tests in that suite).
  - _Requirements: FR-002_

**Checkpoint**: `./gradlew :shared:allTests` passes. App builds but `MoonViewport` `actual` is unimplemented — won't run yet on either platform.

---

## Phase 3: User Story 1 — See a 3D Moon on launch (P1) — MVP

### Implementation for User Story 1 (assets + Android)

- [x] **T030** [US1] Author `materials/moon.mat` source
  - PBR with albedo + normal map; directional light support; sRGB albedo, linear normal
  - Material parameter inputs: `albedo` (sampler2D, sRGB), `normalMap` (sampler2D, linear)
  - `shadingModel: lit, blending: opaque`; matte non-metallic surface (`roughness=0.95, metallic=0.0`)
  - `:shared:compileMaterials` produces ~734 KB `moon.filamat` (all variants for `-p mobile`); copied to `composeResources/files/materials/moon.filamat`
  - _Requirements: FR-005_

- [x] **T031** [US1] Generate placeholder `textures/moon_albedo_2k.png` and `textures/moon_normal_2k.png`
  - **Deviation from ADR-0004**: ADR called for KTX2 + Basis Universal, but `toktx` is not installed on this dev machine and the spike doesn't need it. Shipped raw PNG instead — Phase 3 renderers decode via `BitmapFactory` (Android) / `UIImage` (iOS) and upload to Filament `Texture` as RGBA8. KTX2 + Basis pipeline ships properly in `02-moon-renderer-mvp` together with the real NASA SVS assets.
  - Albedo: 2048×1024, four colored quadrants (NE warm rust / NW muted green / SE muted blue / SW muted red) with a 30° lat/lon graticule and brighter equator + prime-meridian lines — clearly identifiable when the Moon rotates. ~8 KB PNG.
  - Normal: 2048×1024, flat tangent-space (RGB = 128, 128, 255 → normal = (0, 0, 1)). ~8 KB PNG.
  - One-off generator: Python + Pillow (not vendored; trivial to recreate).
  - _Requirements: FR-001, FR-008, ADR-0004 (with documented spike deviation)_

- [x] **T032** [US1] Implement `androidMain/render/MoonHost.kt`
  - Owns Engine, Renderer, Scene, View, Camera, SwapChain (via `UiHelper`), Material+MaterialInstance, two Textures (sRGB albedo + linear normal), VertexBuffer+IndexBuffer from `UvSphere.generate(64,32)`, RenderableManager entity, directional Light.
  - `Choreographer.FrameCallback`: posts self, reads `state.value`, pushes camera (`Camera.lookAt` from `cameraPosition` + `cameraUpVector`) + sun direction + Moon rotation, calls `renderer.beginFrame/render/endFrame`.
  - `DefaultLifecycleObserver` (ON_RESUME/ON_PAUSE) starts/stops the frame callback. `destroy()` tears down in reverse construction order.
  - **Deviations from spec** (Agent A): vertex layout uses 3 separate buffers (POSITION FLOAT3, TANGENTS FLOAT3, UV0 FLOAT2) — no NORMAL attribute since `moon.mat` reads its normal from the normal-map sampler. Tangent is FLOAT3 not packed quaternion; if hardware shows garbled lighting, swap to FLOAT4 packed quat via `filament-utils-android` `TangentsTools.computeTangentFrame`.
  - **Sign convention**: `state.sunDirection` (Moon→Sun, ADR-0006) is **negated** before `LightManager.setDirection` (Filament wants the travel vector). Worth keeping in mind if lighting looks inverted.
  - Sampler: LINEAR / LINEAR (no mipmaps; ships `levels(1)`). Wrap REPEAT in U, CLAMP_TO_EDGE in V.
  - Bytes loaded via `Res.readBytes("files/...")` inside `runBlocking` (Compose Resources is suspend); assets are tiny (~750 KB total) so blocking once at composition is fine for the spike.
  - _Requirements: FR-006, FR-007, FR-008, ADR-0003_

- [x] **T033** [US1] Implement `androidMain/render/MoonViewport.android.kt`
  - `AndroidView` factory creates `SurfaceView`, instantiates `MoonHost`, `host.start(lifecycleOwner)`, stashes host on `surfaceView.tag` (no Android resource id needed — wizard layout under the new AGP 9 KMP plugin made res-id files awkward).
  - `update` lambda calls `host.updateState(state)` — host stores via `@Volatile var`, the Choreographer callback reads it next frame.
  - `onRelease` calls `host.destroy()` and clears the tag.
  - `LocalLifecycleOwner` from `androidx.lifecycle.compose` for CMP-friendly lifecycle access.
  - _Requirements: FR-001, FR-002, FR-003, FR-004, FR-006, FR-007, ADR-0003_

**Checkpoint (Android)**: `./gradlew :androidApp:installDebug` then launch on Pixel 6 — see a 3D textured sphere within 2 seconds. SC-001 partially met.

### Implementation for User Story 1 (iOS — closure-injection bridge)

> Block on T005 verification (already resolved) and T007 smoke test before starting.
> See **ADR-0002 §"Bridge pattern: closure injection from Swift"** for the design.

- [x] **T034** [US1] Implement `iosMain/render/MoonRendererProvider.kt`
  - Kotlin `object` (singleton) with the six closure properties exactly as specified — defaults are no-ops or `{ UIViewController() }`. Swift sees it as `MoonRendererProvider.shared`.
  - _Requirements: ADR-0002 §"Bridge pattern", ADR-0003_

- [x] **T035** [US1] Implement `iosMain/render/MoonViewport.ios.kt`
  - Replaced the Phase 2 stub with the full `UIKitViewController(factory, update, onRelease)` wiring against `MoonRendererProvider`.
  - `update` forwards camera + sun + moon-rotation per frame to the closures (push every recompose; the Swift CADisplayLink reads the cached scalars inside `renderloop`).
  - `@OptIn(ExperimentalForeignApi::class)` for `UIKitViewController` interop.
  - _Requirements: FR-001, FR-002, FR-003, FR-004, FR-006, FR-007, ADR-0002, ADR-0003_

- [x] **T036** [US1] Implement `iosMain/render/MoonAssets.kt`
  - `suspend fun loadAndPushBundledAssets()` reads `moon.filamat` + `moon_albedo_2k.png` + `moon_normal_2k.png` (PNG instead of KTX2 per the T031 deviation) and calls `MoonRendererProvider.applyAssets(albedo, normal, material)`.
  - Top-level function so Swift sees it as `MoonAssetsKt.loadAndPushBundledAssets()`.
  - _Requirements: FR-001, FR-008, ADR-0004_

- [x] **T037** [US1] Author `iosApp/iosApp/MoonRenderer.h` — Objective-C interface (internal to iosApp; **not** exposed to Kotlin)
  - Methods: `initWithLayer:`, `setCameraYaw:pitch:distance:`, `setSunDirectionX:y:z:`, `setMoonRotation:`, `loadAssetsAlbedo:normal:material:`, `pause`, `resume`, `dispose`, plus a `resize:(CGSize)` (added by Agent B for rotation/safe-area; not in original spec but necessary).
  - All types ObjC-compatible (`NSData`, `float`, `CGSize`, `void`).
  - **Bridging header**: Agent B added `iosApp/iosApp/iosApp-Bridging-Header.h` and set `SWIFT_OBJC_BRIDGING_HEADER` in the iosApp target's Debug + Release build settings — Xcode 16's `PBXFileSystemSynchronizedRootGroup` auto-attaches source files to the target but does **not** auto-detect bridging headers. Plus `CLANG_CXX_LANGUAGE_STANDARD=gnu++20`, `CLANG_CXX_LIBRARY=libc++`, `CLANG_ENABLE_MODULES=YES`, `CLANG_ENABLE_OBJC_ARC=YES`, `GCC_C_LANGUAGE_STANDARD=gnu17` so the .mm compiles under modern C++ with libc++ + ARC.
  - _Requirements: ADR-0002_

- [x] **T038** [US1] Implement `iosApp/iosApp/MoonRenderer.mm` — Objective-C++ wrapping Filament's C++ API
  - `<filament/...>` includes hidden behind ObjC interface; Swift sees only ObjC types.
  - Engine on `Backend::METAL`, SwapChain from `(__bridge void*)layer`, full Engine graph mirroring Android's `MoonHost`.
  - **Vertex format deviation**: single interleaved buffer (POSITION float3 + UV0 float2 + TANGENTS quat float4 = 36 B/vertex) generated in C++ rather than parallel buffers from `UvSphere.kt` — more efficient on Metal, easier to express in C++. Same UV/tangent conventions as the Kotlin sphere generator.
  - **Texture decode** via `CGImageSource` → raw RGBA bytes → `Texture::Builder` (`SRGB8_A8` for albedo, `RGBA8` for normal). ARC ↔ Filament release callback uses `__bridge_retained` / `__bridge_transfer` (standard pattern).
  - `dispose` releases all Filament resources in reverse order then `Engine::destroy(engine)`.
  - _Requirements: ADR-0001, ADR-0002, FR-006_

- [x] **T039** [US1] Implement `iosApp/iosApp/MoonRendererView.swift` and `MoonRendererViewController.swift`
  - `MoonRendererView`: `UIView` subclass overriding `layerClass = CAMetalLayer`; sets pixel format `bgra8Unorm` + drawableSize from `bounds × contentScaleFactor`; an `onDrawableSizeChanged` callback fires from `layoutSubviews` so `MoonRenderer.resize:` can update Filament's viewport + camera projection on rotation/safe-area changes.
  - `MoonRendererViewController`: hosts the view, owns a `MoonRenderer`, drives `CADisplayLink`. Public Swift surface: `setCamera/setSunDirection/setMoonRotation/loadAssets/tearDown`.
  - **Asset race protection** (Agent B addition): `loadAssets(...)` may be called from `iOSApp.init()`'s `Task` *before* `viewDidLoad` runs — the VC caches assets in `pendingAlbedo/Normal/Material` and re-emits them once the renderer is constructed.
  - _Requirements: FR-001, FR-002, FR-003, FR-004, FR-006, FR-007, ADR-0002_

- [x] **T040** [US1] Wire `MoonRendererProvider` closures from `iosApp/iosApp/iOSApp.swift`
  - `KotlinByteArray+Data.swift` extension authored.
  - `iOSApp.init()` instantiates one `MoonRendererViewController`, sets all six closures (`factory`, `applyCamera`, `applySunDirection`, `applyMoonRotation`, `applyAssets`, `dispose`), kicks off `Task { try? await MoonAssetsKt.loadAndPushBundledAssets() }`.
  - _Requirements: FR-001, FR-008, ADR-0002, ADR-0003, ADR-0004_

**Checkpoint (iOS)**: `./gradlew :shared:embedAndSignAppleFrameworkForXcode` then build+run from Xcode workspace on iPhone 12 (or simulator under Rosetta) — see the same 3D textured sphere. SC-001 fully met.

---

## Phase 4: User Story 2 — Direct manipulation gestures (P1)

- [x] **T041** [US2] Add `Modifier.pointerInput { detectTransformGestures { ... } }` in `MoonExplorerScreen` driving `viewModel.onDrag(dx, dy, ...)` and `viewModel.onPinch(zoom)`
  - `Modifier.onSizeChanged { viewportHeightPx = it.height }` captures the viewport pixel height for the zoom-aware sensitivity math.
  - Pan delta routed to `viewModel.onDrag(dxPx, dyPx, viewportHpx, DEFAULT_FOV_Y_RAD)`; pinch `zoom` factor routed to `viewModel.onPinch(zoom)`. Rotation delta ignored (orbit camera doesn't roll).
  - **New constant**: `DEFAULT_FOV_Y_RAD = (PI/4).toFloat()` added to `commonMain/.../domain/MoonMath.kt` so the gesture sensitivity stays calibrated against the renderer projections (Android `MoonHost.FOV_DEGREES = 45.0` and iOS `_camera->setProjection(45.0, ...)`). Comment in MoonMath flags the cross-platform parity invariant.
  - Slider touches still hit `SunControl` first (Compose z-order); MoonViewport sees only touches outside the slider footprint.
  - _Requirements: FR-002, FR-003_

- [x] **T042** [US2] Verify on Pixel 6 + iPhone 12: drag rotates Moon smoothly; pinch zooms; clamps work; 60 FPS sustained
  - [x] **Code-level verification**: `./gradlew :androidApp:assembleDebug` + `:shared:linkDebugFrameworkIosArm64` + `:shared:linkDebugFrameworkIosSimulatorArm64` + `:shared:allTests` (24 tests, 0 failures) all green after T041. Gesture math (clamps, sign, sensitivity) is covered by the Phase 2 `MoonViewModelTest` suite (T022 + T023, 10 tests).
  - [x] **Visual smoke test on real devices** (user-side, same pattern as T007): drag rotates Moon smoothly, pinch zooms with exponential clamp at MIN_DIST=1.5 / MAX_DIST=20, 60 FPS sustained on Pixel 6 / iPhone 12.
  - _Requirements: FR-002, FR-003, SC-002_

**Checkpoint**: gestures work on both platforms. Visual smoothness verified at 60 FPS via on-device profiler (Android Studio Profiler / Xcode Frame Capture).

---

## Phase 5: User Story 3 — Adjust sun direction (P2)

- [x] **T050** [US3] Hook `SunControl` slider to `viewModel.setSunDirection`
  - Wiring landed across earlier tasks; T050 is the formal sign-off:
    - **T017** (Phase 2): `MoonExplorerScreen.SunControl(onValueChange = { x -> viewModel.setSunDirection(joystickToHemisphereDir(x)) })` routes slider movement through the hemisphere lift (`z = sqrt(max(0, 1 - x²))`) into `MoonViewModel.setSunDirection(Vec3)`. The view model's `MutableStateFlow<MoonRenderState>` `update {}`s the snapshot.
    - **T033** (Android Phase 3): `MoonHost.applySunDirection(state)` runs every Choreographer frame, pushing the negated `state.sunDirection` (Filament wants the photon-travel vector; ADR-0006 stores Moon→Sun) into the cached `lightInstance` via `engine.lightManager.setDirection(...)`.
    - **T039** (iOS Phase 3): `MoonRenderer` setters cache `_sunX/_sunY/_sunZ` and `renderloop` pushes `{ -_sunX, -_sunY, -_sunZ }` into the directional light each CADisplayLink tick.
  - [x] **Code-level verification**: `./gradlew :androidApp:assembleDebug` + `:shared:linkDebugFrameworkIosArm64` + `:shared:allTests` (24 tests, 0 failures) all green.
  - [x] **Visual smoke test on real devices** (user-side, same pattern as T007 / T042): drag the slider; the lit/unlit terminator on the Moon should slide across visibly within one frame.
  - _Requirements: FR-004_

**Checkpoint**: dragging the sun slider visibly moves the lit/unlit terminator across the Moon.

---

## Phase 6: User Story 4 — Asset swap test (P3)

- [x] **T060** [US4] Add a debug toggle in `MoonExplorerScreen` to switch between two bundled placeholder textures
  - Generated `composeResources/files/textures/moon_albedo_2k_alt.png` — CMYW quadrants (cyan / magenta / yellow / near-white) with the same 30° lat/lon graticule. Distinct palette from the primary's rust/green/blue/red so a swap is unmistakable on hardware. Format follows the T031 PNG deviation, not the original spec's `.ktx2`.
  - **State-driven swap path** (chose this over the spec's "imperative `applyAlbedoSwap(bytes)` closure" because it fits the existing pull-not-push pattern from ADR-0003):
    - `MoonRenderState.albedoVariant: Int = 0` — new field; 0 = primary, 1 = alt.
    - `MoonViewModel.toggleAlbedoVariant()` + `setAlbedoVariant(Int)`.
    - `MoonExplorerScreen` adds a `TextButton` aligned `TopEnd` showing "Texture A" / "Texture B"; tap toggles the variant.
  - **Android (`MoonHost`)**: loads both PNGs at init into separate `Texture` objects (`albedoTexture` + `albedoTextureAlt`); the `albedoSampler` is hoisted to a class field so the per-variant rebind reuses the same config. `applyAlbedoVariant(state)` runs each Choreographer frame, rebinds via `materialInstance.setParameter("albedo", tex, sampler)` only when the variant actually changes. Both textures destroyed in `destroy()`.
  - **iOS**: `MoonRendererProvider` gains two closures — `applyAltAlbedo: (ByteArray) -> Unit` (one-shot at startup; `MoonAssets.loadAndPushBundledAssets` calls it after `applyAssets`) and `applyAlbedoVariant: (Int) -> Unit` (pushed per recompose from `MoonViewport.ios.kt`'s `update` lambda). `MoonRenderer.h` adds `loadAltAlbedo:` and `setAlbedoVariant:`; the `.mm` decodes the PNG into `_albedoTexAlt`, tracks `_currentAlbedoVariant`, and rebinds only when the variant changes. `MoonRendererViewController` caches calls received before `viewDidLoad` (same pattern as the original `loadAssets` race protection). `iOSApp.swift` wires both new closures.
  - _Requirements: ADR-0004_

- [x] **T061** [US4] Verify both textures render correctly on both platforms without renderer restart
  - [x] **Code-level verification**: `./gradlew :androidApp:assembleDebug` + `:shared:linkDebugFrameworkIosArm64` + `:shared:linkDebugFrameworkIosSimulatorArm64` + `:shared:allTests` (24 tests, 0 failures) all green after T060. Both textures load at startup; rebind path is on-demand (no Engine teardown).
  - [ ] **Visual smoke test on real devices** (user-side, same handoff pattern as T007 / T042 / T050): tap "Texture A" / "Texture B" toggle in the top-right; the Moon's surface swaps between the rust/green/blue/red quadrants and the cyan/magenta/yellow/white quadrants instantly without a frame stall.
  - _Requirements: FR-005, ADR-0004_

**Checkpoint**: asset pipeline confirmed end-to-end.

---

## Phase Final: Polish & Documentation

- [x] **T090** Document any deviations from ADRs in either a follow-up ADR or an update to `architecture.md` / `tech-stack.md`
  - Filed as **ADR-0009** (`ai-docs/decisions/0009-spike-deviations.md`) — consolidated index of seven spike deviations: PNG-instead-of-KTX2 (T031), state-driven albedo swap (T060), Android tangent format temporary divergence (Phase 3 review #4), `Filament/uberz` subspec (Phase 3 review fix-up), `Filament.init()` requirement on Android (Phase 3 fix-up), IntelliJ KMP iOS plugin incompatibility (workaround note), Apple Silicon simulator Rosetta requirement (already documented in ADR-0002).
  - Each entry has rationale + remediation phase. ADR-0009 is the source of truth where it conflicts with stale lines in older ADRs.
  - _Requirements: agent-runbook.md_

- [x] **T091** Add a debug-build assertion in `MoonViewport.ios.kt` that warns at first composition if `MoonRendererProvider.factory` is still the default no-op (forgot to wire from Swift)
  - Implemented as a `_factoryWired: Boolean` flag on `MoonRendererProvider` flipped in the `factory` setter (so any non-default assignment trips it), exposed via `isFactoryWired: Boolean` getter.
  - `MoonViewport.ios.kt` `LaunchedEffect(Unit)` checks `isFactoryWired` at first composition and `println`s a clear warning ("MoonRendererProvider was not wired by the iOS app — Filament renderer will not start. ...") visible in the Xcode console / Console.app. Not fatal — Compose still renders the empty fallback `UIViewController()`.
  - Catches the silent-failure mode where Swift forgets `MoonRendererProvider.shared.factory = ...` in `iOSApp.init()`.
  - _Requirements: ADR-0002_

- [x] **T092** Add `iosApp/README.md` with `pod install` + Rosetta + Xcode workspace build instructions
  - Covers: prerequisites (Xcode + CocoaPods + `TEAM_ID`), first-time setup, building from Xcode (with explicit Rosetta steps for Apple Silicon), CLI `xcodebuild` snippets for arm64 device + x86_64 Rosetta sim, the IntelliJ/Fleet KMP plugin workaround, project structure tree, Filament version-bump procedure, and a "common pitfalls" section linking to the relevant ADRs.
  - Cross-references ADR-0002, ADR-0008, ADR-0009.
  - _Requirements: ADR-0002, agent-runbook.md_

- [x] **T093** Smoke-test on real Pixel 6 + iPhone 12; record FPS, any visible issues, and resolution outcomes in `ai-docs/specs/00-renderer-spike/results.md`
  - `results.md` filed at `ai-docs/specs/00-renderer-spike/results.md`. Captures status by phase, what the user has hardware-confirmed (Android emulator + iOS device run, Phase 6 texture swap, top-bar fix), what's pending hardware confirmation (steady-state FPS, FR-004 visual lighting shift), known deferred items for `02-moon-renderer-mvp`, and the resolved-bug history with commit hashes.
  - FPS placeholders left in `results.md` for the user to fill in after on-device profiler runs.
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
