package org.jetbrains.moonexplorer.render

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitViewController
import kotlinx.cinterop.ExperimentalForeignApi
import org.jetbrains.moonexplorer.state.MoonRenderState

/**
 * iOS actual for the renderer-host seam (ADR-0003). The platform host is the
 * Swift `MoonRendererViewController` (in iosApp/), wired in via
 * [MoonRendererProvider] closures (ADR-0002 §"Bridge pattern: closure
 * injection from Swift").
 *
 * Per-frame state delivery is pull-not-push (ADR-0003): each `update` call —
 * which Compose triggers when `state` changes — pushes the latest yaw / pitch /
 * distance / sun direction / moon rotation through the provider closures.
 * The Swift CADisplayLink reads them inside its `renderloop` selector.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun MoonViewport(state: MoonRenderState, modifier: Modifier) {
    // Phase Final (T091) wiring guard. Fires once at first composition. If
    // the iOS app forgot to set MoonRendererProvider.factory in iOSApp.init(),
    // the default `{ UIViewController() }` would silently produce an empty
    // host and the user would see a black screen with no Filament rendering
    // and no obvious error. The println surfaces in the Xcode console so the
    // mistake is loud rather than silent. Not fatal — Compose still renders.
    LaunchedEffect(Unit) {
        if (!MoonRendererProvider.isFactoryWired) {
            println(
                "[MoonExplorer] WARNING: MoonRendererProvider was not wired " +
                    "by the iOS app — Filament renderer will not start. " +
                    "Wire MoonRendererProvider.shared.{factory, applyCamera, " +
                    "applySunDirection, applyMoonRotation, applyAssets, " +
                    "applyAltAlbedo, applyAlbedoVariant, dispose} in " +
                    "iOSApp.init(). See ADR-0002 §\"Bridge pattern\" and " +
                    "iosApp/README.md.",
            )
        }
    }
    val vc = remember { MoonRendererProvider.factory() }
    UIKitViewController(
        factory = { vc },
        update = {
            MoonRendererProvider.applyCamera(state.cameraYawRad, state.cameraPitchRad, state.cameraDistance)
            MoonRendererProvider.applySunDirection(state.sunDirection.x, state.sunDirection.y, state.sunDirection.z)
            MoonRendererProvider.applyMoonRotation(state.moonRotationRad)
            MoonRendererProvider.applyAlbedoVariant(state.albedoVariant)
        },
        onRelease = { MoonRendererProvider.dispose() },
        modifier = modifier,
    )
}
