package org.jetbrains.moonexplorer.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Refined Material 3 shape tokens for the Moon Explorer theme. T503 / 05-modern-theme.
 *
 * `extraLarge` is the load-bearing override: bumped from M3's default 28 dp to 32 dp on
 * the top corners only (sheets keep flat bottoms because `ModalBottomSheet` rests against
 * the bottom edge). Other tokens stay close to defaults — `small` for buttons, `medium`
 * for cards, `large` for dialog containers.
 */
fun moonExplorerShapes(): Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(
        topStart = 32.dp,
        topEnd = 32.dp,
        bottomStart = 0.dp,
        bottomEnd = 0.dp,
    ),
)
