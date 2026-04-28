package org.jetbrains.moonexplorer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.moonexplorer.render.MoonViewport
import org.jetbrains.moonexplorer.state.MoonViewModel

/**
 * Top-level Moon Explorer screen: hosts the platform renderer (MoonViewport)
 * and the placeholder sun control (T017). Gesture wiring (T041 in Phase 4)
 * and real DI (`01-app-shell`) are deferred — for the spike the ViewModel
 * is created per composition via `remember`.
 */
@Composable
fun MoonExplorerScreen(modifier: Modifier = Modifier) {
    val viewModel = remember { MoonViewModel() }
    val state = viewModel.state.collectAsState().value

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        MoonViewport(state = state, modifier = Modifier.fillMaxSize())
        SunControl(
            value = state.sunDirection.x,
            onValueChange = { x -> viewModel.setSunDirection(joystickToHemisphereDir(x)) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
        )
    }
}
