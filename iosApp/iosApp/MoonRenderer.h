#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/**
 * Objective-C++ wrapper around Filament's C++ API. The .mm hides every
 * `<filament/...>` and `<math/...>` include behind this interface — Swift
 * (and Kotlin/Native, transitively) only ever sees ObjC types.
 *
 * Lifecycle (see ADR-0002 §"Bridge pattern" + filament-cmp-integration.md §3):
 *   init             → Engine, SwapChain (from `layer`), Renderer, View,
 *                       Scene, Camera, default Material+MaterialInstance,
 *                       directional sun light, procedural UV sphere mesh,
 *                       CADisplayLink (paused).
 *   resume           → CADisplayLink to NSRunLoopCommonModes.
 *   pause            → invalidate CADisplayLink (must stop posting frames
 *                       before the surface goes away).
 *   loadAssets:...   → upload the albedo + normal textures and rebind the
 *                       material instance. Called at app startup from the
 *                       `MoonRendererProvider.applyAssets` closure (T040).
 *   set...           → cache state in instance variables; renderloop applies
 *                       them once per frame (pull-not-push, ADR-0003).
 *   dispose          → destroy everything in reverse construction order,
 *                       finally `Engine::destroy(engine)`.
 */
@interface MoonRenderer : NSObject

- (instancetype)initWithLayer:(CALayer *)layer;
- (void)setCameraYaw:(float)yaw pitch:(float)pitch distance:(float)distance;
- (void)setSunDirectionX:(float)x y:(float)y z:(float)z;
- (void)setMoonRotation:(float)rotation;
- (void)loadAssetsAlbedo:(NSData *)albedo normal:(NSData *)normal material:(NSData *)material;
/** Phase 6 (T060). Decode + upload the alt albedo PNG once at startup so
 *  the variant toggle below can rebind without going back to disk. */
- (void)loadAltAlbedo:(NSData *)albedo;
/** Phase 6 (T060). 0 = primary albedo, 1 = alt. Idempotent — only rebinds
 *  the material's `albedo` sampler when the variant actually changes. */
- (void)setAlbedoVariant:(int)variant;
- (void)resize:(CGSize)drawableSize;
- (void)pause;
- (void)resume;
- (void)dispose;

@end

NS_ASSUME_NONNULL_END
