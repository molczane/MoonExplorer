# ADR-0009: Phase 0 spike deviations from earlier ADRs

**Status**: Accepted
**Date**: 2026-04-28
**Refines**: ADR-0001, ADR-0002, ADR-0004
**Supersedes**: —

## Context

The `00-renderer-spike` work landed several intentional deviations from the earlier ADRs as we negotiated trade-offs between spike velocity and load-bearing architectural commitments. This ADR enumerates them so future agents (and a future reader) don't have to reverse-engineer the deviations from commit history. Closes T090.

## Deviations and remediation

### 1. PNG textures instead of KTX2 + Basis Universal — refines ADR-0004

T031 ships placeholder Moon textures as raw PNG (`moon_albedo_2k.png`, `moon_normal_2k.png`, plus the Phase 6 `moon_albedo_2k_alt.png`). ADR-0004 calls for KTX2 + Basis Universal so Filament's `Ktx2Reader` can transcode at load time to the GPU's native compressed format.

- **Why deviated**: `toktx` (KTX-Software) wasn't installed on the dev machine. Installing it via `brew install ktx` was a non-trivial side-effect to take on for a SPIKE; PNG via `BitmapFactory` (Android) / `CGImageSource` (iOS) decode + RGBA8 upload works fine for placeholder textures.
- **Remediation**: `02-moon-renderer-mvp` introduces the proper KTX2/Basis pipeline together with the real NASA SVS assets — at that point `BitmapFactory` and `CGImageSource` decode paths get replaced with Filament's `Ktx2Reader`.

### 2. State-driven albedo swap instead of imperative closure — refines tasks.md T060 design intent

T060's prose suggested an `applyAlbedoSwap: (ByteArray) -> Unit` closure called directly from the toggle. We instead added `MoonRenderState.albedoVariant: Int` and let both renderers detect the change in their per-frame state read.

- **Why deviated**: ADR-0003 commits to "renderers pull state per frame; UI never reaches into the renderer imperatively." The state-driven path keeps the discipline. Both textures preload at startup; the swap is a sampler rebind, not a teardown.
- **Remediation**: none — this is the better design and it's in. Future swap-style features (sun preset, lighting preset, etc.) follow the same state-driven pattern.

### 3. Filament Android tangent format temporarily diverged — refines ADR-0001

Phase 3 Android initially shipped `TANGENTS` as a `FLOAT3` direction vector while iOS shipped `FLOAT4` packed quaternion (Filament's preferred encoding). The Phase 3 review fixed this — `UvSphere.kt` now emits packed-quat `FLOAT4` matching iOS.

- **Why deviated**: Agent A's initial Android implementation chose the simpler FLOAT3 layout. The flat placeholder normal map happened to mask the divergence at runtime — real normal maps would have broken Android's lighting in `02-moon-renderer-mvp`.
- **Remediation**: fixed in commit `e96a0c8` (refactor(spike): Phase 3 review fixes). `UvSphereTest.generate_tangentsArePackedUnitQuaternions` guards the encoding so it can't silently regress.

### 4. Filament/uberz subspec required for ZSTD decompression — refines ADR-0002

ADR-0002's "Subspec selection (locked for Phase 0/1)" listed `Filament/filament + Filament/ktxreader`. iOS link failed at runtime with `Undefined symbol: _ZSTD_decompress` because `libzstd.a` (used internally by Filament's `.filamat` material loader) lives only in the `Filament/uberz` subspec.

- **Why deviated**: missed in the original ADR-0002 verification — the subspec graph dependency wasn't obvious from the podspec alone (the `Filament/filament` static archive references zstd symbols externally).
- **Remediation**: fixed in commit `bbdfa8c` (fix(spike): Android Filament.init() + iOS uberz subspec for libzstd.a). The `iosApp/Podfile` now declares all three subspecs (`Filament/filament`, `Filament/ktxreader`, `Filament/uberz`) and ADR-0002's locked-subspec list is mechanically stale on this point — defer to this ADR.

### 5. `Filament.init()` required from MoonHost companion — clarifies ADR-0001

Filament 1.71.x deliberately doesn't auto-init the JNI library from any class's static block; consumers must call `Filament.init()` explicitly before the first native call. Initial Android cut omitted this and crashed at first `Engine.create()` with `UnsatisfiedLinkError`.

- **Why deviated**: not flagged by the upstream samples Agent A referenced (some Filament versions auto-init via the Engine class; 1.71.x doesn't).
- **Remediation**: fixed in commit `bbdfa8c`. `MoonHost`'s private companion-object `init {}` now calls `Filament.init()` before any instance's primary constructor.

### 6. IntelliJ IDEA / Fleet KMP iOS run config doesn't cope with the CocoaPods workspace

Building iOS via the IntelliJ KMP plugin fails with "Build failed in 14 ms" and no actionable detail. Building via Xcode (`iosApp.xcworkspace`) works.

- **Status**: known incompatibility, not a project-side fix. Documented in `iosApp/README.md` as a workaround note; affected developers build iOS from Xcode directly. Android continues to work fine in IntelliJ / Android Studio.

### 7. iOS `EXCLUDED_ARCHS[sdk=iphonesimulator*] = arm64` from Filament pod — accepted in ADR-0002 verification

Apple Silicon Mac developers must run the iOS Simulator under Rosetta to use this app. Documented in ADR-0002 §"Verification" + `iosApp/README.md`. Not strictly a deviation — listed here for the consolidated index.

## Consequences

- Future readers find the spike's deviations in one place rather than scattered across commits.
- ADR-0004 (KTX2/Basis) is honored once `02-moon-renderer-mvp` lands; until then PNG is the de-facto format.
- ADR-0002's locked-subspec list is mechanically stale on uberz — this ADR is the source of truth for the actual Podfile.
- `iosApp/README.md` (T092) is the source of truth for developer-side workflow gotchas (Rosetta, IDE quirk).

## References

- `tasks.md` T031 (PNG), T060 (state-driven swap), Phase 3 review fixes
- ADR-0001 (Filament renderer), ADR-0002 (Filament-on-iOS), ADR-0003 (renderer host pattern), ADR-0004 (asset strategy), ADR-0008 (Filament pod via raw URL)
- Commits: `e96a0c8` (Phase 3 review), `bbdfa8c` (Filament.init + uberz)
- `iosApp/README.md`
