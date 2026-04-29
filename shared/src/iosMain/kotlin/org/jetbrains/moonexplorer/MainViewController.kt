package org.jetbrains.moonexplorer

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import org.jetbrains.moonexplorer.assets.IosStorageDir

fun MainViewController() = ComposeUIViewController {
    val storage = remember { IosStorageDir() }
    App(storage = storage)
}