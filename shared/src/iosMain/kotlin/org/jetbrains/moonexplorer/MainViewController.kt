package org.jetbrains.moonexplorer

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import org.jetbrains.moonexplorer.assets.StorageDir

fun MainViewController() = ComposeUIViewController {
    val storage = remember { StorageDir() }
    App(storage = storage)
}