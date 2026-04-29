package org.jetbrains.moonexplorer.state

/**
 * The texture pair currently bound to the Moon material. T114.
 *
 * Drives the renderer's per-frame "rebind samplers when this changes" path. Placeholder is
 * the cold-start default — renderer shows the geometric silhouette using its clear color
 * while [Bundled2K] is being read off compose resources. [Hd8K] supersedes [Bundled2K] once
 * the CDN download lands and the SHA-256 verifies.
 *
 * `Bundled2K` carries paths (Compose Resources URIs); `Hd8K` carries the raw bytes since
 * the loader has them in memory after the cache or HTTP fetch — no extra disk round-trip
 * before binding.
 *
 * Equality semantics: `Bundled2K` uses string equality on the paths; `Hd8K` falls back to
 * `ByteArray` reference identity. That's load-bearing — the renderer treats "same byte
 * arrays" as "same upload" and skips re-binding. Pushing new bytes always allocates a new
 * `ByteArray`, so a real change always triggers a real rebind.
 */
sealed class TextureSet {
    object Placeholder : TextureSet()

    data class Bundled2K(
        val albedoPath: String,
        val normalPath: String,
    ) : TextureSet()

    data class Hd8K(
        val albedoBytes: ByteArray,
        val normalBytes: ByteArray,
    ) : TextureSet()
}
