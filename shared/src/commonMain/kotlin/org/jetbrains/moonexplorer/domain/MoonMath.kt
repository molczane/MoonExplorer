package org.jetbrains.moonexplorer.domain

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Selenographic ↔ 3D Cartesian conversions and orbit-camera helpers.
 *
 * Conventions per ADR-0006 + ai-docs/research/selenographic-math-camera.md:
 *   right-handed, Y-up, north pole at +Y, prime meridian (sub-Earth point,
 *   lat=0, lon=0) at +Z, east longitude (toward Mare Crisium) → +X.
 *   Internal angles are in radians; the API boundary uses degrees.
 */

const val DEG_TO_RAD: Float = (PI / 180.0).toFloat()
const val RAD_TO_DEG: Float = (180.0 / PI).toFloat()

/** ~89.4° — keeps lookAt's up-vector cross product well-defined near the poles. */
const val PITCH_LIMIT_RAD: Float = ((PI / 2) - 0.01).toFloat()

data class LatLon(val latDeg: Float, val lonDeg: Float)

/** Selenographic (lat°, lon°) → Cartesian on the unit Moon. */
fun latLonToCartesian(latDeg: Float, lonDeg: Float): Vec3 {
    val lat = latDeg * DEG_TO_RAD
    val lon = lonDeg * DEG_TO_RAD
    val cl = cos(lat)
    return Vec3(cl * sin(lon), sin(lat), cl * cos(lon))
}

/** Cartesian on (or near) the unit sphere → selenographic (lat°, lon°), east-positive. */
fun cartesianToLatLon(p: Vec3): LatLon {
    val n = p.normalize()
    val lat = asin(n.y.coerceIn(-1f, 1f))
    val lon = atan2(n.x, n.z) // east-positive, range (-PI, PI]
    return LatLon(lat * RAD_TO_DEG, lon * RAD_TO_DEG)
}

/**
 * Camera position on the orbit sphere of the given radius. yaw rotates
 * around +Y; pitch elevates above (or below) the equatorial plane.
 * yaw=0, pitch=0 → camera at (0, 0, distance), looking toward the origin
 * from the near-side direction.
 */
fun cameraPosition(yawRad: Float, pitchRad: Float, distance: Float): Vec3 {
    val cp = cos(pitchRad)
    return Vec3(
        distance * cp * sin(yawRad),
        distance * sin(pitchRad),
        distance * cp * cos(yawRad),
    )
}

/**
 * "Up" vector for lookAt that stays well-defined as pitch approaches the
 * poles. With pitch clamped to ±PITCH_LIMIT_RAD, plain +Y works for almost
 * all camera orientations; the conditional is belt-and-braces.
 */
fun cameraUpVector(pitchRad: Float): Vec3 = if (abs(pitchRad) > PITCH_LIMIT_RAD - 0.05f) {
    if (pitchRad > 0f) Vec3(0f, 0f, -1f) else Vec3(0f, 0f, 1f)
} else {
    Vec3.UP
}
