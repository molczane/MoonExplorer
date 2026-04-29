package org.jetbrains.moonexplorer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import org.jetbrains.moonexplorer.assets.StorageDir

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val ctx = applicationContext
        setContent {
            val storage = remember(ctx) { StorageDir(ctx) }
            App(storage = storage)
        }
    }
}