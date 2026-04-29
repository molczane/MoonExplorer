package org.jetbrains.moonexplorer.domain

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import org.jetbrains.moonexplorer.actions.LightingPreset

/**
 * T410 / 04-sun-control. Verifies the preset Vec3 table is unit-length and the
 * concrete values match the (lat°, lon°) table in `plan.md` § "Lighting preset table".
 */
class LightingPresetsTest {

    @Test
    fun day_isPlusZ() {
        // Full Moon — sub-solar at (0°, 0°) → +Z.
        assertVec3Near(Vec3(0f, 0f, 1f), lightingPresetSunDir(LightingPreset.Day))
    }

    @Test
    fun terminator_isPlusX() {
        // Half Moon — sub-solar at (0°, +90°) → +X.
        assertVec3Near(Vec3(1f, 0f, 0f), lightingPresetSunDir(LightingPreset.Terminator))
    }

    @Test
    fun highContrast_isApolloAngle() {
        // Apollo — sub-solar at (0°, +60°) → (sin 60°, 0, cos 60°) = (0.866, 0, 0.5).
        assertVec3Near(Vec3(0.8660254f, 0f, 0.5f), lightingPresetSunDir(LightingPreset.HighContrast))
    }

    @Test
    fun night_isMinusZ() {
        // New Moon — sub-solar at (0°, +180°) → -Z.
        assertVec3Near(Vec3(0f, 0f, -1f), lightingPresetSunDir(LightingPreset.Night))
    }

    @Test
    fun allPresets_areUnitLength() {
        for (preset in LightingPreset.entries) {
            val v = lightingPresetSunDir(preset)
            val len = v.length()
            assertTrue(
                abs(len - 1f) < TOL,
                "lightingPresetSunDir($preset) = $v has length $len, expected ≈ 1",
            )
        }
    }

    private fun assertVec3Near(expected: Vec3, actual: Vec3) {
        assertTrue(
            abs(expected.x - actual.x) < TOL &&
                abs(expected.y - actual.y) < TOL &&
                abs(expected.z - actual.z) < TOL,
            "expected $expected ± $TOL, got $actual",
        )
    }

    companion object {
        private const val TOL: Float = 1e-6f
    }
}
