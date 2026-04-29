package org.jetbrains.moonexplorer.domain

import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * T302 — exercises [projectSiteToScreen] against a fixed camera + viewport. Tests anchor on
 * the worked-through cases in `plan.md` § "Projection.kt": centre / far-side / limb /
 * off-axis / polar / aspect / limb-fade-monotonic / viewport-zero.
 */
class ProjectionTest {

    @Test
    fun centreSite_projectsToViewportCentre() {
        // Site at (lat=0, lon=0) sits on +Z axis at unit radius. Camera at (yaw=0, pitch=0,
        // distance=2) is at (0, 0, 2) looking back. The site should project to the viewport's
        // exact centre.
        val pos = projectSiteToScreen(
            latDeg = 0.0, lonDeg = 0.0,
            cameraYawRad = 0f, cameraPitchRad = 0f, cameraDistance = 2f,
            viewportWidthPx = 1000f, viewportHeightPx = 800f,
            fovYRad = (PI / 4).toFloat(),
        )
        assertNotNull(pos)
        assertNear(500f, pos.xPx, 0.5f, "centre x")
        assertNear(400f, pos.yPx, 0.5f, "centre y")
        assertNear(1f, pos.limbAlpha, 1e-3f, "limbAlpha at centre")
    }

    @Test
    fun farSideSite_returnsNull() {
        // Site at (lat=0, lon=180°) is the far side from a camera at (yaw=0). dot ≤ 0 → cull.
        val pos = projectSiteToScreen(
            latDeg = 0.0, lonDeg = 180.0,
            cameraYawRad = 0f, cameraPitchRad = 0f, cameraDistance = 2f,
            viewportWidthPx = 1000f, viewportHeightPx = 800f,
        )
        assertNull(pos)
    }

    @Test
    fun limbSite_returnsNull() {
        // Site at exactly 90° from camera → dot = 0 → cull (matches FR-002's strict-inequality).
        val pos = projectSiteToScreen(
            latDeg = 0.0, lonDeg = 90.0,
            cameraYawRad = 0f, cameraPitchRad = 0f, cameraDistance = 2f,
            viewportWidthPx = 1000f, viewportHeightPx = 800f,
        )
        assertNull(pos)
    }

    @Test
    fun offAxisEastSite_projectsRightOfCentre() {
        val pos = projectSiteToScreen(
            latDeg = 0.0, lonDeg = 30.0,
            cameraYawRad = 0f, cameraPitchRad = 0f, cameraDistance = 5f,
            viewportWidthPx = 1000f, viewportHeightPx = 800f,
            fovYRad = (PI / 4).toFloat(),
        )
        assertNotNull(pos)
        assertTrue(pos.xPx > 500f, "expected x > 500 (right of centre), got ${pos.xPx}")
        assertNear(400f, pos.yPx, 0.5f, "y should stay on the equator line")
        assertNear(1f, pos.limbAlpha, 0.05f, "30° east is squarely in the visible hemisphere")
    }

    @Test
    fun offAxisWestSite_projectsLeftOfCentre() {
        val pos = projectSiteToScreen(
            latDeg = 0.0, lonDeg = -30.0,
            cameraYawRad = 0f, cameraPitchRad = 0f, cameraDistance = 5f,
            viewportWidthPx = 1000f, viewportHeightPx = 800f,
            fovYRad = (PI / 4).toFloat(),
        )
        assertNotNull(pos)
        assertTrue(pos.xPx < 500f, "expected x < 500 (left of centre), got ${pos.xPx}")
    }

    @Test
    fun northernSite_projectsAboveCentre() {
        // Y axis is flipped (Y down on screen), so a higher latitude → smaller yPx.
        val pos = projectSiteToScreen(
            latDeg = 30.0, lonDeg = 0.0,
            cameraYawRad = 0f, cameraPitchRad = 0f, cameraDistance = 5f,
            viewportWidthPx = 1000f, viewportHeightPx = 800f,
            fovYRad = (PI / 4).toFloat(),
        )
        assertNotNull(pos)
        assertNear(500f, pos.xPx, 0.5f, "lat-only site stays on prime meridian x")
        assertTrue(pos.yPx < 400f, "expected y < 400 (above centre), got ${pos.yPx}")
    }

    @Test
    fun southernSite_projectsBelowCentre() {
        val pos = projectSiteToScreen(
            latDeg = -30.0, lonDeg = 0.0,
            cameraYawRad = 0f, cameraPitchRad = 0f, cameraDistance = 5f,
            viewportWidthPx = 1000f, viewportHeightPx = 800f,
            fovYRad = (PI / 4).toFloat(),
        )
        assertNotNull(pos)
        assertTrue(pos.yPx > 400f, "expected y > 400 (below centre), got ${pos.yPx}")
    }

    @Test
    fun aspectChange_narrowsHorizontalSpan() {
        // A site at lon=15° east projects further right when the viewport is square (aspect=1)
        // than when it's wide-screen (aspect=2). Same Y both times.
        val args = mapOf(
            "square" to 800f to 800f,
        )
        val square = projectSiteToScreen(
            latDeg = 0.0, lonDeg = 15.0,
            cameraYawRad = 0f, cameraPitchRad = 0f, cameraDistance = 5f,
            viewportWidthPx = 800f, viewportHeightPx = 800f,
            fovYRad = (PI / 4).toFloat(),
        )
        val wide = projectSiteToScreen(
            latDeg = 0.0, lonDeg = 15.0,
            cameraYawRad = 0f, cameraPitchRad = 0f, cameraDistance = 5f,
            viewportWidthPx = 1600f, viewportHeightPx = 800f,
            fovYRad = (PI / 4).toFloat(),
        )
        assertNotNull(square); assertNotNull(wide)

        // Express each x as fraction of its viewport width — the wide one should be a smaller
        // fraction past centre because the same FOV-Y gives a wider FOV-X.
        val squareFrac = (square.xPx - 400f) / 400f          // distance past centre / half-width
        val wideFrac = (wide.xPx - 800f) / 800f
        assertTrue(squareFrac > wideFrac, "expected square fraction $squareFrac > wide fraction $wideFrac")
    }

    @Test
    fun limbAlphaIsMonotonic_facingCenterToLimb() {
        // Sweep longitude from 0° (centre) toward 90° (limb). Alpha should decrease monotonically.
        val alphas = listOf(0.0, 30.0, 50.0, 70.0, 80.0).map { lon ->
            val p = projectSiteToScreen(
                latDeg = 0.0, lonDeg = lon,
                cameraYawRad = 0f, cameraPitchRad = 0f, cameraDistance = 5f,
                viewportWidthPx = 1000f, viewportHeightPx = 800f,
            )
            p?.limbAlpha ?: -1f
        }
        // None should be culled before 90° → all positive.
        assertTrue(alphas.all { it >= 0f }, "expected all visible, got $alphas")
        // Monotonic non-increasing.
        for (i in 1 until alphas.size) {
            assertTrue(alphas[i] <= alphas[i - 1] + 1e-4f, "alpha should not increase: $alphas")
        }
        // At 0° we're saturated.
        assertEquals(1f, alphas[0], "centre alpha")
        // Approaching limb the alpha drops.
        assertTrue(alphas.last() < alphas.first(), "alpha at 80° < alpha at 0°: $alphas")
    }

    @Test
    fun zeroViewport_returnsNull() {
        val pos = projectSiteToScreen(
            latDeg = 0.0, lonDeg = 0.0,
            cameraYawRad = 0f, cameraPitchRad = 0f, cameraDistance = 2f,
            viewportWidthPx = 0f, viewportHeightPx = 800f,
        )
        assertNull(pos)
    }

    @Test
    fun cameraFollowsLatLon_centresWhenAimed() {
        // Camera looks at lat=20°, lon=30°: site at the same lat/lon should project to centre.
        val site = MoonSiteCoords(latDeg = 20.0, lonDeg = 30.0)
        val (yaw, pitch) = latLonToYawPitch(site.latDeg, site.lonDeg)
        val pos = projectSiteToScreen(
            latDeg = site.latDeg, lonDeg = site.lonDeg,
            cameraYawRad = yaw, cameraPitchRad = pitch, cameraDistance = 5f,
            viewportWidthPx = 1000f, viewportHeightPx = 800f,
        )
        assertNotNull(pos)
        // Centre within a couple of pixels — float math doesn't quite hit the exact midpoint.
        assertNear(500f, pos.xPx, 1f, "centred x")
        assertNear(400f, pos.yPx, 1f, "centred y")
        assertNear(1f, pos.limbAlpha, 1e-3f, "centred alpha = 1")
    }
}

private data class MoonSiteCoords(val latDeg: Double, val lonDeg: Double)

private fun assertNear(expected: Float, actual: Float, tol: Float, label: String) {
    assertTrue(abs(expected - actual) <= tol, "$label: expected $expected ± $tol, got $actual")
}
