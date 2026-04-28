package org.jetbrains.moonexplorer.domain

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

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
 * **Tangents are packed as a FLOAT4 quaternion** (xyzw), the encoding Filament
 * expects in its TANGENTS vertex attribute. This matches the iOS C++ side
 * (`MoonRenderer.mm`'s `packTangentFrame`) and is the format the Filament PBR
 * shader needs to decode the TBN matrix correctly when a real normal map ships
 * in `02-moon-renderer-mvp`. (Fixed in the Phase 3 review — was previously
 * FLOAT3, which only happened to render correctly with a flat normal map.)
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
        val tangents: ByteArray,   // float4 per vertex (packed TBN quaternion, xyzw)
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
        val tangents = ByteArray(vertexCount * 4 * FLOAT_BYTES) // packed TBN quaternion
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

                // Surface tangent T in the direction of increasing longitude (east).
                // d/dlon of position is (cl·cos(lon), 0, -cl·sin(lon)); after
                // normalization (drop the cl factor) this is (cos(lon), 0, -sin(lon)).
                val tx = c
                val ty = 0f
                val tz = -s
                // Bitangent B = N × T (north-pointing on the sphere).
                val bx = py * tz - pz * ty
                val by = pz * tx - px * tz
                val bz = px * ty - py * tx
                // Pack the right-handed orthonormal (T, B, N) into Filament's
                // expected TBN quaternion encoding (FLOAT4 xyzw).
                pTan = writeTangentQuat(tangents, pTan, tx, ty, tz, bx, by, bz, px, py, pz)

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

    private fun writeFloat4(buf: ByteArray, p: Int, x: Float, y: Float, z: Float, w: Float): Int {
        val q = writeFloat(buf, p, x)
        val r = writeFloat(buf, q, y)
        val s = writeFloat(buf, r, z)
        return writeFloat(buf, s, w)
    }

    private fun writeUShort(buf: ByteArray, p: Int, v: Int): Int {
        buf[p]     = (v and 0xFF).toByte()
        buf[p + 1] = ((v ushr 8) and 0xFF).toByte()
        return p + INDEX_BYTES
    }

    /**
     * Pack an orthonormal right-handed tangent frame (T, B, N) into a unit
     * quaternion (xyzw) and write it to [buf] at offset [p]. Returns the new
     * offset. T × B must equal N — caller's responsibility.
     *
     * Standard Shoemake mat→quat on the column matrix M = [T B N]. Filament's
     * own `mat3f::packTangentFrame` uses the equivalent algorithm (see
     * `MoonRenderer.mm`); same encoding ⇒ Android and iOS feed the PBR shader
     * the same TBN frames per vertex.
     */
    private fun writeTangentQuat(
        buf: ByteArray, p: Int,
        tx: Float, ty: Float, tz: Float,
        bx: Float, by: Float, bz: Float,
        nx: Float, ny: Float, nz: Float,
    ): Int {
        // Matrix elements (row, col): M_00=Tx, M_01=Bx, M_02=Nx, M_10=Ty, ..., M_22=Nz.
        val trace = tx + by + nz
        val qx: Float; val qy: Float; val qz: Float; val qw: Float
        when {
            trace > 0f -> {
                val s = sqrt(trace + 1f) * 2f                // s = 4·qw
                qw = 0.25f * s
                qx = (bz - ny) / s                            // (M_21 - M_12) / s
                qy = (nx - tz) / s                            // (M_02 - M_20) / s
                qz = (ty - bx) / s                            // (M_10 - M_01) / s
            }
            tx > by && tx > nz -> {
                val s = sqrt(1f + tx - by - nz) * 2f          // s = 4·qx
                qw = (bz - ny) / s
                qx = 0.25f * s
                qy = (bx + ty) / s                            // (M_01 + M_10) / s
                qz = (nx + tz) / s                            // (M_02 + M_20) / s
            }
            by > nz -> {
                val s = sqrt(1f + by - tx - nz) * 2f          // s = 4·qy
                qw = (nx - tz) / s
                qx = (bx + ty) / s
                qy = 0.25f * s
                qz = (ny + bz) / s                            // (M_12 + M_21) / s
            }
            else -> {
                val s = sqrt(1f + nz - tx - by) * 2f          // s = 4·qz
                qw = (ty - bx) / s
                qx = (nx + tz) / s
                qy = (ny + bz) / s
                qz = 0.25f * s
            }
        }
        return writeFloat4(buf, p, qx, qy, qz, qw)
    }
}
