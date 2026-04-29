# 00-renderer-spike — Results

Records the outcomes of the spike's smoke testing per T093. Visual / FPS verification is hardware-dependent; what's recorded here is **what shipped + what the user has confirmed running**.

## Status by phase

| Phase | Code complete | User-verified on hardware |
|---|---|---|
| Phase 1 — setup | ✓ | ✓ (Android build, iOS workspace via `pod install`) |
| Phase 2 — commonMain + tests | ✓ | n/a (no UI yet) |
| Phase 3 — US1 MVP renderer | ✓ | ✓ Android emulator + iOS device |
| Phase 4 — gestures | ✓ | (pending visual confirmation) |
| Phase 5 — sun control | ✓ | (pending visual confirmation) |
| Phase 6 — albedo toggle | ✓ | ✓ texture swap confirmed by user |
| Phase Final — polish + docs | ✓ | n/a |

## Confirmed by user

- **2026-04-28 — Android (emulator)**: app launches, shows the procedurally-textured Moon sphere with placeholder colored-quadrant albedo + flat normal map.
- **2026-04-28 — iOS (physical iPhone via Xcode)**: app launches and renders the Moon. Initial run hit a CoreDevice launch error caused by stale install state; resolved by deleting the prior install and re-running.
- **2026-04-28 — Phase 6 albedo toggle**: tapping "Texture A" / "Texture B" flips the bound albedo between the primary (rust/green/blue/red quadrants) and the alt (cyan/magenta/yellow/white quadrants). The swap is instant — no Engine teardown.
- **2026-04-28 — Phase Final UI fix**: "Texture A / B" toggle was sitting under the iOS notch / status bar (un-tappable). Fixed with `Modifier.statusBarsPadding()`; user confirmed clickable after rebuild (commit `d0bd48a`).

## Pending hardware confirmation

The following acceptance criteria from `spec.md` are coded + tested at the unit-test level but a real-device verification is the user's call. None are blocking.

- **SC-002**: 60 FPS sustained on Pixel 6 / iPhone 12 (or comparable).
- **SC-003**: no Filament-related warnings or errors in `adb logcat` / Xcode console under steady state.
- **FR-002 / FR-003 visual feel**: drag/pinch produce smooth, calibrated motion. The math is covered by `MoonViewModelTest` T022 + T023 (10 tests) and the Phase 4 wiring is straight Compose `detectTransformGestures` → ViewModel.
- **FR-004 visual lighting shift**: dragging the sun slider visibly moves the lit/unlit terminator. Code path is end-to-end wired (slider → `setSunDirection` → state → renderer's `applySunDirection` → Filament directional light), so visual behavior is high-confidence; just unconfirmed.

## Known visual-quality items deferred to `02-moon-renderer-mvp`

- **Placeholder textures** (CMYW / RGBR colored-quadrant test patterns) — per T031 PNG deviation; see [ADR-0009](../../decisions/0009-spike-deviations.md). Real NASA SVS textures + KTX2/Basis pipeline land in the next spec.
- **Flat normal map** (RGB=128,128,255) — no surface detail. Real LDEM-baked normal map ships in `02-moon-renderer-mvp`.
- **No mip-mapping** (`levels(1)`) — fine for the spike's static camera distance; will introduce filtering streaks at very close zoom. Mip-chain ships with the real assets.

## Bug history (resolved during the spike)

| When | What | Resolution |
|---|---|---|
| 2026-04-28 | Android `UnsatisfiedLinkError: Engine.nCreateBuilder` | `Filament.init()` from `MoonHost` companion init (commit `bbdfa8c`). Filament 1.71.x doesn't auto-init the JNI library. |
| 2026-04-28 | iOS `Undefined symbol: _ZSTD_decompress` (and friends) | Added `Filament/uberz` subspec to Podfile (commit `bbdfa8c`). `libzstd.a` is bundled with `uberz`, not the core `Filament/filament`. |
| 2026-04-28 | iOS Xcode launch — "application not installed" | Stale install on device; user manually deleted the old app and re-installed. |
| 2026-04-28 | IntelliJ IDEA / Fleet KMP plugin: "Build failed in 14 ms" on iOS | Workaround documented in [`iosApp/README.md`](../../../iosApp/README.md) — build iOS from Xcode directly. Android continues to work in IntelliJ. |
| 2026-04-28 | "Texture A / B" toggle un-tappable under iOS notch / Android status bar | `Modifier.statusBarsPadding()` on the toggle; `navigationBarsPadding()` on the bottom slider for symmetry (commit `d0bd48a`). |
| 2026-04-28 | Phase 3 review — visible cross-platform divergence | Fixed: post-processing toggle, camera exposure, sun light color, and tangent format unified across Android + iOS (commit `e96a0c8`). |

## Performance notes (placeholder — to be filled in after on-device measurement)

- **Pixel 6 (Android emulator first cut)**: TBD frames per second under steady-state with one Moon sphere + one directional light.
- **iPhone 13 (physical device)**: TBD.
- **iPad Air M3**: TBD.

User-side measurement procedure when ready: Android Studio Profiler → "Frame Rate" track for Android; Xcode Frame Capture or the SwiftUI inspector → FPS HUD for iOS.

## Hand-off

The spike's deferred items — **real NASA SVS textures, async asset loading, attribution UI, HD streaming** — landed in `02-moon-renderer-mvp`. ADR-0009's items #1 (PNG placeholder textures) and #6 (`runBlocking` in `MoonHost.init`) are resolved on Android; ADR-0011 narrows the original "KTX2 + Basis on both platforms" plan to "PNG bundled + KTX2-on-iOS-only HD streaming" because Filament 1.71.x has no public Java binding for `Ktx2Reader` on Android. See [`../02-moon-renderer-mvp/results.md`](../02-moon-renderer-mvp/results.md) for the spec's own results write-up. Hand-off branch: `main`.

## References

- [`spec.md`](spec.md) — acceptance criteria
- [`plan.md`](plan.md) — implementation
- [`tasks.md`](tasks.md) — task list
- [`../../decisions/0009-spike-deviations.md`](../../decisions/0009-spike-deviations.md) — deviations log
- [`../../../iosApp/README.md`](../../../iosApp/README.md) — iOS build workflow
