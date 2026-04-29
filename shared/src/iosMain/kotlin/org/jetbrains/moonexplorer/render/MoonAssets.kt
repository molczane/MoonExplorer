package org.jetbrains.moonexplorer.render

import moonexplorer.shared.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi

/**
 * iOS-only one-shot at app startup: read the bundled `.filamat` material payload from
 * compose resources and push it through [MoonRendererProvider.applyMaterial]. The Swift
 * `iOSApp.init()` calls this from a `Task { try? await … }` so the renderer is ready by
 * the time first frame fires.
 *
 * Texture loading is no longer this function's concern — `MoonAssetLoader` (commonMain)
 * pushes the bundled 2 K + (iOS-only) HD KTX2 byte sets via `state.textureSet`, and
 * `MoonViewport.ios.kt`'s `LaunchedEffect` forwards them through
 * `MoonRendererProvider.applyTextureSet`.
 */
@OptIn(ExperimentalResourceApi::class)
suspend fun loadAndPushMaterial() {
    val material = Res.readBytes("files/materials/moon.filamat")
    MoonRendererProvider.applyMaterial(material)
}
