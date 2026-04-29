package org.jetbrains.moonexplorer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Refined Material 3 type scale for the Moon Explorer theme. T502 / 05-modern-theme.
 *
 * Starts from M3's default [Typography] and overrides only the four styles the app
 * actually uses heavily:
 *   - `headlineSmall` — sheet titles ("Moon Explorer", "Settings", site name)
 *   - `titleMedium`   — section headers ("Lunar surface imagery", "Celestial background")
 *   - `bodyMedium`    — body copy in sheets
 *   - `labelLarge`    — button labels (preset buttons, search row)
 *
 * System fonts only — no custom typeface in v1.
 */
fun moonExplorerTypography(): Typography {
    val base = Typography()
    return base.copy(
        headlineSmall = base.headlineSmall.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.sp,
        ),
        titleMedium = base.titleMedium.copy(
            fontWeight = FontWeight.Medium,
        ),
        bodyMedium = base.bodyMedium.copy(
            lineHeight = 22.sp,
        ),
        labelLarge = base.labelLarge.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp,
        ),
    )
}
