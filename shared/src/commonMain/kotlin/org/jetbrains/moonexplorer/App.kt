package org.jetbrains.moonexplorer

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import org.jetbrains.moonexplorer.ui.MoonExplorerScreen

@Composable
@Preview
fun App() {
    MaterialTheme {
        MoonExplorerScreen()
    }
}
