package org.jetbrains.moonexplorer.render

import android.graphics.BitmapFactory
import android.view.Choreographer
import android.view.Surface
import android.view.SurfaceView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.google.android.filament.Box
import com.google.android.filament.Camera
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.Filament
import com.google.android.filament.IndexBuffer
import com.google.android.filament.LightManager
import com.google.android.filament.Material
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.SwapChain
import com.google.android.filament.Texture
import com.google.android.filament.TextureSampler
import com.google.android.filament.TransformManager
import com.google.android.filament.VertexBuffer
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.UiHelper
import kotlinx.coroutines.runBlocking
import moonexplorer.shared.generated.resources.Res
import org.jetbrains.moonexplorer.domain.UvSphere
import org.jetbrains.moonexplorer.domain.cameraPosition
import org.jetbrains.moonexplorer.domain.cameraUpVector
import org.jetbrains.moonexplorer.state.MoonRenderState
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Filament-backed renderer host for one [SurfaceView]. Owns the full Engine
 * object graph (Engine / SwapChain / Renderer / View / Scene / Camera /
 * Material / Texture / Mesh) and a Choreographer-driven frame loop.
 *
 * Per ADR-0003 the host is a pull-not-push reader of [MoonRenderState]:
 * Compose's `update` lambda calls [updateState] on every recomposition, the
 * volatile field is read by the next Choreographer tick, and Filament objects
 * are mutated only on the main thread inside [FrameCallback.doFrame].
 *
 * Lifecycle: posts the frame callback on `ON_RESUME`, removes it on
 * `ON_PAUSE`. [destroy] tears the Engine graph down in reverse construction
 * order — see ai-docs/research/filament-cmp-integration.md §1.
 */
internal class MoonHost(private val surfaceView: SurfaceView) : DefaultLifecycleObserver {

    // --- Filament root + frame helpers ---
    private val engine: Engine = Engine.create()
    private val renderer: Renderer = engine.createRenderer()
    private val scene: Scene = engine.createScene()
    private val view: View = engine.createView()
    private val cameraEntity: Int = EntityManager.get().create()
    private val camera: Camera = engine.createCamera(cameraEntity)
    private val uiHelper: UiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
    private val choreographer: Choreographer = Choreographer.getInstance()

    // --- Material + GPU-side asset handles ---
    private val material: Material
    private val materialInstance: MaterialInstance
    private val albedoTexture: Texture       // primary
    private val albedoTextureAlt: Texture    // alt — Phase 6 (T060) swap target
    private val normalTexture: Texture
    private val albedoSampler: TextureSampler
    private val vertexBuffer: VertexBuffer
    private val indexBuffer: IndexBuffer
    private val renderableEntity: Int
    private val lightEntity: Int
    private val moonEntity: Int
    private var swapChain: SwapChain? = null

    // Cached LightManager + TransformManager handles. Filled in after the
    // entities are built so the per-frame `apply*` calls skip re-fetching
    // `getInstance` each tick (Phase 3 review #7).
    private var lightInstance: Int = 0
    private var moonTransformInstance: Int = 0

    // Last-applied albedo variant. The frame callback rebinds the material's
    // `albedo` sampler whenever `state.albedoVariant` differs (Phase 6 T060).
    private var lastAppliedAlbedoVariant: Int = 0

    // --- State delivered from Compose (read each Choreographer tick) ---
    @Volatile
    private var currentState: MoonRenderState = MoonRenderState()

    private var frameCallbackPosted: Boolean = false

    private val frameCallback: Choreographer.FrameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            // Re-post first so a render error doesn't stop the loop.
            choreographer.postFrameCallback(this)

            val sc = swapChain ?: return
            if (!uiHelper.isReadyToRender) return

            val state = currentState
            applyCamera(state)
            applySunDirection(state)
            applyMoonRotation(state)
            applyAlbedoVariant(state)

            if (renderer.beginFrame(sc, frameTimeNanos)) {
                renderer.render(view)
                renderer.endFrame()
            }
        }
    }

    init {
        view.scene = scene
        view.camera = camera

        // --- 1. Material + textures ---------------------------------------------------
        // Phase 0 spike loads tiny bundled assets (~750 KB material + 8 KB PNGs)
        // synchronously on the UI thread. Acceptable jank budget for a spike;
        // 02-moon-renderer-mvp will hoist these into a `LaunchedEffect` async load
        // (Phase 3 review #9, also noted in tasks.md T032).
        val matBytes = runBlocking { Res.readBytes(MATERIAL_PATH) }
        material = Material.Builder()
            .payload(ByteBuffer.wrap(matBytes), matBytes.size)
            .build(engine)
        materialInstance = material.createInstance()

        val albedoBytes = runBlocking { Res.readBytes(ALBEDO_PATH) }
        val albedoAltBytes = runBlocking { Res.readBytes(ALBEDO_ALT_PATH) }
        val normalBytes = runBlocking { Res.readBytes(NORMAL_PATH) }
        albedoTexture = uploadTexture(
            engine = engine,
            pngBytes = albedoBytes,
            internalFormat = Texture.InternalFormat.SRGB8_A8,
        )
        albedoTextureAlt = uploadTexture(
            engine = engine,
            pngBytes = albedoAltBytes,
            internalFormat = Texture.InternalFormat.SRGB8_A8,
        )
        normalTexture = uploadTexture(
            engine = engine,
            pngBytes = normalBytes,
            internalFormat = Texture.InternalFormat.RGBA8,
        )
        // levels(1) below means we ship a single mip; pair with LINEAR (no mipmap chain).
        // U wraps around longitude; V clamps at the poles. Hoisted to a field so
        // T060's per-variant rebind reuses the same sampler config.
        albedoSampler = TextureSampler(
            TextureSampler.MinFilter.LINEAR,
            TextureSampler.MagFilter.LINEAR,
            TextureSampler.WrapMode.REPEAT,
        ).apply {
            wrapModeS = TextureSampler.WrapMode.REPEAT
            wrapModeT = TextureSampler.WrapMode.CLAMP_TO_EDGE
        }
        materialInstance.setParameter("albedo", albedoTexture, albedoSampler)
        materialInstance.setParameter("normalMap", normalTexture, albedoSampler)

        // --- 2. Mesh ------------------------------------------------------------------
        val mesh = UvSphere.generate(SPHERE_SEGMENTS, SPHERE_RINGS)
        vertexBuffer = buildVertexBuffer(engine, mesh)
        indexBuffer = buildIndexBuffer(engine, mesh)

        // --- 3. Renderable entity (the Moon) -----------------------------------------
        renderableEntity = EntityManager.get().create()
        moonEntity = renderableEntity
        RenderableManager.Builder(1)
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vertexBuffer, indexBuffer, 0, mesh.indexCount)
            .material(0, materialInstance)
            // Unit sphere centered at the origin; small slack in the half-extent.
            .boundingBox(Box(0f, 0f, 0f, BOUNDING_HALF_EXTENT, BOUNDING_HALF_EXTENT, BOUNDING_HALF_EXTENT))
            .culling(true)
            .receiveShadows(false)
            .castShadows(false)
            .build(engine, renderableEntity)
        scene.addEntity(renderableEntity)

        // --- 4. Sun (directional light) ----------------------------------------------
        lightEntity = EntityManager.get().create()
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(1.0f, 0.96f, 0.92f)
            .intensity(110_000.0f) // outdoor / sunlight-ish in lux
            .direction(0f, 0f, -1f) // initial; replaced per frame from currentState.sunDirection
            .castShadows(false)
            .build(engine, lightEntity)
        scene.addEntity(lightEntity)

        // Cache the per-frame instance handles (Phase 3 review #7).
        lightInstance = engine.lightManager.getInstance(lightEntity)
        moonTransformInstance = engine.transformManager.getInstance(moonEntity)

        // --- 5. View defaults --------------------------------------------------------
        view.blendMode = View.BlendMode.OPAQUE
        renderer.clearOptions = renderer.clearOptions.apply {
            clear = true
            clearColor = floatArrayOf(0f, 0f, 0f, 1f)
        }

        // Initial camera projection — refined when the surface is sized.
        val width = surfaceView.width.coerceAtLeast(1)
        val height = surfaceView.height.coerceAtLeast(1)
        updateCameraProjection(width, height)
        // Explicit exposure for cross-platform parity with iOS (Phase 3 review #2).
        // Filament's default is f/16 ISO 100 1/125s — the same values, but stating
        // them keeps the two renderers visually aligned and protects against any
        // future Filament default change.
        camera.setExposure(16.0f, 1.0f / 125.0f, 100.0f)

        // --- 6. UiHelper wiring -----------------------------------------------------
        uiHelper.renderCallback = object : UiHelper.RendererCallback {
            override fun onNativeWindowChanged(surface: Surface) {
                swapChain?.let { engine.destroySwapChain(it) }
                swapChain = engine.createSwapChain(surface)
            }

            override fun onDetachedFromSurface() {
                swapChain?.let {
                    engine.destroySwapChain(it)
                    engine.flushAndWait()
                    swapChain = null
                }
            }

            override fun onResized(width: Int, height: Int) {
                view.viewport = Viewport(0, 0, width, height)
                updateCameraProjection(width, height)
            }
        }
        uiHelper.attachTo(surfaceView)
    }

    /**
     * Attaches lifecycle observation. Compose's [LocalLifecycleOwner] is the
     * activity (or nav-host fragment) that owns the SurfaceView; resume/pause
     * gates the Choreographer to avoid rendering to a destroyed surface.
     */
    fun start(lifecycleOwner: LifecycleOwner) {
        lifecycleOwner.lifecycle.addObserver(this)
        // If the lifecycle is already RESUMED at the time of attachment, the
        // observer's onResume won't fire — handle that explicitly.
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            postFrameCallback()
        }
    }

    /** Called by the Compose `update` lambda on every recomposition. */
    fun updateState(state: MoonRenderState) {
        currentState = state
    }

    override fun onResume(owner: LifecycleOwner) {
        postFrameCallback()
    }

    override fun onPause(owner: LifecycleOwner) {
        removeFrameCallback()
    }

    private fun postFrameCallback() {
        if (!frameCallbackPosted) {
            choreographer.postFrameCallback(frameCallback)
            frameCallbackPosted = true
        }
    }

    private fun removeFrameCallback() {
        if (frameCallbackPosted) {
            choreographer.removeFrameCallback(frameCallback)
            frameCallbackPosted = false
        }
    }

    private fun applyCamera(state: MoonRenderState) {
        val eye = cameraPosition(state.cameraYawRad, state.cameraPitchRad, state.cameraDistance)
        val up = cameraUpVector(state.cameraPitchRad)
        camera.lookAt(
            eye.x.toDouble(), eye.y.toDouble(), eye.z.toDouble(),
            0.0, 0.0, 0.0,
            up.x.toDouble(), up.y.toDouble(), up.z.toDouble(),
        )
    }

    private fun applySunDirection(state: MoonRenderState) {
        if (lightInstance == 0) return
        // Filament's directional-light direction is the photon travel vector;
        // ADR-0006 sunDirection points from Moon → Sun, so negate.
        engine.lightManager.setDirection(
            lightInstance,
            -state.sunDirection.x, -state.sunDirection.y, -state.sunDirection.z,
        )
    }

    private fun applyMoonRotation(state: MoonRenderState) {
        if (moonTransformInstance == 0) return
        // Y-axis rotation only (selenographic spin).
        val cos = kotlin.math.cos(state.moonRotationRad)
        val sin = kotlin.math.sin(state.moonRotationRad)
        val matrix = floatArrayOf(
            cos, 0f, -sin, 0f,
            0f,  1f,   0f, 0f,
            sin, 0f,  cos, 0f,
            0f,  0f,   0f, 1f,
        )
        engine.transformManager.setTransform(moonTransformInstance, matrix)
    }

    /**
     * Rebinds the material's `albedo` sampler when `state.albedoVariant`
     * differs from what's currently bound (Phase 6 T060). Both textures stay
     * resident in GPU memory across the swap; we only swap the binding.
     * Filament's parameter change applies to the next `renderer.render(...)`.
     */
    private fun applyAlbedoVariant(state: MoonRenderState) {
        if (state.albedoVariant == lastAppliedAlbedoVariant) return
        val tex = if (state.albedoVariant == 0) albedoTexture else albedoTextureAlt
        materialInstance.setParameter("albedo", tex, albedoSampler)
        lastAppliedAlbedoVariant = state.albedoVariant
    }

    private fun updateCameraProjection(width: Int, height: Int) {
        val aspect = width.toDouble() / height.coerceAtLeast(1).toDouble()
        camera.setProjection(
            FOV_DEGREES,
            aspect,
            NEAR_PLANE,
            FAR_PLANE,
            Camera.Fov.VERTICAL,
        )
    }

    /**
     * Tears the Engine graph down in reverse construction order. Idempotent —
     * Compose may invoke `onRelease` more than once across configuration
     * changes if the parent view tree is unusual.
     */
    fun destroy() {
        removeFrameCallback()
        // No explicit `engine.flushAndWait()` here — `uiHelper.detach()` below
        // already calls flushAndWait internally before destroying the SwapChain
        // (per filament-cmp-integration.md §1). Phase 3 review #8.

        scene.removeEntity(renderableEntity)
        scene.removeEntity(lightEntity)

        engine.destroyEntity(renderableEntity)
        engine.destroyEntity(lightEntity)
        EntityManager.get().destroy(renderableEntity)
        EntityManager.get().destroy(lightEntity)

        engine.destroyVertexBuffer(vertexBuffer)
        engine.destroyIndexBuffer(indexBuffer)
        engine.destroyMaterialInstance(materialInstance)
        engine.destroyMaterial(material)
        engine.destroyTexture(albedoTexture)
        engine.destroyTexture(albedoTextureAlt)
        engine.destroyTexture(normalTexture)

        engine.destroyView(view)
        engine.destroyScene(scene)
        engine.destroyCameraComponent(cameraEntity)
        EntityManager.get().destroy(cameraEntity)
        engine.destroyRenderer(renderer)

        // Detach UiHelper last — it'll destroy the SwapChain via its callback.
        uiHelper.detach()

        engine.destroy()
    }

    // -----------------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------------

    private fun buildVertexBuffer(engine: Engine, mesh: UvSphere.Mesh): VertexBuffer {
        // Three separate buffers: positions (FLOAT3), packed-quat tangents (FLOAT4),
        // uvs (FLOAT2). We omit a NORMAL attribute because the Moon material reads
        // its normal from the normal-map sampler in tangent space; Position + UV +
        // packed-quat tangent covers the material's `requires` list (see moon.mat).
        // The FLOAT4 quaternion encoding matches iOS (MoonRenderer.mm's
        // packTangentFrame) and is the format Filament's PBR shader expects for
        // TBN-decoded normal maps (Phase 3 review #4 — was previously FLOAT3,
        // which only happened to render correctly with the flat normal map).
        val vb = VertexBuffer.Builder()
            .vertexCount(mesh.vertexCount)
            .bufferCount(3)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3)
            .attribute(VertexBuffer.VertexAttribute.TANGENTS, 1, VertexBuffer.AttributeType.FLOAT4)
            .attribute(VertexBuffer.VertexAttribute.UV0, 2, VertexBuffer.AttributeType.FLOAT2)
            .build(engine)

        vb.setBufferAt(engine, 0, wrapDirect(mesh.positions))
        vb.setBufferAt(engine, 1, wrapDirect(mesh.tangents))
        vb.setBufferAt(engine, 2, wrapDirect(mesh.uvs))
        return vb
    }

    private fun buildIndexBuffer(engine: Engine, mesh: UvSphere.Mesh): IndexBuffer {
        val ib = IndexBuffer.Builder()
            .indexCount(mesh.indexCount)
            .bufferType(IndexBuffer.Builder.IndexType.USHORT)
            .build(engine)
        ib.setBuffer(engine, wrapDirect(mesh.indices))
        return ib
    }

    private fun uploadTexture(
        engine: Engine,
        pngBytes: ByteArray,
        internalFormat: Texture.InternalFormat,
    ): Texture {
        val bitmap = BitmapFactory.decodeByteArray(pngBytes, 0, pngBytes.size)
            ?: error("Failed to decode PNG bytes (${pngBytes.size} bytes)")
        val width = bitmap.width
        val height = bitmap.height

        // Pull RGBA8888 pixels out of the Android Bitmap. Bitmap.Config.ARGB_8888 is the
        // default decode format for non-indexed PNGs but `copyPixelsToBuffer` writes in
        // native-platform order which Filament expects when paired with PixelDataFormat.RGBA.
        val pixelBuffer = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
        bitmap.copyPixelsToBuffer(pixelBuffer)
        pixelBuffer.flip()

        val texture = Texture.Builder()
            .width(width)
            .height(height)
            .levels(1)
            .sampler(Texture.Sampler.SAMPLER_2D)
            .format(internalFormat)
            .build(engine)
        val descriptor = Texture.PixelBufferDescriptor(
            pixelBuffer,
            Texture.Format.RGBA,
            Texture.Type.UBYTE,
        )
        texture.setImage(engine, 0, descriptor)
        bitmap.recycle()
        return texture
    }

    /**
     * Wrap a `ByteArray` in a direct `ByteBuffer`. The `order(nativeOrder())`
     * call is cosmetic for our usage — `put(ByteArray)` copies bytes verbatim
     * and Filament's JNI reads the raw memory directly (no `getFloat()`-style
     * accessors). UvSphere already encodes floats little-endian, which matches
     * every Android device's native order. Kept for documentation value (Phase
     * 3 review #11).
     */
    private fun wrapDirect(src: ByteArray): Buffer {
        val direct = ByteBuffer.allocateDirect(src.size).order(ByteOrder.nativeOrder())
        direct.put(src)
        direct.flip()
        return direct
    }

    private companion object {
        init {
            // Load libfilament-jni.so once per process. Without this, the first
            // native call (Engine.create() in instance init) throws
            // UnsatisfiedLinkError — Filament 1.71.x deliberately doesn't
            // auto-init from any class's static block; consumers call
            // Filament.init() explicitly. The companion's class-load init runs
            // before any MoonHost instance's primary constructor.
            Filament.init()
        }

        const val SPHERE_SEGMENTS = 64
        const val SPHERE_RINGS = 32
        const val BOUNDING_HALF_EXTENT = 1.05f
        const val FOV_DEGREES = 45.0
        const val NEAR_PLANE = 0.1
        const val FAR_PLANE = 100.0

        const val MATERIAL_PATH = "files/materials/moon.filamat"
        const val ALBEDO_PATH = "files/textures/moon_albedo_2k.png"
        const val ALBEDO_ALT_PATH = "files/textures/moon_albedo_2k_alt.png"
        const val NORMAL_PATH = "files/textures/moon_normal_2k.png"
    }
}
