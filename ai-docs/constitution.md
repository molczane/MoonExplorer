# Moon Explorer Constitution

> Durable, project-wide principles. Constitution gates apply to every feature spec and plan. Changes require an ADR with explicit ratification.

## Product Context

**Moon Explorer** is a mobile-only Kotlin Multiplatform + Compose Multiplatform app for Android and iOS. The product is **a beautiful interactive Moon globe with realistic lighting, curated places, and (eventually) an AI guide** — explicitly *not* a scientific lunar GIS tool, *not* an encyclopedia.

**Core experience** (in user terms):
1. Open app → see the Moon in 3D.
2. Drag to rotate. Pinch to zoom. Move the Sun.
3. Tap famous sites. Search by name. Fly to Apollo 11.
4. (Phase 3) Ask the agent: "Show me Tycho." Watch it happen.

**Audience.** Casual curious users who enjoy a "tactile lunar globe" experience on their phone. Not lunar scientists.

## Core Principles

### I. Mobile-only — Android + iOS, nothing else
No desktop, no web, no Wear, no TV. Every architectural decision is judged by its mobile-Android-and-iOS impact. KMP targets are `android`, `iosArm64`, `iosSimulatorArm64`. Adding any other target requires an ADR.

### II. Tactile lunar globe, not a scientific GIS
Visual fidelity, smooth gestures, and curated places matter more than scientific exhaustiveness or accuracy. When in doubt, drop a feature rather than half-build it. We do not promise selenographic accuracy beyond visual feel.

### III. KMP-shared state, platform-thin renderers
Domain model, state, view models, gestures, fly-to math, site catalog, search, and command surface live in `:shared/commonMain`. Each platform owns only the renderer host (`AndroidView`/`UIKitViewController`) and app entry point. The Filament renderer itself is platform-specific (per ADR-0002) but is driven by shared `MoonRenderState`.

### IV. Agent-ready by design — but Koog deferred
Every UI mutation routes through `MoonExplorerActions` from day 1 (per ADR-0005). Phase 3 plugs Koog tool wrappers into the same interface. We do *not* add the `ai.koog:koog-agents` dependency before Phase 3.

### V. Demo-friendly over scientifically precise
Pick the visual that looks great in a 30-second demo. Phase angle / libration approximations are fine; full ephemeris is out of scope. Site coordinates use the IAU east-positive selenographic convention (per ADR-0006).

### VI. Test boundaries, not internals
`commonTest` exercises domain logic (lat/lon math, fly-to interpolation, site search, action surface). Per-platform tests cover renderer-host wiring. Visual regression is manual on a real device. We do not chase coverage on platform-specific renderer internals.

### VII. Specs and ADRs are the source of truth
Architecture and tech-stack documents are authoritative; code follows. When code drifts from a spec, the spec is updated *or* an ADR is filed for the deviation, before the next merge. Drift without documentation is a bug.

## Tech Stack & Constraints

See `ai-docs/tech-stack.md` for pinned versions and full plugin/library list. Non-negotiables:

- **Kotlin 2.3.x**, **AGP 9.0.x** with `com.android.kotlin.multiplatform.library`, **Compose Multiplatform 1.10.x**, **Gradle 9.1.x**.
- **JVM 11**, **minSdk 24**, **compileSdk 36**, **iOS deployment target 13.0**.
- **Filament** is the renderer (per ADR-0001); **Filament-on-iOS via Swift-side hosting** (per ADR-0002).
- **No pure-Compose 3D rendering** in any phase.
- **No SwiftPM dependencies** in `:shared` until Kotlin ≥ 2.4.0-Beta2 ships stable.
- **No Filament symbols inside `Shared.framework`**.

## Project Structure & Conventions

See `ai-docs/architecture.md` for the full module layout. Non-negotiables:

- `:shared` module contains all `commonMain` shared code; Android side via `kotlin { android { ... } }` (the new AGP 9 plugin).
- `:androidApp` is a regular Android application module hosting `MainActivity` + the Android renderer host.
- `iosApp/` is a separate Xcode project consuming `Shared.framework` and the Filament CocoaPod.
- Package root: `org.jetbrains.moonexplorer.*`.
- File naming: PascalCase for types; lowercase package names; `Platform.android.kt` / `Platform.ios.kt` style for `actual` declarations.
- One `expect`/`actual` per architectural seam — not for convenience.
- `data class` for state, `interface` for actions, `StateFlow` for cross-thread state.
- Comments explain *why*, not *what*. Default to none.

## Development Workflow

- **Branch model**: trunk + short-lived feature branches named `<NN>-<feature-slug>` matching the spec folder (e.g., `00-renderer-spike`).
- **One feature spec per branch** under `ai-docs/specs/`.
- **PR description** links to the feature's `spec.md` and lists which `tasks.md` items are completed.
- **Conventional commits** for messages (`feat:`, `fix:`, `chore:`, `docs:`).
- **No work without a spec** for non-trivial changes (more than one file or non-obvious refactor).

## Quality Gates

Before merge, every PR must:

1. Build on Android (`./gradlew :androidApp:assembleDebug`).
2. Build on iOS (`./gradlew :shared:embedAndSignAppleFrameworkForXcode` + Xcode build).
3. Pass `commonTest` (`./gradlew :shared:allTests`).
4. Have updated `tasks.md` checkboxes for any completed items.
5. Have an ADR for any architectural change (anything touching `architecture.md` or `tech-stack.md`).
6. Acceptance criteria from the feature `spec.md` are verified manually for UI tasks. If automated, the test name references the FR ID.

UI feel and visual fidelity are verified on a physical device; emulators/simulators are not sufficient sign-off.

## Governance

- **Constitution amendments**: require explicit user approval and a new ADR. The ADR notes which principle is changed, why, and which prior decisions are affected.
- **ADRs are append-only**: once accepted, do not edit. Supersede via a new ADR with `**Supersedes**: ADR-XXXX` in the frontmatter.
- **Architecture / tech-stack updates**: written before code changes that depend on them; reference the ADR that authorizes the change.

---

**Version**: 0.1.0 (Draft) | **Ratified**: pending | **Last amended**: 2026-04-28
