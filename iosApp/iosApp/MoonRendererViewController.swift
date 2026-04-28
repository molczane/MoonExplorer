import UIKit
import Foundation

/// Hosts the CAMetalLayer-backed view and forwards setter calls into the
/// Objective-C++ MoonRenderer (which talks to Filament). This is the
/// `UIViewController` the Compose `UIKitViewController { factory = ... }`
/// integration sees from Kotlin/Native — Kotlin only ever sees the
/// supertype, so MoonRenderer's C++ inclusions never cross the KMP boundary.
///
/// Lifecycle (per filament-cmp-integration.md §7 + ADR-0003 threading invariants):
///   - `loadView`         creates the MoonRendererView.
///   - `viewDidLoad`      instantiates MoonRenderer with the metal layer.
///   - `viewWillAppear`   calls `renderer.resume` (CADisplayLink fires).
///   - `viewWillDisappear` calls `renderer.pause` (must stop posting frames
///                        before the surface goes away).
///   - `tearDown`         is called from the Kotlin `MoonRendererProvider.dispose`
///                        closure when Compose releases the host.
final class MoonRendererViewController: UIViewController {

    private var renderer: MoonRenderer?
    private var rendererView: MoonRendererView?
    private var pendingAlbedo: Data?
    private var pendingNormal: Data?
    private var pendingMaterial: Data?
    private var pendingAltAlbedo: Data?
    private var pendingAlbedoVariant: Int?

    override func loadView() {
        let v = MoonRendererView(frame: .zero)
        v.backgroundColor = .black
        v.translatesAutoresizingMaskIntoConstraints = true
        v.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        v.onDrawableSizeChanged = { [weak self] size in
            self?.renderer?.resize(size)
        }
        self.view = v
        self.rendererView = v
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        guard let v = rendererView else { return }
        let r = MoonRenderer(layer: v.layer)
        renderer = r
        // Push any assets that arrived before the renderer existed.
        if let a = pendingAlbedo, let n = pendingNormal, let m = pendingMaterial {
            r.loadAssetsAlbedo(a, normal: n, material: m)
            pendingAlbedo = nil; pendingNormal = nil; pendingMaterial = nil
        }
        if let alt = pendingAltAlbedo {
            r.loadAltAlbedo(alt)
            pendingAltAlbedo = nil
        }
        if let v = pendingAlbedoVariant {
            r.setAlbedoVariant(Int32(v))
            pendingAlbedoVariant = nil
        }
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        renderer?.resume()
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        renderer?.pause()
    }

    // MARK: - Forwarders (called from Swift closures wired in iOSApp.swift)

    @objc func setCamera(yaw: Float, pitch: Float, distance: Float) {
        renderer?.setCameraYaw(yaw, pitch: pitch, distance: distance)
    }

    @objc func setSunDirection(x: Float, y: Float, z: Float) {
        renderer?.setSunDirectionX(x, y: y, z: z)
    }

    @objc func setMoonRotation(_ rotation: Float) {
        renderer?.setMoonRotation(rotation)
    }

    @objc func loadAssets(albedo: Data, normal: Data, material: Data) {
        if let r = renderer {
            r.loadAssetsAlbedo(albedo, normal: normal, material: material)
        } else {
            pendingAlbedo = albedo
            pendingNormal = normal
            pendingMaterial = material
        }
    }

    @objc func loadAltAlbedo(albedo: Data) {
        if let r = renderer {
            r.loadAltAlbedo(albedo)
        } else {
            pendingAltAlbedo = albedo
        }
    }

    @objc func setAlbedoVariant(_ variant: Int) {
        if let r = renderer {
            r.setAlbedoVariant(Int32(variant))
        } else {
            pendingAlbedoVariant = variant
        }
    }

    @objc func tearDown() {
        renderer?.dispose()
        renderer = nil
    }
}
