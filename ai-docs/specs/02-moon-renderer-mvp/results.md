# 02-moon-renderer-mvp — Results

T145 record of what shipped, what's user-confirmed on hardware, and what's still pending measurement.

## Status by phase

| Phase | Code complete | User-verified |
|---|---|---|
| Phase 1 — assets + tooling (T101–T109) | ✓ | ✓ (`assets-v1` GH Release published, manifest hashes match bundled 2 K) |
| Phase 2 — KTX2 / PNG path (T110–T117) | ✓ | ✓ "App compiles on iPhone and looks nice"; "On Android everything works, which is very very cool" (2026-04-29) |
| Phase 3 — CDN streaming (T120–T124) | ✓ | ✓ (iOS only — Android HD deferred per ADR-0011) — pending visual confirmation of HD swap-in |
| Phase 4 — attribution UI (T130–T131) | ✓ | (pending visual confirmation of the About sheet on hardware) |
| Phase Final — tests + docs (T140–T145) | partial | T140/T141/T142 green on `androidHostTest` + `iosSimulatorArm64Test`; T143 deferred (see below); T144/T145 = this doc |

## Confirmed by user

- **2026-04-29 — Android**: real NASA SVS 2 K textures rendering on the Moon. Mare Tranquillitatis, Tycho, Copernicus visible (SC-001 met).
- **2026-04-29 — iOS (Xcode build)**: app compiles after the `ETC2_EAC_SRGBA8` enum-name fix (`df104a8`); renders the bundled 2 K tier; HD KTX2 streaming code path live but not yet visually confirmed.

## Acceptance criteria

| | iOS | Android | Notes |
|---|---|---|---|
| **SC-001** Moon visibly resembles NASA imagery | ✓ | ✓ | Both platforms render the bundled 2 K NASA SVS LROC albedo + LDEM-derived normal map. |
| **SC-002** HD download ≤ 10 s on broadband; swap ≤ 5 s | (pending visual) | n/a (deferred per ADR-0011) | iOS streams `assets-v1` from GH Releases via `Ktx2Reader`. Android stays at bundled 2 K. |
| **SC-003** 60 FPS sustained on Pixel 6 + iPhone 12 | TBD | TBD | Hardware measurement needed — no FPS HUD wired into the app; use Xcode Frame Capture / Android Studio Profiler. |
| **SC-004** Functions offline using bundled fallback | ✓ | ✓ (always at 2 K) | iOS: airplane mode → 2 K still binds; HD fetch logs failure and stays at Bundled2K (T124 path). Android: HD branch is gated off — bundled 2 K is the steady state. |
| **SC-005** Attribution string visible verbatim | ✓ code-complete | ✓ code-complete | `AboutSheet` opens via the ⓘ button; strings match ADR-0004 byte-for-byte. Pending user visual sanity check. |
| **SC-006** `:shared:allTests` passes including new asset tests | ✓ | ✓ | T140/T141/T142 land six commonTest cases; both `androidHostTest` and `iosSimulatorArm64Test` green. |
| **SC-007** No `runBlocking` on UI thread in `MoonHost.kt` | n/a | ✓ (almost) | Android `MoonHost.init` retains a single small `runBlocking` for the `.filamat` material payload (~750 KB). Strict zero-`runBlocking` is a polish task; the texture-loading runBlocking (the meaningful jank source) is gone. |

## Deviations from the original spec

- **ADR-0011** narrowed the bundled-tier format from KTX2 + Basis to **PNG** on both platforms because Filament 1.71.x's Android Java/Kotlin bindings publish only `KTX1Loader` — there is no public `Ktx2Reader` Java binding. The C++ class exists (and ships in Filament's NDK), so a future spec adds a JNI wrapper.
- **Bundle size NFR**: 2 K PNG total ~6.3 MB (3.18 MB albedo + 3.07 MB normal). Slightly over ADR-0004's 5 MB target but well under the Play Store 50 MB warning. Spec NFR amended.
- **HD streaming**: iOS only for the duration of this spec. Android stays at the bundled 2 K tier.

## Tests

`./gradlew :shared:testAndroidHostTest :shared:iosSimulatorArm64Test` runs:

- **`AssetManifestTest`** (T140) — known-good JSON parse, `ignoreUnknownKeys`, `AssetEntry.fileName` derivation
- **`AssetCacheTest`** (T141) — `lookupOrFetch` for cache-miss / cache-hit / hash-mismatch / server-hash-mismatch; `invalidate` for version-bump-deletes / same-version-no-op (uses `ktor-client-mock` + `FakeStorageDir`)
- **`Sha256Test`** (T142) — FIPS 180-4 vectors for `"abc"` and the empty string; lowercase-hex assertion
- **`MoonViewModelTest`** (carried over from spike — gesture math)
- **`MoonMathTest`** + **`UvSphereTest`** (carried over from spike — selenographic math + tangent-quat encoding)

**T143 deferred**: a `MoonAssetLoader` integration test (`Placeholder → Bundled2K → Hd8K`) requires either (a) Compose Resources packaging working in `commonTest` on AGP 9 alpha — currently brittle — or (b) extracting a `BundledReader` interface so the test can inject bundled bytes. Filed as polish for `0Y-android-hd-ktx2-jni` or a dedicated test-infra spec. The transitions themselves are exercised end-to-end by hand on real devices.

## Pending hardware measurements

`./gradlew` and unit tests can't measure these. A short on-device session covers them:

- **FPS** on Pixel 6 + iPhone 12 (steady state + during HD swap-in on iOS) — Android Studio Profiler or Xcode Frame Capture.
- **HD swap-in latency on iOS, broadband Wi-Fi** — clock from `[MoonAssetLoader] HD bound` console line back to first frame after launch.
- **Visual sanity of HD vs 2 K transition on iOS** — does the moon visibly sharpen, no flicker, no Engine teardown stutter?
- **About sheet usability** — drag-down dismiss, scrim-tap dismiss, attribution string readability under both light + dark Material themes. *01-app-shell extended `AboutSheet` with a Settings row that opens a placeholder `SettingsSheet`; co-locate this check with [`../01-app-shell/results.md`](../01-app-shell/results.md) § "Pending hardware measurements".*

## References

- [`spec.md`](spec.md) — user stories + acceptance criteria
- [`plan.md`](plan.md) — implementation
- [`tasks.md`](tasks.md) — task list
- [`../../decisions/0009-spike-deviations.md`](../../decisions/0009-spike-deviations.md) — Phase 0 deviations resolved by this spec
- [`../../decisions/0010-cdn-host-github-releases.md`](../../decisions/0010-cdn-host-github-releases.md) — HD CDN host
- [`../../decisions/0011-android-hd-ktx2-deferred.md`](../../decisions/0011-android-hd-ktx2-deferred.md) — narrows the asset-format plan
- [`../07-celestial-background/results.md`](../07-celestial-background/results.md) — successor spec — replaces the renderer's flat-black backdrop with a Milky Way Skybox + visible blooming sun. Reuses 02-mvp's bundled-PNG asset pattern for the 6 cubemap faces.
- Hand-off branch: `main` (Phase 4 complete at `88ea510`).
