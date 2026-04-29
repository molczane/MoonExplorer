package org.jetbrains.moonexplorer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.moonexplorer.actions.LightingPreset

/**
 * 2x2 grid of lighting-preset buttons. T421 / 04-sun-control.
 *
 * Tapping a button fires [onPresetTap] with the corresponding [LightingPreset] enum value.
 * The screen-level callback handles the cancel-prior-job-then-launch pattern (T433),
 * mirroring the `currentFlyJob` / `currentLightingJob` plumbing used for animated fly-to.
 *
 * Labels map ADR-0005's locked enum to user-facing words:
 *   Day → "Full"           — sub-solar at (0°,    0°)
 *   Terminator → "Half"    — sub-solar at (0°,  +90°)
 *   HighContrast → "Apollo"— sub-solar at (0°,  +60°)
 *   Night → "New"          — sub-solar at (0°, +180°)
 */
@Composable
fun LightingPresetRow(
    onPresetTap: (LightingPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PresetButton(LightingPreset.Day, "Full", onPresetTap)
            PresetButton(LightingPreset.Terminator, "Half", onPresetTap)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PresetButton(LightingPreset.HighContrast, "Apollo", onPresetTap)
            PresetButton(LightingPreset.Night, "New", onPresetTap)
        }
    }
}

@Composable
private fun PresetButton(
    preset: LightingPreset,
    label: String,
    onTap: (LightingPreset) -> Unit,
) {
    // T520 / 05-modern-theme — override the FilledTonalButton's container colour from
    // the theme's surface tone to `secondaryContainer` (warm amber). Visually distinguishes
    // sun-control surfaces from the cool-blue rest of the UI without screaming about it.
    FilledTonalButton(
        onClick = { onTap(preset) },
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        modifier = Modifier
            .width(PRESET_BUTTON_WIDTH_DP)
            .height(PRESET_BUTTON_HEIGHT_DP),
    ) {
        Text(label)
    }
}

private val PRESET_BUTTON_WIDTH_DP = 88.dp
private val PRESET_BUTTON_HEIGHT_DP = 40.dp
