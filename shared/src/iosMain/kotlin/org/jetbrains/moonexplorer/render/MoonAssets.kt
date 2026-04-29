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

/**
 * iOS-only one-shot at app startup: read the 6 bundled cubemap face PNGs from compose
 * resources and push them through [MoonRendererProvider.applyStarsCubemap]. Mirrors
 * [loadAndPushMaterial]'s pattern: Swift `iOSApp.init()` calls this from a `Task` so
 * the Skybox is ready by the time first frame fires. T704 / 07-celestial-background.
 *
 * Face order matches Filament's cubemap enum: +X, -X, +Y, -Y, +Z, -Z.
 */
@OptIn(ExperimentalResourceApi::class)
suspend fun loadAndPushStarsCubemap() {
    val px = Res.readBytes("files/stars/px.png")
    val nx = Res.readBytes("files/stars/nx.png")
    val py = Res.readBytes("files/stars/py.png")
    val ny = Res.readBytes("files/stars/ny.png")
    val pz = Res.readBytes("files/stars/pz.png")
    val nz = Res.readBytes("files/stars/nz.png")
    MoonRendererProvider.applyStarsCubemap(px, nx, py, ny, pz, nz)
}

/**
 * iOS-only one-shot at app startup: read the bundled `sun.filamat` material payload from
 * compose resources and push it through [MoonRendererProvider.applySunMaterial]. Mirrors
 * [loadAndPushMaterial]'s pattern. T713 / 07-celestial-background.
 */
@OptIn(ExperimentalResourceApi::class)
suspend fun loadAndPushSunMaterial() {
    val material = Res.readBytes("files/materials/sun.filamat")
    MoonRendererProvider.applySunMaterial(material)
}
