package org.jetbrains.moonexplorer.assets

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH

@OptIn(ExperimentalForeignApi::class)
actual fun sha256(bytes: ByteArray): String {
    val digest = UByteArray(CC_SHA256_DIGEST_LENGTH)
    digest.usePinned { digestPin ->
        if (bytes.isEmpty()) {
            // Empty ByteArray — addressOf(0) would throw, so pass null explicitly. CC_SHA256
            // accepts (null, 0, md) and writes the empty-string digest.
            CC_SHA256(data = null, len = 0u, md = digestPin.addressOf(0))
        } else {
            bytes.usePinned { dataPin ->
                CC_SHA256(
                    data = dataPin.addressOf(0),
                    len = bytes.size.convert(),
                    md = digestPin.addressOf(0),
                )
            }
        }
    }
    return buildString(digest.size * 2) {
        for (b in digest) {
            val v = b.toInt() and 0xff
            append(HEX[v ushr 4])
            append(HEX[v and 0x0f])
        }
    }
}

private val HEX = "0123456789abcdef".toCharArray()
