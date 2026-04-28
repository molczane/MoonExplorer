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
    var factory: () -> UIViewController = { UIViewController() }
    var applyCamera: (yawRad: Float, pitchRad: Float, distance: Float) -> Unit = { _, _, _ -> }
    var applySunDirection: (x: Float, y: Float, z: Float) -> Unit = { _, _, _ -> }
    var applyMoonRotation: (rotationRad: Float) -> Unit = { _ -> }
    var applyAssets: (albedo: ByteArray, normal: ByteArray, material: ByteArray) -> Unit = { _, _, _ -> }
    var dispose: () -> Unit = {}
}
