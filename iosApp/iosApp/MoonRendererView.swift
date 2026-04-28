import UIKit
import QuartzCore

/// CAMetalLayer-backed UIView. The Filament SwapChain is created from this
/// layer pointer (see MoonRendererViewController + MoonRenderer.mm).
///
/// Per filament-cmp-integration.md §3, the layer needs `pixelFormat = bgra8Unorm`
/// and a `drawableSize` that tracks `bounds.size * contentScaleFactor`. The
/// ViewController forwards `layoutSubviews`-driven size changes to Filament so
/// rotation / split-screen resize keep the projection in sync.
final class MoonRendererView: UIView {

    /// Optional callback fired when the drawable size changes (rotation,
    /// safe-area changes, etc). The ViewController hooks this up to forward
    /// to MoonRenderer's `resize:`.
    var onDrawableSizeChanged: ((CGSize) -> Void)?

    override class var layerClass: AnyClass {
        CAMetalLayer.self
    }

    var metalLayer: CAMetalLayer { self.layer as! CAMetalLayer }

    override init(frame: CGRect) {
        super.init(frame: frame)
        configureMetalLayer()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        configureMetalLayer()
    }

    private func configureMetalLayer() {
        let layer = metalLayer
        layer.pixelFormat = .bgra8Unorm
        layer.framebufferOnly = true
        layer.contentsScale = window?.screen.scale ?? UIScreen.main.scale
        backgroundColor = .black
        isOpaque = true
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        if let scale = window?.screen.scale {
            metalLayer.contentsScale = scale
        }
        updateDrawableSize()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        updateDrawableSize()
    }

    private var lastReportedSize: CGSize = .zero

    private func updateDrawableSize() {
        let scale = metalLayer.contentsScale > 0 ? metalLayer.contentsScale : 1.0
        let size = CGSize(
            width: max(1.0, bounds.size.width * scale),
            height: max(1.0, bounds.size.height * scale)
        )
        if abs(size.width - lastReportedSize.width) < 0.5 &&
            abs(size.height - lastReportedSize.height) < 0.5 {
            return
        }
        metalLayer.drawableSize = size
        lastReportedSize = size
        onDrawableSizeChanged?(size)
    }
}
