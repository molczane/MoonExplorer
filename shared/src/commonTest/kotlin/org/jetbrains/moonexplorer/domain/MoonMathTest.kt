package org.jetbrains.moonexplorer.domain

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.moonexplorer.actions.LightingPreset

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

    // ---- T320 / 03-sites-and-flyto: shortestYawDelta + easeInOutCubic ---------------

    @Test
    fun shortestYawDelta_zeroIsZero() {
        assertNear(0f, shortestYawDelta(0f, 0f), TOL_TIGHT, "zero delta")
    }

    @Test
    fun shortestYawDelta_quarterTurnIsQuarterTurn() {
        val q = (PI / 2.0).toFloat()
        assertNear(q, shortestYawDelta(0f, q), TOL_TIGHT, "0 → π/2")
        assertNear(-q, shortestYawDelta(q, 0f), TOL_TIGHT, "π/2 → 0")
    }

    @Test
    fun shortestYawDelta_takesShortPathOverWraparound() {
        // 170° → -170° is 20° the short way, not 340° the long way.
        val from = (170.0 * PI / 180.0).toFloat()
        val to = (-170.0 * PI / 180.0).toFloat()
        val expected = (20.0 * PI / 180.0).toFloat()
        assertNear(expected, shortestYawDelta(from, to), TOL_TIGHT, "+170° → -170°")
        assertNear(-expected, shortestYawDelta(to, from), TOL_TIGHT, "-170° → +170°")
    }

    @Test
    fun shortestYawDelta_modularInputs() {
        // Adding a full revolution to either operand shouldn't change the delta.
        val twoPi = (2.0 * PI).toFloat()
        val expected = shortestYawDelta(0.5f, 1.5f)
        assertNear(expected, shortestYawDelta(0.5f + twoPi, 1.5f), TOL_TIGHT, "+2π on from")
        assertNear(expected, shortestYawDelta(0.5f, 1.5f + twoPi), TOL_TIGHT, "+2π on to")
    }

    @Test
    fun easeInOutCubic_endpointsAndMidpoint() {
        assertEquals(0f, easeInOutCubic(0f), "ease(0) should be 0")
        assertNear(0.5f, easeInOutCubic(0.5f), TOL_TIGHT, "ease(0.5) should be 0.5")
        assertEquals(1f, easeInOutCubic(1f), "ease(1) should be 1")
    }

    @Test
    fun easeInOutCubic_acceleratesThenDecelerates() {
        // First quarter: accelerating → eased < linear → eased(0.25) < 0.25
        assertTrue(easeInOutCubic(0.25f) < 0.25f, "expected ease(0.25) < 0.25 (acceleration)")
        // Last quarter: decelerating → eased > linear → eased(0.75) > 0.75
        assertTrue(easeInOutCubic(0.75f) > 0.75f, "expected ease(0.75) > 0.75 (deceleration)")
    }

    @Test
    fun easeInOutCubic_isSymmetricAroundHalf() {
        // S-curve: ease(t) + ease(1-t) ≈ 1.
        for (t in listOf(0.1f, 0.2f, 0.3f, 0.4f)) {
            val sum = easeInOutCubic(t) + easeInOutCubic(1f - t)
            assertNear(1f, sum, TOL_TIGHT, "ease($t) + ease(${1f - t}) should sum to 1")
        }
    }

    @Test
    fun easeInOutCubic_clampsOutOfRange() {
        assertEquals(0f, easeInOutCubic(-0.5f), "negative t should clamp to 0")
        assertEquals(1f, easeInOutCubic(1.5f), "t > 1 should clamp to 1")
    }

    // ---- T411 / 04-sun-control: joystickToSunDir + lerpSunDirection -----------------

    @Test
    fun joystickToSunDir_centerIsForward() {
        // (0, 0) inside disk → +Z (camera-facing hemisphere apex).
        assertVec3Near(Vec3(0f, 0f, 1f), joystickToSunDir(0f, 0f), TOL_TIGHT)
    }

    @Test
    fun joystickToSunDir_diskBoundaryEast() {
        // (1, 0) on disk edge → (1, 0, 0); z grazes to 0 (terminator-on-meridian).
        assertVec3Near(Vec3(1f, 0f, 0f), joystickToSunDir(1f, 0f), TOL_TIGHT)
    }

    @Test
    fun joystickToSunDir_diskBoundaryNorth() {
        // (0, 1) on disk edge → (0, 1, 0); z grazes to 0.
        assertVec3Near(Vec3(0f, 1f, 0f), joystickToSunDir(0f, 1f), TOL_TIGHT)
    }

    @Test
    fun joystickToSunDir_outsideDiskClampsToBoundary() {
        // (2, 0) outside the disk → projected onto boundary (1, 0, 0).
        assertVec3Near(Vec3(1f, 0f, 0f), joystickToSunDir(2f, 0f), TOL_TIGHT)
    }

    @Test
    fun joystickToSunDir_offDiagonalOnBoundary() {
        // (0.6, 0.8): 0.36 + 0.64 = 1 exactly; lands on the disk edge with z = 0.
        // Float roundoff may push r² a hair above 1, sending us through the clamp branch —
        // either path yields the same boundary point within TOL_TIGHT.
        assertVec3Near(Vec3(0.6f, 0.8f, 0f), joystickToSunDir(0.6f, 0.8f), TOL_TIGHT)
    }

    @Test
    fun joystickToSunDir_interiorIsUnitLength() {
        // (0.5, 0.5) interior → unit-length result with z = sqrt(1 − 0.5).
        val v = joystickToSunDir(0.5f, 0.5f)
        assertNear(1f, v.length(), TOL_TIGHT, "|joystick interior| ≈ 1")
        assertNear(sqrt(0.5f), v.z, TOL_TIGHT, "z = sqrt(1 − x² − y²)")
    }

    @Test
    fun lerpSunDirection_endpointsAreExact() {
        val a = lightingPresetSunDir(LightingPreset.Day)
        val b = lightingPresetSunDir(LightingPreset.HighContrast)
        assertVec3Near(a, lerpSunDirection(a, b, 0f), TOL_TIGHT)
        assertVec3Near(b, lerpSunDirection(a, b, 1f), TOL_TIGHT)
    }

    @Test
    fun lerpSunDirection_dayToNightPassesThroughTerminator() {
        // Day → Night via lat/lon lerp: shortestYawDelta(0, π) = +π (because -π is
        // excluded from (-π, π]); so the path goes 0° → 90° → 180°, passing through
        // (1, 0, 0) — the Terminator preset's sub-solar point — at t = 0.5.
        val day = lightingPresetSunDir(LightingPreset.Day)
        val night = lightingPresetSunDir(LightingPreset.Night)
        val mid = lerpSunDirection(day, night, 0.5f)
        assertVec3Near(Vec3(1f, 0f, 0f), mid, TOL_TIGHT)
    }

    @Test
    fun lerpSunDirection_halfToApolloMidArc() {
        // Half = (0°, +90°), Apollo = (0°, +60°). shortestYawDelta(+π/2, +π/3) = -π/6.
        // Mid (t=0.5) at lat=0, lon = +π/2 - π/12 = +5π/12 = +75°.
        val half = lightingPresetSunDir(LightingPreset.Terminator)
        val apollo = lightingPresetSunDir(LightingPreset.HighContrast)
        val mid = lerpSunDirection(half, apollo, 0.5f)
        val midLat = asin(mid.y.coerceIn(-1f, 1f))
        val midLon = atan2(mid.x, mid.z)
        assertNear(0f, midLat, TOL_TIGHT, "Half→Apollo mid lat = 0")
        assertNear((75.0 * PI / 180.0).toFloat(), midLon, TOL_TIGHT, "Half→Apollo mid lon = +75°")
    }

    @Test
    fun lerpSunDirection_unitLengthAcrossT() {
        // Reconstructed result is unit-length by construction at any t. Use an
        // off-equator joystick start to exercise non-zero lat lerp.
        val from = joystickToSunDir(0.4f, -0.3f)
        val to = lightingPresetSunDir(LightingPreset.HighContrast)
        for (t in floatArrayOf(0f, 0.1f, 0.25f, 0.5f, 0.75f, 0.9f, 1f)) {
            val len = lerpSunDirection(from, to, t).length()
            assertTrue(
                abs(len - 1f) < TOL_TIGHT,
                "lerpSunDirection(.., t=$t).length = $len, expected ≈ 1",
            )
        }
    }

    private fun assertNear(expected: Float, actual: Float, tol: Float, label: String) {
        assertTrue(abs(expected - actual) <= tol, "$label: expected $expected ± $tol, got $actual")
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
