package org.jetbrains.moonexplorer.render

import moonexplorer.shared.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi

/**
 * Loads the Phase 0 spike's bundled material + texture bytes from
 * `composeResources/files/...` and pushes them into the Swift-side renderer
 * via [MoonRendererProvider.applyAssets].
 *
 * Called from `iOSApp.swift`'s `init()` task: `Task { try? await
 * MoonAssetsKt.loadAndPushBundledAssets() }` (top-level Kotlin function →
 * `MoonAssetsKt.loadAndPushBundledAssets()` from Swift).
 *
 * Filenames per Phase 0 deviation from tasks.md (T031): `.png` for both
 * textures (KTX2 deferred to a later phase). Material is `.filamat`.
 */
@OptIn(ExperimentalResourceApi::class)
suspend fun loadAndPushBundledAssets() {
    val albedo = Res.readBytes("files/textures/moon_albedo_2k.png")
    val normal = Res.readBytes("files/textures/moon_normal_2k.png")
    val material = Res.readBytes("files/materials/moon.filamat")
    MoonRendererProvider.applyAssets(albedo, normal, material)

    // Phase 6 (T060) — push the alt albedo so the runtime texture-swap toggle
    // can rebind without going back to disk. Both textures stay resident in
    // GPU memory; ~6 MB combined for the 2 K spike, fine for the spike budget.
    val albedoAlt = Res.readBytes("files/textures/moon_albedo_2k_alt.png")
    MoonRendererProvider.applyAltAlbedo(albedoAlt)
}
