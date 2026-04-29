package org.jetbrains.moonexplorer.ui.theme

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

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

/**
 * Themed `ModalBottomSheet` wrapper. T510 / 05-modern-theme.
 *
 * Bakes the modern-theme defaults into one helper so `AboutSheet`, `SettingsSheet`, and
 * `LocationInfoSheet` don't repeat the three parameter overrides:
 *   * `containerColor` — `surface.copy(alpha = SHEET_CONTAINER_ALPHA)` — translucent dark
 *     "glass" surface that lets the scrim-dimmed Moon read faintly through.
 *   * `scrimColor` — `colorScheme.scrim` (alpha 0.8 black) — tuned darker than Material's
 *     default ~0.32 so the Moon stays as a faint silhouette behind the sheet, not a
 *     distracting glare.
 *   * `shape` — `shapes.extraLarge` (32 dp top corners, flat bottom) — softer than M3's
 *     default 28 dp; gives sheets a "pillowy" feel.
 *
 * Other parameters (`dragHandle`, `windowInsets`, `tonalElevation`, etc.) keep
 * `ModalBottomSheet`'s defaults — they pick up the theme already.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoonModalBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface.copy(
            alpha = MoonColors.SHEET_CONTAINER_ALPHA,
        ),
        scrimColor = MaterialTheme.colorScheme.scrim,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier,
        content = content,
    )
}
