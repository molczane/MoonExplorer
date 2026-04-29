package org.jetbrains.moonexplorer.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Named colour constants for the Moon Explorer dark theme. T501 / 05-modern-theme.
 *
 * Cool-blue ([MoonBlue]) + warm-amber ([SunAmber]) split: cool primary feels space-like
 * and drives most of the UI's interactive surfaces (switches, highlighted markers); warm
 * secondary visually distinguishes sun-related controls (`LightingPresetRow` buttons).
 *
 * WCAG AA spot-check (verified by [MoonExplorerColorSchemeTest]):
 *   - MoonBlue on SpaceBlack       → ~7.4 : 1   (passes 4.5:1 AA threshold)
 *   - SunAmber on SpaceBlack       → ~10.6 : 1  (passes generously)
 *   - Bone     on DeepBlueGray     → ~16   : 1
 */
object MoonColors {
    /** Pure black. Matches the Filament renderer's clear color exactly — no seam. */
    val SpaceBlack: Color = Color(0xFF000000)

    /** Sheet surfaces (opaque base). Translucent variants are derived via `.copy(alpha = …)`. */
    val DeepBlueGray: Color = Color(0xFF0F1218)

    /** Primary accent — cool electric blue. Switches, highlighted markers, primary buttons. */
    val MoonBlue: Color = Color(0xFF5B86FF)

    /** Primary container — same hue, dimmer; for tonal containers / less-prominent surfaces. */
    val MoonBlueDim: Color = Color(0xFF2E4485)

    /** Secondary accent — warm sun-like amber. Sun-control surfaces (preset buttons). */
    val SunAmber: Color = Color(0xFFFFAB40)

    /** Secondary container — dimmer amber; FilledTonalButton container colour. */
    val SunAmberDim: Color = Color(0xFF8E5A1F)

    /** High-emphasis on-surface text. */
    val Bone: Color = Color(0xFFE8EEF5)

    /** Medium-emphasis on-surface text (alpha 0.7). */
    val BoneDim: Color = Color(0xB3E8EEF5)

    /** Subtle outlines on surface containers, dividers. */
    val Outline: Color = Color(0xFF3A3F4A)

    /** Sheet scrim — alpha 0.8 black. Tuned darker than Material's default ~0.32 to obscure
     *  the Moon viewport behind sheets without going pitch-black. */
    val Scrim: Color = Color(0xCC000000)
}

/**
 * Dark `ColorScheme` for the Moon Explorer theme. T501.
 *
 * `surfaceContainer` / `surfaceContainerHigh` / `surfaceContainerHighest` are all set to
 * [MoonColors.DeepBlueGray] so `ModalBottomSheet`'s default surface picks the right colour
 * regardless of which slot the M3 version it picks. Translucent surface for the sheets
 * is applied at the call site via `.copy(alpha = …)`, not on the scheme itself.
 */
fun moonExplorerDarkScheme(): ColorScheme = darkColorScheme(
    primary = MoonColors.MoonBlue,
    onPrimary = MoonColors.SpaceBlack,
    primaryContainer = MoonColors.MoonBlueDim,
    onPrimaryContainer = MoonColors.Bone,
    secondary = MoonColors.SunAmber,
    onSecondary = MoonColors.SpaceBlack,
    secondaryContainer = MoonColors.SunAmberDim,
    onSecondaryContainer = MoonColors.Bone,
    background = MoonColors.SpaceBlack,
    onBackground = MoonColors.Bone,
    surface = MoonColors.DeepBlueGray,
    onSurface = MoonColors.Bone,
    surfaceVariant = MoonColors.DeepBlueGray,
    onSurfaceVariant = MoonColors.BoneDim,
    outline = MoonColors.Outline,
    scrim = MoonColors.Scrim,
    surfaceContainer = MoonColors.DeepBlueGray,
    surfaceContainerHigh = MoonColors.DeepBlueGray,
    surfaceContainerHighest = MoonColors.DeepBlueGray,
)
