package org.jetbrains.moonexplorer.domain

import kotlin.math.tan

/**
 * Result of [projectSiteToScreen]: pixel coords on the viewport (origin top-left, Y down)
 * plus a limb-fade alpha in `[0, 1]` for smooth edge culling. T301 / 03-sites-and-flyto.
 *
 * `(0, 0)` is the top-left pixel of the viewport. The renderer's projection (Filament's
 * `setProjection(FOV, aspect, NEAR, FAR)`) and this overlay's projection share `fovYRad` +
 * the same physical viewport pixel size, so a `ScreenPos` always falls on top of the
 * Filament-rendered point.
 */
data class ScreenPos(
    val xPx: Float,
    val yPx: Float,
    /** 0.0 (limb / culled-but-just-visible) → 1.0 (squarely facing the camera). */
    val limbAlpha: Float,
)

/**
 * Project a selenographic point on the unit Moon to screen pixels using the same perspective
 * camera the Filament renderer uses. T301 / 03-sites-and-flyto.
 *
 * Returns `null` if the site is on the far side (camera-facing dot product ≤ 0), behind the
 * camera (defensive — shouldn't happen for unit Moon + cameraDistance ≥ 1.5), or the
 * viewport hasn't been sized yet. Returns a `ScreenPos` otherwise.
 *
 * Math (per `selenographic-math-camera.md` §5 + §7):
 *   1. site = latLonToCartesian(lat, lon)               — unit vector
 *   2. cam  = cameraPosition(yaw, pitch, distance)
 *   3. camDir = cam.normalize()
 *   4. dot = site · camDir; cull if dot ≤ 0
 *   5. limbAlpha = smoothstep(0, 0.3, dot)
 *   6. forward = −camDir, up = cameraUpVector(pitch), right = (forward × up).normalize();
 *      re-orthogonalise up = right × forward
 *   7. (xCam, yCam, depth) = decompose(site − cam) into the (right, up, forward) basis
 *   8. Perspective divide:
 *        xNdc = xCam / (depth · tan(fov/2) · aspect)
 *        yNdc = yCam / (depth · tan(fov/2))
 *   9. NDC → screen px (Y flipped):
 *        xPx = (xNdc + 1) · 0.5 · viewportW
 *        yPx = (1 − yNdc) · 0.5 · viewportH
 */
fun projectSiteToScreen(
    latDeg: Double,
    lonDeg: Double,
    cameraYawRad: Float,
    cameraPitchRad: Float,
    cameraDistance: Float,
    viewportWidthPx: Float,
    viewportHeightPx: Float,
    fovYRad: Float = DEFAULT_FOV_Y_RAD,
): ScreenPos? {
    if (viewportWidthPx <= 0f || viewportHeightPx <= 0f) return null
    if (cameraDistance <= 0f || fovYRad <= 0f) return null

    val site = latLonToCartesian(latDeg.toFloat(), lonDeg.toFloat())
    val cam = cameraPosition(cameraYawRad, cameraPitchRad, cameraDistance)
    val camDir = cam.normalize()

    val dot = site.dot(camDir)
    if (dot <= 0f) return null
    val limbAlpha = smoothstep(0f, 0.3f, dot)

    val forward = -camDir
    val rightRaw = forward.cross(cameraUpVector(cameraPitchRad))
    if (rightRaw.lengthSquared() < 1e-12f) return null  // degenerate basis (shouldn't happen with clamped pitch)
    val right = rightRaw.normalize()
    val up = right.cross(forward).normalize()

    val toSite = site - cam
    val xCam = toSite.dot(right)
    val yCam = toSite.dot(up)
    val depthCam = toSite.dot(forward)
    if (depthCam <= 1e-6f) return null  // behind camera or coplanar

    val tanHalfFov = tan(fovYRad * 0.5f.toDouble()).toFloat()
    val aspect = viewportWidthPx / viewportHeightPx
    val xNdc = xCam / (depthCam * tanHalfFov * aspect)
    val yNdc = yCam / (depthCam * tanHalfFov)

    val xPx = (xNdc + 1f) * 0.5f * viewportWidthPx
    val yPx = (1f - yNdc) * 0.5f * viewportHeightPx

    return ScreenPos(xPx, yPx, limbAlpha)
}

/** GLSL-style smoothstep: 0 below `edge0`, 1 above `edge1`, smooth Hermite curve in between. */
private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
