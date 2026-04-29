package org.jetbrains.moonexplorer.domain

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

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

/**
 * Default vertical field of view shared between the platform renderer hosts
 * and `MoonViewModel.onDrag`'s pixel-to-radian sensitivity math
 * (`ai-docs/research/selenographic-math-camera.md` §4). Stating the value
 * once here keeps the gesture-feel calibrated against the actual projection;
 * divergent values would produce drag rates that don't track the Moon's
 * apparent size.
 *
 * **Cross-platform parity invariant**: if this changes, update the renderer
 * projections too — `MoonHost.FOV_DEGREES` (Android) and the `setProjection`
 * call in `iosApp/iosApp/MoonRenderer.mm` (iOS).
 */
const val DEFAULT_FOV_Y_RAD: Float = (PI / 4).toFloat() // 45°

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

/**
 * Selenographic (lat°, lon°) → (yawRad, pitchRad) that puts the site at the camera's view
 * centre. T211a / 01-app-shell — used by `MoonExplorerActionsImpl.flyToMoonLocation`.
 *
 * Derivation: the camera looks at the origin from `cameraPosition(yaw, pitch, dist)` =
 * `dist * (cos(p) sin(y), sin(p), cos(p) cos(y))`, which is `dist * latLonToCartesian(p, y)`
 * with `p = pitch_rad`, `y = yaw_rad`. So a site at (lat°, lon°) sits at the centre of the
 * view iff `pitch = lat` and `yaw = lon` (both in radians).
 */
fun latLonToYawPitch(latDeg: Double, lonDeg: Double): Pair<Float, Float> =
    (lonDeg.toFloat() * DEG_TO_RAD) to (latDeg.toFloat() * DEG_TO_RAD)

/** Inverse of [latLonToYawPitch]. Returns `(latDeg, lonDeg)` as Doubles for the action API. */
fun yawPitchToLatLon(yawRad: Float, pitchRad: Float): Pair<Double, Double> =
    (pitchRad.toDouble() * RAD_TO_DEG.toDouble()) to (yawRad.toDouble() * RAD_TO_DEG.toDouble())

/**
 * Great-circle distance between two selenographic points on the Moon's surface, in
 * kilometres. Haversine formula on a sphere of radius 1737.4 km (IAU mean lunar radius).
 */
fun greatCircleDistKm(lat1Deg: Double, lon1Deg: Double, lat2Deg: Double, lon2Deg: Double): Double {
    val phi1 = lat1Deg * PI / 180.0
    val phi2 = lat2Deg * PI / 180.0
    val dPhi = (lat2Deg - lat1Deg) * PI / 180.0
    val dLambda = (lon2Deg - lon1Deg) * PI / 180.0
    val sinHalfPhi = sin(dPhi * 0.5)
    val sinHalfLambda = sin(dLambda * 0.5)
    val a = sinHalfPhi * sinHalfPhi + cos(phi1) * cos(phi2) * sinHalfLambda * sinHalfLambda
    val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
    return MOON_RADIUS_KM * c
}

/** IAU mean lunar radius. */
const val MOON_RADIUS_KM: Double = 1737.4

/**
 * Wraps `toRad − fromRad` into `(−π, π]`. T320 / 03-sites-and-flyto. Used by
 * `MoonExplorerActionsImpl.flyToMoonLocation` so that a 350° angular delta becomes −10° —
 * the camera takes the shorter path instead of spinning the long way around.
 */
fun shortestYawDelta(fromRad: Float, toRad: Float): Float {
    val twoPi = (2.0 * PI).toFloat()
    val pif = PI.toFloat()
    var d = (toRad - fromRad) % twoPi
    if (d > pif) d -= twoPi
    if (d < -pif) d += twoPi
    return d
}

/**
 * Cubic ease-in-out: `t ∈ [0, 1] → eased ∈ [0, 1]` with `eased(0) = 0`, `eased(0.5) = 0.5`,
 * `eased(1) = 1`, symmetric around 0.5. T320. Matches the perceptual feel of Material's
 * `FastOutSlowInEasing` — acceleration in the first half, deceleration in the second.
 *
 * Out-of-range `t` is clamped to `[0, 1]` defensively (the time-source loop in
 * `flyToMoonLocation` already coerces, but this keeps the function safe to call standalone).
 */
fun easeInOutCubic(t: Float): Float {
    val tc = t.coerceIn(0f, 1f)
    return if (tc < 0.5f) {
        4f * tc * tc * tc
    } else {
        val u = -2f * tc + 2f
        1f - (u * u * u) * 0.5f
    }
}

/**
 * 2D joystick → unit hemisphere per `ai-docs/research/selenographic-math-camera.md` §6
 * mode (a). For `(x, y)` inside the unit disk, returns `Vec3(x, y, sqrt(1 − x² − y²))` —
 * the camera-facing hemisphere bulges out of the screen. For `(x, y)` outside the disk,
 * clamps to the disk boundary with `z = 0` — the sun grazes the limb (terminator-on-meridian).
 *
 * Always unit-length. Replaces the 1-axis `joystickToHemisphereDir(x)` helper that lived
 * in `ui/SunControl.kt` (deleted in T441). T411 / 04-sun-control.
 */
fun joystickToSunDir(x: Float, y: Float): Vec3 {
    val r2 = x * x + y * y
    return if (r2 <= 1f) {
        Vec3(x, y, sqrt(1f - r2))
    } else {
        // Outside the disk: project onto the boundary, z = 0.
        val s = 1f / sqrt(r2)
        Vec3(x * s, y * s, 0f)
    }
}

/**
 * Interpolates two unit-length sun directions on the lat/lon surface. lat lerps linearly
 * via `asin(y)`; lon takes the shorter arc via [shortestYawDelta]. The result is reconstructed
 * via the `latLonToCartesian` formula (`cos(lat) sin(lon), sin(lat), cos(lat) cos(lon)`),
 * which is unit-length by construction.
 *
 * Used by the animated `MoonExplorerActionsImpl.setLightingPreset` (T431). lat/lon lerp
 * sidesteps slerp's antipodal degenerate (Day↔Night with `dot = -1`): the equatorial
 * preset table makes lat/lon lerp == slerp on the equator, and Day→Night routes through
 * `(0°, +90°)` (the Terminator preset) at `t = 0.5` because `shortestYawDelta(0, π) = +π`.
 *
 * T411 / 04-sun-control. `t` is clamped to `[0, 1]` defensively.
 */
fun lerpSunDirection(from: Vec3, to: Vec3, t: Float): Vec3 {
    val tt = t.coerceIn(0f, 1f)
    val fromLat = asin(from.y.coerceIn(-1f, 1f))
    val toLat = asin(to.y.coerceIn(-1f, 1f))
    val fromLon = atan2(from.x, from.z)
    val toLon = atan2(to.x, to.z)
    val lonDelta = shortestYawDelta(fromLon, toLon)
    val newLat = fromLat + (toLat - fromLat) * tt
    val newLon = fromLon + lonDelta * tt
    val cl = cos(newLat)
    return Vec3(cl * sin(newLon), sin(newLat), cl * cos(newLon))
}
