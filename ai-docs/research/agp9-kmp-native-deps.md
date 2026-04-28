# Wiring Filament into a `:shared` KMP module on AGP 9 + Kotlin 2.3

> Research output. Source: agent run 2026-04-28. Project context: AGP `9.0.0-alpha06`, Kotlin `2.3.20`, KMP plugin + `com.android.kotlin.multiplatform.library`, static `Shared.framework` for `iosArm64`/`iosSimulatorArm64`, separate Xcode project at `iosApp/`.

## 1. Android target — Filament's AAR

**Coordinates and current version** (from Maven Central). Filament publishes under `com.google.android.filament`; latest is **1.71.1** (April 2026). Component versions are released together but not always identical:

- `com.google.android.filament:filament-android:1.71.1` — core engine (mandatory)
- `com.google.android.filament:gltfio-android:1.71.1` — glTF 2.0 loader (only if you load glTF assets; for v1 with a UV sphere, this is optional)
- `com.google.android.filament:filament-utils-android:1.71.1` — KTX loader, Kotlin math, `ModelViewer`, gesture/orbit camera helpers (recommended — saves you from rolling your own orbit camera)
- `com.google.android.filament:filamat-android:1.71.1` — runtime material compiler. Big and only needed if you compile materials at runtime; bundle pre-compiled `.filamat` from `composeResources/files/` instead. Skip it.
- `com.google.android.filament:filament-android-debug:1.71.1` — debug-instrumented variant; unused in this single-variant plugin (see below).

Sources: [filament-android on Maven Central](https://central.sonatype.com/artifact/com.google.android.filament/filament-android), [google/filament GitHub](https://github.com/google/filament), [Filament releases](https://github.com/google/filament/releases).

**Source set placement.** With `com.android.kotlin.multiplatform.library`, Android-specific deps go in `androidMain.dependencies { }` inside the `sourceSets` block — same place you already use `commonMain.dependencies { }`. That is the official location per [Set up the Android Gradle Library Plugin for KMP](https://developer.android.com/kotlin/multiplatform/plugin) and [Updating multiplatform projects with Android apps to use AGP 9](https://kotlinlang.org/docs/multiplatform/multiplatform-project-agp-9-migration.html). Note: the source set name is `androidMain` (not `androidUnitTest`/`androidTest`); the new plugin renames host/device test sets to `androidHostTest` and `androidDeviceTest`.

Add to `:shared/build.gradle.kts` inside the existing `sourceSets { … }`:

```kotlin
sourceSets {
    commonMain.dependencies { /* unchanged */ }
    androidMain.dependencies {
        implementation("com.google.android.filament:filament-android:1.71.1")
        implementation("com.google.android.filament:filament-utils-android:1.71.1")
        // add gltfio-android only when you start loading .glb assets
    }
}
```

And add to `gradle/libs.versions.toml`:

```toml
[versions]
filament = "1.71.1"
[libraries]
filament-android        = { module = "com.google.android.filament:filament-android",       version.ref = "filament" }
filament-utils-android  = { module = "com.google.android.filament:filament-utils-android", version.ref = "filament" }
```

**APK size and ABI filter — important caveat.** Each Filament ABI ships ~5–7 MB of native `.so` (libfilament-jni). Shipping `arm64-v8a` + `armeabi-v7a` + `x86_64` adds ~18–20 MB to a fat APK. For a mobile-only release, **drop `armeabi-v7a` and `x86_64` from the release APK and ship arm64-only via Play App Bundle splits** — every modern Android device since Android 10 is arm64. Keep `x86_64` enabled in debug builds for emulators.

The catch: the AGP 9 KMP library plugin **does not expose** `ndk { abiFilters = … }` or `packagingOptions` — the new DSL deliberately removes the legacy `android {}` extension surface ([AGP 9.0 release notes](https://developer.android.com/build/releases/agp-9-0-0-release-notes), "the `android` DSL classes now implement only new public interfaces"). Library variants/flavors aren't supported either ([JetBrains migration doc](https://blog.jetbrains.com/kotlin/2026/01/update-your-projects-for-agp9/)). So:

- The `:shared` module ships **all** ABIs that Filament publishes (`arm64-v8a`, `armeabi-v7a`, `x86_64`) inside its AAR.
- Apply the ABI filter in `:androidApp` (which is `com.android.application` and still has the full `android {}` DSL): `android.defaultConfig.ndk.abiFilters += setOf("arm64-v8a")`, plus enable App Bundle ABI splits in `android.bundle.abi.enableSplit = true`. This is the place where ABI selection actually controls your shipped artifact.

**Other AGP 9 quirks vs. the legacy combo:**
- **No `debugImplementation` / `releaseImplementation`** — single-variant plugin. Use `androidRuntimeClasspath(...)` for runtime-only artifacts, or just don't ship the debug-instrumented `filament-android-debug`.
- **Resources are off by default**; you already enabled them with `androidResources { enable = true }` — keep it.
- **R8 / minification** lives in `:androidApp`. Filament classes use JNI heavily — add a Filament keep rule there. The KMP library can ship consumer keep rules: `kotlin.android.optimization { consumerKeepRules.publish = true }` (per the AGP 9 KMP plugin doc).
- **Native lib stripping**: AGP 9 strips on release by default; nothing to do.
- **No BuildConfig generation** — irrelevant here.

## 2. iOS target — comparing the four routes

### a. CocoaPods via `kotlin("native.cocoapods")`

**Filament does publish a CocoaPods spec.** The pod is named `Filament` (capital F), source: `ios/CocoaPods/Filament.podspec` in the repo, current version `1.71.1`, install line: `pod 'Filament', '~> 1.71.1'` ([Filament README](https://github.com/google/filament/blob/main/README.md), [Filament iOS samples README](https://github.com/google/filament/blob/main/ios/samples/README.md)). The pod ships static archives (`libfilament.a`, `libbackend.a`, `libfilabridge.a`, `libfilaflat.a`, `libgeometry.a`, `libutils.a`, `libsmol-v.a`) for `arm64` device + `arm64` simulator.

> **CONFLICT FLAG with the Filament integration agent**, which says the pod **excludes** arm64 simulator. Resolve by reading the actual current podspec content before committing.

Block to add to `:shared/build.gradle.kts` — note `kotlin("native.cocoapods")` is a **separate plugin** that has to be applied:

```kotlin
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    kotlin("native.cocoapods") version libs.versions.kotlin.get()
}
kotlin {
    cocoapods {
        version       = "1.0"
        summary       = "Shared Moon Explorer KMP module"
        homepage      = "https://example.com"
        ios.deploymentTarget = "13.0"
        framework {
            baseName = "Shared"
            isStatic = true
        }
        pod("Filament") {
            version = "~> 1.71.1"
            // headers exposed: filament/Engine.h, filament/Scene.h, etc.
            // language is C++ — see section 3 for the wrapper
        }
    }
}
```

**Will this work?** Mechanically yes — `native.cocoapods` is compatible with `com.android.kotlin.multiplatform.library` (they touch different targets). But there are two real problems for *this* project:

1. The plugin generates a podspec for `Shared` and expects the iOS app to consume `Shared` *as a pod*. Your `iosApp/` is a plain Xcode project, not a Podfile workspace. Switching it to CocoaPods is invasive and going against the industry direction (CocoaPods trunk goes read-only Dec 2 2026).
2. **Filament's public API is C++** — the pod gives you `.a` archives plus C++ headers. Kotlin/Native cinterop cannot consume C++ headers directly (see section 3). The pod-import path doesn't bypass this; you'd still need a wrapper. So the pod buys you nothing over route (c) here.

**Verdict for route (a): not recommended.** The CocoaPods bridge is cleanest when the third-party API is Objective-C (e.g., Firebase, SDWebImage). For C++ Filament it doesn't help.

### b. Swift Package Manager via `swiftPMDependencies`

Status as of late 2025/early 2026: the new SPM import DSL exists but is **experimental** and requires **Kotlin Multiplatform Gradle plugin 2.4.0-Beta2 or later** ([Adding Swift packages as dependencies to KMP modules](https://kotlinlang.org/docs/multiplatform/multiplatform-spm-import.html)). You're on **2.3.20**, so this DSL is not available to you — full stop. Even if you upgraded to a 2.4.x beta, the docs explicitly say "pure Swift pods are not supported" ([Adding iOS dependencies](https://kotlinlang.org/docs/multiplatform/multiplatform-ios-dependencies.html)) and the same constraint applies to SPM packages whose interface is C++.

**Filament does not publish to SPM.** No `Package.swift` in the Filament repo; iOS distribution is CocoaPods + tarball releases only.

**Verdict for route (b): unavailable on Kotlin 2.3.20, and Filament wouldn't ship via it anyway.** Reasonable to revisit when KMP `2.4.x` stabilises and *if* Filament adds an SPM target, but neither is true today.

### c. Direct framework + cinterop

Download `filament-v1.71.1-ios.tgz` from [Filament releases](https://github.com/google/filament/releases), extract under `:shared/src/iosMain/resources/filament/` (or anywhere outside `src/`; put it under `shared/native/filament/` to keep it out of resource packaging). The tarball contains static archives for arm64 device and arm64 simulator and the `include/` headers.

`shared/src/nativeInterop/cinterop/filament.def`:

```
language = Objective-C
package = filament
modules =

# C wrappers we'll write — see section 3
headers = filament_c_api.h

# include search path for the C wrapper header and Filament headers
compilerOpts.ios_arm64           = -I/path/to/shared/native/filament/include -I/path/to/shared/native/wrapper/include
compilerOpts.ios_simulator_arm64 = -I/path/to/shared/native/filament/include -I/path/to/shared/native/wrapper/include

# link Filament + our wrapper, plus required system frameworks
linkerOpts.ios_arm64 = \
    -L/path/to/shared/native/filament/lib/arm64 \
    -lfilament -lbackend -lfilabridge -lfilaflat -lgeometry -lutils -lsmol-v \
    -L/path/to/shared/native/wrapper/lib/arm64 -lfilament_c \
    -framework Metal -framework MetalKit -framework Foundation -framework CoreGraphics -framework QuartzCore \
    -lc++
linkerOpts.ios_simulator_arm64 = \
    -L/path/to/shared/native/filament/lib/arm64-simulator \
    -lfilament -lbackend -lfilabridge -lfilaflat -lgeometry -lutils -lsmol-v \
    -L/path/to/shared/native/wrapper/lib/arm64-simulator -lfilament_c \
    -framework Metal -framework MetalKit -framework Foundation -framework CoreGraphics -framework QuartzCore \
    -lc++
```

`.def` keys reference: [Interoperability with C](https://kotlinlang.org/docs/native-c-interop.html) — `headers`, `package`, `language`, `compilerOpts`, `linkerOpts`, `modules`. Per-target suffixes (`.ios_arm64`, `.ios_simulator_arm64`) are how you give different paths/libs per Konan target.

Cinterop block in `:shared/build.gradle.kts`, added inside `kotlin { … }`:

```kotlin
listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
    target.compilations.getByName("main") {
        cinterops.create("filament") {
            defFile(project.file("src/nativeInterop/cinterop/filament.def"))
        }
    }
}
```

**Verdict for route (c): viable, but requires the C wrapper from section 3 and is the most code to maintain.**

### d. Hybrid — Filament purely on the Swift side of `iosApp/`

Keep `:shared` Filament-free. Define a Kotlin renderer-host interface in `commonMain`:

```kotlin
// commonMain
expect class MoonRenderer {
    fun setSunDirection(x: Float, y: Float, z: Float)
    fun setCameraOrbit(yaw: Float, pitch: Float, distance: Float)
    fun loadMoonAssets(albedo: ByteArray, normal: ByteArray)
}
```

`iosMain` declares `actual class MoonRenderer` as a thin Kotlin class that holds an opaque pointer or just calls into a Swift protocol exposed back to Kotlin. The Swift side in `iosApp/` implements the renderer with `pod 'Filament'` directly in the iosApp Podfile (or builds Filament from the tarball into an Xcode target — fully native iOS territory). Compose drives the renderer through `UIKitViewController { … }` which instantiates a Swift `MoonRendererViewController`.

**This is the recommended route.** Reasoning:

1. Filament's API is C++. Calling C++ from Swift is *first-class* (Swift 5.9+ has direct C++ interop and Objective-C++ has always worked). Calling C++ from Kotlin/Native requires a hand-written wrapper layer (section 3). On iOS you have a host language that already speaks Filament.
2. Android side stays clean: `filament-android` from Maven, JNI handled by Filament itself.
3. `Shared.framework` stays small — no Filament symbols leak into it, no static archive ballast (~30 MB of arm64 + simulator archives), faster Kotlin/Native link times.
4. Keeps your iOS app as a regular Xcode project — no forced CocoaPods migration of `iosApp/`. (The Podfile only lives inside `iosApp/`, separate from `:shared`.)
5. The "renderer-host interface" pattern is exactly how SceneView, RealityKit, and the Android Filament samples are structured anyway — it matches your `ai-docs/initial-idea.md` plan ("platform-specific renderer host on Android/iOS").

The downside is two renderer implementations (Kotlin/JNI on Android, Swift on iOS), which means two places where shading/material code can drift. Mitigate this by keeping all material parameters and scene state in `commonMain` and only treating the platform side as a thin "submit frame, push uniforms" layer.

## 3. Kotlin/Native cinterop with C++

`cinterop` is fundamentally a **C tool** (and Objective-C, since Objective-C is C-derived). It does not consume C++ headers — name mangling, templates, virtuals, and exceptions all break. The [official C interop docs](https://kotlinlang.org/docs/native-c-interop.html) only describe `language = C` and `language = Objective-C`; there is no `language = C++`. This is an explicit, long-standing limitation (see [kotlin-native#2835](https://github.com/JetBrains/kotlin-native/issues/2835) and the [Kotlin Discussions thread on building C++ into a framework](https://discuss.kotlinlang.org/t/how-can-i-build-c-code-into-framework-with-cinterop/28438)).

Two recommended approaches, both required if you take route (c):

**Option 1 — Pure C wrapper (`filament_c_api.h` + `filament_c_api.cpp`).** Write a tiny C ABI in front of Filament:

```cpp
// filament_c_api.h
#ifdef __cplusplus
extern "C" {
#endif
typedef struct FmEngine FmEngine;
typedef struct FmScene  FmScene;
FmEngine* fm_engine_create(void* metalDevice);
void      fm_engine_destroy(FmEngine*);
FmScene*  fm_scene_create(FmEngine*);
void      fm_set_sun_direction(FmScene*, float x, float y, float z);
void      fm_render_frame(FmEngine*, FmScene*, void* caMetalLayer);
#ifdef __cplusplus
}
#endif
```

The `.cpp` includes `<filament/Engine.h>` etc. and forwards. Compile both archs into `libfilament_c.a` with a small CMake script invoked from a Gradle `Exec` task. Cinterop then reads `filament_c_api.h` with `language = C` and you get clean Kotlin bindings.

**Option 2 — Objective-C++ shim (`.mm` files).** Wrap Filament in `@interface MoonRenderer : NSObject` whose `.mm` implementation imports `<filament/...>`. Cinterop reads the Objective-C `.h` header (`language = Objective-C`, `modules = MoonRendererKit`). The advantage: Kotlin sees real Objective-C classes with ARC-managed lifetimes. This is the standard pattern for wrapping Swift-only APIs ([Going Swiftly](https://dev.to/ttypic/going-swiftly-using-a-swift-only-libraries-in-your-kotlin-multiplatform-app-1ml9), [Stripe walkthrough](https://medium.com/@soumenpal3241/using-native-ios-sdks-in-kotlin-compose-multiplatform-a-cinterop-walkthrough-with-stripe-a7bb5f23d53c)) and works equally well for C++.

**No published Filament Kotlin/Native bindings.** Searching produced zero "filament-kotlin-native" projects. The Android Filament Java/Kotlin bindings live in the Filament repo's `android/` tree but are JNI-based and don't translate to Kotlin/Native.

If you take route (c), use **Option 2** — Objective-C++ is less hand-written boilerplate and gives you class-shaped bindings instead of opaque pointers. But this is exactly the wrapper code that already exists naturally in route (d), written in Swift instead of `.mm`. Which is why route (d) wins.

## 4. Asset packaging for 50–200 MB Moon textures

Three options on the table:

**Compose Multiplatform `composeResources/files/`** ([usage docs](https://kotlinlang.org/docs/multiplatform/compose-multiplatform-resources-usage.html)). Files placed under `shared/src/commonMain/composeResources/files/...` are packed as Android `assets/` and as bundle files on iOS, accessed with `Res.readBytes("files/moon_albedo.ktx")`. The docs **do not impose a size limit** on mobile platforms (only web has the 244 KiB warning). It works for binary blobs of this size, but every byte goes into the APK + IPA. With 50–200 MB of textures, install size grows by that amount (compressed PNG/JPEG; KTX2 with BasisU compression is much better — budget for 30–80 MB compressed if you use BasisU). Update friction: any texture change forces a full app update.

**Per-platform raw resources** (`androidMain/resources/`, iOS bundle copy phase). Same install-size cost. Splits texture maintenance across two trees — worse than `composeResources` and offers nothing in return.

**On-demand CDN download.** Ship the app at ~30 MB (engine + UI + a tiny baked-in 2K fallback texture) and stream the high-res tile pyramid post-install. Cache to platform `Cache`/`Files` directory, validate with ETag/Hash. Update friction near zero — texture revisions don't need an app store release. Requires a first-run download UX (~30 sec on Wi-Fi for 80 MB) and offline behaviour design (fall back to baked-in 2K texture if cache empty and offline).

**Recommendation:** **bundle a small fallback (one ~5 MB 2K equirectangular albedo + 2 MB normal map) in `composeResources/files/` and stream the high-res 8K tile pyramid from CDN on first launch.** Reasoning:

- Keeps Play Store install <50 MB so you avoid the [50 MB asset-pack threshold warning](https://support.google.com/googleplay/android-developer/answer/9859372) and stay well under iOS's 4 GB IPA hard limit with comfortable margin for the binary itself.
- App is usable instantly with the 2K texture (good enough for the "see the Moon, rotate, zoom" first-30-seconds experience your `ai-docs/initial-idea.md` describes).
- High-res textures are revisable without app updates — important when iterating on lighting/normal maps in early development.
- Mature CDN behavior (Cloudflare R2, GitHub Releases, S3) is trivial to add later via Ktor client in `commonMain`.
- Textures are static and cacheable — perfect CDN workload, no auth needed.

Avoid putting 200 MB into the bundled APK/IPA in v1. You'll regret it the first time you re-tile or compress textures differently.

## 5. Gotchas in `com.android.kotlin.multiplatform.library`

Definitive list from the [AGP 9.0 release notes](https://developer.android.com/build/releases/agp-9-0-0-release-notes), [Set up the Android Gradle Library Plugin for KMP](https://developer.android.com/kotlin/multiplatform/plugin), [Updating multiplatform projects with Android apps to use AGP 9](https://kotlinlang.org/docs/multiplatform/multiplatform-project-agp-9-migration.html), and [the JetBrains AGP 9 blog post](https://blog.jetbrains.com/kotlin/2026/01/update-your-projects-for-agp9/):

- **No `android {}` block.** All Android config lives inside `kotlin { android { … } }`. You already do this correctly. `applicationVariants`/`libraryVariants` APIs are removed — use `androidComponents.onVariants` if you need variant-aware behavior.
- **No build variants/flavors, no `debugImplementation` / `releaseImplementation`.** Use `androidRuntimeClasspath(...)` for runtime-only deps. This rules out shipping `filament-android-debug` alongside `filament-android` the legacy way.
- **No `android.ndk { abiFilters = … }` or `packagingOptions {}` DSL.** ABI filtering and `.so` packaging policies move to the consuming app module (`:androidApp`). For Filament that means: the AAR brings in all four ABIs; you filter at app level.
- **Source set names are different.** Main is `androidMain`; tests are `androidHostTest` / `androidDeviceTest` (your `withHostTest {}` opts into host tests). The legacy `androidTest`/`androidUnitTest` names are gone.
- **Resources are off by default.** You correctly opted in with `androidResources { enable = true }`.
- **No BuildConfig generation.** Use BuildKonfig or hand-rolled `expect val` if you need flags.
- **No Data Binding / View Binding.** Compose-only.
- **Same module cannot apply `org.jetbrains.kotlin.multiplatform` and `com.android.library` (or `com.android.application`) together.** Your `:androidApp` module must stay separate — it does.
- **Built-in Kotlin support enabled by default in AGP 9.** You no longer need `org.jetbrains.kotlin.android` on the `:androidApp` module. Confirm and remove it from `androidApp/build.gradle.kts` if it's there.
- **Consumer R8 keep rules** must be opted-in: `kotlin.android.optimization { consumerKeepRules.publish = true; consumerKeepRules.file("consumer-rules.pro") }` — relevant because Filament JNI bindings need keep rules and you'll want them to flow to `:androidApp` automatically.
- **`androidDependencies` and `sourceSets` reporting tasks were removed** (release notes). If your CI scrapes them, switch to `dependencies` task or the variant API.
- **Known bug fixed:** `com.android.kotlin.multiplatform.library` previously crashed with Gradle Managed Devices ([issuetracker.google.com/436887358](https://issuetracker.google.com/issues/436887358)) — fixed in 9.0.0; you're on `9.0.0-alpha06` and could still hit fallout from related issues if you wire GMDs.
- **`kotlin("native.cocoapods")` (route a) is compatible** with this plugin — they touch separate targets. But CocoaPods fundamentally pulls Filament *into* `Shared.framework`, which the Xcode app then sees with C++ symbols it cannot reach from Swift through Kotlin's framework header — that's a strict no-go without a wrapper layer.

## Bottom line / recommended setup

- **Android side:** add `filament-android` and `filament-utils-android` to `androidMain.dependencies` in `:shared`. Apply `arm64-v8a`-only ABI filter in `:androidApp` (because the new KMP plugin lacks the DSL for it).
- **iOS side:** route (d) — keep `:shared` Filament-free, define a Kotlin renderer-host `expect class`, implement the actual renderer in Swift inside `iosApp/` using `pod 'Filament', '~> 1.71.1'` in the iosApp's Podfile. Bridge through `UIKitViewController { … }` from Compose. This avoids the C++/cinterop morass entirely and keeps `Shared.framework` lean.
- **Skip cinterop and CocoaPods on the `:shared` module itself.** SPM via `swiftPMDependencies` isn't available on Kotlin 2.3.20 anyway (needs ≥2.4.0-Beta2) and Filament doesn't publish via SPM.
- **Assets:** small bundled fallback in `composeResources/files/`, stream high-res tile pyramid from CDN.

## Files referenced in `MoonExplorer/`

- `shared/build.gradle.kts`
- `gradle/libs.versions.toml`
- `build.gradle.kts`
- `settings.gradle.kts`
- `ai-docs/initial-idea.md`

## Sources

- [Set up the Android Gradle Library Plugin for KMP](https://developer.android.com/kotlin/multiplatform/plugin)
- [Android Gradle plugin 9.0 release notes](https://developer.android.com/build/releases/agp-9-0-0-release-notes)
- [Updating multiplatform projects with Android apps to use AGP 9 (Kotlin docs)](https://kotlinlang.org/docs/multiplatform/multiplatform-project-agp-9-migration.html)
- [Update your Kotlin projects for Android Gradle Plugin 9.0 (JetBrains blog)](https://blog.jetbrains.com/kotlin/2026/01/update-your-projects-for-agp9/)
- [Interoperability with C — Kotlin docs (.def file format)](https://kotlinlang.org/docs/native-c-interop.html)
- [Adding iOS dependencies — Kotlin docs (cinterop + CocoaPods)](https://kotlinlang.org/docs/multiplatform/multiplatform-ios-dependencies.html)
- [Adding Swift packages as dependencies to KMP modules (swiftPMDependencies, requires KGP 2.4.0-Beta2+)](https://kotlinlang.org/docs/multiplatform/multiplatform-spm-import.html)
- [Switch Kotlin Multiplatform project from CocoaPods to SwiftPM dependencies](https://kotlinlang.org/docs/multiplatform/multiplatform-cocoapods-spm-migration.html)
- [Compose Multiplatform resources — usage (composeResources/files, Res.readBytes)](https://kotlinlang.org/docs/multiplatform/compose-multiplatform-resources-usage.html)
- [google/filament README](https://github.com/google/filament/blob/main/README.md)
- [google/filament iOS samples README](https://github.com/google/filament/blob/main/ios/samples/README.md)
- [filament-android on Maven Central](https://central.sonatype.com/artifact/com.google.android.filament/filament-android)
- [filament-utils-android on Maven Central](https://central.sonatype.com/artifact/com.google.android.filament/filament-utils-android)
- [Filament releases (1.71.1, April 2026)](https://github.com/google/filament/releases)
- [kotlin-native#2835 — using C++ via cinterop with C wrappers](https://github.com/JetBrains/kotlin-native/issues/2835)
- [Kotlin Discussions: building C++ into a framework with cinterop](https://discuss.kotlinlang.org/t/how-can-i-build-c-code-into-framework-with-cinterop/28438)
