package org.jetbrains.moonexplorer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.moonexplorer.actions.LightingPreset
import org.jetbrains.moonexplorer.domain.Vec3

/**
 * Sun control panel — composes [SunJoystick] (live drag) and [LightingPresetRow] (4
 * preset buttons). T422 / 04-sun-control. Replaces `SunControl`'s 1-axis slider at
 * BottomCenter (T440).
 *
 * Two side-effect tracks per `plan.md` § "Architecture flow":
 *   * Joystick drag → continuous gesture → [onJoystickDrag] direct to viewmodel
 *     (bypasses MoonExplorerActions, mirroring the onDrag/onPinch pattern from 02-mvp).
 *   * Preset tap → discrete command → [onPresetTap] → MoonExplorerActions.setLightingPreset
 *     (animated, default 500 ms ease-in-out — see T431).
 *
 * The knob's render position auto-tracks [sunDirection], so external direction changes
 * (preset animation, future Koog tool) move the knob without local UI state.
 */
@Composable
fun SunPanel(
    sunDirection: Vec3,
    onJoystickDrag: (x: Float, y: Float) -> Unit,
    onPresetTap: (LightingPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SunJoystick(
            sunDirection = sunDirection,
            onDrag = onJoystickDrag,
        )
        LightingPresetRow(onPresetTap = onPresetTap)
    }
}
