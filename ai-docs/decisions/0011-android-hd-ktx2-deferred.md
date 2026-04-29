# ADR-0011: Android HD KTX2 streaming — deferred; ship 2K PNG on both platforms

**Status**: Accepted
**Date**: 2026-04-29
**Refines**: ADR-0004 §"Packaging" (asset format) — the bundled-tier format pivots from KTX2 to PNG; the HD CDN tier remains KTX2, but is consumed by iOS only for now.
**Supersedes**: —

## Context

ADR-0004 picked **KTX2 + Basis Universal** as the on-disk asset format for both the bundled 2 K tier and the HD 8 K CDN tier. The promise: one container, one set of build artefacts, transcoded to GPU-native (ASTC / ETC2 / BC7) at load time by Filament's `Ktx2Reader`. The iOS `Filament/ktxreader` CocoaPods subspec exposes `Ktx2Reader` directly. The Phase 0 spike used PNG placeholders; this spec was supposed to replace them with KTX2 on both platforms.

Implementing T115 (Android KTX2 path) surfaced a hard upstream gap:

- **Filament 1.71.x's Android Java/Kotlin bindings (latest is `v1.71.2`) ship `KTX1Loader` only.** There is **no public `Ktx2Reader` Java binding** in `filament-android`, `filament-utils-android`, or `gltfio-android`. The C++ `Ktx2Reader` exists in `libs/ktxreader/` and is statically linked into Filament's NDK distribution, but no JNI shim surfaces it to Kotlin.
- `libfilament-jni.so` (the `.so` shipped inside the `filament-android` AAR) was inspected with `nm`; it does **not** export `Ktx2Reader::*` symbols — they are linker-private.
- `toktx --encode astc` always emits **KTX2** in `toktx` 4.4.x (regardless of `--t1`). KTX-Software effectively deprecated KTX1 + compressed for new pipelines; `KTX1Loader` can read KTX1 + ASTC if the bytes exist, but `toktx` won't produce them.

Three workable paths were evaluated:

| Path | Effort | Trade-off |
|---|---|---|
| **A. KTX1 + ASTC via `astcenc` + custom KTX1 packer** | 1–2 hrs | New tooling (`astcenc` + ~150 LOC Python). Open question on `Ktx1Reader` ASTC mapping coverage. Loses Basis Universal's transcode-at-load-time portability. |
| **B. JNI wrapper for `Ktx2Reader` on Android** | 3–5+ hrs of build-infra work (NDK install, `externalNativeBuild` integration with the new `androidKmpLibrary` plugin on AGP 9 alpha, static link order across 30+ Filament libs, runtime symbol-overlap risk with the existing `libfilament-jni.so`) | Architecturally correct. The right answer long-term. Significant complexity for a hobby project on alpha AGP. |
| **C. Defer Android HD; ship 2 K PNG bundled, KTX2 HD on iOS only** | <2 hrs | Preserves the user-visible win (real NASA textures on the Moon) on both platforms. Defers HD-on-Android to a follow-up spec when AGP 9 stabilises and a JNI wrapper can be tackled with a clear head. |

## Decision

Adopt **Option C (narrowed)**:

1. **Bundled 2 K tier — PNG, both platforms.** `composeResources/files/textures/moon_albedo_2k.png` + `moon_normal_2k.png` ship with the app. Android decodes via `BitmapFactory`; iOS decodes via the existing `decodePngToRgba8` in `MoonRenderer.mm`. Same path the Phase 0 spike used, just with real NASA-derived bytes instead of placeholder quadrants.
2. **HD 8 K tier — KTX2 + Basis Universal, iOS only.** The `assets-v1` GH Release stays in place; the bundled `manifest.json` still points at `https://github.com/molczane/MoonExplorer/releases/download/assets-v1/...`. iOS fetches and binds via `Ktx2Reader`. Android **does not** fetch HD — the loader skips the network entirely.
3. **`isHdStreamingSupported` expect/actual.** A single boolean flag in `assets/`: `true` on iOS, `false` on Android. `MoonAssetLoader` consults it to gate the HD fetch.
4. **Future spec for Android HD KTX2.** When AGP 9 stabilises (or Filament publishes a `Ktx2Reader` Java binding upstream), file `0Y-android-hd-ktx2-jni` covering: NDK install, JNI shim, build wiring, runtime test. Until then, Android stays at the bundled 2 K tier.

The KTX2 + Basis tooling stays in `tools/build-ktx2/` for the iOS HD path. The bundled 2 K KTX2 files (which were committed in `a877beb`) are deleted from `composeResources/`; iOS reads PNG for 2 K bundled to keep the loader symmetric across platforms.

## Rationale

- **Visual goal stays met.** The whole point of `02-moon-renderer-mvp` was "see the real Moon, not colored quadrants." 2 K NASA SVS imagery delivers that on every Pixel and iPhone.
- **The asymmetry is bounded.** Only HD streaming differs between platforms. Both have the same loader contract; iOS just executes one extra branch.
- **Avoids alpha-AGP rabbit holes.** AGP 9 alpha06 + the new `com.android.kotlin.multiplatform.library` plugin's interaction with `externalNativeBuild` is uncharted. Opting out keeps the build green.
- **Preserves the JNI escape hatch.** Nothing about this ADR forecloses Option B. The bake pipeline (`tools/build-ktx2/`), the GH Release (`assets-v1`), the manifest schema, and `Ktx2Reader` on iOS are all in place. A future spec adding `JNIKtx2Loader` slots in cleanly: it just changes `isHdStreamingSupported` to `true` on Android.
- **Bundle size.** 2 K PNG total ~6.3 MB (3.2 MB albedo + 3.1 MB normal). Slightly over ADR-0004's 5 MB target but well under the Play Store 50 MB warning. Spec NFR amended in `02-moon-renderer-mvp/spec.md`.

## Alternatives rejected

- **Option A (`astcenc` + custom KTX1 packer).** The KTX1 + ASTC path through `Ktx1Reader` is plausible per OpenGL's `glInternalFormat` extension constants but unverified end-to-end. Half a day's work with non-zero chance of dead-end if Filament's reader doesn't recognise our ASTC `glInternalFormat`. Falls back to Option C anyway in the worst case.
- **Option B (JNI wrapper, full).** The right architectural answer. Deferred for the time-budget reasons above. The infrastructure groundwork (`tools/filament-ndk/` download task, `Ktx2Loader.cpp`, `CMakeLists.txt`) was sketched in this branch and reverted; a future spec can pick those up as a starting point.
- **Bundle 8 K PNG on Android.** ~50 MB compressed, decompresses to ~134 MB GPU memory at load. Blows both the install-size budget and a low-end device's GPU memory budget. Non-starter.
- **Drop HD streaming from the spec entirely.** Would lose the iOS visual upgrade for no benefit.

## Consequences

- **`02-moon-renderer-mvp/spec.md`** updates:
  - FR-001 amended: bundled tier is PNG on both platforms (KTX2 stays the HD format).
  - FR-002 amended: HD streaming is iOS-only for the duration of this spec; Android stays at the bundled 2 K tier.
  - NFR bundle-size: 5 MB target → ~6.3 MB acceptable.
  - SC-001 still met on both platforms (Mare Tranquillitatis, Tycho, Copernicus all visible at 2 K).
  - SC-002 (HD swap-in within ~5 s on Wi-Fi) — **iOS only**.
- **`02-moon-renderer-mvp/tasks.md`** T115 reframed to the PNG path; T116's KTX2 work stays as written (iOS path); T117 stays.
- **`assets/` package** gains `isHdStreamingSupported` expect/actual. Default-false on Android keeps the network call from running.
- **No regression in the iOS path.** iOS still gets the full HD upgrade flow described in the original spec.
- **Future spec (`0Y-android-hd-ktx2-jni`) inherits**: a working `tools/build-ktx2/` pipeline, an `assets-v1` GH Release, `MoonAssetLoader` already gated on `isHdStreamingSupported`, and a known list of Filament static libs the JNI shim needs to link against (per the reverted `CMakeLists.txt` in this branch's history).

## References

- ADR-0004 §"Packaging" — the asset-format decision being narrowed
- ADR-0010 — HD CDN host (still applies for iOS HD)
- `ai-docs/specs/02-moon-renderer-mvp/spec.md` — FR-001, FR-002 amendments
- `ai-docs/research/moon-assets.md` §4 — KTX2 Android Java-binding gap (now concrete)
- [Filament `KTX1Loader.kt`](https://github.com/google/filament/blob/v1.71.2/android/filament-utils-android/src/main/java/com/google/android/filament/utils/KTX1Loader.kt) — extant Android binding
- [Filament `libs/ktxreader/`](https://github.com/google/filament/tree/v1.71.2/libs/ktxreader) — C++ `Ktx2Reader` (not exposed to Java/Kotlin on Android)
