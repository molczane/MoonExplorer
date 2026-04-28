# Moon Explorer Tech Stack

> Pinned versions, plugins, libraries, and rationale. Authoritative for dependency choices. Bumps go through ADRs.

## Pinned Versions

| Component | Version | Source / Note |
|---|---|---|
| Kotlin | 2.3.20 | `gradle/libs.versions.toml` |
| AGP | 9.0.0-alpha06 | `com.android.kotlin.multiplatform.library` plugin (new in AGP 9) |
| Gradle | 9.1.0 | `gradle/wrapper/gradle-wrapper.properties` |
| Compose Multiplatform | 1.10.3 | shared module |
| Material3 (Compose) | 1.10.0-alpha05 | `compose-material3` |
| AndroidX Activity Compose | 1.13.0 | androidApp |
| AndroidX Lifecycle | 2.10.0 | viewmodel-compose, runtime-compose |
| AndroidX Core | 1.18.0 | androidApp |
| JVM target | 11 | `compilerOptions { jvmTarget = JVM_11 }` |
| compileSdk | 36 | shared + androidApp |
| minSdk | 24 | shared + androidApp |
| targetSdk | 36 | androidApp |
| iOS deployment target | 13.0 | iosApp + (when added) cocoapods DSL |
| Filament | 1.71.x | per ADR-0001, ADR-0002 |
| Koog (Phase 3) | 0.8.x or later | per ADR-0005, deferred |

## Plugins (root + modules)

| Plugin | Where | Version |
|---|---|---|
| `org.jetbrains.kotlin.multiplatform` | `:shared` | 2.3.20 |
| `com.android.kotlin.multiplatform.library` | `:shared` | 9.0.0-alpha06 |
| `org.jetbrains.compose` | `:shared` | 1.10.3 |
| `org.jetbrains.kotlin.plugin.compose` | `:shared`, `:androidApp` | 2.3.20 |
| `com.android.application` | `:androidApp` | 9.0.0-alpha06 |

Plugin aliases in root `build.gradle.kts` use `apply false`; concrete application happens in module `build.gradle.kts` files.

## Libraries (commonMain)

| Library | Coords | Use |
|---|---|---|
| Compose runtime | `org.jetbrains.compose.runtime:runtime` | core |
| Compose foundation | `org.jetbrains.compose.foundation:foundation` | layout |
| Compose Material3 | `org.jetbrains.compose.material3:material3` | UI |
| Compose UI | `org.jetbrains.compose.ui:ui` | core |
| Compose components-resources | `org.jetbrains.compose.components:components-resources` | `Res` API |
| Compose UI tooling preview | `org.jetbrains.compose.ui:ui-tooling-preview` | @Preview |
| Lifecycle ViewModel Compose | `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose` | ViewModel host |
| Lifecycle Runtime Compose | `org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose` | lifecycle ↔ Compose |

## Libraries (androidMain) — Phase 1 additions

| Library | Coords | Use |
|---|---|---|
| Filament Android | `com.google.android.filament:filament-android:1.71.x` | renderer (Phase 0+) |
| Filament Utils Android | `com.google.android.filament:filament-utils-android:1.71.x` | KTX loader, math, helpers |

Per `ai-docs/research/agp9-kmp-native-deps.md` §1, these go in `androidMain.dependencies { ... }` of `:shared`.

> **Open question (verify before Phase 0):** the two completed research agents disagreed on whether the renderer host should live in `:shared/androidMain` or in `:androidApp`. Resolution depends on whether `MoonViewport`'s `actual` Composable lives in `:shared` (then deps go to `:shared/androidMain`) or in `:androidApp` (then deps go there). Lean: `:shared/androidMain` to match the iOS symmetry where `MoonViewport`'s `actual` lives in `:shared/iosMain` (calling into `iosApp/`'s Swift via Compose UIKit interop). To be locked in `00-renderer-spike/plan.md`.

## Libraries (iosApp/) — Phase 1 additions

CocoaPods Podfile in `iosApp/`:

```ruby
pod 'Filament', '~> 1.71.1'
```

`Shared.framework` does **not** depend on Filament. Filament lives entirely on the iosApp side per ADR-0002.

## Tooling

| Tool | Where | Use |
|---|---|---|
| `matc` | Filament release tarball, vendored under `tools/matc/` | compile `.mat` → `.filamat` |
| `toktx` | KTX-Software (https://github.com/KhronosGroup/KTX-Software) | PNG/TIFF → KTX2 + Basis Universal |
| `basisu` | Basis Universal (https://github.com/BinomialLLC/basis_universal) | alternative KTX2 transcoder |
| Python 3.11+ with NumPy + Pillow | system | normal-map baking from LDEM, see `tools/bake-normal-map/` |
| ImageMagick | system | TIFF format conversions |

Tools are checked into `tools/` where binary distribution permits; otherwise the spec for the relevant feature documents the install command.

## Forbidden / Avoided

| What | Why | Alternative |
|---|---|---|
| Pure-Compose 3D rendering | Cannot sustain 60 FPS at full screen | Filament (per ADR-0001) |
| Filament symbols inside `Shared.framework` | Bloats framework, leaks C++ via unstable interop | Filament on Swift side (per ADR-0002) |
| `kotlin("native.cocoapods")` on `:shared` | Forces CocoaPods workspace on iosApp; doesn't bypass C++ wrapper need | Plain `iosApp/Podfile` |
| `swiftPMDependencies` on `:shared` | Requires Kotlin ≥2.4.0-Beta2; we're on 2.3.20 | CocoaPods in iosApp/ |
| `ai.koog:koog-agents` in any module before Phase 3 | Alpha-stability churn; migration debt | `MoonExplorerActions` interface in commonMain (per ADR-0005) |
| Build variants / flavors in `:shared` | Not supported by `com.android.kotlin.multiplatform.library` | Flavor lives in `:androidApp` if needed |
| `android {}` block in `:shared` | Removed in AGP 9 KMP plugin | `kotlin { android { ... } }` |
| ABI filters in `:shared` | `ndk.abiFilters` not exposed by AGP 9 KMP plugin | Apply in `:androidApp` |
| `BuildConfig` generation | Not in AGP 9 KMP plugin | Hand-rolled `expect val` or BuildKonfig |
| Annotation-based Koog tools (`@Tool`) | JVM-only | Class-based `Tool<Args,Result>` |
| West-positive longitude | Contradicts modern IAU convention | East-positive (per ADR-0006) |
| NASA insignia/logotype/worm in app | NASA brand, not public domain | Plain attribution string only (per ADR-0004) |

## Update Policy

- **Patch bumps** (e.g., 1.10.3 → 1.10.4): no ADR; documented in commit message.
- **Minor bumps** (e.g., 2.3.20 → 2.3.21): no ADR; documented in commit message; smoke-test all platforms.
- **Major bumps** (e.g., Compose MP 1.10 → 1.11; Kotlin 2.3 → 2.4; AGP 9 → 10): require ADR; pilot on a feature branch first.
- **New library**: requires reference in either an ADR or a feature `plan.md`. No "I added Ktor because we needed HTTP" without a doc.
- **Breaking change in alpha dep** (Filament, AGP alpha, Koog): pin a version, read upstream release notes, file an ADR if affected.

## Verification commands

```bash
./gradlew :androidApp:assembleDebug                  # Android build
./gradlew :shared:embedAndSignAppleFrameworkForXcode # iOS framework for Xcode
./gradlew :shared:testAndroidHostTest                # Android JVM unit tests
./gradlew :shared:iosSimulatorArm64Test              # iOS simulator tests
./gradlew :shared:allTests                           # all platform tests
```

## References

- `ai-docs/research/filament-cmp-integration.md`
- `ai-docs/research/agp9-kmp-native-deps.md`
- `ai-docs/research/koog-framework.md`
- ADR-0001 .. ADR-0007
- [AGP 9.0 release notes](https://developer.android.com/build/releases/agp-9-0-0-release-notes)
- [JetBrains AGP 9 migration blog](https://blog.jetbrains.com/kotlin/2026/01/update-your-projects-for-agp9/)
