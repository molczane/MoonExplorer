package org.jetbrains.moonexplorer.state

import kotlin.math.tan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.jetbrains.moonexplorer.domain.PITCH_LIMIT_RAD
import org.jetbrains.moonexplorer.domain.Vec3

/**
 * Owns the live MoonRenderState. Compose UI mutates via the gesture handlers
 * + sun setter; renderer hosts read snapshots from `state.value` per frame
 * (ADR-0003 pull-not-push).
 *
 * Math conventions from ai-docs/research/selenographic-math-camera.md §3, §4:
 *   - Pinch zoom is exponential (`distance / scale`) with a [MIN_DIST, MAX_DIST] clamp.
 *   - Drag-to-rotate sensitivity scales with (distance - 1) so dragging at
 *     close zoom moves the surface ~1:1, while at far zoom rotates the globe
 *     more freely. Yaw decreases with rightward drag (drag-the-surface feel).
 *   - Pitch is clamped to ±PITCH_LIMIT_RAD (~89.4°) to keep lookAt stable.
 */
class MoonViewModel(initial: MoonRenderState = MoonRenderState()) {

    private val _state = MutableStateFlow(initial)
    val state: StateFlow<MoonRenderState> = _state.asStateFlow()

    fun onDrag(dxPx: Float, dyPx: Float, viewportHpx: Int, fovYRad: Float) {
        if (viewportHpx <= 0 || fovYRad <= 0f) return
        val current = _state.value
        val k = 2f * tan(fovYRad * 0.5f) * (current.cameraDistance - 1f) / viewportHpx
        _state.update { s ->
            s.copy(
                cameraYawRad = s.cameraYawRad - dxPx * k,
                cameraPitchRad = (s.cameraPitchRad + dyPx * k)
                    .coerceIn(-PITCH_LIMIT_RAD, PITCH_LIMIT_RAD),
            )
        }
    }

    /**
     * scale > 1 = fingers spread = zoom in = smaller distance.
     * scale < 1 = fingers pinched = zoom out = larger distance.
     * Division gives the exponential mapping (selenographic-math-camera.md §3).
     */
    fun onPinch(scale: Float) {
        if (scale <= 0f) return
        _state.update { s ->
            s.copy(cameraDistance = (s.cameraDistance / scale).coerceIn(MIN_DIST, MAX_DIST))
        }
    }

    fun setSunDirection(direction: Vec3) {
        _state.update { it.copy(sunDirection = direction) }
    }

    /**
     * Toggle the Filament `Skybox` cubemap. T705 / 07-celestial-background. Direct setter,
     * not a command — concurrent calls are last-writer-wins which is fine for a boolean
     * toggle. The renderer hosts read `state.showStars` per frame and conditionally call
     * `scene.setSkybox(skybox)` / `setSkybox(null)`.
     */
    fun setShowStars(value: Boolean) {
        _state.update { it.copy(showStars = value) }
    }

    fun highlightLocation(id: String?) {
        _state.update { it.copy(highlightedSiteId = id) }
    }

    /**
     * Snap the orbit camera to a target (yaw, pitch) without touching distance, sun
     * direction, or texture set. T225 / 01-app-shell — invoked by
     * `MoonExplorerActionsImpl.flyToMoonLocation` once it resolves a site's lat/lon to
     * (yaw, pitch) via `MoonMath.latLonToYawPitch`. Animated transitions are
     * `03-sites-and-flyto`'s job; this is the snap path.
     *
     * Pitch is clamped to ±PITCH_LIMIT_RAD to match the gesture handler — keeps lookAt's
     * up-vector cross product well-defined near the poles.
     */
    fun setCameraTarget(yawRad: Float, pitchRad: Float) {
        _state.update { s ->
            s.copy(
                cameraYawRad = yawRad,
                cameraPitchRad = pitchRad.coerceIn(-PITCH_LIMIT_RAD, PITCH_LIMIT_RAD),
            )
        }
    }

    /**
     * Pushes a new [TextureSet] for the renderer to bind on its next frame (T114). The
     * loader (T117) drives this through `Placeholder → Bundled2K → Hd8K` over the
     * lifetime of a launch.
     */
    fun setTextureSet(textureSet: TextureSet) {
        _state.update { it.copy(textureSet = textureSet) }
    }

    companion object {
        /** Just above the surface — never enter the Moon. */
        const val MIN_DIST: Float = 1.5f

        /** Comfortable max framing for the spike. Tune in polish. */
        const val MAX_DIST: Float = 20f
    }
}
