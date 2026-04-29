package org.jetbrains.moonexplorer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * Outermost theme wrapper for the Moon Explorer app. T504 / 05-modern-theme.
 *
 * Composes [moonExplorerDarkScheme], [moonExplorerTypography], and [moonExplorerShapes]
 * into a single Material 3 [MaterialTheme] block. All Compose surfaces descended from
 * `MoonExplorerScreen` inherit these via the standard `CompositionLocal` plumbing —
 * `MaterialTheme.colorScheme.*`, `MaterialTheme.typography.*`, `MaterialTheme.shapes.*`
 * resolve to the spec'd values automatically.
 *
 * Dark-only for v1 (the app's context — Filament-rendered Moon on a black backdrop —
 * makes light theme an awkward fit). Light-theme support is a one-task follow-up if
 * anyone asks: switch to `if (isSystemInDarkTheme()) moonExplorerDarkScheme() else …`.
 */
@Composable
fun MoonExplorerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = moonExplorerDarkScheme(),
        typography = moonExplorerTypography(),
        shapes = moonExplorerShapes(),
        content = content,
    )
}
