# ADR-0002: Filament-on-iOS via Swift-side hosting (Hybrid route)

**Status**: Accepted
**Date**: 2026-04-28
**Supersedes**: —

## Context

Filament's iOS distribution model is C++ static archives + headers. Kotlin/Native cinterop does not consume C++ headers directly (only C and Objective-C). The integration question affects every downstream architectural decision. Four routes were evaluated:

(a) **CocoaPods via `kotlin("native.cocoapods")`** — pull `pod 'Filament'` into `:shared`. Mechanically valid, but the C++ API still requires a wrapper, and it forces `iosApp/` into a CocoaPods workspace.

(b) **SwiftPM via `swiftPMDependencies`** — not available on Kotlin 2.3.20 (the DSL requires ≥2.4.0-Beta2). Filament does not publish a `Package.swift` either. Both blockers; not viable today.

(c) **Direct cinterop with C wrapper or Objective-C++ shim** — write a hand-rolled C ABI in front of Filament, link via `cinterop`. Maximum maintenance burden; needs a CMake step + per-target `.def` files.

(d) **Hybrid: Filament purely on the Swift side of `iosApp/`** — keep `:shared` Filament-free; expose a renderer-host seam through Compose's `UIKitViewController { ... }` interop API; Swift implementation in `iosApp/` consumes Filament via `pod 'Filament'` in the iosApp Podfile.

## Decision

Use **route (d)** — Filament integrated purely on the Swift side of `iosApp/`. Compose Multiplatform drives a Swift `MoonRendererViewController` that holds the Filament `Engine`, `SwapChain`, and CADisplayLink-driven render loop. `:shared` exposes the seam through `UIKitViewController { factory = { MoonRendererProvider.factory() } }`.

### Bridge pattern: closure injection from Swift

`:shared/iosMain` declares a Kotlin **`MoonRendererProvider`** singleton with mutable closure properties:

```kotlin
// :shared/src/iosMain/kotlin/org/jetbrains/moonexplorer/render/MoonRendererProvider.kt
package org.jetbrains.moonexplorer.render

import platform.UIKit.UIViewController

object MoonRendererProvider {
    var factory: () -> UIViewController = { UIViewController() }
    var applyCamera: (yawRad: Float, pitchRad: Float, distance: Float) -> Unit = { _, _, _ -> }
    var applySunDirection: (x: Float, y: Float, z: Float) -> Unit = { _, _, _ -> }
    var applyMoonRotation: (rotationRad: Float) -> Unit = { _ -> }
    var applyAssets: (albedo: ByteArray, normal: ByteArray, material: ByteArray) -> Unit = { _, _, _ -> }
    var dispose: () -> Unit = {}
}
```

`MoonViewport.ios.kt` uses it directly:

```kotlin
// :shared/src/iosMain/kotlin/org/jetbrains/moonexplorer/render/MoonViewport.ios.kt
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun MoonViewport(state: MoonRenderState, modifier: Modifier) {
    val vc = remember { MoonRendererProvider.factory() }
    UIKitViewController(
        factory = { vc },
        update = {
            MoonRendererProvider.applyCamera(state.cameraYawRad, state.cameraPitchRad, state.cameraDistance)
            MoonRendererProvider.applySunDirection(state.sunDirection.x, state.sunDirection.y, state.sunDirection.z)
            MoonRendererProvider.applyMoonRotation(state.moonRotationRad)
        },
        onRelease = { MoonRendererProvider.dispose() },
        modifier = modifier,
    )
}
```

The iOS app wires the closures from Swift at app startup, capturing a real `MoonRendererViewController` instance:

```swift
// iosApp/iosApp/iOSApp.swift
import SwiftUI
import Shared

@main
struct iOSApp: App {
    init() {
        let renderer = MoonRendererViewController()
        MoonRendererProvider.shared.factory = { renderer }
        MoonRendererProvider.shared.applyCamera = { yaw, pitch, dist in
            renderer.setCamera(yaw: yaw.floatValue, pitch: pitch.floatValue, distance: dist.floatValue)
        }
        MoonRendererProvider.shared.applySunDirection = { x, y, z in
            renderer.setSunDirection(x: x.floatValue, y: y.floatValue, z: z.floatValue)
        }
        MoonRendererProvider.shared.applyMoonRotation = { rot in
            renderer.setMoonRotation(rot.floatValue)
        }
        MoonRendererProvider.shared.applyAssets = { albedo, normal, material in
            renderer.loadAssets(albedo: albedo.toData(), normal: normal.toData(), material: material.toData())
        }
        MoonRendererProvider.shared.dispose = { renderer.tearDown() }

        // Pre-fetch bundled fallback assets via Compose Resources, then push.
        Task { await MoonAssetsKt.loadAndPushBundledAssets() }
    }

    var body: some Scene { WindowGroup { ContentView() } }
}
```

`Float`/`KotlinByteArray` Swift bridging is standard K/N output — `kotlin.Float` becomes Swift `KotlinFloat` (NSNumber-shaped); `ByteArray` exposes a `.toData()` extension provided by a small `KotlinByteArray+Data.swift` helper in `iosApp/`.

### Why this works without cinterop on `iosApp/` headers

- Kotlin/Native sees the Swift-instantiated `MoonRendererViewController` only as `UIViewController` (its supertype, which K/N already knows from the cinterop'd UIKit module). The Swift class itself never crosses the KMP boundary.
- Closures created in Swift are bridged automatically to Kotlin `() -> T` function types. The bridge is just function references — no `.def` file, no header coupling, no `kotlin("native.cocoapods")` plugin.
- The Filament wrapping inside the Swift renderer (`MoonRenderer.h` + `MoonRenderer.mm`, an Objective-C++ class hiding `<filament/...>` includes) stays purely inside `iosApp/` and is invisible to `:shared`. Swift consumes the ObjC class normally; Kotlin never sees it.

## Rationale

- Swift 5.9+ has first-class C++ interop; Objective-C++ has always worked. The host language already speaks Filament's API natively.
- Calling C++ from Kotlin/Native requires a hand-written wrapper layer that adds ongoing maintenance burden for no functional gain.
- **Closure injection avoids cinterop on `iosApp/` headers entirely** — Kotlin sees only `UIViewController` and `() -> T`, both of which K/N handles natively.
- `Shared.framework` stays small — no Filament symbols leak in, no static archive ballast (~30 MB), faster Kotlin/Native link times.
- Keeps `iosApp/` as a normal Xcode project; only its `Podfile` changes. Avoids a forced CocoaPods migration of `:shared`.
- The "renderer-host interface" pattern matches the Android side symmetrically — both platforms expose a per-platform host that reads `MoonRenderState` from `commonMain` (see ADR-0003).

## Verification — 2026-04-28

**Confirmed by direct fetch of [Filament.podspec](https://github.com/google/filament/blob/main/ios/CocoaPods/Filament.podspec)** (Filament `1.71.1`):

The podspec **explicitly excludes the iOS simulator arm64 slice**. Both `spec.pod_target_xcconfig` and `spec.user_target_xcconfig` set:

```ruby
'EXCLUDED_ARCHS[sdk=iphonesimulator*]' => 'arm64'
```

with the inline comment: *"Fix linking error with Xcode 12; we do not yet support the simulator on Apple silicon."*

The vendored static archives live under `lib/universal/` and are **device-universal** (device arm64 + simulator x86_64) — *not* full universal. This resolves the conflict between the two research agents: the **Filament+CMP integration agent was correct**, the AGP plumbing agent's claim that arm64 simulator is supported was wrong.

### Chosen mitigation: Rosetta on Apple Silicon

Run the iOS simulator under **Rosetta** during development on Apple Silicon Macs. In Xcode: select the iOS Simulator run destination → right-click → "Open in Rosetta". The pod's x86_64 simulator slice runs under Rosetta translation. Slower than native simulator, acceptable for personal dev. Re-evaluate on every Filament version bump (track [release notes](https://github.com/google/filament/releases)).

### Alternative mitigations (if Rosetta becomes too slow)

1. **Build Filament from source** with `./build.sh -p ios release` and vendor the resulting simulator-arm64 archives via a local podspec override. Native simulator performance at the cost of a one-time build setup (~30–60 min on a recent M-series Mac).
2. **Develop only on physical iOS devices.** Cleanest path; requires a wired iPhone for every dev session.

### Subspec selection (locked for Phase 0/1)

Phase 0 + Phase 1 need only the core engine and KTX2 reader. The `iosApp/Podfile` declares:

```ruby
pod 'Filament/filament',  '~> 1.71.1'   # core: Engine, Renderer, Scene, Camera, Material
pod 'Filament/ktxreader', '~> 1.71.1'   # Ktx2Reader for KTX2/Basis textures
```

Transitive deps (`utils`, `math`, `tsl`, `image`, `filabridge`, `filaflat`, `backend`, `ibl`, `geometry`) come along through subspec dependencies declared in the podspec. We deliberately **skip**:

- `Filament/filamat` — we pre-compile materials via `matc` (per ADR-0001 + 0004).
- `Filament/gltfio_core` — Phase 0/1 uses a procedurally-generated UV sphere, no glTF.
- `Filament/viewer` — server-side debug viewer, not needed in app.

### Other podspec facts captured

- Pod version: **1.71.1** (pin in `iosApp/Podfile`).
- iOS deployment target on the pod: **11.0** (our app requires 13.0; pod is more permissive — fine).
- License: Apache 2.0.
- Source: GitHub release tarball, not git checkout.

## Alternatives rejected

- **(a) CocoaPods through `:shared`**: still needs a C++ wrapper to bridge Kotlin/Native → C++; forces CocoaPods workspace on `iosApp/`; pollutes `Shared.framework` with Filament symbols.
- **(b) SwiftPM**: blocked by Kotlin 2.3.20 (needs ≥2.4.0-Beta2) and missing Filament SPM publish.
- **(c) Direct cinterop**: highest maintenance cost (custom CMake script, per-target `.def` files, hand-managed C wrapper); Objective-C++ shim is essentially what Swift gives us for free in route (d).
- **Earlier (d) variant: cinterop on `iosApp/MoonRendererBridge.h`** — initial sketch coupled `:shared/iosMain` to a header in `iosApp/` via a `.def` file. Replaced with closure injection above; same end-state, fewer moving parts.

## Consequences

- Two renderer implementations: Kotlin/JNI on Android (via `filament-android`), Swift on iOS (Objective-C++ wrapping C++ `<filament/...>`). Drift risk mitigated by keeping all material parameters and scene state in `commonMain` — only frame-submit and uniform-push run platform-side.
- `iosApp/` gains Podfile entries for `pod 'Filament/filament'` + `pod 'Filament/ktxreader'`, both `'~> 1.71.1'`, and becomes a CocoaPods workspace (`iosApp.xcworkspace`).
- `Shared.framework` stays C++-symbol-free; smaller binary; faster Kotlin/Native link.
- **Single `MoonRendererViewController` instance per app run.** The closure captures it once at app init. Multi-instance globe display is not currently a requirement; if it ever is, the factory closure can be promoted to construct fresh VCs on each call.
- **Swift mutates Kotlin singleton properties at startup** via the K/N-generated `.shared` accessor — standard Kotlin↔Swift pattern, well documented.
- Material `.filamat` blobs and texture KTX2 files are loaded from `composeResources/files/...` in `commonMain` (asset bytes are platform-neutral); the iOS app fetches them via `Res.readBytes(...)` and pushes through `MoonRendererProvider.applyAssets` at startup.
- **iOS simulator on Apple Silicon Macs requires Rosetta** until Filament adds simulator-arm64 support upstream (no ETA).

## References

- [Filament CocoaPods spec (verified 2026-04-28)](https://github.com/google/filament/blob/main/ios/CocoaPods/Filament.podspec)
- [google/filament iOS samples](https://github.com/google/filament/tree/main/ios/samples)
- [Filament releases](https://github.com/google/filament/releases)
- [Compose Multiplatform UIKit interop](https://kotlinlang.org/docs/multiplatform/compose-uikit-integration.html)
- [Calling Kotlin from Swift / Objective-C](https://kotlinlang.org/docs/native-objc-interop.html#calling-kotlin-from-swift-objective-c)
- `ai-docs/research/filament-cmp-integration.md` §2, §3, §8
- `ai-docs/research/agp9-kmp-native-deps.md` §2, §3
