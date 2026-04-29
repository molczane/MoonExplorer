package org.jetbrains.moonexplorer.assets

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * T142 — FIPS 180-4 sample vector for SHA-256 of "abc". Runs on both androidHostTest
 * (MessageDigest impl) and iosSimulatorArm64Test (CC_SHA256 impl) via the existing
 * expect/actual; ensures the two platforms produce byte-identical hex output and matches
 * the reference value the manifest's published hashes were computed against.
 */
class Sha256Test {

    @Test
    fun sha256_abc_matchesFips180Vector() {
        val expected = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        assertEquals(expected, sha256("abc".encodeToByteArray()))
    }

    @Test
    fun sha256_emptyInput_matchesKnownVector() {
        // SHA-256 of the empty string — second well-known FIPS vector.
        val expected = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        assertEquals(expected, sha256(ByteArray(0)))
    }

    @Test
    fun sha256_isLowercaseHex() {
        val digest = sha256("anything".encodeToByteArray())
        assertEquals(64, digest.length, "SHA-256 hex length should be 64 chars")
        assertEquals(digest.lowercase(), digest, "SHA-256 output should be lowercase hex")
    }
}
