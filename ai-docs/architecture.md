# Moon Explorer Architecture

> System shape, module boundaries, data flow. Authoritative for cross-module design. Specific decisions delegated to ADRs.

## Module Layout

```
MoonExplorer/
├── shared/                          (KMP module: kotlin.multiplatform + com.android.kotlin.multiplatform.library)
│   ├── src/commonMain/
│   │   ├── kotlin/org/jetbrains/moonexplorer/
│   │   │   ├── App.kt               (Compose root: hosts MoonExplorerScreen)
│   │   │   ├── domain/              (MoonSite, lat/lon math, site catalog)
│   │   │   ├── state/               (MoonRenderState, MoonViewModel)
│   │   │   ├── actions/             (MoonExplorerActions interface + impl)
│   │   │   ├── ui/                  (Compose: viewport, panels, controls)
│   │   │   └── render/              (MoonViewport expect Composable)
│   │   └── composeResources/files/
│   │       ├── textures/            (KTX2 albedo + normal, fallback tier)
│   │       └── materials/           (compiled .filamat)
│   ├── src/androidMain/             (actual MoonViewport: AndroidView + Filament JNI)
│   └── src/iosMain/                 (actual MoonViewport: UIKitViewController + Swift Filament host)
├── androidApp/                      (com.android.application)
│   └── src/main/kotlin/.../MainActivity.kt
├── iosApp/                          (Xcode project consuming Shared.framework + pod 'Filament')
└── ai-docs/                         (this directory: SDD docs)
```

`:shared` uses the new AGP 9 plugin `com.android.kotlin.multiplatform.library`. Android targets are configured inside `kotlin { android { ... } }`, not via the legacy `androidTarget()` + `com.android.library` combo. Per `ai-docs/research/agp9-kmp-native-deps.md` §5, this means: no `android {}` block, no build variants, no `ndk.abiFilters` here — those go in `:androidApp`.

## Layers (in `:shared/commonMain`)

```
┌─────────────────────────────────────────────────────────────┐
│  UI (Compose Multiplatform)                                 │
│  • MoonExplorerScreen, panels, controls, search             │
│  • Receives gestures, calls into actions                    │
└─────────────────────────────────────────────────────────────┘
                          ↓ calls
┌─────────────────────────────────────────────────────────────┐
│  Actions (MoonExplorerActions interface)                    │
│  • Single command surface for UI and (Phase 3) Koog         │
│  • Side-effecting methods serialized via Mutex              │
└─────────────────────────────────────────────────────────────┘
                          ↓ mutates
┌─────────────────────────────────────────────────────────────┐
│  State (MoonViewModel)                                      │
│  • MutableStateFlow<MoonRenderState>                        │
│  • Immutable data class snapshots                           │
└─────────────────────────────────────────────────────────────┘
                          ↓ read each frame
┌──────────────────────┬──────────────────────────────────────┐
│  Renderer host       │  Renderer host                       │
│  (androidMain)       │  (iosMain)                           │
│  • AndroidView +     │  • UIKitViewController +             │
│    SurfaceView +     │    Swift MoonRendererViewController +│
│    Filament JNI      │    Swift Filament direct C++ interop │
└──────────────────────┴──────────────────────────────────────┘
```

## Data Flow

### Inbound (user input → state)

1. Compose gesture detector (`pointerInput { detectTransformGestures { ... } }`) reports `pan`, `zoom`, `rotate`.
2. Compose handler calls `viewModel.onDrag(dx, dy)` etc.
3. ViewModel calls `_state.update { it.copy(...) }`.
4. New `MoonRenderState` is the live snapshot.

### Outbound (state → frame)

1. Per-platform renderer host is a `@Composable expect fun MoonViewport(state: MoonRenderState, modifier: Modifier)`.
2. Inside the host, a frame callback (Choreographer on Android, CADisplayLink on iOS) fires ~60 Hz.
3. Frame callback reads `state.value` (atomic, immutable snapshot).
4. Host pushes camera + sun + transforms into Filament objects on the main thread.
5. Host calls `renderer.beginFrame() / render(view) / endFrame()`.

### Action call (UI button / future Koog tool)

1. Caller invokes `actions.flyToMoonLocation("apollo-11", durationMs = 1500)`.
2. Impl acquires `Mutex` (side-effecting method).
3. Impl computes target `(yaw, pitch, distance)` from site coords + stored target distance.
4. Impl launches a coroutine animating the StateFlow with frame-accurate samples.
5. Releases mutex; returns `ActionAck(ok=true, message="Camera flying to Apollo 11.")`.

## Key Contracts (commonMain)

### `MoonRenderState`

Immutable. Pulled by both renderers per frame.

```kotlin
data class MoonRenderState(
    val cameraYawRad: Float,
    val cameraPitchRad: Float,
    val cameraDistance: Float,        // unit-radius Moon: 1.5 .. 20
    val sunDirection: Vec3,           // unit vector in world space
    val moonRotationRad: Float,       // for libration / spin animation later
    val highlightedSiteId: String? = null,
)
```

Per ADR-0006: right-handed, Y-up, north pole at +Y, prime meridian at +Z, east longitude → +X.

### `MoonExplorerActions`

Per ADR-0005. Sole entry point for UI mutations and (Phase 3) agent tool calls.

```kotlin
interface MoonExplorerActions {
    suspend fun searchMoonLocations(query: String, limit: Int = 10): List<MoonSite>
    suspend fun getCurrentView(): CurrentView
    suspend fun explainCurrentView(): String
    suspend fun flyToMoonLocation(id: String, durationMs: Long = 1500): ActionAck
    suspend fun setLightingPreset(preset: LightingPreset): ActionAck
    suspend fun setSunDirection(lat: Double, lon: Double): ActionAck
    suspend fun highlightLocation(id: String, on: Boolean = true): ActionAck
    suspend fun compareLocations(id1: String, id2: String): ComparisonResult
}
```

### `MoonViewport`

Per ADR-0003.

```kotlin
@Composable
expect fun MoonViewport(state: MoonRenderState, modifier: Modifier = Modifier)
```

## Cross-cutting Concerns

### Threading

- All Filament calls are on the main thread.
- All `MoonRenderState` mutations are on the main thread (Compose UI thread).
- `MoonRenderState` is immutable; the StateFlow holds a reference, which is read atomically per frame.
- `MoonExplorerActions` methods are `suspend`; their bodies may dispatch to `Dispatchers.Default` for compute (e.g., search), but mutations to `_state` always happen back on `Dispatchers.Main`.
- Side-effecting actions are serialized via a `Mutex` in `MoonExplorerActionsImpl` — even if the caller (LLM tool dispatcher) attempts parallel calls.

### Asset loading

Per ADR-0004:
- Bundled fallback (2K KTX2 albedo + 2K KTX2 normal, ~2–3 MB) at `:shared/src/commonMain/composeResources/files/textures/`.
- HD tier (8K) downloaded from CDN on first launch; cached to platform Files dir.
- Compiled material (`moon.filamat`) bundled at `:shared/src/commonMain/composeResources/files/materials/moon.filamat` — built via custom `matc` Gradle task.

### Renderer thread vs. async work

- Site search, JSON parsing, CDN download all use `Dispatchers.Default` / `Dispatchers.IO`.
- Results are merged back into the StateFlow on the main thread via `withContext(Dispatchers.Main)`.

### Camera math

Lives in `:shared/commonMain/.../domain/`. Stateless. Tested in `commonTest`. Per ADR-0006 + `ai-docs/research/selenographic-math-camera.md`.

## Module Dependencies

```
:androidApp ──→ :shared
                 ↑
iosApp/ ────────┘ (via Shared.framework + pod 'Filament' in iosApp/Podfile)

(Phase 3 only:)
:androidApp ──→ :shared-ai ──→ :shared
                                ↑
iosApp/ ──→ Shared-ai.framework (via :shared-ai)
```

## Future module: `:shared-ai` (Phase 3)

Per ADR-0005. New module, depends on `:shared` + `ai.koog:koog-agents`. Hosts:
- `Tool<Args, Result>` subclasses, one per `MoonExplorerActions` method.
- `ToolRegistry` builder.
- `AIAgent` configuration.
- LLM provider executor configuration.

`:shared-ai` is added to `:androidApp` and `iosApp/` only when AI is enabled.

## References

- ADR-0001 (Filament renderer)
- ADR-0002 (Filament-on-iOS via Swift)
- ADR-0003 (Renderer host pattern)
- ADR-0004 (Asset strategy)
- ADR-0005 (Koog adoption timing)
- ADR-0006 (Selenographic convention)
- `ai-docs/research/filament-cmp-integration.md`
- `ai-docs/research/agp9-kmp-native-deps.md`
- `ai-docs/research/koog-framework.md`
