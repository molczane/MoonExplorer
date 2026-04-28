package org.jetbrains.moonexplorer.render

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.jetbrains.moonexplorer.state.MoonRenderState

/**
 * Phase 2 stub. Replaced by the Filament-backed AndroidView host in Phase 3
 * (T032 MoonHost + T033 MoonViewport.android.kt — full Filament Engine /
 * SwapChain / Renderer / View / Scene wiring per ADR-0001 + ADR-0003).
 *
 * The stub intentionally ignores `state` so it stays a pure visual placeholder
 * that never accidentally becomes part of the test surface.
 */
@Composable
actual fun MoonViewport(state: MoonRenderState, modifier: Modifier) {
    Box(modifier = modifier.background(Color(0xFF1A1A1A)))
}
