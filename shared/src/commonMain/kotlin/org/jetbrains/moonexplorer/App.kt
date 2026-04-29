package org.jetbrains.moonexplorer

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import org.jetbrains.moonexplorer.assets.StorageDir
import org.jetbrains.moonexplorer.ui.MoonExplorerScreen

@Composable
fun App(storage: StorageDir) {
    MaterialTheme {
        MoonExplorerScreen(storage = storage)
    }
}
