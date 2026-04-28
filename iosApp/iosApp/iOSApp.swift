import SwiftUI
import Shared

/// App entry. Wires the Kotlin `MoonRendererProvider` closures to a single
/// `MoonRendererViewController` instance per ADR-0002 §"Bridge pattern" and
/// kicks off bundled-asset loading via Compose Resources.
///
/// The renderer VC is captured by every closure — Compose only ever sees a
/// `UIViewController` (its supertype), so no Filament symbols leak into the
/// Kotlin-side framework.
@main
struct iOSApp: App {

    init() {
        let renderer = MoonRendererViewController()

        let provider = MoonRendererProvider.shared
        provider.factory = { renderer }
        provider.applyCamera = { yaw, pitch, dist in
            renderer.setCamera(
                yaw: yaw.floatValue,
                pitch: pitch.floatValue,
                distance: dist.floatValue
            )
        }
        provider.applySunDirection = { x, y, z in
            renderer.setSunDirection(
                x: x.floatValue,
                y: y.floatValue,
                z: z.floatValue
            )
        }
        provider.applyMoonRotation = { rot in
            renderer.setMoonRotation(rot.floatValue)
        }
        provider.applyAssets = { albedo, normal, material in
            renderer.loadAssets(
                albedo: albedo.toData(),
                normal: normal.toData(),
                material: material.toData()
            )
        }
        provider.applyAltAlbedo = { altAlbedo in
            renderer.loadAltAlbedo(albedo: altAlbedo.toData())
        }
        provider.applyAlbedoVariant = { variant in
            renderer.setAlbedoVariant(Int(truncating: variant))
        }
        provider.dispose = { renderer.tearDown() }

        // Pre-fetch bundled material + textures via Compose Resources, then
        // push them through the provider. The K/N suspend completion callback
        // is wrapped automatically into a Swift async function.
        Task {
            do {
                try await MoonAssetsKt.loadAndPushBundledAssets()
            } catch {
                print("loadAndPushBundledAssets failed: \(error)")
            }
        }
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
