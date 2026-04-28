# ADR-0005: Koog adoption timing — defer dependency, lock interface today

**Status**: Accepted
**Date**: 2026-04-28
**Supersedes**: —

## Context

Phase 3 of Moon Explorer adds an AI guide using JetBrains' [Koog](https://docs.koog.ai) framework. The agent will both answer questions about Moon sites and drive in-app actions ("show me Apollo 11" → app rotates Moon, zooms, highlights site).

Question: pull `ai.koog:koog-agents` into `:shared` now (with a feature flag) so the integration surface is locked, or defer the dependency entirely until Phase 3?

Relevant facts (verified in `ai-docs/research/koog-framework.md`):

- Koog stable version is 0.8.0 (April 2026). Officially **Alpha** stability badge.
- 0.7.3 and 0.8.0 both shipped breaking changes. Migration debt is real.
- `iosArm64` + `iosSimulatorArm64` are Tier-1 targets in 0.8.0.
- Annotation-based tools (`@Tool`/`@LLMDescription`) are **JVM-only**. KMP must use class-based `Tool<Args, Result>`.
- Koog pulls in Ktor 3.2.2, kotlinx-serialization 1.10, kotlinx-coroutines 1.10.2 transitively.

## Decision

**Defer `ai.koog:koog-agents` dependency entirely.** Lock in a Koog-free `MoonExplorerActions` interface in `:shared/commonMain` today. Phase 3 adds a new `:shared-ai` Gradle module that depends on `:shared` and `ai.koog:koog-agents`; Koog `Tool<Args, Result>` subclasses live there and delegate to an injected `MoonExplorerActions`.

### Action surface (commonMain, today)

```kotlin
package org.jetbrains.moonexplorer.actions

@Serializable data class MoonSite(
    val id: String, val name: String,
    val lat: Double, val lon: Double,
    val tags: List<String>,
)

@Serializable data class CurrentView(
    val cameraLat: Double, val cameraLon: Double, val zoom: Float,
    val sunLat: Double, val sunLon: Double,
    val highlightedSiteId: String?,
)

@Serializable data class ActionAck(val ok: Boolean, val message: String = "")
@Serializable enum class LightingPreset { Day, Night, Terminator, HighContrast }

interface MoonExplorerActions {
    // Read / pure (parallel-safe)
    suspend fun searchMoonLocations(query: String, limit: Int = 10): List<MoonSite>
    suspend fun getCurrentView(): CurrentView
    suspend fun explainCurrentView(): String

    // Side-effecting (sequential — mutate shared state; defend with Mutex in impl)
    suspend fun flyToMoonLocation(id: String, durationMs: Long = 1500): ActionAck
    suspend fun setLightingPreset(preset: LightingPreset): ActionAck
    suspend fun setSunDirection(lat: Double, lon: Double): ActionAck
    suspend fun highlightLocation(id: String, on: Boolean = true): ActionAck

    // Hybrid (returns data + has UI side effect)
    suspend fun compareLocations(id1: String, id2: String): ComparisonResult
}

@Serializable data class ComparisonResult(
    val a: MoonSite, val b: MoonSite,
    val distanceKm: Double, val notes: String,
)
```

UI button taps and the future Koog tool calls both call into this interface. `MoonExplorerActionsImpl` (also in commonMain) is backed by the same `MoonViewModel` / `MutableStateFlow<MoonRenderState>` that the Compose UI already reads.

## Rationale

- **The hard design work is the action surface itself**, which is Koog-agnostic. Locking that today buys us most of the value.
- **Phase-3 Koog wiring is mechanical** — one `Tool<Args, Result>` subclass per action method, plus a `ToolRegistry { tool(FlyToMoonLocation); ... }` builder.
- **Pulling Koog now is migration debt for free.** Alpha API churn between 0.7.3 → 0.8.0 already broke `ToolRegistry.Builder` and `LLMProvider` singletons. Touching it earlier means migrating again before launch.
- **A feature flag would not buy isolation** — once `Tool` subclasses are in `commonMain`, the dependency is in our iOS framework regardless of whether the agent runs.
- **Driving UI through `MoonExplorerActions` from day 1** has independent value: forces an honest separation of side-effecting vs. data-returning operations, gives a reusable command surface for tests and macros.

## Alternatives rejected

- **Pull Koog dep now behind feature flag**: pays full cost (binary size, dep version constraints, churn migration) for no early benefit.
- **Skip the action surface today; design when Phase 3 starts**: forces a refactor of every UI mutation site at Phase 3.
- **Build our own minimal agent framework**: re-invents what Koog already does; doesn't save real work because Koog's API is not the bottleneck.

## Consequences

- `:shared/commonMain` defines `MoonExplorerActions` and a default `MoonExplorerActionsImpl`. UI code never bypasses this interface — gestures and button taps both go through the interface.
- `:shared-ai` Gradle module added in Phase 3 with `ai.koog:koog-agents` dependency. Tool subclasses + `AIAgent` wiring live there.
- `:androidApp` and `iosApp/` add `:shared-ai` as a dependency only when AI is enabled (e.g., via build flavor or startup flag).
- `MoonExplorerActionsImpl` must defend side-effecting methods with a `Mutex` even when called concurrently — Koog's `toParallelToolCallsRaw` can dispatch multiple side-effecting tool calls in parallel, and we don't want to trust the LLM to serialize.
- Tests in `commonTest` exercise `MoonExplorerActions` directly; Koog's mock LLM (`getMockExecutor { ... }`) becomes useful only in `:shared-ai` tests.

## References

- `ai-docs/research/koog-framework.md`
- [docs.koog.ai](https://docs.koog.ai/)
- [Koog 0.8.0 release notes](https://github.com/JetBrains/koog/releases/tag/0.8.0)
- [Koog testing utilities](https://docs.koog.ai/testing/)
