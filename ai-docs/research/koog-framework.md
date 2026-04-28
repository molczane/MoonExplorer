# Koog for Moon Explorer — Phase 3 AI Guide Research

> Research output. Source: agent run 2026-04-28. Focus: API surface and KMP support for the deferred Koog agent layer; recommended `MoonExplorerActions` shape designed today, integration deferred to Phase 3.

## 1. Architecture

Koog is a Kotlin Multiplatform agent framework whose canonical artifact is `ai.koog:koog-agents`. Current stable is **0.8.0** (published 10 April 2026, [release notes](https://github.com/JetBrains/koog/releases/tag/0.8.0)); the GitHub README still ships a 0.7.3 dependency snippet, so paste 0.8.0 by hand. Stability is officially **Alpha** ([README badge](https://github.com/JetBrains/koog/blob/release/0.8.0/README.md)) — keep this in mind for §7.

Core concepts ([overview](https://docs.koog.ai/), [glossary](https://docs.koog.ai/glossary/)):

- **`AIAgent`** — top-level runner. Configured with a `promptExecutor`, an `llmModel`, optional `toolRegistry`, and either a default ReAct loop (`AIAgent(...)` "single-run") or a custom `strategy` graph.
- **Strategy graph** — a typed graph of nodes (`nodeLLMRequest`, `nodeExecuteTool`, custom `node<I,O>{}`); edges with predicates control flow. Subgraphs and `parallel(...)` blocks compose larger workflows ([nodes](https://docs.koog.ai/nodes-and-components/), [parallel](https://docs.koog.ai/parallel-node-execution/)).
- **Tools** — callable units the LLM can invoke. Two flavors:
  - **Annotation-based** (`@Tool`/`@LLMDescription` on a `ToolSet`) — **JVM-only**, explicitly stated in the docs ([annotation-based-tools](https://docs.koog.ai/annotation-based-tools/) "Note: Annotation-based tools are JVM-only and not available for other platforms"). Reflection-driven.
  - **Class-based** (`Tool<Args, Result>` / `SimpleTool<Args>`) — **multiplatform Kotlin**, what you must use for iOS ([class-based-tools](https://docs.koog.ai/class-based-tools/)).
- **Prompt** / **PromptExecutor** — the message-construction DSL plus the wire-level LLM client. Executors are pluggable per provider.
- **Sessions** — `llm.writeSession { ... }` / `readSession { ... }` blocks inside nodes that mutate or read prompt history; this is where you call `requestLLMWithoutTools()` etc. ([sessions](https://docs.koog.ai/sessions/)).
- **Memory** — three orthogonal layers: short-term prompt history (with [history compression](https://docs.koog.ai/history-compression/)), [chat memory](https://docs.koog.ai/features/chat-memory/) (durable conversation state), and [long-term memory](https://docs.koog.ai/features/long-term-memory/) over `rag-base`/vector stores.

**Concrete tool example** — straight from [class-based-tools](https://docs.koog.ai/class-based-tools/), abridged:

```kotlin
object CalculatorTool : Tool<CalculatorTool.Args, Int>(
    argsType = typeToken<Args>(),
    resultType = typeToken<Int>(),
    name = "calculator",
    description = "A simple calculator that can add two digits (0-9)."
) {
    @Serializable
    data class Args(
        @property:LLMDescription("The first digit to add (0-9)")  val digit1: Int,
        @property:LLMDescription("The second digit to add (0-9)") val digit2: Int,
    )
    override suspend fun execute(args: Args): Int = args.digit1 + args.digit2
}
```

Note `suspend` execute and the requirement that `Args`/`Result` be `@Serializable` (kotlinx-serialization 1.10.0 per [`gradle/libs.versions.toml`](https://github.com/JetBrains/koog/blob/release/0.8.0/gradle/libs.versions.toml)).

## 2. KMP support — the load-bearing question

**Confirmed targets** in 0.8.0, from the convention plugin [`ai.kotlin.multiplatform.gradle.kts`](https://github.com/JetBrains/koog/blob/release/0.8.0/convention-plugin-ai/src/main/kotlin/ai.kotlin.multiplatform.gradle.kts) that every public module applies:

```kotlin
iosSimulatorArm64()   // Tier 1
iosArm64()            // Tier 1
iosX64()              // Tier 3
androidTarget()
jvm()
js(IR)
wasmJs()
```

So **`iosArm64` + `iosSimulatorArm64` are first-class Kotlin/Native Tier-1 targets** in 0.8.0 stable. The release also publishes a prebuilt `Koog-0.8.0.xcframework.zip` ([release assets](https://github.com/JetBrains/koog/releases/tag/0.8.0)) for Swift consumers — irrelevant to your KMP setup, but it confirms the iOS path is officially supported.

Caveats:

- **Annotation-based tools are JVM-only** (cited above). You must use `Tool<Args,Result>` subclasses for anything wired in `commonMain`.
- **Spring AI / Spring Boot / Bedrock JDBC / Ktor server plugin** are JVM-only. For Moon Explorer this is fine — those are server-side modules.
- The README under "Supported targets" says "JVM, JS, WasmJS and iOS targets" ([README](https://github.com/JetBrains/koog/blob/release/0.8.0/README.md#L83)). It does not separately call out **Android**, but the convention plugin clearly includes `androidTarget()` and the docs' "Key features" list says "across JVM, JS, WasmJS, **Android**, and iOS targets."
- Kotlin 2.3.10 is the build version; the README requires consumers to be on 2.3.10+ — a noticeable bump. Verify your KMP project is on a compatible Kotlin (your repo is freshly initialized on AGP 9, on 2.3.20).

## 3. Model providers

Out-of-the-box providers ([llm-providers](https://docs.koog.ai/llm-providers/)): **OpenAI** (incl. Azure), **Anthropic**, **Google**, **DeepSeek**, **OpenRouter**, **Amazon Bedrock**, **Mistral**, **Alibaba DashScope**, **Ollama** (local). Each has a dedicated `prompt-executor-*-client` module — all multiplatform per the convention plugin. Configuration is constructor-injected:

```kotlin
val agent = AIAgent(
    promptExecutor = simpleAnthropicExecutor(apiKey = readEnvKey()),
    systemPrompt = "You are a Moon expedition guide.",
    llmModel = AnthropicModels.Sonnet_4_5,
    toolRegistry = moonToolRegistry,
)
```

There is **no first-class "JetBrains AI" / Grazie provider** in the OSS list — the docs only mention `packages.jetbrains.team/.../grazie-platform-public` as the **Maven repo for nightly builds**, not as a runtime LLM endpoint ([quickstart](https://docs.koog.ai/quickstart/)). If you want JetBrains AI specifically, you'd have to write a custom `PromptExecutor` against its HTTP API — non-trivial.

**Mock / fake-LLM mode for tests is excellent and explicitly designed for tool-call verification** ([testing](https://docs.koog.ai/testing/), `agents-test` module):

```kotlin
val mockLLMApi = getMockExecutor {
    mockLLMToolCall(FlyToMoonLocation, FlyToMoonLocation.Args("apollo-11")) onRequestEquals
        "show me Apollo 11"
    mockTool(SearchMoonLocations) returns listOf(Site("apollo-11", ...)) onArgumentsMatching {
        it.query.contains("Apollo")
    }
}
val agent = AIAgent(promptExecutor = mockLLMApi, llmModel = anyModel, toolRegistry = registry) {
    withTesting()
}
```

This is exactly the seam you want for Phase-3 tests in `commonTest`.

## 4. Tool-call orchestration

The default single-run agent ([basic-agents](https://docs.koog.ai/agents/basic-agents/)) implements a standard **ReAct loop**: agent → LLM call → if tool calls in response, execute them via the `ToolRegistry` against the agent's `Environment`, append results to history → loop until LLM emits a final assistant message. Streaming is supported with parallel tool-call extraction (`toParallelToolCallsRaw(toolClass = ...)`, [streaming-api](https://docs.koog.ai/streaming-api/)). Strategy graphs let you customize: e.g. `parallel<I,O>(nodeA, nodeB) { selectByMax { it.length } }`.

**Side-effecting tools are first-class.** The `Tool<TArgs, TResult>` KDoc in [`Tool.kt`](https://github.com/JetBrains/koog/blob/release/0.8.0/agents/agents-tools/src/commonMain/kotlin/ai/koog/agents/core/tools/Tool.kt#L19-L20) literally reads: *"Tools are usually used to return results, **make changes to the environment**, or perform other actions."* Mechanically, every tool must return *something* (the model needs a tool-result message in the chat history to keep going), but that "something" can be a tiny ack object. The conventional pattern is:

```kotlin
@Serializable data class ActionAck(val ok: Boolean, val message: String = "")

object FlyToMoonLocation : Tool<FlyToMoonLocation.Args, ActionAck>(...) {
    @Serializable data class Args(val locationId: String, val durationMs: Long = 1500)

    override suspend fun execute(args: Args): ActionAck {
        actions.flyToMoonLocation(args.locationId, args.durationMs)  // side effect
        return ActionAck(ok = true, message = "Camera flying to ${args.locationId}.")
    }
}
```

The ack message is what the LLM sees and uses to continue narration ("OK — we're now over Tranquility Base; the Sea of Tranquility is to your west..."). Loop:

```
user: "show me Apollo 11"
  → LLM call (with tool schemas in system prompt)
  → LLM returns tool_use { name: flyToMoonLocation, args: { locationId: "apollo-11" } }
  → ToolRegistry.execute → your MoonExplorerActions impl mutates camera StateFlow
  → returns ActionAck(ok=true) → appended as tool_result
  → LLM call again with updated history → final text "We're over Tranquility Base..."
```

## 5. Recommended `MoonExplorerActions` shape

Place this in `:shared/commonMain`. It is **deliberately Koog-free** — pure Kotlin interface so the app implements it today and Koog's `Tool` subclasses wrap it later.

```kotlin
package com.you.moonexplorer.actions

@Serializable data class MoonSite(val id: String, val name: String, val lat: Double, val lon: Double, val tags: List<String>)
@Serializable data class CurrentView(val cameraLat: Double, val cameraLon: Double, val zoom: Float, val sunLat: Double, val sunLon: Double, val highlightedSiteId: String?)
@Serializable data class ActionAck(val ok: Boolean, val message: String = "")
@Serializable enum class LightingPreset { Day, Night, Terminator, HighContrast }

interface MoonExplorerActions {
    // --- Read / pure (parallel-safe) ---
    suspend fun searchMoonLocations(query: String, limit: Int = 10): List<MoonSite>      // returns data; parallel-safe
    suspend fun getCurrentView(): CurrentView                                              // returns data; parallel-safe
    suspend fun explainCurrentView(): String                                               // returns data (LLM-friendly summary); parallel-safe

    // --- Side-effecting (sequential — they mutate the same camera/lighting state) ---
    suspend fun flyToMoonLocation(id: String, durationMs: Long = 1500): ActionAck          // side effect; serial
    suspend fun setLightingPreset(preset: LightingPreset): ActionAck                       // side effect; serial
    suspend fun setSunDirection(lat: Double, lon: Double): ActionAck                       // side effect; serial
    suspend fun highlightLocation(id: String, on: Boolean = true): ActionAck               // side effect; serial (UI overlay)

    // --- Hybrid: returns data AND has a UI side effect (briefly highlights both) ---
    suspend fun compareLocations(id1: String, id2: String): ComparisonResult                // side-effecting + returns data; serial
}

@Serializable data class ComparisonResult(val a: MoonSite, val b: MoonSite, val distanceKm: Double, val notes: String)
```

Per-tool concurrency policy you'll later encode either via a single-flight mutex inside the `actions` impl or via Koog strategy edges (don't rely on the LLM to serialize — clamp it server-side):

| Tool | Returns | Parallel? | Notes |
|---|---|---|---|
| `searchMoonLocations` | `List<MoonSite>` | yes | pure read |
| `getCurrentView` | `CurrentView` | yes | snapshot of state |
| `explainCurrentView` | `String` | yes | derived from state, no mutation |
| `flyToMoonLocation` | `ActionAck` | **no** | mutates camera; queue with mutex |
| `setLightingPreset` | `ActionAck` | **no** | mutates sun |
| `setSunDirection` | `ActionAck` | **no** | mutates sun |
| `highlightLocation` | `ActionAck` | yes (multiple highlights ok) | additive UI state |
| `compareLocations` | `ComparisonResult` | **no** | side-effecting overlay |

Then in Phase 3, write thin Koog wrappers (one `Tool<Args,Result>` per action, all in `:shared` or a new `:shared-ai` source set) that delegate to an injected `MoonExplorerActions`. The interface itself never imports `ai.koog.*`.

## 6. Adoption timing

**Recommendation: do not add Koog as a dependency now. Lock in the `MoonExplorerActions` interface only.**

Arguments:

- **Cost of waiting is low.** Phase-3 wiring is mechanical — one `Tool` subclass per action method, plus a `ToolRegistry { tool(FlyToMoonLocation); ... }` builder. The hard design work is the action surface itself, which lives in your interface and is Koog-agnostic.
- **Cost of pulling Koog in early is real.** Koog is **Alpha** ([badge](https://github.com/JetBrains/koog/blob/release/0.8.0/README.md)), shipped a **breaking change in 0.7.3** (`ToolRegistry.Builder` removed) and another in 0.8.0 (`LLMProvider` singletons). Touching Koog now buys you migration debt for free. It also pulls in Ktor 3.2.2, kotlinx-serialization 1.10, kotlinx-coroutines 1.10.2 transitively, which constrains your KMP version matrix.
- **A feature flag would not actually buy you isolation** — once `Tool` subclasses are in `commonMain`, the dependency is in your iOS framework regardless of whether the agent ever runs.
- **What to do today:** ship `MoonExplorerActions` + a `MoonExplorerActionsImpl` backed by your existing UI state in `:shared`. Drive every Compose UI mutation through it (button taps call the same methods Koog will later call). This forces the API to be honest about which operations are side-effecting and gives you a reusable command surface for tests and hypothetical macros today.
- **What to add when Phase 3 starts:** a new `:shared-ai` Kotlin Multiplatform module that depends on `:shared` and `ai.koog:koog-agents`. Tool subclasses + agent wiring live there. Your iOS/Android apps add this module to their app-level dependencies only when AI is enabled.

## 7. Risks / unknowns

- **Alpha-stability churn.** Koog is on its 8th minor in ~12 months and 0.7.3/0.8.0 both shipped breaking changes. Pin a version, read [release notes](https://github.com/JetBrains/koog/releases) before bumping. The active KG-* YouTrack project ([issues.koog.ai](https://youtrack.jetbrains.com/issues/KG)) is the canonical bug surface, not GitHub Issues.
- **Annotation-based tools won't work in `commonMain`.** Easy to forget given how heavily the docs lead with `@Tool`. Plan around `Tool<Args,Result>` from day one.
- **Apple HTTP transport.** All multiplatform LLM clients use Ktor 3.2.2; on iOS this means the `ktor-client-darwin` engine. Watch for TLS / IPv6 quirks under low-connectivity. On real devices, NSURLSession-based Darwin engine is fine; on simulators it's also fine — but background coroutines on iOS need a `MainScope`/`Dispatchers.Main` story on the consumer side.
- **API key handling on mobile.** All provider configs are `apiKey: String` constructor params. Shipping API keys in a mobile binary is a non-starter. For a real product you need a thin proxy server; for personal/dev use, a debug-only flag in `local.properties` is fine. Koog has no opinion on this — it's your problem.
- **Tool-call concurrency on shared mutable UI state.** Even if you mark side-effecting tools "serial" in §5, the LLM may emit *parallel* tool calls (Koog supports them via `toParallelToolCallsRaw`). Defend with a `Mutex` in `MoonExplorerActionsImpl`, not just by trust.
- **JetBrains AI as a provider is not first-class.** If you intend to dogfood Grazie/JB-AI specifically, budget for writing a custom `PromptExecutor`. There is no built-in client.
- **Reasoning-model edge cases.** 0.7.3 release notes call out DeepSeek `reasoningContent` merge bugs and OpenAI status-400s on reasoning ID mismatches. If you adopt o1/o3/Claude-thinking models, expect to hit at least one such edge case.
- **iOS framework size.** Koog's xcframework is ~39 MB ([release asset size](https://github.com/JetBrains/koog/releases/tag/0.8.0)). Your KMP-built iOS framework will be smaller (only what's reachable), but it's a meaningful binary-size delta worth measuring before you ship.

**Sources cited above:** [docs.koog.ai](https://docs.koog.ai/), [GitHub release 0.8.0](https://github.com/JetBrains/koog/releases/tag/0.8.0), [convention-plugin-ai/.../multiplatform.gradle.kts](https://github.com/JetBrains/koog/blob/release/0.8.0/convention-plugin-ai/src/main/kotlin/ai.kotlin.multiplatform.gradle.kts), [agents-tools/Tool.kt](https://github.com/JetBrains/koog/blob/release/0.8.0/agents/agents-tools/src/commonMain/kotlin/ai/koog/agents/core/tools/Tool.kt), [gradle/libs.versions.toml](https://github.com/JetBrains/koog/blob/release/0.8.0/gradle/libs.versions.toml), [annotation-based-tools](https://docs.koog.ai/annotation-based-tools/), [class-based-tools](https://docs.koog.ai/class-based-tools/), [testing](https://docs.koog.ai/testing/), [llm-providers](https://docs.koog.ai/llm-providers/), [parallel-node-execution](https://docs.koog.ai/parallel-node-execution/), [streaming-api](https://docs.koog.ai/streaming-api/), [quickstart](https://docs.koog.ai/quickstart/), [YouTrack KG project](https://youtrack.jetbrains.com/issues/KG).
