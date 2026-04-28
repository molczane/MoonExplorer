package org.jetbrains.moonexplorer.domain

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Procedural UV sphere mesh generator for the Moon. Outputs separate
 * little-endian ByteArrays for positions / normals / tangents / uvs and
 * an UShort-encoded index array — ready to hand to Filament's VertexBuffer
 * + IndexBuffer in Phase 3 (T032).
 *
 * UV mapping per ADR-0006 §"Texture mapping":
 *   u = lon/360 + 0.5  →  i / segments at vertex (i, j)
 *   v = 0.5 - lat/180  →  1 - j / rings   at vertex (i, j)
 *
 * Default 64×32 segments × rings is more than enough for a smooth normal-mapped
 * Moon at mobile resolutions (per filament-cmp-integration.md §6).
 */
object UvSphere {

    private const val FLOAT_BYTES = 4
    private const val INDEX_BYTES = 2 // UShort

    data class Mesh(
        val positions: ByteArray,  // float3 per vertex, little-endian
        val normals: ByteArray,    // float3 per vertex
        val tangents: ByteArray,   // float3 per vertex (∂position/∂lon, normalized)
        val uvs: ByteArray,        // float2 per vertex
        val indices: ByteArray,    // UShort triangles, little-endian
        val vertexCount: Int,
        val indexCount: Int,
    ) {
        // ByteArray equality/hashCode is identity-based; for a data class with
        // these fields the auto-generated implementations would compare arrays
        // by reference. Tests don't rely on equality, so we leave defaults.
        override fun equals(other: Any?): Boolean = this === other
        override fun hashCode(): Int = vertexCount * 31 + indexCount
    }

    fun generate(segments: Int = 64, rings: Int = 32): Mesh {
        require(segments >= 3) { "segments must be ≥ 3 (got $segments)" }
        require(rings >= 2) { "rings must be ≥ 2 (got $rings)" }

        val vertexCount = (segments + 1) * (rings + 1)
        val triangleCount = segments * rings * 2
        val indexCount = triangleCount * 3

        val positions = ByteArray(vertexCount * 3 * FLOAT_BYTES)
        val normals = ByteArray(vertexCount * 3 * FLOAT_BYTES)
        val tangents = ByteArray(vertexCount * 3 * FLOAT_BYTES)
        val uvs = ByteArray(vertexCount * 2 * FLOAT_BYTES)
        val indices = ByteArray(indexCount * INDEX_BYTES)

        val degToRad = PI.toFloat() / 180f

        var pPos = 0
        var pNorm = 0
        var pTan = 0
        var pUv = 0
        for (j in 0..rings) {
            // lat: -90° (south pole) at j=0, +90° (north pole) at j=rings.
            val latRad = (-90f + 180f * j.toFloat() / rings) * degToRad
            val cl = cos(latRad)
            val sl = sin(latRad)
            for (i in 0..segments) {
                // lon: -180° at i=0, +180° at i=segments. Vertices at i=0 and i=segments
                // share a position but different uvs — required for seamless wrap.
                val lonRad = (-180f + 360f * i.toFloat() / segments) * degToRad
                val c = cos(lonRad)
                val s = sin(lonRad)

                val px = cl * s
                val py = sl
                val pz = cl * c
                pPos = writeFloat3(positions, pPos, px, py, pz)
                pNorm = writeFloat3(normals, pNorm, px, py, pz) // unit sphere → normal == position
                // Surface tangent in the direction of increasing longitude (east).
                // d/dlon of position is (cl·cos(lon), 0, -cl·sin(lon)); after
                // normalization (drop the cl factor) this is (cos(lon), 0, -sin(lon)).
                pTan = writeFloat3(tangents, pTan, c, 0f, -s)

                val u = i.toFloat() / segments
                val v = 1f - j.toFloat() / rings
                pUv = writeFloat2(uvs, pUv, u, v)
            }
        }

        var pIdx = 0
        for (j in 0 until rings) {
            for (i in 0 until segments) {
                val a = j * (segments + 1) + i               // (i,   j)
                val b = a + 1                                 // (i+1, j)
                val c = a + (segments + 1)                    // (i,   j+1)
                val d = c + 1                                 // (i+1, j+1)
                // CCW for outward-facing (Filament default front-face): a, b, c then b, d, c.
                pIdx = writeUShort(indices, pIdx, a)
                pIdx = writeUShort(indices, pIdx, b)
                pIdx = writeUShort(indices, pIdx, c)
                pIdx = writeUShort(indices, pIdx, b)
                pIdx = writeUShort(indices, pIdx, d)
                pIdx = writeUShort(indices, pIdx, c)
            }
        }

        return Mesh(positions, normals, tangents, uvs, indices, vertexCount, indexCount)
    }

    // --- Little-endian writers ---

    private fun writeFloat(buf: ByteArray, p: Int, v: Float): Int {
        val bits = v.toRawBits()
        buf[p]     = (bits and 0xFF).toByte()
        buf[p + 1] = ((bits ushr 8) and 0xFF).toByte()
        buf[p + 2] = ((bits ushr 16) and 0xFF).toByte()
        buf[p + 3] = ((bits ushr 24) and 0xFF).toByte()
        return p + FLOAT_BYTES
    }

    private fun writeFloat2(buf: ByteArray, p: Int, x: Float, y: Float): Int {
        val q = writeFloat(buf, p, x)
        return writeFloat(buf, q, y)
    }

    private fun writeFloat3(buf: ByteArray, p: Int, x: Float, y: Float, z: Float): Int {
        val q = writeFloat(buf, p, x)
        val r = writeFloat(buf, q, y)
        return writeFloat(buf, r, z)
    }

    private fun writeUShort(buf: ByteArray, p: Int, v: Int): Int {
        buf[p]     = (v and 0xFF).toByte()
        buf[p + 1] = ((v ushr 8) and 0xFF).toByte()
        return p + INDEX_BYTES
    }
}
