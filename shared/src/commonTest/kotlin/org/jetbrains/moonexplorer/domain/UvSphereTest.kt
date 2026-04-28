package org.jetbrains.moonexplorer.domain

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Geometry checks on the procedural UV sphere used by the Phase 3 Filament
 * renderer. Verifies vertex/index counts, that all vertices land on the unit
 * sphere, and that tangents are orthogonal to normals (required for correct
 * normal-mapped shading).
 */
class UvSphereTest {

    @Test
    fun generate_vertexCount_default() {
        val mesh = UvSphere.generate() // 64×32
        assertEquals((64 + 1) * (32 + 1), mesh.vertexCount)
    }

    @Test
    fun generate_indexCount_default() {
        // segments × rings × 2 triangles × 3 indices.
        val mesh = UvSphere.generate()
        assertEquals(64 * 32 * 2 * 3, mesh.indexCount)
    }

    @Test
    fun generate_allPositionsOnUnitSphere() {
        val mesh = UvSphere.generate(segments = 32, rings = 16)
        forEachVec3(mesh.positions, mesh.vertexCount) { i, v ->
            val len = v.length()
            assertTrue(abs(len - 1f) < TOL, "vertex $i: |position| = $len, expected ≈ 1")
        }
    }

    @Test
    fun generate_normalsMatchPositionsOnUnitSphere() {
        val mesh = UvSphere.generate(segments = 32, rings = 16)
        val positions = readVec3Array(mesh.positions, mesh.vertexCount)
        val normals = readVec3Array(mesh.normals, mesh.vertexCount)
        for (i in 0 until mesh.vertexCount) {
            val p = positions[i]; val n = normals[i]
            assertTrue(
                abs(p.x - n.x) < TOL && abs(p.y - n.y) < TOL && abs(p.z - n.z) < TOL,
                "vertex $i: normal $n != position $p (expected equal on unit sphere)",
            )
        }
    }

    @Test
    fun generate_tangentsArePackedUnitQuaternions() {
        // Tangents now ship as packed TBN quaternions (FLOAT4 xyzw) so Android
        // and iOS feed Filament the same encoding (matches MoonRenderer.mm's
        // `packTangentFrame`). Invariant we check: each quaternion is unit-length.
        val mesh = UvSphere.generate(segments = 32, rings = 16)
        val expectedSize = mesh.vertexCount * 4 * Float.SIZE_BYTES
        assertEquals(expectedSize, mesh.tangents.size, "tangents buffer should be FLOAT4 per vertex")
        for (i in 0 until mesh.vertexCount) {
            val p = i * 4 * Float.SIZE_BYTES
            val qx = readFloat(mesh.tangents, p)
            val qy = readFloat(mesh.tangents, p + 4)
            val qz = readFloat(mesh.tangents, p + 8)
            val qw = readFloat(mesh.tangents, p + 12)
            val mag = kotlin.math.sqrt(qx * qx + qy * qy + qz * qz + qw * qw)
            assertTrue(abs(mag - 1f) < TOL, "vertex $i: |tangent quat| = $mag, expected ≈ 1")
        }
    }

    @Test
    fun generate_minimalMeshIsValid() {
        val mesh = UvSphere.generate(segments = 3, rings = 2)
        assertEquals((3 + 1) * (2 + 1), mesh.vertexCount)
        assertEquals(3 * 2 * 2 * 3, mesh.indexCount)
    }

    @Test
    fun generate_segmentsTooSmallThrows() {
        assertFailsWith<IllegalArgumentException> { UvSphere.generate(segments = 2, rings = 2) }
    }

    @Test
    fun generate_ringsTooSmallThrows() {
        assertFailsWith<IllegalArgumentException> { UvSphere.generate(segments = 3, rings = 1) }
    }

    private inline fun forEachVec3(buf: ByteArray, count: Int, action: (Int, Vec3) -> Unit) {
        for (i in 0 until count) {
            val p = i * VEC3_BYTES
            val x = readFloat(buf, p)
            val y = readFloat(buf, p + 4)
            val z = readFloat(buf, p + 8)
            action(i, Vec3(x, y, z))
        }
    }

    private fun readVec3Array(buf: ByteArray, count: Int): Array<Vec3> = Array(count) { i ->
        val p = i * VEC3_BYTES
        Vec3(readFloat(buf, p), readFloat(buf, p + 4), readFloat(buf, p + 8))
    }

    /** Read a little-endian Float at offset `p` in `buf`. */
    private fun readFloat(buf: ByteArray, p: Int): Float {
        val bits = (buf[p].toInt() and 0xFF) or
            ((buf[p + 1].toInt() and 0xFF) shl 8) or
            ((buf[p + 2].toInt() and 0xFF) shl 16) or
            ((buf[p + 3].toInt() and 0xFF) shl 24)
        return Float.fromBits(bits)
    }

    companion object {
        private const val VEC3_BYTES = 12  // 3 floats × 4 bytes
        private const val TOL: Float = 1e-5f
    }
}
