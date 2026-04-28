package org.jetbrains.moonexplorer.render

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.moonexplorer.state.MoonRenderState

/**
 * Per-platform 3D Moon renderer host. Reads MoonRenderState per frame
 * (pull-not-push, per ADR-0003).
 *
 * Phase 2 ships only this expect declaration plus minimal stub `actual`s
 * (a dark Box) so commonMain compiles end-to-end. Real Filament-backed
 * implementations replace those stubs in Phase 3:
 *   - androidMain: T032 (MoonHost) + T033 (MoonViewport.android.kt)
 *   - iosMain:     T034..T040 (closure-injection bridge per ADR-0002)
 */
@Composable
expect fun MoonViewport(state: MoonRenderState, modifier: Modifier = Modifier)
