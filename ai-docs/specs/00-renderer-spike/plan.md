# Implementation Plan: 00 — Renderer Spike

**Branch:** `00-renderer-spike` | **Date:** 2026-04-28 | **Spec:** ./spec.md

## Summary

Add a Filament-driven `MoonViewport` Composable to the existing CMP shell, replacing the wizard "Click me!" screen. End-to-end validation of the renderer-host pattern (ADR-0003) and the iOS-Filament-via-Swift path (ADR-0002, including its closure-injection bridge) on both Android and iOS, before committing to MVP work.

## Technical Context

| Concern | Value | Source |
|---|---|---|
| Language | Kotlin 2.3.20 + Swift 5.9 + Objective-C++ (iosApp Filament wrapper) | `tech-stack.md` |
| Renderer | Filament 1.71.1 | ADR-0001, ADR-0002 |
| AGP | 9.0.0-alpha06 with `com.android.kotlin.multiplatform.library` | `tech-stack.md` |
| CMP | 1.10.3 | `tech-stack.md` |
| Targets | `android`, `iosArm64`, `iosSimulatorArm64` | `tech-stack.md` |
| Performance goal | 60 FPS on Pixel 6 / iPhone 12 | `spec.md` NFR |
| Test devices | Pixel 6, iPhone 12 (real hardware) | `spec.md` Assumptions |

## Constitution Check

- [x] I. Mobile-only — no targets added.
- [x] II. Tactile lunar globe — sphere + drag + zoom + sun control matches the brief.
- [x] III. KMP-shared state, platform-thin renderers — `MoonRenderState` in commonMain; renderer hosts are thin per-platform.
- [x] IV. Agent-ready — no Koog dep added.
- [x] V. Demo-friendly — placeholder textures are fine for the spike.
- [x] VI. Test boundaries — `commonTest` for math + state mutations only.
- [x] VII. Specs and ADRs are authoritative — this plan defers all architectural calls to the relevant ADRs.

## Architecture

(See `ai-docs/architecture.md` for the system shape. The spike implements the Renderer-host seam end-to-end.)

```
Compose UI (commonMain) ── gestures ──▶ MoonViewModel ── StateFlow ──▶ MoonRenderState
                                                                              │
                ┌────────────────────────┬────────────────────────────────────┘
                ▼                        ▼
       Android renderer host       iOS renderer host (closure-injection bridge)
       (AndroidView+SurfaceView)   (UIKitViewController + MoonRendererProvider)
                │                        │
                ▼                        ▼
       Filament JNI bindings      Swift MoonRendererViewController
       (filament-android AAR)         │
                │                     ▼
                │              Objective-C++ MoonRenderer (.mm)
                │                     │
                ▼                     ▼
       GL/Vulkan backend       Filament C++ API → Metal backend
```

## Components and Interfaces

### `commonMain` additions

| File | Purpose |
|---|---|
| `org.jetbrains.moonexplorer.domain.Vec3` | Immutable 3-float value class. No platform types. |
| `org.jetbrains.moonexplorer.domain.MoonMath` | `latLonToCartesian`, `cartesianToLatLon`, `cameraPosition`, `lookAt`-friendly helpers. Stateless. |
| `org.jetbrains.moonexplorer.domain.UvSphere` | Procedural UV sphere mesh generator. Returns `ByteArray` for positions/normals/tangents/uvs and `ByteArray` for indices, ready to hand to Filament. |
| `org.jetbrains.moonexplorer.state.MoonRenderState` | Immutable `data class`. ADR-0003. |
| `org.jetbrains.moonexplorer.state.MoonViewModel` | Wraps `MutableStateFlow<MoonRenderState>`. Exposes `onDrag`, `onPinch`, `setSunDirection`. |
| `org.jetbrains.moonexplorer.render.MoonViewport` | `@Composable expect fun MoonViewport(state: MoonRenderState, modifier: Modifier = Modifier)`. ADR-0003. |
| `org.jetbrains.moonexplorer.ui.MoonExplorerScreen` | Replaces `App()`'s "Click me!" content. Hosts `MoonViewport` + sun-control slider. Wires gestures. |
| `org.jetbrains.moonexplorer.ui.SunControl` | Single-axis Compose slider mapping `[-1, 1]` to a sun direction X. Placeholder. |

### `androidMain` additions

| File | Purpose |
|---|---|
| `org.jetbrains.moonexplorer.render.MoonViewport.android.kt` | `actual @Composable fun MoonViewport(...)`. Wraps `SurfaceView` in `AndroidView`. Pulls `state.value` per Choreographer frame. |
| `org.jetbrains.moonexplorer.render.MoonHost` | Manages Filament `Engine`, `SwapChain`, `Renderer`, `View`, `Scene`, `Camera`, `Material`, `Texture`s, mesh resources. Implements `LifecycleObserver` for pause/resume. `destroy()` releases in reverse order per `filament-cmp-integration.md` §1. |

### `iosMain` additions

| File | Purpose |
|---|---|
| `org.jetbrains.moonexplorer.render.MoonRendererProvider` | Kotlin `object` (singleton) with mutable closure properties: `factory`, `applyCamera`, `applySunDirection`, `applyMoonRotation`, `applyAssets`, `dispose`. Default no-ops so commonMain works without iOS-app wiring (e.g., previews/tests). Set from Swift at app init. ADR-0002 §"Bridge pattern". |
| `org.jetbrains.moonexplorer.render.MoonViewport.ios.kt` | `actual @Composable fun MoonViewport(...)`. Wraps `MoonRendererProvider.factory()` via CMP's `UIKitViewController { ... }`. `update` invokes the provider's apply-closures with current state. `onRelease` invokes `dispose`. |
| `org.jetbrains.moonexplorer.render.MoonAssets.kt` | Top-level `suspend fun loadAndPushBundledAssets()` — reads bytes from `Res.readBytes("files/textures/...")` and `Res.readBytes("files/materials/moon.filamat")`, then calls `MoonRendererProvider.applyAssets(...)`. Exposed to Swift. |

### `iosApp/iosApp/` additions

| File | Purpose |
|---|---|
| `iOSApp.swift` | **Modified** — `init()` instantiates a `MoonRendererViewController`, wires `MoonRendererProvider.shared.{factory,applyCamera,applySunDirection,applyMoonRotation,applyAssets,dispose}` closures, then kicks off `MoonAssetsKt.loadAndPushBundledAssets()`. |
| `KotlinByteArray+Data.swift` | Tiny extension converting `KotlinByteArray` → Swift `Data` for asset closures. |
| `MoonRendererViewController.swift` | `UIViewController` hosting `MoonRendererView`, owns a `MoonRenderer` instance. Drives `CADisplayLink` render loop. Methods exposed to Swift callers: `setCamera(yaw:pitch:distance:)`, `setSunDirection(x:y:z:)`, `setMoonRotation(_:)`, `loadAssets(albedo:normal:material:)`, `tearDown()`. |
| `MoonRendererView.swift` | `UIView` subclass with `+ (Class)layerClass = CAMetalLayer`. Hosts the Metal rendering target. |
| `MoonRenderer.h` | Objective-C interface (internal to iosApp; **not** exposed to Kotlin). Methods mirror what the Swift VC needs: `init`, `setCameraYaw:pitch:distance:`, `setSunDirectionX:y:z:`, `setMoonRotation:`, `loadAssetsAlbedo:normal:material:`, `pause`, `resume`, `dispose`. ObjC-compatible types (`NSData`, `float`, `void`). |
| `MoonRenderer.mm` | Objective-C++ wrapping Filament's C++ API. `<filament/Engine.h>` etc. hidden behind the ObjC interface so Swift sees only ObjC types. |
| `Podfile` | `pod 'Filament/filament', '~> 1.71.1'`, `pod 'Filament/ktxreader', '~> 1.71.1'`. |

### Bundled assets

| File | Purpose |
|---|---|
| `:shared/src/commonMain/composeResources/files/materials/moon.mat` | PBR material source (sRGB albedo + linear normal map + directional light). |
| `:shared/src/commonMain/composeResources/files/materials/moon.filamat` | Compiled output (gitignored under build dir, copied by Gradle task into `composeResources/`). |
| `:shared/src/commonMain/composeResources/files/textures/moon_albedo_2k.ktx2` | Placeholder for spike (test pattern). KTX2 + Basis Universal ETC1S. |
| `:shared/src/commonMain/composeResources/files/textures/moon_normal_2k.ktx2` | Placeholder normal map. KTX2 + Basis Universal UASTC. |

## Data Models

```kotlin
package org.jetbrains.moonexplorer.state

data class MoonRenderState(
    val cameraYawRad: Float = 0f,
    val cameraPitchRad: Float = 0f,
    val cameraDistance: Float = 5f,
    val sunDirection: Vec3 = Vec3(0f, 0f, 1f),  // sub-Earth point lit
    val moonRotationRad: Float = 0f,
    val highlightedSiteId: String? = null,
)
```

`Vec3` is a simple `data class Vec3(val x: Float, val y: Float, val z: Float)` for the spike. A value-class packing is a Phase 2 optimization if needed.

## Error Handling

- **Filament Engine creation failure**: surface as a `RenderError("Engine init failed")` field on `MoonRenderState` (or a separate `MutableStateFlow<RenderError?>`). UI shows a fallback "renderer unavailable" Compose surface; no crash.
- **Asset load failure** (material or texture): same fallback path.
- **`pod install` failure on iOS**: build error; `iosApp/README.md` documents recovery.
- **iOS app forgets to wire `MoonRendererProvider` closures**: defaults are no-ops so the app shows a blank `UIViewController`, but does not crash. Add a debug-build warning if `factory` is still the default at first `MoonViewport` composition.

## Testing Strategy

| Test | Where | Asserts |
|---|---|---|
| `MoonMathTest.latLonToCartesian` | `commonTest` | (0,0)→(0,0,1); (0,90)→(1,0,0); (90,0)→(0,1,0); Apollo 11 (0.674, 23.473) → (0.398, 0.012, 0.917) per ADR-0006. |
| `UvSphereTest.generate` | `commonTest` | Vertex count = (segments+1)·(rings+1); `\|position\|` ≈ 1.0 within 1e-5 for all vertices; tangent⊥normal. |
| `MoonViewModelTest.onPinch` | `commonTest` | Clamps at `MIN_DIST = 1.5f` and `MAX_DIST = 20f`. |
| `MoonViewModelTest.onDrag` | `commonTest` | Drag right (positive dx) decreases yaw; drag down (positive dy) increases pitch; pitch clamped at ±89°. |
| **Manual smoke test** | Real Pixel 6 + iPhone 12 | All four user-story acceptance criteria pass. |

No automated visual regression tests for Phase 0. Visual-regression tooling is a Phase 2 polish concern.

## Project Structure (files added/modified by this spike)

```
shared/src/commonMain/kotlin/org/jetbrains/moonexplorer/
├── App.kt                                       (modified: hosts MoonExplorerScreen)
├── ui/MoonExplorerScreen.kt                     (new)
├── ui/SunControl.kt                             (new)
├── render/MoonViewport.kt                       (new — expect)
├── state/MoonRenderState.kt                     (new)
├── state/MoonViewModel.kt                       (new)
├── domain/Vec3.kt                               (new)
├── domain/MoonMath.kt                           (new)
└── domain/UvSphere.kt                           (new)

shared/src/androidMain/kotlin/org/jetbrains/moonexplorer/
├── render/MoonViewport.android.kt               (new — actual)
└── render/MoonHost.kt                           (new)

shared/src/iosMain/kotlin/org/jetbrains/moonexplorer/
├── render/MoonViewport.ios.kt                   (new — actual)
├── render/MoonRendererProvider.kt               (new — Kotlin singleton with closure properties)
└── render/MoonAssets.kt                         (new — suspend asset loader exposed to Swift)

shared/src/commonMain/composeResources/files/
├── materials/moon.mat                           (new — source)
├── textures/moon_albedo_2k.ktx2                 (new — placeholder)
└── textures/moon_normal_2k.ktx2                 (new — placeholder)

shared/src/commonTest/kotlin/org/jetbrains/moonexplorer/
├── domain/MoonMathTest.kt                       (new)
├── domain/UvSphereTest.kt                       (new)
└── state/MoonViewModelTest.kt                   (new)

iosApp/iosApp/
├── iOSApp.swift                                 (modified — wires MoonRendererProvider closures)
├── KotlinByteArray+Data.swift                   (new — small bridging extension)
├── MoonRendererViewController.swift             (new)
├── MoonRendererView.swift                       (new)
├── MoonRenderer.h                               (new — internal ObjC interface, not cinterop'd)
└── MoonRenderer.mm                              (new — ObjC++ wrapping Filament)

iosApp/Podfile                                   (new — pod 'Filament/filament', 'Filament/ktxreader')
iosApp/README.md                                 (new — pod install + Rosetta + Xcode build instructions)

tools/matc/                                      (new — vendored matc + LICENSE)

shared/build.gradle.kts                          (modified: filament deps in androidMain; matc Exec task)
gradle/libs.versions.toml                        (modified: filament version + library aliases)
```

## Complexity Tracking

| Violation | Why needed | Simpler alternative rejected because |
|---|---|---|
| Vendoring `matc` binary in `tools/matc/` | Material compile-time tool not on Maven | No published Gradle plugin exists; manual `Exec` task is the standard pattern (per `filament-cmp-integration.md` §5) |
| Two renderer host implementations (Kotlin/JNI + Swift+ObjC++) | Per ADR-0002 — Filament's C++ API + Kotlin/Native cinterop limitations | Single-language renderer would require a hand-written C++→K/N wrapper for higher maintenance |
| Mutable global closures on `MoonRendererProvider` | Per ADR-0002 §"Bridge pattern" — Swift sets them once at app init | Cinterop on `iosApp/` headers is more code, more coupling, and forces a `.def` file maintained alongside Xcode-managed headers |

## Open verifications (resolve as part of this spike)

- **ADR-0002**: ~~`Filament.podspec` simulator-arm64 status~~ ✅ Resolved 2026-04-28 — `arm64` simulator excluded; mitigation = Rosetta. See ADR-0002 §"Verification".
- **tech-stack.md** open question: `MoonViewport` `actual` location. Locked here as `:shared/iosMain` (matching the Android `:shared/androidMain` symmetry); both renderer hosts are inside `:shared`, with the iOS host calling out to Swift via `MoonRendererProvider`.

## References

- ADR-0001..0007
- `ai-docs/research/filament-cmp-integration.md`
- `ai-docs/research/agp9-kmp-native-deps.md`
- `ai-docs/research/selenographic-math-camera.md`
- `./spec.md`
- `./tasks.md`
