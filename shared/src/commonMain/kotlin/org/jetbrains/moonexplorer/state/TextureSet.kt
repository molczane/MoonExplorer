package org.jetbrains.moonexplorer.state

/**
 * The texture pair currently bound to the Moon material. T114.
 *
 * Drives the renderer's per-frame "rebind samplers when this changes" path. Placeholder is
 * the cold-start default — renderer shows the geometric silhouette using its clear color
 * while [Bundled2K] is being read off compose resources. [Hd8K] supersedes [Bundled2K] once
 * the CDN download lands and the SHA-256 verifies (iOS only — see ADR-0011).
 *
 * Both variants carry raw bytes since the loader has them in memory after the resource read
 * or HTTP fetch — no extra disk round-trip before binding. `Bundled2K` bytes are PNG (per
 * ADR-0011); `Hd8K` bytes are KTX2 + Basis Universal.
 *
 * Equality semantics: data classes generate equality from each component. `ByteArray.equals`
 * uses reference identity, which is load-bearing here — the renderer treats "same byte
 * arrays" as "same upload" and skips re-binding. The loader always allocates fresh arrays
 * when pushing a new state, so a real change always triggers a real rebind.
 */
sealed class TextureSet {
    object Placeholder : TextureSet()

    data class Bundled2K(
        val albedoBytes: ByteArray,
        val normalBytes: ByteArray,
    ) : TextureSet()

    data class Hd8K(
        val albedoBytes: ByteArray,
        val normalBytes: ByteArray,
    ) : TextureSet()
}
