package org.jetbrains.moonexplorer.assets

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.onDownload
import io.ktor.client.request.get
import io.ktor.http.isSuccess

/**
 * StorageDir-backed cache for HD asset bytes. T113.
 *
 * Reads from disk if the cached file matches the manifest's SHA-256; otherwise fetches via
 * Ktor, verifies, and atomically writes to disk before returning. Version-bumps invalidate
 * all tracked files so a stale cache from a prior asset release doesn't linger.
 *
 * Tracking note: the StorageDir contract has no `list()`, so the cache maintains a tiny
 * inventory sidecar (`.moonexplorer.cache_inventory`) listing every filename it has
 * written. [invalidate] reads the inventory and deletes each file when the version changes.
 * The SHA-check in [lookupOrFetch] is the second line of defence for any leftover bytes
 * the inventory failed to track (process crash mid-write, file written outside this class).
 */
class AssetCache(
    private val storage: StorageDir,
    private val http: HttpClient,
) {

    suspend fun lookupOrFetch(entry: AssetEntry): ByteArray {
        val cached = storage.read(entry.fileName)
        if (cached != null && sha256(cached) == entry.sha256) {
            recordInventory(entry.fileName)
            log("cache hit: ${entry.fileName} (${cached.size} bytes)")
            return cached
        }
        // Either missing or hash mismatch (corrupt / tampered / partial write).
        if (cached != null) {
            log("sha256 mismatch on cached ${entry.fileName}; refetching")
            storage.delete(entry.fileName)
        }

        log("fetching ${entry.url}")
        var lastLoggedPct = 0
        val response = http.get(entry.url) {
            onDownload { received, contentLength ->
                if (contentLength != null && contentLength > 0L) {
                    val pct = (received * 100L / contentLength).toInt()
                    if (pct >= lastLoggedPct + 25) {
                        lastLoggedPct = pct
                        log("${entry.fileName}: ~$pct% ($received / $contentLength)")
                    }
                }
            }
        }
        check(response.status.isSuccess()) {
            "GET ${entry.url} returned ${response.status}"
        }
        val fetched: ByteArray = response.body()
        val actualHash = sha256(fetched)
        check(actualHash == entry.sha256) {
            "downloaded ${entry.url} sha256 mismatch — expected ${entry.sha256}, got $actualHash"
        }

        storage.writeAtomically(entry.fileName, fetched)
        recordInventory(entry.fileName)
        log("cached ${entry.fileName} (${fetched.size} bytes)")
        return fetched
    }

    /**
     * Stamps the current manifest [version] into a sentinel file. If the stored sentinel
     * differs, every previously-recorded inventory file is deleted before stamping —
     * clean slate for the new asset release (FR-009).
     */
    suspend fun invalidate(version: String) {
        val stored = storage.read(VERSION_FILE)?.decodeToString()
        if (stored == version) return
        val tracked = readInventory()
        for (name in tracked) storage.delete(name)
        storage.delete(INVENTORY_FILE)
        storage.writeAtomically(VERSION_FILE, version.encodeToByteArray())
    }

    private suspend fun recordInventory(name: String) {
        val current = readInventory().toMutableSet()
        if (current.add(name)) {
            storage.writeAtomically(
                INVENTORY_FILE,
                current.joinToString("\n").encodeToByteArray(),
            )
        }
    }

    private suspend fun readInventory(): Set<String> {
        val text = storage.read(INVENTORY_FILE)?.decodeToString() ?: return emptySet()
        return text.lineSequence().filter { it.isNotBlank() }.toSet()
    }

    private fun log(message: String) {
        // Plain stdout — surfaces in `adb logcat` on Android and the Xcode console on iOS.
        // Cheap enough that gating on a debug flag isn't worth it for the few lines we emit
        // per asset release.
        println("[AssetCache] $message")
    }

    private companion object {
        const val VERSION_FILE = ".moonexplorer.cache_version"
        const val INVENTORY_FILE = ".moonexplorer.cache_inventory"
    }
}
