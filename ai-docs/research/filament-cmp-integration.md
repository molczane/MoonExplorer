# Embedding Filament in a CMP App on Android + iOS

> Research output. Source: agent run 2026-04-28. Filament v1.71.x (current as of late 2025), Compose Multiplatform 1.10.x, AGP 9.0.0-alpha06 with `com.android.kotlin.multiplatform.library`.

## 1. Android side — Compose `AndroidView` host

### Filament classes that participate

The minimal object graph is documented in [Filament's main README](https://github.com/google/filament/blob/main/README.md) and confirmed in the `sample-hello-triangle` Kotlin source ([MainActivity.kt](https://github.com/google/filament/blob/main/android/samples/sample-hello-triangle/src/main/java/com/google/android/filament/hellotriangle/MainActivity.kt)):

- `Engine` — the root. Owns the GPU device, all resources. Created **once per surface lifecycle**.
- `SwapChain` — wraps the native window (the `Surface` from the `SurfaceView`). Recreated on surface destroy/recreate.
- `Renderer` — orchestrates `beginFrame`/`render(view)`/`endFrame`. One per Engine.
- `View` — viewport, post-processing, MSAA, render target. Holds the `Camera` and `Scene`.
- `Scene` — entity container. Renderable entities + lights are added here.
- `Camera` — projection + view matrix. Created from an `EntityManager` entity (`engine.createCamera(em.create())`).
- `Material` — compiled `.filamat` blob (see §5).
- `MaterialInstance` — per-renderable parameter set (textures, floats, vec3s). Cheap to create from a `Material`.
- For the Moon: a `VertexBuffer` + `IndexBuffer` for the sphere mesh (or use `IcoSphere` from `filament-utils-android`, see [reference](https://prideout.net/slides/filawasm/reference.html)), a `Texture` for albedo, another for normal map, plus a directional light entity created via `LightManager.Builder(LightManager.Type.DIRECTIONAL)`.

### `SurfaceView` vs `TextureView`

Both are wired through Filament's `UiHelper` ([UiHelper.java](https://github.com/google/filament/blob/main/android/filament-android/src/main/java/com/google/android/filament/android/UiHelper.java)). The official sample-gltf-viewer uses `SurfaceView` — see [MainActivity.kt#L72](https://github.com/google/filament/blob/main/android/samples/sample-gltf-viewer/src/main/java/com/google/android/filament/gltf/MainActivity.kt). **Why**: `SurfaceView` has a dedicated GPU compositor surface (lower power, lower latency, no extra copy through the View system). `TextureView` is required when you need to **rotate, scale, alpha-blend, or animate** the 3D content as part of the View hierarchy — at the cost of an extra GPU copy. For Moon Explorer, where the Moon fills its container and you mostly need clean compositing under Compose chrome, **start with `SurfaceView`**. If you later want the Moon viewport to fade/animate within Compose transitions, switch to `TextureView` (UiHelper supports both transparently).

### Compose `AndroidView` + Choreographer

```kotlin
@Composable
fun MoonRendererAndroid(state: MoonRenderState, modifier: Modifier = Modifier) {
    val lifecycleOwner = LocalLifecycleOwner.current
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            SurfaceView(ctx).also { sv ->
                val host = MoonHost(sv, state)              // wraps Engine/Renderer/View/Scene/Camera
                sv.setTag(R.id.moon_host_tag, host)
                lifecycleOwner.lifecycle.addObserver(host)  // pause/resume frame callback
            }
        },
        onRelease = { sv ->
            (sv.getTag(R.id.moon_host_tag) as MoonHost).destroy()
        }
    )
}
```

`MoonHost` schedules a `Choreographer.FrameCallback` whose `doFrame(frameTimeNanos)`:
1. Posts itself again: `Choreographer.getInstance().postFrameCallback(this)`.
2. Reads the latest `MoonRenderState` snapshot and pushes camera/light updates into Filament objects.
3. Calls `if (uiHelper.isReadyToRender && renderer.beginFrame(swapChain, frameTimeNanos)) { renderer.render(view); renderer.endFrame() }`.

This is the exact pattern in [sample-gltf-viewer's `FrameCallback`](https://github.com/google/filament/blob/main/android/samples/sample-gltf-viewer/src/main/java/com/google/android/filament/gltf/MainActivity.kt).

### Lifecycle and explicit destroy

From the same sample's `onResume`/`onPause`/`onDestroy`:

- `onResume` → `choreographer.postFrameCallback(frameScheduler)`.
- `onPause` → `choreographer.removeFrameCallback(frameScheduler)`. Critical: you must stop posting frames or you'll render to a destroyed surface.
- `onDestroy` (and `AndroidView.onRelease` in our Compose case) → destroy in reverse construction order. From [`MainActivity.kt`](https://github.com/google/filament/blob/main/android/samples/sample-gltf-viewer/src/main/java/com/google/android/filament/gltf/MainActivity.kt):
  ```kotlin
  engine.destroyEntity(renderable); engine.destroyVertexBuffer(vb)
  engine.destroyIndexBuffer(ib); engine.destroyMaterialInstance(mi)
  engine.destroyMaterial(mat); engine.destroyTexture(albedo)
  engine.destroyTexture(normalMap); engine.destroyView(view)
  engine.destroyScene(scene); engine.destroyCameraComponent(camera.entity)
  engine.destroyRenderer(renderer); engine.destroy()  // last
  ```
  `UiHelper.detach()` destroys the SwapChain via `engine.destroySwapChain(...)` after `engine.flushAndWait()` — see [UiHelper](https://github.com/google/filament/blob/main/android/filament-android/src/main/java/com/google/android/filament/android/UiHelper.java).

Configuration changes are handled by `UiHelper.SurfaceCallback.onResized()` which updates the camera projection and view viewport.

Maven coords: `com.google.android.filament:filament-android:1.71.x` and `com.google.android.filament:filament-utils-android:1.71.x` ([Maven Central](https://mvnrepository.com/artifact/com.google.android.filament)). With AGP 9's `com.android.kotlin.multiplatform.library`, put these in the **app module** (`androidApp/build.gradle.kts`), not in `shared/` — Filament classes are platform-specific and the host wiring lives in the app.

> Note: this advice from this agent contradicts the AGP 9 plumbing agent, which recommends `androidMain` of `:shared`. See `agp9-kmp-native-deps.md` for the alternative. To resolve: depends on whether the renderer host Composable lives in `:shared` (then `androidMain`) or in `:androidApp` (then app module). The recommended hybrid route below puts the iOS host in Swift/`iosApp`; symmetry argues for putting the Android host in `:androidApp` too.

## 2. iOS distribution — what Filament actually ships

As of v1.71.x (late 2025/early 2026):

- **CocoaPods**: yes. The official spec lives at [`ios/CocoaPods/Filament.podspec`](https://github.com/google/filament/blob/main/ios/CocoaPods/Filament.podspec). It downloads `filament-vX.Y.Z-ios.tgz` from GitHub Releases, vendors `libfilament.a`, `libfilamat.a`, `libgltfio_core.a`, `libviewer.a`, and exposes them as subspecs (`filament`, `filamat`, `gltfio_core`, `camutils`, `filameshio`, `image`, `utils`, `math`, `ktxreader`, `viewer`, `uberz`). iOS deployment target is 11.0. Caveat per the podspec comment: **arm64 simulator (Apple Silicon) is excluded** — relevant because your project targets `iosSimulatorArm64`. You will need to either run on a device, run under Rosetta, or rebuild from source with simulator-arm64 enabled. Latest podspec is also published to CocoaPods trunk as `pod 'Filament', '~> 1.71.1'` per the repo README.

> **CONFLICT FLAG:** the AGP 9 plumbing agent reports that the pod "ships static archives... for arm64 device + arm64 simulator". Verify the actual current podspec content before committing to a route. This affects whether running on an Apple Silicon Mac simulator works out-of-the-box.

- **Swift Package Manager**: **no.** Tracking issue [#7350](https://github.com/google/filament/issues/7350) has been open since November 2023 with no team response and no PR. Don't expect SPM in the near term.
- **Prebuilt artifact**: a single `filament-vX.Y.Z-ios.tgz` per release on [github.com/google/filament/releases](https://github.com/google/filament/releases). It contains static `.a` libraries (universal device + simulator-x86_64), C++ headers, and host tools. Not an `.xcframework` — you would have to repackage if you want one.
- **Build from source**: documented in [`README.md`](https://github.com/google/filament/blob/main/README.md) and [`BUILDING.md`](https://github.com/google/filament/blob/main/BUILDING.md) — `./build.sh -p ios release` on a Mac. This is the only path to fix the `iosSimulatorArm64` gap if you can't use a physical device for development.

**Net**: CocoaPods is the supported distribution, with a known simulator-arm64 hole. You will likely combine CocoaPods (for device builds) with a from-source rebuild (for simulator-arm64).

## 3. iOS hosting in Compose Multiplatform

### Native side: a UIView with a CAMetalLayer

Filament's official iOS sample [`hello-pbr/FilamentView.mm`](https://github.com/google/filament/blob/main/ios/samples/hello-pbr/hello-pbr/FilamentView.mm) shows the canonical setup. The view is an Objective-C++ `UIView` subclass:

```objc
+ (Class)layerClass { return [CAMetalLayer class]; }   // make backing layer Metal

- (void)initializeMetalLayer {
    CAMetalLayer* metalLayer = (CAMetalLayer*)self.layer;
    metalLayer.pixelFormat = MTLPixelFormatBGRA8Unorm;
    metalLayer.drawableSize = CGSizeMake(self.bounds.size.width  * self.contentScaleFactor,
                                         self.bounds.size.height * self.contentScaleFactor);
}

// Engine + SwapChain: the layer pointer goes straight into createSwapChain
engine    = Engine::create(Engine::Backend::METAL);
swapChain = engine->createSwapChain((__bridge void*)self.layer);
renderer  = engine->createRenderer();
view      = engine->createView();
scene     = engine->createScene();
camera    = engine->createCamera(EntityManager::get().create());
view->setCamera(camera); view->setScene(scene);

displayLink = [CADisplayLink displayLinkWithTarget:self selector:@selector(renderloop)];
[displayLink addToRunLoop:NSRunLoop.mainRunLoop forMode:NSRunLoopCommonModes];
```

`renderloop` is `if (renderer->beginFrame(swapChain)) { renderer->render(view); renderer->endFrame(); }`.

There is **no public Swift API** — Filament's headers are C++. You have to write a small Objective-C++ (`.mm`) wrapper that exposes a Swift/ObjC class like `MoonViewController : UIViewController` whose `loadView` installs your custom Metal-backed `UIView`. That ObjC class is what crosses the KMP boundary.

### Hosting in CMP

CMP 1.10.3's iOS interop API is `UIKitViewController { ... }` documented at [kotlinlang.org/docs/multiplatform/compose-uikit-integration.html](https://kotlinlang.org/docs/multiplatform/compose-uikit-integration.html). The `iosMain` `actual` for your `expect fun MoonRendererPlatform(...)`:

```kotlin
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun MoonRendererPlatform(state: MoonRenderState, modifier: Modifier) {
    UIKitViewController(
        factory = {
            // MoonViewController is the ObjC++ class from your iosApp's pod or .mm file
            MoonViewController().apply { attach(state.toBridge()) }
        },
        modifier = modifier,
        update = { vc -> vc.update(state.toBridge()) }
    )
}
```

To make `MoonViewController` callable from Kotlin, the cleanest path is: declare it in your iosApp Xcode target as a Pod or local `.mm`, expose it as an Objective-C class with `@interface MoonViewController : UIViewController`, then use the Kotlin/Native CocoaPods plugin (`kotlin { cocoapods { pod("FilamentMoon") { ... } } }`) — see [CocoaPods DSL reference](https://kotlinlang.org/docs/multiplatform/multiplatform-cocoapods-dsl-reference.html). With AGP 9's KMP plugin you keep this in the `shared/` module's iOS-only sourceSet — the new Android plugin doesn't change anything iOS-side.

**Note on Info.plist**: per [the CMP iOS docs](https://kotlinlang.org/docs/multiplatform/compose-uikit-integration.html), add `CADisableMinimumFrameDurationOnPhone = true` so Compose runs at the device's full refresh rate — this also benefits Filament's CADisplayLink.

### Existing wrappers

Searches for `filament + compose multiplatform + ios` on GitHub return: nothing usable. The closest is [SceneView](https://github.com/SceneView/sceneview-android) — but per their [project page](https://sceneview.github.io/), iOS uses **RealityKit + SwiftUI**, not Filament. There is no public KMP/CMP wrapper for Filament-on-iOS as of April 2026. You will be the first.

## 4. Shared driving state in commonMain

Keep `MoonRenderState` an immutable value type with no platform leakage. Use `MutableStateFlow` for inbound mutations from gestures, and have each renderer **pull a snapshot per frame** rather than subscribing — this avoids cross-thread races (see §7) and a missed frame is harmless.

```kotlin
// commonMain
data class MoonRenderState(
    val cameraAzimuthRad: Float,
    val cameraElevationRad: Float,
    val cameraRadius: Float,
    val sunDirection: Vec3,        // your own value class — not Filament's float3
    val moonRotationRad: Float
)

class MoonViewModel {
    private val _state = MutableStateFlow(MoonRenderState(0f, 0f, 5f, Vec3(1f, 0f, 0f), 0f))
    val state: StateFlow<MoonRenderState> = _state.asStateFlow()

    fun onDrag(dx: Float, dy: Float) = _state.update {
        it.copy(cameraAzimuthRad = it.cameraAzimuthRad + dx * 0.005f,
                cameraElevationRad = (it.cameraElevationRad + dy * 0.005f).coerceIn(-1.5f, 1.5f))
    }
    fun onPinch(scale: Float) = _state.update { it.copy(cameraRadius = (it.cameraRadius / scale).coerceIn(1.5f, 20f)) }
    fun setSunDirection(v: Vec3) = _state.update { it.copy(sunDirection = v) }
}
```

In each platform host's frame callback:

```kotlin
val snapshot = viewModel.state.value         // atomic read of immutable data class
camera.lookAt(snapshot.cameraPosition(), origin, up)
sunLightInstance.setDirection(snapshot.sunDirection.toFloat3())
moonTransform.setRotation(snapshot.moonRotationRad)
```

Why pull-not-subscribe: Filament objects are not thread-safe; the frame callback is the only thread that touches them. `StateFlow.value` is a lock-free read of an immutable snapshot, so it's safe across the UI-thread → render-thread boundary.

## 5. Material/shader pipeline (matc / .filamat)

Per the [matc man page](https://manpages.debian.org/testing/libfilament-tools/matc.1.en.html) and [Materials guide](https://google.github.io/filament/Materials.html):

- Source: `.mat` (text). Compiled with `matc` to `.filamat` (binary blob). Cross-platform: a single `.filamat` built with `-a all -p mobile` works on both Android (GLES/Vulkan) and iOS (Metal) backends because it contains all variants.
- `matc` is a host-side tool shipped in the release tgz (Mac/Linux/Windows binaries) — **not** in the runtime libraries.

Recommended layout for your project:

```
shared/
  src/commonMain/composeResources/files/materials/moon.mat   ← source
  build/generated/filamat/moon.filamat                       ← compiled (gitignored)
```

Build via a custom Gradle task in `shared/build.gradle.kts`:

```kotlin
val matcBin = layout.projectDirectory.file("../tools/matc/matc")  // checked-in or downloaded
val compileMaterials by tasks.registering(Exec::class) {
    val src = layout.projectDirectory.file("src/commonMain/composeResources/files/materials/moon.mat")
    val out = layout.buildDirectory.file("generated/filamat/moon.filamat").get().asFile
    inputs.file(src); outputs.file(out)
    doFirst { out.parentFile.mkdirs() }
    commandLine(matcBin.asFile.absolutePath, "-a", "all", "-p", "mobile", "-o", out.absolutePath, src.asFile.absolutePath)
}
```

Wire `compileMaterials` as a dependency of `processResources` (Android) and copy the output into `commonMain/composeResources` so Compose Multiplatform's resources system ([CMP resources docs](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-multiplatform-resources.html)) picks it up. At runtime, both platforms can `Res.readBytes("files/materials/moon.filamat")` and feed the bytes to `Material.Builder().payload(buffer, size).build(engine)`. **No published Gradle plugin for matc exists** — search of Maven Central and the Filament repo confirms; you write the Exec task yourself or rely on per-platform asset folders. The `composeResources` route is cleaner because it dedupes the asset across Android/iOS bundles.

## 6. Mesh asset pipeline

Filament has **no built-in primitive on the runtime side** — neither `filament-android` nor the iOS static libs ship a sphere builder. However:

- `filament-utils-android` includes [`IcoSphere`](https://prideout.net/slides/filawasm/reference.html) (subdivided icosahedron, returns vertex/tangent/triangle arrays) — Android-only.
- `filamesh` is a host tool that converts `.obj`/`.fbx` → `.filamesh` (a fast-loading binary).
- `gltfio` loads glTF at runtime — heavier but flexible.

**v1 recommendation**: generate a UV sphere procedurally in shared `commonMain` Kotlin (~80 lines: spherical parameterisation, write positions/normals/tangents/uvs/indices into `ByteBuffer`), then upload via `VertexBuffer`/`IndexBuffer` on each platform. UV-mapping is required for the albedo + normal textures, and a UV sphere gives you a perfect `(longitude, latitude) → (u, v)` mapping for free — which you'll want anyway for the lat/lon → screen position projection used by site markers (Apollo 11, Tycho, etc.). 64x32 segments is more than enough for a smooth normal-mapped Moon at mobile resolutions. Skip `.filamesh` for v1; revisit if startup time becomes an issue.

## 7. Threading

From the README, [issue #1054](https://github.com/google/filament/issues/1054), and the [Filament docs](https://google.github.io/filament/Filament.html):

- **Engine is single-threaded from the caller's perspective.** All API calls on `Engine`, `Renderer`, `View`, `Scene`, `Camera`, `MaterialInstance`, transform manager etc. must come from **one** thread — typically the thread that created the Engine. Internally, Filament has a backend driver thread, but the public API is not thread-safe.
- On Android, the sample uses the main (UI) thread for both Compose state and Filament calls — that's safe because the Choreographer callback runs on the main thread.
- On iOS, the sample uses the main thread (CADisplayLink runs on the main runloop by default).
- **Implication for your design**: do not push `MoonRenderState` mutations directly into Filament objects from a coroutine on `Dispatchers.Default`. Instead, the gesture handler updates the `StateFlow`, and the per-frame callback (which is on the Filament thread = the main thread) reads the snapshot and applies it. This is the mailbox pattern; you don't need an explicit ring buffer because `StateFlow.value` already gives you a single atomic snapshot.

## 8. Risk and fallback

If iOS-Filament integration burns more than ~3 days (typical pain points: simulator-arm64, ObjC++ wrapper, podspec/cinterop friction), fall back:

- **F1 — Filament on Android, SceneKit on iOS.** Keep `MoonRenderState` in commonMain unchanged. Android host as in §1. iOS host: a Swift `UIViewController` containing an `SCNView` with one `SCNSphere` node, an `SCNLight(.directional)` node, and a `SCNCamera` orbiting; export through the same `UIKitViewController { ... }` factory. Cost: two renderers to maintain, but SceneKit is mature, Apple-supported, no third-party dependency, and PBR-capable (`SCNMaterial.lightingModel = .physicallyBased`).
- **F2 — Hand-rolled GLES on Android + Metal on iOS.** Two render backends, two shader sets (GLSL + MSL). Maximum control, no external deps, but you're writing your own normal-mapped PBR shader, render loop, and asset loader on both sides. Probably more total effort than F1 unless you specifically need a feature SceneKit lacks.
- **F3 — Pure Compose Canvas with a CPU sphere shader.** A `Canvas` modifier with `drawIntoCanvas { ... }` that ray-marches a sphere per pixel in Kotlin, using albedo and normal-map sampling against a Lambertian model. Tractable at low resolution (~256x256) for v0; will not animate smoothly at full screen on most phones. Useful as a literal weekend prototype to validate the UX (gestures, sun control, marker math) without any 3D pipeline at all, then throw away.

## Authoritative sources

- Filament repo: [github.com/google/filament](https://github.com/google/filament)
- Filament releases: [github.com/google/filament/releases](https://github.com/google/filament/releases)
- iOS samples README: [ios/samples/README.md](https://github.com/google/filament/blob/main/ios/samples/README.md)
- iOS hello-pbr Metal/CAMetalLayer wiring: [hello-pbr/FilamentView.mm](https://github.com/google/filament/blob/main/ios/samples/hello-pbr/hello-pbr/FilamentView.mm)
- Android sample-gltf-viewer (Choreographer + lifecycle): [MainActivity.kt](https://github.com/google/filament/blob/main/android/samples/sample-gltf-viewer/src/main/java/com/google/android/filament/gltf/MainActivity.kt)
- Filament iOS podspec: [ios/CocoaPods/Filament.podspec](https://github.com/google/filament/blob/main/ios/CocoaPods/Filament.podspec)
- SPM tracking issue: [#7350](https://github.com/google/filament/issues/7350) (open since Nov 2023, no progress)
- Threading discussion: [#1054](https://github.com/google/filament/issues/1054)
- matc manpage: [manpages.debian.org/.../matc.1](https://manpages.debian.org/testing/libfilament-tools/matc.1.en.html)
- CMP UIKit interop: [kotlinlang.org/docs/multiplatform/compose-uikit-integration.html](https://kotlinlang.org/docs/multiplatform/compose-uikit-integration.html)
- KMP CocoaPods DSL: [kotlinlang.org/docs/multiplatform/multiplatform-cocoapods-dsl-reference.html](https://kotlinlang.org/docs/multiplatform/multiplatform-cocoapods-dsl-reference.html)
- KMP iOS dependencies: [kotlinlang.org/docs/multiplatform/multiplatform-ios-integration-overview.html](https://kotlinlang.org/docs/multiplatform/multiplatform-ios-integration-overview.html)
- SceneView (alt 3D SDK, RealityKit on iOS): [sceneview.github.io](https://sceneview.github.io/)
- Filament Materials guide: [google.github.io/filament/Materials.html](https://google.github.io/filament/Materials.html)
