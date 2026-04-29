package org.jetbrains.moonexplorer.domain

import org.jetbrains.moonexplorer.actions.LightingPreset

/**
 * Sub-solar Vec3 for each [LightingPreset], derived from the (lat°, lon°) table
 * in `ai-docs/specs/04-sun-control/plan.md` § "Lighting preset table". All four
 * presets sit on the equator (sub-solar lat = 0°); the lons (0°, +60°, +90°, +180°)
 * pick visually distinct moods spaced so no pair is antipodal in lon-space except
 * Day↔Night, which lat/lon lerp handles cleanly via the +90° intermediate.
 *
 * Adjusting the numbers (e.g., nudging Apollo from +60° to +70° if QA finds the
 * shadows too short) is a one-line tuning task — the animation, UI, and tests
 * don't care about the exact values as long as each result is unit-length.
 *
 * T410 / 04-sun-control.
 */
fun lightingPresetSunDir(preset: LightingPreset): Vec3 = when (preset) {
    LightingPreset.Day          -> Vec3(0f, 0f, 1f)              // (0°,    0°) — Full
    LightingPreset.Terminator   -> Vec3(1f, 0f, 0f)              // (0°,  +90°) — Half
    LightingPreset.HighContrast -> Vec3(0.8660254f, 0f, 0.5f)    // (0°,  +60°) — Apollo
    LightingPreset.Night        -> Vec3(0f, 0f, -1f)             // (0°, +180°) — New
}
