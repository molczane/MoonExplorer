package org.jetbrains.moonexplorer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import org.jetbrains.moonexplorer.domain.DEFAULT_FOV_Y_RAD
import org.jetbrains.moonexplorer.render.MoonViewport
import org.jetbrains.moonexplorer.state.MoonViewModel

/**
 * Top-level Moon Explorer screen: hosts the platform renderer (MoonViewport),
 * the placeholder sun control (T017), and (Phase 4 T041) the touch gesture
 * surface that drives the orbit camera.
 *
 * Gesture wiring (T041): `Modifier.pointerInput { detectTransformGestures { ... } }`
 * on the viewport routes drag pan into `viewModel.onDrag` and pinch zoom into
 * `viewModel.onPinch`. The viewport's pixel height is captured via
 * `Modifier.onSizeChanged` so the zoom-aware pixel-to-radian sensitivity math
 * (selenographic-math-camera.md §4) calibrates against the actual viewport.
 *
 * State propagates back to the renderer via the StateFlow snapshot the
 * platform host pulls per frame (ADR-0003 pull-not-push).
 *
 * The MoonViewModel is created per composition via `remember` for the spike;
 * proper DI follows in `01-app-shell`.
 */
@Composable
fun MoonExplorerScreen(modifier: Modifier = Modifier) {
    val viewModel = remember { MoonViewModel() }
    val state = viewModel.state.collectAsState().value
    var viewportHeightPx by remember { mutableStateOf(0) }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        MoonViewport(
            state = state,
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { size -> viewportHeightPx = size.height }
                .pointerInput(Unit) {
                    // Compose's transform-gesture detector emits incremental
                    // pan/zoom/rotation deltas; we ignore rotation (the orbit
                    // camera doesn't roll). pan is in pixels, zoom is a
                    // multiplicative factor (1.0 = no change, >1 = pinch out).
                    detectTransformGestures(panZoomLock = false) { _, pan, zoom, _ ->
                        if (pan != Offset.Zero && viewportHeightPx > 0) {
                            viewModel.onDrag(
                                dxPx = pan.x,
                                dyPx = pan.y,
                                viewportHpx = viewportHeightPx,
                                fovYRad = DEFAULT_FOV_Y_RAD,
                            )
                        }
                        if (zoom != 1f) {
                            viewModel.onPinch(zoom)
                        }
                    }
                },
        )
        SunControl(
            value = state.sunDirection.x,
            onValueChange = { x -> viewModel.setSunDirection(joystickToHemisphereDir(x)) },
            // navigationBarsPadding() pushes the slider above the iOS home
            // indicator and the Android gesture-nav bar; the viewport stays
            // edge-to-edge (background bleeds behind the system UI).
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp),
        )
        // Phase 6 (T060) debug toggle — flips between the two bundled
        // placeholder albedos. Reachable in the Phase 0 spike UI; will be
        // gated behind a debug build flag (or removed entirely) before any
        // public release. The renderer-side rebind is on `state.albedoVariant`,
        // so the swap propagates through the same per-frame state path as
        // camera/sun (no out-of-band imperative call needed).
        // statusBarsPadding() keeps the button below the iOS notch / Dynamic
        // Island and the Android status bar — without it the hit target
        // overlaps the system UI and is hard to tap.
        TextButton(
            onClick = { viewModel.toggleAlbedoVariant() },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(16.dp),
        ) {
            Text(
                text = if (state.albedoVariant == 0) "Texture A" else "Texture B",
                color = Color.White,
            )
        }
    }
}
