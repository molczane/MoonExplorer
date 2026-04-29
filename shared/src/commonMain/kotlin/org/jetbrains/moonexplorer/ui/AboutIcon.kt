package org.jetbrains.moonexplorer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Hand-drawn "info" icon. T522 / 05-modern-theme. Replaces the bare Unicode `ⓘ` glyph in
 * `MoonExplorerScreen`'s About button. JetBrains Compose 1.10 dropped standalone
 * `material-icons-core`/`material-icons-extended` artifacts, so a small Canvas drawing
 * is the cleanest way to ship a stroke-style info glyph without dragging in a dep.
 *
 * The icon scales with the size of [modifier]: a 28-dp size matches the original glyph's
 * footprint. Stroke width is ~6% of the diameter, dot radius ~7% — tuned to read
 * cleanly at 28 dp while still scaling up to e.g. 64 dp without artefacts.
 */
@Composable
fun AboutIcon(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val diameter = size.minDimension
        val stroke = diameter * 0.06f
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = (diameter / 2f) - stroke / 2f

        // Outer ring.
        drawCircle(
            color = tint,
            radius = radius,
            center = center,
            style = Stroke(width = stroke),
        )

        // Lower-case "i" inside the ring: a small dot above a vertical stem.
        val dotR = diameter * 0.07f
        val dotCy = center.y - radius * 0.42f
        drawCircle(
            color = tint,
            radius = dotR,
            center = Offset(center.x, dotCy),
        )

        val stemTopY = center.y - radius * 0.12f
        val stemBottomY = center.y + radius * 0.50f
        drawLine(
            color = tint,
            start = Offset(center.x, stemTopY),
            end = Offset(center.x, stemBottomY),
            strokeWidth = stroke * 1.6f,
            cap = StrokeCap.Round,
        )
    }
}
