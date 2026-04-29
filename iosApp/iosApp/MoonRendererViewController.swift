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

    // Pending pushes that arrive before viewDidLoad creates the underlying MoonRenderer.
    private var pendingMaterial: Data?
    private var pendingTextureAlbedo: Data?
    private var pendingTextureNormal: Data?
    private var pendingTextureIsHd: Bool = false

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
        // Drain any pushes that arrived before viewDidLoad: material first (it builds the
        // mesh + renderable), then the texture set (binds samplers).
        if let m = pendingMaterial {
            r.loadMaterial(m)
            pendingMaterial = nil
        }
        if let a = pendingTextureAlbedo, let n = pendingTextureNormal {
            r.loadTextureSetAlbedo(a, normal: n, isHd: pendingTextureIsHd)
            pendingTextureAlbedo = nil
            pendingTextureNormal = nil
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

    @objc func loadMaterial(material: Data) {
        if let r = renderer {
            r.loadMaterial(material)
        } else {
            pendingMaterial = material
        }
    }

    @objc func loadTextureSet(albedo: Data, normal: Data, isHd: Bool) {
        if let r = renderer {
            r.loadTextureSetAlbedo(albedo, normal: normal, isHd: isHd)
        } else {
            pendingTextureAlbedo = albedo
            pendingTextureNormal = normal
            pendingTextureIsHd = isHd
        }
    }

    @objc func tearDown() {
        renderer?.dispose()
        renderer = nil
    }
}
