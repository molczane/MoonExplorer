package org.jetbrains.moonexplorer.assets

import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import moonexplorer.shared.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.moonexplorer.state.MoonViewModel
import org.jetbrains.moonexplorer.state.TextureSet

/**
 * Drives the renderer's `state.textureSet` over a launch's lifetime. T117.
 *
 *   Placeholder ──(read bundled PNG)──▶ Bundled2K ──(if iOS) HD fetch──▶ Hd8K
 *
 * The bundled PNGs ship in `composeResources/files/textures/`. The HD KTX2 manifest ships
 * at `composeResources/files/manifest.json` (per ADR-0010 / ADR-0011). On Android,
 * [isHdStreamingSupported] is `false` so the HD fetch is skipped — bundled 2 K stays bound.
 *
 * Throws `CancellationException` if the launch is interrupted (e.g. backgrounded mid-fetch);
 * other errors are logged and leave the state at whatever level was last successfully bound.
 */
class MoonAssetLoader(
    private val storage: StorageDir,
    private val http: HttpClient,
    private val viewModel: MoonViewModel,
) {
    private val cache: AssetCache = AssetCache(storage, http)

    /**
     * T121 — single-flight guard. `tryLock`-based dedup means concurrent triggers (e.g. a
     * config change that recomposes MoonExplorerScreen and the same loader instance still
     * has loadInto in flight) no-op rather than queue up redundant fetches. Each new
     * MoonAssetLoader instance has its own Mutex, so a fresh launch retries from scratch.
     */
    private val loadMutex: Mutex = Mutex()

    @OptIn(ExperimentalResourceApi::class)
    suspend fun loadInto() {
        if (!loadMutex.tryLock()) {
            println("[MoonAssetLoader] loadInto() already in flight; skipping concurrent trigger")
            return
        }
        try {
            // 1. Bundled 2 K PNGs — both platforms. Res.readBytes is suspending and dispatches
            // its own I/O internally; no withContext needed here.
            val albedo2K = Res.readBytes(BUNDLED_ALBEDO_PATH)
            val normal2K = Res.readBytes(BUNDLED_NORMAL_PATH)
            viewModel.setTextureSet(TextureSet.Bundled2K(albedo2K, normal2K))

            if (!isHdStreamingSupported) return

            // 2. HD KTX2 — iOS only for now (ADR-0011). Route through Dispatchers.Default so
            // network + sha256 + disk I/O don't tie up the Compose dispatcher. (Dispatchers.IO
            // would be marginally better on JVM/Android but isn't in commonMain — Default's
            // pool handles I/O-bound work correctly across both platforms.)
            withContext(Dispatchers.Default) {
                try {
                    val manifestBytes = Res.readBytes(MANIFEST_PATH)
                    val manifest = AssetManifest.parse(manifestBytes.decodeToString())
                    cache.invalidate(manifest.version)
                    val albedoHd = cache.lookupOrFetch(manifest.albedo)
                    val normalHd = cache.lookupOrFetch(manifest.normal)
                    viewModel.setTextureSet(TextureSet.Hd8K(albedoHd, normalHd))
                    println(
                        "[MoonAssetLoader] HD bound: version=${manifest.version}, " +
                            "total=${albedoHd.size + normalHd.size} bytes",
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    // T124 — network / parse / cache failure. Bundled 2 K stays bound;
                    // loader retries on next launch (FR-006).
                    println(
                        "[MoonAssetLoader] HD fetch failed: " +
                            "${e::class.simpleName}: ${e.message} — staying at Bundled2K",
                    )
                }
            }
        } finally {
            loadMutex.unlock()
        }
    }

    private companion object {
        const val BUNDLED_ALBEDO_PATH = "files/textures/moon_albedo_2k.png"
        const val BUNDLED_NORMAL_PATH = "files/textures/moon_normal_2k.png"
        const val MANIFEST_PATH = "files/manifest.json"
    }
}
