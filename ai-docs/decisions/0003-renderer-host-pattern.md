# ADR-0003: Renderer host pattern — expect/actual Composable + StateFlow snapshot

**Status**: Accepted
**Date**: 2026-04-28
**Supersedes**: —

## Context

Compose Multiplatform UI lives in `commonMain`. The 3D renderer is platform-specific (per ADR-0002: Filament-on-Android via JNI, Filament-on-iOS via Swift). We need a uniform way for shared Compose code to embed a per-platform renderer that reads shared state.

Two cross-cutting concerns:

1. **State delivery model.** Push (renderer subscribes to state changes) vs. pull (renderer reads a snapshot per frame). Filament's `Engine` is single-threaded per Engine instance; mutations from arbitrary coroutines into Filament objects are unsafe.
2. **Compose ↔ native view interop.** CMP exposes `AndroidView { ... }` (Android) and `UIKitViewController { ... }` / `UIKitView { ... }` (iOS) for embedding native views.

## Decision

### Renderer-host seam (commonMain)

Declare an `expect` Composable in `commonMain`:

```kotlin
@Composable
expect fun MoonViewport(
    state: MoonRenderState,
    modifier: Modifier = Modifier,
)
```

`androidMain` actual: wraps a `SurfaceView` in `AndroidView { ... }`, runs a `Choreographer.FrameCallback`, reads `state` per frame, pushes to Filament via JNI.

`iosMain` actual: uses CMP's `UIKitViewController` interop with a closure-injection bridge (see ADR-0002 §"Bridge pattern: closure injection from Swift"). The iOS app provides the `UIViewController` instance via `MoonRendererProvider.factory`; `update` calls `MoonRendererProvider.applyCamera` / `applySunDirection` / `applyMoonRotation` to push state per frame; the Swift side runs `CADisplayLink` and Filament internally.

### State delivery model

`commonMain` `MoonViewModel` holds a single `MutableStateFlow<MoonRenderState>`. `MoonRenderState` is an immutable `data class`. All gesture handlers update via `_state.update { it.copy(...) }`. Each renderer's frame callback reads `state.value` once per frame — atomic, lock-free read of an immutable snapshot.

Threading: Filament `Engine` lives on the main thread (Android Choreographer + iOS CADisplayLink both fire on main). Compose UI thread is also main. Therefore mutations from Compose handlers and reads from the renderer are on the same thread; `StateFlow.value` is the correct lightweight conduit.

### Why pull, not push

If the renderer subscribed to `StateFlow` (e.g., `collect`), state changes might land between frames on a different dispatcher; that risks pushing partial updates into Filament from non-main threads. Pull-per-frame: missing one frame's update is harmless (just renders the previous frame's state); over-shooting impossible.

## Rationale

- One mailbox = one StateFlow; no double-buffering needed (immutable snapshot is the buffer).
- Renderer code never blocks on a coroutine; it just reads `state.value`.
- Same pattern on both platforms — testable shared state, platform-thin renderers.
- Matches the threading model documented in `ai-docs/research/filament-cmp-integration.md` §7.

## Alternatives rejected

- **Push model with subscription on render thread**: requires explicit dispatcher discipline; risks cross-thread race into Filament.
- **Render thread separate from main**: Filament is single-threaded per Engine; main thread is fine and removes synchronization complexity.
- **Direct mutation of Filament objects from gestures**: violates Filament's threading invariant; would crash unpredictably in production.

## Consequences

- `MoonRenderState` must be a pure-data immutable type with no platform-specific fields (no `Mat4`, `float3`, etc. — define your own `Vec3` value class in commonMain).
- All renderer outputs that need to round-trip back to UI (e.g., projected screen positions of markers for label layout) must come back through the same StateFlow seam — *not* by holding a reference to the renderer.
- Tests can drive the renderer by constructing a `MoonRenderState` and asserting on the resulting frame (visual-regression tests are platform-specific; pure state evolution tests run in `commonTest`).
- ViewModel can be a regular `class` (no Android `ViewModel` base) since `lifecycle-viewmodel-compose` provides a CMP-friendly host.

## References

- `ai-docs/research/filament-cmp-integration.md` §1, §3, §4, §7
- [Compose Multiplatform UIKit interop](https://kotlinlang.org/docs/multiplatform/compose-uikit-integration.html)
- [Compose `AndroidView` reference](https://developer.android.com/jetpack/compose/interop/interop-apis#views-in-compose)
