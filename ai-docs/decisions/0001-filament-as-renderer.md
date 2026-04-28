# ADR-0001: Filament as the 3D renderer

**Status**: Accepted
**Date**: 2026-04-28
**Supersedes**: —

## Context

Moon Explorer requires real-time 3D rendering on Android and iOS:
- A UV sphere (the Moon) with albedo + normal map
- One directional light (the sun) with realistic shading
- Orbit camera with smooth gestures
- 60 FPS target on mid-range devices

Three classes of options were evaluated:

1. **PBR engines**: Google Filament, Unity, Unreal.
2. **Platform-native**: SceneKit/RealityKit on iOS, OpenGL ES/Vulkan hand-roll on Android.
3. **Compose Canvas with custom shading**: pure Kotlin, software-rendered.

## Decision

Use **Google Filament** as the 3D renderer on both Android and iOS.

## Rationale

- Filament is a physically-based renderer designed for mobile, with proven Android and iOS distributions ([google/filament](https://github.com/google/filament)).
- Materials system (`matc` → `.filamat`) compiles once; same compiled blob runs on Android (GLES/Vulkan) and iOS (Metal) — single shader pipeline for both platforms.
- Mature normal-map support, directional light, post-processing — all the boxes for the Moon-globe visual brief.
- Active maintenance by Google with routine releases (1.71.x as of April 2026).
- Apache 2.0 license, no commercial cost.

## Alternatives rejected

- **Unity / Unreal**: 50+ MB runtime overhead, cross-platform-engine paradigm not idiomatic for a KMP/CMP app.
- **SceneKit on iOS, hand-roll on Android**: doubles the renderer codebase, splits material/shader pipeline; visual fidelity becomes a per-platform concern.
- **Compose Canvas with custom shading**: cannot sustain 60 FPS at full screen on mobile resolutions even with simple sphere ray-marching.

## Consequences

- Filament's public API is C++. Android wraps via JNI (the `filament-android` AAR provides Kotlin bindings); iOS surface is C++ headers — see ADR-0002 for the iOS distribution route.
- Android: ~5–7 MB native library per ABI in the AAR.
- iOS: ~30 MB static archive (per-arm64 slice) bundled with the iOS app.
- Material/shader pipeline requires the `matc` host tool wired into CI (custom Gradle `Exec` task).
- Threading: Filament Engine is single-threaded per Engine instance (driver lives on the calling thread). All renderer state mutations happen on the main thread; gesture handlers update a `StateFlow` snapshot, renderer pulls per frame. See ADR-0003.

## References

- [google/filament](https://github.com/google/filament)
- [Filament documentation](https://google.github.io/filament/)
- `ai-docs/research/filament-cmp-integration.md`
- `ai-docs/research/agp9-kmp-native-deps.md`
