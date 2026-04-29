package org.jetbrains.moonexplorer.assets

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Versioned descriptor of the HD (8 K) KTX2 asset tier. Bootstrap copy ships at
 * `composeResources/files/manifest.json`; future specs may layer a remote-manifest
 * fetch on top (see ADR-0010).
 *
 * Schema is intentionally minimal — the runtime only needs URL + hash + size + dims
 * to fetch, verify, and bind. `Json { ignoreUnknownKeys = true }` lets the build-side
 * `manifest.py` add diagnostic fields (e.g. `mipLevels`) without breaking parse.
 */
@Serializable
data class AssetManifest(
    val version: String,
    val albedo: AssetEntry,
    val normal: AssetEntry,
) {
    companion object {
        private val json: Json = Json { ignoreUnknownKeys = true }

        fun parse(jsonText: String): AssetManifest = json.decodeFromString(serializer(), jsonText)
    }
}

@Serializable
data class AssetEntry(
    val url: String,
    val sha256: String,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
) {
    /** Last URL segment — used as the on-disk cache key. `…/moon_albedo_8k.ktx2` → `moon_albedo_8k.ktx2`. */
    val fileName: String get() = url.substringAfterLast('/')
}
