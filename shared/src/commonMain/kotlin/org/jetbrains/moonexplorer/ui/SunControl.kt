package org.jetbrains.moonexplorer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt
import org.jetbrains.moonexplorer.domain.Vec3

/**
 * Phase 0 placeholder sun control: a single-axis slider in [-1, 1] that maps
 * to the sun's X coordinate. Y stays 0; Z is computed to keep the result on
 * the camera-facing hemisphere (selenographic-math-camera.md §6 mode (a),
 * with y locked to 0). The full 2D joystick + presets ship in 04-sun-control.
 */
@Composable
fun SunControl(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val display = (value.coerceIn(-1f, 1f) * 100f).roundToInt() / 100f
    Column(modifier = modifier.fillMaxWidth()) {
        Text(text = "Sun direction (x): $display")
        Slider(
            value = value.coerceIn(-1f, 1f),
            onValueChange = onValueChange,
            valueRange = -1f..1f,
        )
    }
}

/**
 * Maps a 1-D joystick X position in [-1, 1] to a unit vector on the
 * camera-facing hemisphere with Y locked to 0:
 *
 *   z = sqrt(max(0, 1 - x²))
 *
 * Outside the unit disk (|x| > 1) z clamps to 0 — equivalent to a
 * terminator-on-meridian preset. Result is unit-length for any |x| ≤ 1.
 */
fun joystickToHemisphereDir(x: Float): Vec3 {
    val cx = x.coerceIn(-1f, 1f)
    val z = sqrt(max(0f, 1f - cx * cx))
    return Vec3(cx, 0f, z)
}
