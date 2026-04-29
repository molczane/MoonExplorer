package org.jetbrains.moonexplorer.assets

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSMutableData
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.appendBytes
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.writeToURL
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
class IosStorageDir : StorageDir {
    private val docDirURL: NSURL = run {
        val urls = NSFileManager.defaultManager.URLsForDirectory(
            directory = NSDocumentDirectory,
            inDomains = NSUserDomainMask,
        )
        urls.firstOrNull() as? NSURL
            ?: error("IosStorageDir: no document directory available")
    }

    override suspend fun read(name: String): ByteArray? = withContext(Dispatchers.Default) {
        val url = urlFor(name)
        val data = NSData.dataWithContentsOfURL(url) ?: return@withContext null
        nsDataToByteArray(data)
    }

    override suspend fun writeAtomically(name: String, bytes: ByteArray) {
        withContext(Dispatchers.Default) {
            val url = urlFor(name)
            // writeToURL:atomically: writes to a temp + atomically renames into place.
            val ok = byteArrayToNSData(bytes).writeToURL(url, atomically = true)
            if (!ok) error("IosStorageDir: write failed for $name")
        }
    }

    override fun exists(name: String): Boolean {
        val path = urlFor(name).path ?: return false
        return NSFileManager.defaultManager.fileExistsAtPath(path)
    }

    override fun delete(name: String) {
        // Best-effort: nil error pointer skips reporting; missing file is success-equivalent.
        NSFileManager.defaultManager.removeItemAtURL(urlFor(name), null)
    }

    private fun urlFor(name: String): NSURL =
        docDirURL.URLByAppendingPathComponent(name)
            ?: error("IosStorageDir: bad path component '$name'")

    private fun nsDataToByteArray(data: NSData): ByteArray {
        val len = data.length.toInt()
        if (len == 0) return ByteArray(0)
        val out = ByteArray(len)
        out.usePinned { pinned -> memcpy(pinned.addressOf(0), data.bytes, len.convert()) }
        return out
    }

    private fun byteArrayToNSData(bytes: ByteArray): NSData {
        if (bytes.isEmpty()) return NSData()
        // K/N's Foundation overlay doesn't expose NSData.dataWithBytes; build via
        // NSMutableData + appendBytes (one memcpy into the freshly-allocated NSData buffer,
        // then writeToURL: streams it to disk).
        val md = NSMutableData()
        bytes.usePinned { pinned ->
            md.appendBytes(pinned.addressOf(0), bytes.size.convert())
        }
        return md
    }
}
