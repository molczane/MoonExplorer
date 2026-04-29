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
        provider.applyMaterial = { material in
            renderer.loadMaterial(material: material.toData())
        }
        provider.applyTextureSet = { albedo, normal, isHd in
            renderer.loadTextureSet(
                albedo: albedo.toData(),
                normal: normal.toData(),
                isHd: isHd.boolValue
            )
        }
        provider.applyStarsCubemap = { px, nx, py, ny, pz, nz in
            renderer.loadStarsCubemap(
                px: px.toData(), nx: nx.toData(),
                py: py.toData(), ny: ny.toData(),
                pz: pz.toData(), nz: nz.toData()
            )
        }
        provider.applyShowStars = { show in
            renderer.setShowStars(show.boolValue)
        }
        provider.dispose = { renderer.tearDown() }

        // Pre-fetch the bundled .filamat material via Compose Resources. Texture loading is
        // handled by MoonAssetLoader (commonMain) → state.textureSet → applyTextureSet.
        Task {
            do {
                try await MoonAssetsKt.loadAndPushMaterial()
            } catch {
                print("loadAndPushMaterial failed: \(error)")
            }
        }
        // T704 — pre-fetch the 6 cubemap face PNGs at startup. Same pattern as the material:
        // a one-shot Task that pushes bytes through MoonRendererProvider.applyStarsCubemap.
        Task {
            do {
                try await MoonAssetsKt.loadAndPushStarsCubemap()
            } catch {
                print("loadAndPushStarsCubemap failed: \(error)")
            }
        }
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
