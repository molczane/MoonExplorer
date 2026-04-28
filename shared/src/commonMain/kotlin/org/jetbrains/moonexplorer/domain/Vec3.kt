package org.jetbrains.moonexplorer.domain

import kotlin.math.sqrt

/**
 * Immutable 3-component float vector. No platform types — used wherever
 * MoonRenderState, the camera/sun math, or marker positions need a
 * direction or a point.
 *
 * Per ADR-0006 the world is right-handed Y-up: north pole at +Y, prime
 * meridian (sub-Earth point) at +Z, east longitude → +X.
 */
data class Vec3(val x: Float, val y: Float, val z: Float) {

    operator fun plus(other: Vec3): Vec3 = Vec3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vec3): Vec3 = Vec3(x - other.x, y - other.y, z - other.z)
    operator fun times(scalar: Float): Vec3 = Vec3(x * scalar, y * scalar, z * scalar)
    operator fun unaryMinus(): Vec3 = Vec3(-x, -y, -z)

    fun dot(other: Vec3): Float = x * other.x + y * other.y + z * other.z

    fun cross(other: Vec3): Vec3 = Vec3(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x,
    )

    fun lengthSquared(): Float = dot(this)
    fun length(): Float = sqrt(lengthSquared())

    fun normalize(): Vec3 {
        val len = length()
        return if (len > 0f) Vec3(x / len, y / len, z / len) else ZERO
    }

    companion object {
        val ZERO: Vec3 = Vec3(0f, 0f, 0f)
        val UP: Vec3 = Vec3(0f, 1f, 0f)
        val FORWARD: Vec3 = Vec3(0f, 0f, 1f) // sub-Earth direction per ADR-0006
        val RIGHT: Vec3 = Vec3(1f, 0f, 0f)
    }
}
