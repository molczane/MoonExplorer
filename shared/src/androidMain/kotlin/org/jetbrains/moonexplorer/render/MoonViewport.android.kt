package org.jetbrains.moonexplorer.render

import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.jetbrains.moonexplorer.state.MoonRenderState

/**
 * Android `actual` for the cross-platform `MoonViewport` Composable. Hosts a
 * Filament-backed [SurfaceView] inside Compose's `AndroidView` interop.
 *
 * Per ADR-0003 the host is a pull-not-push reader of [MoonRenderState]. The
 * `update` lambda forwards every recomposed snapshot into the host's volatile
 * field; the Choreographer frame callback inside [MoonHost] reads it on the
 * next frame. Lifecycle observation is wired through [LocalLifecycleOwner].
 *
 * The host is stashed on the SurfaceView via [SurfaceView.setTag] (no
 * Android resource id required). `onRelease` tears down the Engine graph.
 */
@Composable
actual fun MoonViewport(state: MoonRenderState, modifier: Modifier) {
    val lifecycleOwner = LocalLifecycleOwner.current
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val surfaceView = SurfaceView(ctx)
            val host = MoonHost(surfaceView).also { it.start(lifecycleOwner) }
            surfaceView.tag = host
            surfaceView
        },
        update = { sv ->
            (sv.tag as? MoonHost)?.updateState(state)
        },
        onRelease = { sv ->
            (sv.tag as? MoonHost)?.destroy()
            sv.tag = null
        },
    )
}
