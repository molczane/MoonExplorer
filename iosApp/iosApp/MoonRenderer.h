#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/**
 * Objective-C++ wrapper around Filament's C++ API. The .mm hides every
 * `<filament/...>` and `<math/...>` include behind this interface — Swift
 * (and Kotlin/Native, transitively) only ever sees ObjC types.
 *
 * Lifecycle (see ADR-0002 §"Bridge pattern" + filament-cmp-integration.md §3):
 *   init               -> Engine, SwapChain (from `layer`), Renderer, View,
 *                          Scene, Camera, directional sun light, CADisplayLink
 *                          (paused).
 *   resume             -> CADisplayLink to NSRunLoopCommonModes.
 *   pause              -> invalidate CADisplayLink (must stop posting frames
 *                          before the surface goes away).
 *   loadMaterial:      -> one-shot at startup. Builds Material +
 *                          MaterialInstance + sphere mesh + Renderable. Pushed
 *                          via `MoonRendererProvider.applyMaterial` from
 *                          `MoonAssets.kt`'s `loadAndPushMaterial`.
 *   loadTextureSet...  -> per-tier-change. Decodes albedo + normal (PNG via
 *                          decodePngToRgba8 for `isHd=NO`; KTX2 + Basis
 *                          Universal via Ktx2Reader for `isHd=YES`), uploads
 *                          fresh Filament Textures, rebinds the material's
 *                          `albedo`/`normalMap` samplers, and destroys the
 *                          previous Textures. Idempotent — calling with the
 *                          same byte refs is a no-op via the renderer's
 *                          dedup check.
 *   set...             -> cache state in instance variables; renderloop
 *                          applies them once per frame (pull-not-push,
 *                          ADR-0003).
 *   dispose            -> destroy everything in reverse construction order,
 *                          finally `Engine::destroy(engine)`.
 */
@interface MoonRenderer : NSObject

- (instancetype)initWithLayer:(CALayer *)layer;
- (void)setCameraYaw:(float)yaw pitch:(float)pitch distance:(float)distance;
- (void)setSunDirectionX:(float)x y:(float)y z:(float)z;
- (void)setMoonRotation:(float)rotation;
- (void)loadMaterial:(NSData *)material;
- (void)loadTextureSetAlbedo:(NSData *)albedo
                      normal:(NSData *)normal
                        isHd:(BOOL)isHd;
- (void)resize:(CGSize)drawableSize;
- (void)pause;
- (void)resume;
- (void)dispose;

@end

NS_ASSUME_NONNULL_END
