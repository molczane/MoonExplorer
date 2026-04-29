package org.jetbrains.moonexplorer.render

import platform.UIKit.UIViewController

/**
 * Closure-injection bridge from Swift (iosApp) to Kotlin/Native (:shared/iosMain).
 * Per ADR-0002 §"Bridge pattern: closure injection from Swift": Kotlin sees only
 * `UIViewController` and `() -> T` here — no cinterop on iosApp headers.
 *
 * Swift wires the closures from `iOSApp.swift`'s `init()` capturing a single
 * `MoonRendererViewController` instance (T039). Defaults are no-ops / a plain
 * `UIViewController` so commonMain compiles + Kotlin/Native tests can run with
 * no iOS-app wiring.
 */
object MoonRendererProvider {
    private var _factoryWired: Boolean = false

    var factory: () -> UIViewController = { UIViewController() }
        set(value) {
            _factoryWired = true
            field = value
        }

    var applyCamera: (yawRad: Float, pitchRad: Float, distance: Float) -> Unit = { _, _, _ -> }
    var applySunDirection: (x: Float, y: Float, z: Float) -> Unit = { _, _, _ -> }
    var applyMoonRotation: (rotationRad: Float) -> Unit = { _ -> }
    /** One-shot at startup. Compiled .filamat material payload — kicks off material + renderable build. */
    var applyMaterial: (material: ByteArray) -> Unit = { _ -> }
    /**
     * Pushed when [state.textureSet] advances (Placeholder → Bundled2K → Hd8K). The
     * `isHd` flag selects the decoder: PNG via decodePngToRgba8 for the bundled 2 K tier,
     * KTX2 + Basis Universal via Ktx2Reader for the HD tier (ADR-0011 keeps HD iOS-only).
     */
    var applyTextureSet: (albedo: ByteArray, normal: ByteArray, isHd: Boolean) -> Unit = { _, _, _ -> }
    /**
     * One-shot at startup. 6 PNG byte arrays in Filament cubemap face order
     * (+X, -X, +Y, -Y, +Z, -Z) — the renderer decodes, builds a SAMPLER_CUBEMAP
     * Texture + Skybox, and attaches it to the scene if `showStars` is currently
     * true. T704 / 07-celestial-background.
     */
    var applyStarsCubemap: (
        px: ByteArray,
        nx: ByteArray,
        py: ByteArray,
        ny: ByteArray,
        pz: ByteArray,
        nz: ByteArray,
    ) -> Unit = { _, _, _, _, _, _ -> }
    /** Per-recomposition push of `state.showStars`. T704 / 07-celestial-background. */
    var applyShowStars: (showStars: Boolean) -> Unit = { _ -> }
    /**
     * One-shot at startup. Compiled `sun.filamat` payload — kicks off the sun
     * MaterialInstance + 1×1 quad mesh + Renderable build on the iOS side.
     * T713 / 07-celestial-background.
     */
    var applySunMaterial: (material: ByteArray) -> Unit = { _ -> }
    /** Per-recomposition push of `state.showSun`. T713 / 07-celestial-background. */
    var applyShowSun: (showSun: Boolean) -> Unit = { _ -> }
    var dispose: () -> Unit = {}

    /**
     * True once Swift has assigned [factory]. False indicates the iOS app forgot to wire
     * `MoonRendererProvider` in `iOSApp.init()` — the Filament renderer will not start.
     * `MoonViewport.ios.kt` logs a warning at first composition when this is still false
     * (visible in the Xcode console / Console.app).
     */
    val isFactoryWired: Boolean get() = _factoryWired
}
