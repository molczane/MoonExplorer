package org.jetbrains.moonexplorer.domain

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Verifies the selenographic → Cartesian conversion against the worked examples
 * in `ai-docs/research/selenographic-math-camera.md` §1 and ADR-0006.
 */
class MoonMathTest {

    @Test
    fun latLonToCartesian_primeMeridian() {
        // (0°, 0°) → (0, 0, 1) — sub-Earth point, the +Z axis (ADR-0006).
        assertVec3Near(Vec3(0f, 0f, 1f), latLonToCartesian(0f, 0f), TOL_TIGHT)
    }

    @Test
    fun latLonToCartesian_eastQuarter() {
        // (0°, +90°) → (+1, 0, 0) — east limb, +X axis.
        assertVec3Near(Vec3(1f, 0f, 0f), latLonToCartesian(0f, 90f), TOL_TIGHT)
    }

    @Test
    fun latLonToCartesian_northPole() {
        // (+90°, anything) → (0, +1, 0).
        assertVec3Near(Vec3(0f, 1f, 0f), latLonToCartesian(90f, 0f), TOL_TIGHT)
    }

    @Test
    fun latLonToCartesian_apollo11() {
        // Apollo 11 landing site: 0.6741°N, 23.4733°E → (0.398, 0.012, 0.917)
        // per ADR-0006 §"Sanity check" / selenographic-math-camera.md §1.
        assertVec3Near(Vec3(0.398f, 0.012f, 0.917f), latLonToCartesian(0.6741f, 23.4733f), TOL_LOOSE)
    }

    @Test
    fun latLonToCartesian_alwaysUnitLength() {
        // Sample a grid of lat/lon and confirm |v| ≈ 1 within Float precision.
        val lats = floatArrayOf(-90f, -75f, -45f, -15f, 0f, 15f, 45f, 75f, 90f)
        val lons = floatArrayOf(-180f, -135f, -90f, -45f, 0f, 45f, 90f, 135f, 180f)
        for (lat in lats) for (lon in lons) {
            val len = latLonToCartesian(lat, lon).length()
            assertTrue(
                abs(len - 1f) < TOL_TIGHT,
                "|latLonToCartesian($lat, $lon)| = $len, expected ≈ 1",
            )
        }
    }

    private fun assertVec3Near(expected: Vec3, actual: Vec3, tol: Float) {
        assertTrue(
            abs(expected.x - actual.x) < tol &&
                abs(expected.y - actual.y) < tol &&
                abs(expected.z - actual.z) < tol,
            "expected $expected ± $tol, got $actual",
        )
    }

    companion object {
        /** Float-precision tolerance for trig identities. */
        private const val TOL_TIGHT: Float = 1e-5f

        /** Looser tolerance for spec-rounded reference values. */
        private const val TOL_LOOSE: Float = 1e-3f
    }
}
