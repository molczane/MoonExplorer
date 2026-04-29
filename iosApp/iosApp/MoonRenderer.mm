#import "MoonRenderer.h"

#import <Metal/Metal.h>
#import <QuartzCore/CAMetalLayer.h>

#include <filament/Camera.h>
#include <filament/Engine.h>
#include <filament/IndexBuffer.h>
#include <filament/LightManager.h>
#include <filament/Material.h>
#include <filament/MaterialEnums.h>
#include <filament/MaterialInstance.h>
#include <filament/RenderableManager.h>
#include <filament/Renderer.h>
#include <filament/Scene.h>
#include <filament/SwapChain.h>
#include <filament/Texture.h>
#include <filament/TextureSampler.h>
#include <filament/TransformManager.h>
#include <filament/VertexBuffer.h>
#include <filament/View.h>
#include <filament/Viewport.h>

#include <math/mat3.h>
#include <math/mat4.h>
#include <math/quat.h>
#include <math/vec2.h>
#include <math/vec3.h>
#include <math/vec4.h>

#include <ktxreader/Ktx2Reader.h>

#include <utils/Entity.h>
#include <utils/EntityManager.h>

#include <cmath>
#include <cstdint>
#include <cstring>
#include <utility>
#include <vector>

using namespace filament;
using namespace filament::math;
using utils::Entity;
using utils::EntityManager;

// --- Procedural UV sphere mesh (mirrors commonMain UvSphere.kt convention) ---
//
// Right-handed Y-up; ADR-0006 §"Texture mapping":
//   u = i / segments,  v = 1 - j / rings.
// The mesh is interleaved (POSITION + UV0 + TANGENT_QUAT) for one
// VertexBuffer/IndexBuffer pair. Tangent is encoded as a quaternion the way
// Filament wants for the TANGENTS attribute (lit shading + normalMap parameter
// from moon.mat). The quaternion is built from a normal/tangent/bitangent
// frame using the helper at the bottom of the file.

namespace {

struct Vertex {
    float3 position;
    float2 uv;
    quatf  tangent;
};

// Build a quaternion that orients (1,0,0) → tangent, (0,1,0) → bitangent,
// (0,0,1) → normal. This matches Filament's expected encoding for the
// TANGENTS attribute (see filament/Materials.md and the gltf sample).
quatf packTangentFrame(const float3& n, const float3& t, const float3& b) {
    // Build a 3x3 matrix and convert to quaternion.
    mat3f m;
    m[0] = t;
    m[1] = b;
    m[2] = n;
    return mat3f::packTangentFrame(m);
}

void generateSphereMesh(uint32_t segments, uint32_t rings,
                        std::vector<Vertex>& outVertices,
                        std::vector<uint16_t>& outIndices) {
    const uint32_t vertexCount = (segments + 1) * (rings + 1);
    outVertices.clear();
    outVertices.reserve(vertexCount);

    const float PI_F = static_cast<float>(M_PI);
    for (uint32_t j = 0; j <= rings; ++j) {
        const float v = 1.0f - (float)j / (float)rings;
        // lat: -PI/2 at j=0 (south), +PI/2 at j=rings (north).
        const float lat = (-0.5f + (float)j / (float)rings) * PI_F;
        const float cl = std::cos(lat);
        const float sl = std::sin(lat);
        for (uint32_t i = 0; i <= segments; ++i) {
            const float u = (float)i / (float)segments;
            // lon: -PI at i=0, +PI at i=segments.
            const float lon = (-1.0f + 2.0f * (float)i / (float)segments) * PI_F;
            const float cs = std::cos(lon);
            const float ss = std::sin(lon);

            float3 position{ cl * ss, sl, cl * cs };
            float3 normal = position; // unit sphere
            // dPos/dlon (east), normalized: (cos(lon), 0, -sin(lon)).
            float3 tangent{ cs, 0.0f, -ss };
            // bitangent = normal × tangent (north-pointing on sphere).
            float3 bitangent = cross(normal, tangent);

            quatf q = packTangentFrame(normal, tangent, bitangent);

            outVertices.push_back({ position, { u, v }, q });
        }
    }

    const uint32_t triCount = segments * rings * 2;
    outIndices.clear();
    outIndices.reserve(triCount * 3);
    for (uint32_t j = 0; j < rings; ++j) {
        for (uint32_t i = 0; i < segments; ++i) {
            uint16_t a = (uint16_t)(j * (segments + 1) + i);
            uint16_t b = (uint16_t)(a + 1);
            uint16_t c = (uint16_t)(a + (segments + 1));
            uint16_t d = (uint16_t)(c + 1);
            outIndices.push_back(a); outIndices.push_back(b); outIndices.push_back(c);
            outIndices.push_back(b); outIndices.push_back(d); outIndices.push_back(c);
        }
    }
}

// Decodes a PNG (or any UIImage-readable format) NSData into a tightly-packed
// RGBA8 buffer in top-down row order. Returns nil on failure.
//
// The texture comes back from CGContext as RGBA in the device color space; we
// declare the Filament internal format (SRGB8_A8 for albedo, RGBA8 for normal)
// independently — the GPU does the right thing with the raw bytes.
NSData* decodePngToRgba8(NSData* pngData, uint32_t* outW, uint32_t* outH) {
    UIImage* image = [UIImage imageWithData:pngData];
    if (!image || !image.CGImage) {
        return nil;
    }
    CGImageRef cgImage = image.CGImage;
    const size_t width = CGImageGetWidth(cgImage);
    const size_t height = CGImageGetHeight(cgImage);
    const size_t bytesPerPixel = 4;
    const size_t bytesPerRow = bytesPerPixel * width;
    const size_t bitsPerComponent = 8;

    NSMutableData* buffer = [NSMutableData dataWithLength:width * height * bytesPerPixel];
    if (!buffer) {
        return nil;
    }

    CGColorSpaceRef colorSpace = CGColorSpaceCreateDeviceRGB();
    // Top-down: do NOT flip the y axis. Filament expects rows top-to-bottom,
    // matching how the source PNG was authored.
    uint32_t bitmapInfo = (uint32_t)kCGImageAlphaPremultipliedLast |
                          (uint32_t)kCGBitmapByteOrder32Big;
    CGContextRef ctx = CGBitmapContextCreate(
        buffer.mutableBytes,
        width, height,
        bitsPerComponent,
        bytesPerRow,
        colorSpace,
        bitmapInfo);
    CGColorSpaceRelease(colorSpace);
    if (!ctx) {
        return nil;
    }

    CGContextDrawImage(ctx, CGRectMake(0, 0, (CGFloat)width, (CGFloat)height), cgImage);
    CGContextRelease(ctx);

    if (outW) *outW = (uint32_t)width;
    if (outH) *outH = (uint32_t)height;
    return buffer;
}

void releaseNsData(void*, size_t, void* user) {
    NSData* keep = (__bridge_transfer NSData*)user;
    (void)keep;
}

void releaseStdVectorBytes(void*, size_t, void* user) {
    auto* vec = static_cast<std::vector<uint8_t>*>(user);
    delete vec;
}

void releaseStdVectorIndices(void*, size_t, void* user) {
    auto* vec = static_cast<std::vector<uint16_t>*>(user);
    delete vec;
}

// Decode an albedo / normal PNG NSData into a freshly-uploaded Filament Texture (RGBA8888,
// single mip — bundled 2 K bytes are small enough that mipmapping isn't worth a polish-grade
// gen-mips pass for the bundled tier).
Texture* uploadPngTexture(Engine& engine, NSData* png, Texture::InternalFormat fmt) {
    uint32_t w = 0, h = 0;
    NSData* rgba = decodePngToRgba8(png, &w, &h);
    if (!rgba) return nullptr;

    Texture* tex = Texture::Builder()
        .width(w).height(h).levels(1)
        .sampler(Texture::Sampler::SAMPLER_2D)
        .format(fmt)
        .build(engine);
    if (!tex) return nullptr;

    const size_t size = (size_t)w * (size_t)h * 4;
    void* keep = (__bridge_retained void*)rgba;
    Texture::PixelBufferDescriptor pbd(
        rgba.bytes, size,
        Texture::Format::RGBA, Texture::Type::UBYTE,
        &releaseNsData, keep);
    tex->setImage(engine, 0, std::move(pbd));
    return tex;
}

// Transcode a KTX2 + Basis Universal blob into a Filament Texture, picking the GPU-native
// compressed format from the requested-format priority list. ASTC 8x8 / ETC2 / RGBA8 fallback
// covers iOS A8+ Metal devices.
Texture* uploadKtx2Texture(Engine& engine, NSData* ktx2, BOOL srgb) {
    if (!ktx2 || ktx2.length == 0) return nullptr;
    ktxreader::Ktx2Reader reader(engine, /*quiet*/ true);
    if (srgb) {
        reader.requestFormat(Texture::InternalFormat::SRGB8_ALPHA8_ASTC_8x8);
        reader.requestFormat(Texture::InternalFormat::SRGB8_ALPHA8_ASTC_6x6);
        reader.requestFormat(Texture::InternalFormat::SRGB8_ALPHA8_ASTC_4x4);
        reader.requestFormat(Texture::InternalFormat::ETC2_EAC_SRGBA8);
        reader.requestFormat(Texture::InternalFormat::SRGB8_A8);
    } else {
        reader.requestFormat(Texture::InternalFormat::RGBA_ASTC_8x8);
        reader.requestFormat(Texture::InternalFormat::RGBA_ASTC_6x6);
        reader.requestFormat(Texture::InternalFormat::RGBA_ASTC_4x4);
        reader.requestFormat(Texture::InternalFormat::ETC2_EAC_RGBA8);
        reader.requestFormat(Texture::InternalFormat::RGBA8);
    }
    return reader.load(
        ktx2.bytes,
        ktx2.length,
        srgb ? ktxreader::Ktx2Reader::TransferFunction::sRGB
             : ktxreader::Ktx2Reader::TransferFunction::LINEAR);
}

} // namespace

// --- ObjC wrapper ---

@implementation MoonRenderer {
    CALayer* _layer;

    Engine* _engine;
    SwapChain* _swapChain;
    Renderer* _renderer;
    View* _view;
    Scene* _scene;
    Camera* _camera;
    Entity _cameraEntity;
    Entity _sunEntity;
    Entity _moonEntity;

    Material* _material;
    MaterialInstance* _materialInstance;
    Texture* _albedoTex;
    Texture* _normalTex;
    VertexBuffer* _vertexBuffer;
    IndexBuffer* _indexBuffer;
    uint32_t _indexCount;

    // Dedup for loadTextureSetAlbedo:normal:isHd: — Compose's LaunchedEffect on textureSet
    // changes only refires when the data class equals differs (ByteArray identity), but a
    // belt-and-braces ptr+length check here protects against re-binding identical bytes.
    const void* _lastAlbedoPtr;
    NSUInteger _lastAlbedoLen;
    const void* _lastNormalPtr;
    NSUInteger _lastNormalLen;
    BOOL _lastWasHd;

    CADisplayLink* _displayLink;
    BOOL _running;
    BOOL _materialBuilt;
    BOOL _meshBuilt;

    // Cached state. **All access is on the main thread** — both the setters
    // (called from Compose's `update` lambda via the Kotlin `MoonRendererProvider`
    // closures) and the reader (`renderloop`, driven by CADisplayLink on the
    // main run loop) share the main thread. No atomicity needed (Phase 3 review
    // #5). If a future setter is ever called off-main, switch to a single
    // atomically-swapped POD struct rather than re-atomicising each field.
    float _yaw;
    float _pitch;
    float _distance;
    float _sunX;
    float _sunY;
    float _sunZ;
    float _moonRotation;
}

- (instancetype)initWithLayer:(CALayer *)layer {
    self = [super init];
    if (!self) return nil;

    _layer = layer;
    _running = NO;
    _materialBuilt = NO;
    _meshBuilt = NO;
    _indexCount = 0;
    _lastAlbedoPtr = nullptr;
    _lastAlbedoLen = 0;
    _lastNormalPtr = nullptr;
    _lastNormalLen = 0;
    _lastWasHd = NO;

    // Initial state matches MoonRenderState defaults.
    _yaw = 0.0f;
    _pitch = 0.0f;
    _distance = 5.0f;
    _sunX = 0.0f;
    _sunY = 0.0f;
    _sunZ = 1.0f;
    _moonRotation = 0.0f;

    // --- Engine + SwapChain (CAMetalLayer) ---
    _engine = Engine::create(Engine::Backend::METAL);
    _swapChain = _engine->createSwapChain((__bridge void*)layer);
    _renderer = _engine->createRenderer();

    Renderer::ClearOptions clearOpts;
    clearOpts.clearColor = { 0.0f, 0.0f, 0.0f, 1.0f };
    clearOpts.clear = true;
    _renderer->setClearOptions(clearOpts);

    _scene = _engine->createScene();
    _view = _engine->createView();
    _view->setScene(_scene);
    // Post-processing left at Filament default (ON) — tone-mapping + gamma are
    // load-bearing for sRGB output. Was `setPostProcessingEnabled(false)` in
    // the initial Phase 3 cut, which made iOS visually diverge from Android
    // (Phase 3 review #1).

    _cameraEntity = EntityManager::get().create();
    _camera = _engine->createCamera(_cameraEntity);
    // Explicit exposure for cross-platform parity with Android (Phase 3
    // review #2). Filament's default is f/16 ISO 100 1/125s — same values,
    // but stating them protects against future Filament default changes.
    _camera->setExposure(16.0f, 1.0f / 125.0f, 100.0f);
    _view->setCamera(_camera);

    // Initial viewport matches the CAMetalLayer drawable size; resize:
    // updates this whenever the host UIView's bounds change.
    const CGFloat scale = layer.contentsScale > 0 ? layer.contentsScale : 1.0;
    const uint32_t w = (uint32_t)std::max(1.0, layer.bounds.size.width * scale);
    const uint32_t h = (uint32_t)std::max(1.0, layer.bounds.size.height * scale);
    _view->setViewport({0, 0, w, h});
    _camera->setProjection(45.0, (double)w / (double)h, 0.1, 100.0,
                           Camera::Fov::VERTICAL);

    // --- Sun light (a single directional). Scene only — no IBL in Phase 0. ---
    // Color matches Android (1.0, 0.96, 0.92) — slightly warm white, the
    // plausible color of unattenuated sunlight (Phase 3 review #3).
    _sunEntity = EntityManager::get().create();
    LightManager::Builder(LightManager::Type::DIRECTIONAL)
        .color({ 1.0f, 0.96f, 0.92f })
        .intensity(110000.0f) // bright outdoor sunlight
        .direction({ 0.0f, 0.0f, -1.0f })
        .castShadows(false)
        .build(*_engine, _sunEntity);
    _scene->addEntity(_sunEntity);

    // --- Display link (paused) ---
    _displayLink = [CADisplayLink displayLinkWithTarget:self
                                               selector:@selector(renderloop)];
    _displayLink.paused = YES;
    [_displayLink addToRunLoop:[NSRunLoop mainRunLoop]
                       forMode:NSRunLoopCommonModes];

    return self;
}

- (void)dealloc {
    [self dispose];
}

- (void)dispose {
    if (_engine == nullptr) {
        return;
    }
    [_displayLink invalidate];
    _displayLink = nil;
    _running = NO;

    // Reverse construction order. Entity-backed components (camera, sun, moon
    // renderable) are destroyed via their components, then the entity itself.
    if (_moonEntity) {
        _scene->remove(_moonEntity);
        _engine->destroy(_moonEntity);
        EntityManager::get().destroy(_moonEntity);
        _moonEntity = Entity{};
    }
    if (_sunEntity) {
        _scene->remove(_sunEntity);
        _engine->destroy(_sunEntity);
        EntityManager::get().destroy(_sunEntity);
        _sunEntity = Entity{};
    }
    if (_indexBuffer)    { _engine->destroy(_indexBuffer);    _indexBuffer    = nullptr; }
    if (_vertexBuffer)   { _engine->destroy(_vertexBuffer);   _vertexBuffer   = nullptr; }
    if (_albedoTex)      { _engine->destroy(_albedoTex);      _albedoTex      = nullptr; }
    if (_normalTex)      { _engine->destroy(_normalTex);      _normalTex      = nullptr; }
    if (_materialInstance) { _engine->destroy(_materialInstance); _materialInstance = nullptr; }
    if (_material)       { _engine->destroy(_material);       _material       = nullptr; }
    if (_view)           { _engine->destroy(_view);           _view           = nullptr; }
    if (_scene)          { _engine->destroy(_scene);          _scene          = nullptr; }
    if (_cameraEntity) {
        _engine->destroyCameraComponent(_cameraEntity);
        EntityManager::get().destroy(_cameraEntity);
        _cameraEntity = Entity{};
        _camera = nullptr;
    }
    if (_renderer)       { _engine->destroy(_renderer);       _renderer       = nullptr; }
    if (_swapChain)      { _engine->destroy(_swapChain);      _swapChain      = nullptr; }

    Engine::destroy(&_engine);
    _engine = nullptr;
}

- (void)resume {
    if (_engine == nullptr) return;
    _running = YES;
    _displayLink.paused = NO;
}

- (void)pause {
    _running = NO;
    _displayLink.paused = YES;
}

- (void)resize:(CGSize)drawableSize {
    if (_engine == nullptr || _view == nullptr || _camera == nullptr) return;
    const uint32_t w = (uint32_t)std::max(1.0, (double)drawableSize.width);
    const uint32_t h = (uint32_t)std::max(1.0, (double)drawableSize.height);
    _view->setViewport({0, 0, w, h});
    _camera->setProjection(45.0, (double)w / (double)h, 0.1, 100.0,
                           Camera::Fov::VERTICAL);
}

- (void)setCameraYaw:(float)yaw pitch:(float)pitch distance:(float)distance {
    _yaw = yaw;
    _pitch = pitch;
    _distance = distance;
}

- (void)setSunDirectionX:(float)x y:(float)y z:(float)z {
    _sunX = x;
    _sunY = y;
    _sunZ = z;
}

- (void)setMoonRotation:(float)rotation {
    _moonRotation = rotation;
}

- (void)loadMaterial:(NSData *)material {
    if (_engine == nullptr || material.length == 0) return;
    if (_materialBuilt) return;

    // The Builder copies the package contents internally; safe to release the NSData after build().
    _material = Material::Builder()
        .package(material.bytes, material.length)
        .build(*_engine);
    if (!_material) return;
    _materialInstance = _material->createInstance("moonInstance");
    _materialBuilt = (_materialInstance != nullptr);
    if (!_materialBuilt) return;

    // --- Mesh + renderable. Built once, immediately after the material is ready. ---
    if (!_meshBuilt) {
        std::vector<Vertex> vertices;
        std::vector<uint16_t> indices;
        generateSphereMesh(64, 32, vertices, indices);

        const uint32_t vertexCount = (uint32_t)vertices.size();
        const uint32_t indexCount = (uint32_t)indices.size();
        const uint32_t stride = (uint32_t)sizeof(Vertex);

        // Copy vertices into a heap buffer the BufferDescriptor will own.
        auto* vbBytes = new std::vector<uint8_t>(vertexCount * stride);
        std::memcpy(vbBytes->data(), vertices.data(), vbBytes->size());

        _vertexBuffer = VertexBuffer::Builder()
            .vertexCount(vertexCount)
            .bufferCount(1)
            .attribute(VertexAttribute::POSITION, 0,
                       VertexBuffer::AttributeType::FLOAT3,
                       (uint32_t)offsetof(Vertex, position), (uint8_t)stride)
            .attribute(VertexAttribute::UV0, 0,
                       VertexBuffer::AttributeType::FLOAT2,
                       (uint32_t)offsetof(Vertex, uv), (uint8_t)stride)
            .attribute(VertexAttribute::TANGENTS, 0,
                       VertexBuffer::AttributeType::FLOAT4,
                       (uint32_t)offsetof(Vertex, tangent), (uint8_t)stride)
            .build(*_engine);
        _vertexBuffer->setBufferAt(*_engine, 0,
            VertexBuffer::BufferDescriptor(vbBytes->data(), vbBytes->size(),
                                           &releaseStdVectorBytes, vbBytes));

        auto* ibBytes = new std::vector<uint16_t>(indices);
        _indexBuffer = IndexBuffer::Builder()
            .indexCount(indexCount)
            .bufferType(IndexBuffer::IndexType::USHORT)
            .build(*_engine);
        _indexBuffer->setBuffer(*_engine,
            IndexBuffer::BufferDescriptor(ibBytes->data(),
                                          ibBytes->size() * sizeof(uint16_t),
                                          &releaseStdVectorIndices, ibBytes));
        _indexCount = indexCount;

        _moonEntity = EntityManager::get().create();
        RenderableManager::Builder(1)
            .boundingBox({{-1.0f, -1.0f, -1.0f}, {1.0f, 1.0f, 1.0f}})
            .material(0, _materialInstance)
            .geometry(0, RenderableManager::PrimitiveType::TRIANGLES,
                      _vertexBuffer, _indexBuffer, 0, indexCount)
            .culling(true)
            .receiveShadows(false)
            .castShadows(false)
            .build(*_engine, _moonEntity);
        _scene->addEntity(_moonEntity);

        _meshBuilt = YES;
    }
}

- (void)loadTextureSetAlbedo:(NSData *)albedo
                      normal:(NSData *)normal
                        isHd:(BOOL)isHd {
    if (_engine == nullptr || _materialInstance == nullptr) return;
    if (albedo.length == 0 || normal.length == 0) return;

    // Dedup: if the same byte buffers + same isHd flag arrive twice in a row, skip — the
    // textureSet hasn't actually changed.
    if (albedo.bytes == _lastAlbedoPtr && albedo.length == _lastAlbedoLen
            && normal.bytes == _lastNormalPtr && normal.length == _lastNormalLen
            && isHd == _lastWasHd) {
        return;
    }

    Texture* newAlbedo = nullptr;
    Texture* newNormal = nullptr;
    if (isHd) {
        newAlbedo = uploadKtx2Texture(*_engine, albedo, /*srgb*/ YES);
        newNormal = uploadKtx2Texture(*_engine, normal, /*srgb*/ NO);
    } else {
        newAlbedo = uploadPngTexture(*_engine, albedo, Texture::InternalFormat::SRGB8_A8);
        newNormal = uploadPngTexture(*_engine, normal, Texture::InternalFormat::RGBA8);
    }
    if (newAlbedo == nullptr || newNormal == nullptr) {
        if (newAlbedo) _engine->destroy(newAlbedo);
        if (newNormal) _engine->destroy(newNormal);
        return;
    }

    // REPEAT in U (longitude wraps), CLAMP_TO_EDGE in V (poles). Filament reads the mip
    // chain when present (KTX2 path); LINEAR_MIPMAP_LINEAR safely degrades to LINEAR for
    // single-level textures (the PNG path), so one sampler config covers both.
    TextureSampler sampler(
        TextureSampler::MinFilter::LINEAR_MIPMAP_LINEAR,
        TextureSampler::MagFilter::LINEAR,
        TextureSampler::WrapMode::REPEAT,
        TextureSampler::WrapMode::CLAMP_TO_EDGE,
        TextureSampler::WrapMode::CLAMP_TO_EDGE);
    _materialInstance->setParameter("albedo", newAlbedo, sampler);
    _materialInstance->setParameter("normalMap", newNormal, sampler);

    if (_albedoTex) _engine->destroy(_albedoTex);
    if (_normalTex) _engine->destroy(_normalTex);
    _albedoTex = newAlbedo;
    _normalTex = newNormal;

    _lastAlbedoPtr = albedo.bytes;
    _lastAlbedoLen = albedo.length;
    _lastNormalPtr = normal.bytes;
    _lastNormalLen = normal.length;
    _lastWasHd = isHd;
}

- (void)renderloop {
    if (_engine == nullptr || !_running) return;

    // Apply pulled state. Both setters and this reader run on the main thread
    // (Compose update → CADisplayLink → renderloop). Plain reads are safe.
    const float yaw = _yaw;
    const float pitch = _pitch;
    const float distance = _distance;

    const float cp = std::cos(pitch);
    const double3 eye = {
        (double)(distance * cp * std::sin(yaw)),
        (double)(distance * std::sin(pitch)),
        (double)(distance * cp * std::cos(yaw))
    };
    const double3 center = { 0.0, 0.0, 0.0 };
    const double3 up = { 0.0, 1.0, 0.0 };
    if (_camera) {
        _camera->lookAt(eye, center, up);
    }

    // Sun direction → directional light. Filament wants the direction the
    // photons travel (away from the source), so negate the lit-from vector.
    // Phase 3 review #10: dropped the defensive normalization step — the
    // shared MoonViewModel only emits unit-length sun directions
    // (joystickToHemisphereDir lifts onto the unit hemisphere).
    if (_sunEntity) {
        auto& lcm = _engine->getLightManager();
        auto inst = lcm.getInstance(_sunEntity);
        if (inst) {
            lcm.setDirection(inst, { -_sunX, -_sunY, -_sunZ });
        }
    }

    // Moon spin around its rotation axis (Y).
    if (_moonEntity) {
        auto& tcm = _engine->getTransformManager();
        auto inst = tcm.getInstance(_moonEntity);
        if (inst) {
            const float r = _moonRotation;
            const float c = std::cos(r);
            const float s = std::sin(r);
            mat4f m{
                float4{ c,    0.0f, -s,   0.0f},
                float4{ 0.0f, 1.0f,  0.0f, 0.0f},
                float4{ s,    0.0f,  c,   0.0f},
                float4{ 0.0f, 0.0f,  0.0f, 1.0f}
            };
            tcm.setTransform(inst, m);
        }
    }

    // Submit the frame.
    if (_renderer->beginFrame(_swapChain)) {
        _renderer->render(_view);
        _renderer->endFrame();
    }
}

@end
