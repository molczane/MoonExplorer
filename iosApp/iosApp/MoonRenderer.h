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
/**
 * One-shot at startup. Decodes 6 PNG faces (Filament cubemap order:
 * +X, -X, +Y, -Y, +Z, -Z), builds a SAMPLER_CUBEMAP Texture, builds
 * a Skybox, and attaches it to the scene if `showStars` is currently
 * true. Pushed via `MoonRendererProvider.applyStarsCubemap` from
 * `MoonAssets.loadAndPushStarsCubemap()`. T704.
 */
- (void)loadStarsCubemapPx:(NSData *)px
                        nx:(NSData *)nx
                        py:(NSData *)py
                        ny:(NSData *)ny
                        pz:(NSData *)pz
                        nz:(NSData *)nz;
/**
 * Toggle the stars Skybox. T704. Idempotent — flips the scene's skybox between
 * the cached cubemap and `nullptr`. Caches the last-applied flag so repeated
 * calls with the same value are no-ops.
 */
- (void)setShowStars:(BOOL)show;
/**
 * One-shot at startup. Builds the sun's MaterialInstance, the 1x1 quad mesh,
 * and the Renderable. Attaches to the scene if `showSun` is currently true.
 * Pushed via `MoonRendererProvider.applySunMaterial` from
 * `MoonAssets.loadAndPushSunMaterial()`. T713.
 */
- (void)loadSunMaterial:(NSData *)material;
/** Toggle the sun billboard Renderable. T713. */
- (void)setShowSun:(BOOL)show;
- (void)resize:(CGSize)drawableSize;
- (void)pause;
- (void)resume;
- (void)dispose;

@end

NS_ASSUME_NONNULL_END
