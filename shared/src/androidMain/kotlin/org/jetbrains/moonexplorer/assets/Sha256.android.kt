package org.jetbrains.moonexplorer.assets

import java.security.MessageDigest

actual fun sha256(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    return buildString(digest.size * 2) {
        for (b in digest) {
            val v = b.toInt() and 0xff
            append(HEX[v ushr 4])
            append(HEX[v and 0x0f])
        }
    }
}

private val HEX = "0123456789abcdef".toCharArray()
