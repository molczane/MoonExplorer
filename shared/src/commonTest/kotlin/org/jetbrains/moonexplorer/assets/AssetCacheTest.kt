package org.jetbrains.moonexplorer.assets

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * T141 — AssetCache.lookupOrFetch + invalidate behaviour. Drives Ktor's MockEngine so HTTP
 * is deterministic; FakeStorageDir holds the cache in-memory so we can inspect contents.
 */
class AssetCacheTest {

    @Test
    fun lookupOrFetch_cacheMiss_fetchesAndStores() = runBlocking {
        val storage = FakeStorageDir()
        val cache = AssetCache(storage, mockClient(MOON_BYTES))

        val out = cache.lookupOrFetch(entryFor(MOON_BYTES))

        assertContentEquals(MOON_BYTES, out)
        assertContentEquals(MOON_BYTES, storage.files[FILE_NAME], "bytes should be persisted")
        // Two writes: the asset file itself + the inventory sidecar that recordInventory() updates.
        assertEquals(2, storage.writeCount, "expected one write each for the file + inventory sidecar")
    }

    @Test
    fun lookupOrFetch_cacheHit_returnsCachedWithoutHttp() = runBlocking {
        val storage = FakeStorageDir().apply { files[FILE_NAME] = MOON_BYTES.copyOf() }
        var httpCalls = 0
        val client = HttpClient(MockEngine { _ ->
            httpCalls++
            respond(MOON_BYTES, HttpStatusCode.OK, headersOf("Content-Type", "application/octet-stream"))
        })
        val cache = AssetCache(storage, client)

        val out = cache.lookupOrFetch(entryFor(MOON_BYTES))

        assertContentEquals(MOON_BYTES, out)
        assertEquals(0, httpCalls, "expected no HTTP call on a SHA-valid cache hit")
    }

    @Test
    fun lookupOrFetch_hashMismatch_discardsCacheAndRefetches() = runBlocking {
        // Cache holds CORRUPT bytes (hash mismatch vs entry); MockEngine returns the right bytes.
        val storage = FakeStorageDir().apply { files[FILE_NAME] = "corrupt".encodeToByteArray() }
        val client = mockClient(MOON_BYTES)
        val cache = AssetCache(storage, client)

        val out = cache.lookupOrFetch(entryFor(MOON_BYTES))

        assertContentEquals(MOON_BYTES, out)
        assertContentEquals(MOON_BYTES, storage.files[FILE_NAME], "corrupt cache should have been replaced")
        assertTrue(storage.deleteCount >= 1, "corrupt file should have been deleted before refetch")
    }

    @Test
    fun lookupOrFetch_serverHashMismatch_throws() = runBlocking {
        val storage = FakeStorageDir()
        val client = mockClient("wrong-bytes-from-server".encodeToByteArray())
        val cache = AssetCache(storage, client)

        assertFailsWith<IllegalStateException> {
            cache.lookupOrFetch(entryFor(MOON_BYTES))  // entry's sha is for MOON_BYTES, not the server response
        }
        // Defensive: the bad bytes should not have been written to storage.
        assertFalse(storage.files.containsKey(FILE_NAME), "bad-hash bytes must not be cached")
    }

    @Test
    fun invalidate_versionChanged_deletesTrackedFiles() = runBlocking {
        val storage = FakeStorageDir()
        val cache = AssetCache(storage, mockClient(MOON_BYTES))
        // Seed by fetching once — populates the inventory.
        cache.lookupOrFetch(entryFor(MOON_BYTES))
        assertTrue(storage.files.containsKey(FILE_NAME))

        cache.invalidate("OLD_VERSION")  // first call writes the sentinel
        cache.invalidate("NEW_VERSION")  // sentinel mismatch → wipe inventory entries

        assertFalse(storage.files.containsKey(FILE_NAME), "tracked file should be deleted on version-bump")
    }

    @Test
    fun invalidate_sameVersion_isNoOp() = runBlocking {
        val storage = FakeStorageDir()
        val cache = AssetCache(storage, mockClient(MOON_BYTES))
        // Stamp the version sentinel first so subsequent same-version calls find a match. (The
        // first invalidate call from a fresh cache always runs the deletion path because the
        // stored sentinel is null — that's defensive, not a no-op.)
        cache.invalidate("v1")
        cache.lookupOrFetch(entryFor(MOON_BYTES))
        assertTrue(storage.files.containsKey(FILE_NAME))

        val deletesBefore = storage.deleteCount
        cache.invalidate("v1")  // sentinel matches → no-op

        assertEquals(
            deletesBefore,
            storage.deleteCount,
            "same-version invalidate should not delete tracked files",
        )
        assertTrue(storage.files.containsKey(FILE_NAME), "file should still be present")
    }

    private fun mockClient(payload: ByteArray): HttpClient = HttpClient(
        MockEngine { _ ->
            respond(
                content = ByteReadChannel(payload),
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/octet-stream"),
            )
        }
    )

    private fun entryFor(payload: ByteArray): AssetEntry = AssetEntry(
        url = "https://example.test/assets/$FILE_NAME",
        sha256 = sha256(payload),
        sizeBytes = payload.size.toLong(),
        width = 8192,
        height = 4096,
    )

    private companion object {
        const val FILE_NAME: String = "moon_albedo_8k.ktx2"
        // 256 deterministic bytes — small but non-trivial. Exact contents don't matter; what
        // matters is that sha256 of these bytes is the entry's expected hash.
        val MOON_BYTES: ByteArray = ByteArray(256) { it.toByte() }
    }
}
