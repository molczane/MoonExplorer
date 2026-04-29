package org.jetbrains.moonexplorer.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * T505 / 05-modern-theme. Spot-checks the dark colour scheme's slot-to-constant mapping +
 * a WCAG AA contrast verification against the spec'd palette.
 */
class MoonExplorerColorSchemeTest {

    @Test
    fun dark_primaryIsCoolBlue() {
        assertEquals(MoonColors.MoonBlue, moonExplorerDarkScheme().primary)
    }

    @Test
    fun dark_secondaryIsWarmAmber() {
        assertEquals(MoonColors.SunAmber, moonExplorerDarkScheme().secondary)
    }

    @Test
    fun dark_backgroundIsSpaceBlack() {
        assertEquals(MoonColors.SpaceBlack, moonExplorerDarkScheme().background)
        // Renderer's clear color is also pure black — visual seam check.
        assertEquals(Color(0xFF000000), moonExplorerDarkScheme().background)
    }

    @Test
    fun dark_surfaceContainerStaysSurface() {
        // ModalBottomSheet picks one of the surfaceContainer* slots for its default
        // container colour depending on the M3 version; making them all equal `surface`
        // means the sheet renders our DeepBlueGray regardless of which slot M3 picks.
        val s = moonExplorerDarkScheme()
        assertEquals(s.surface, s.surfaceContainer)
        assertEquals(s.surface, s.surfaceContainerHigh)
        assertEquals(s.surface, s.surfaceContainerHighest)
    }

    @Test
    fun moonColors_passWcagAa() {
        // WCAG 2.1 AA threshold for normal text is 4.5:1; for large text 3:1.
        // We verify the two primary accent-on-background pairings.
        val primaryRatio = contrastRatio(MoonColors.MoonBlue, MoonColors.SpaceBlack)
        assertTrue(
            primaryRatio >= 4.5f,
            "MoonBlue on SpaceBlack contrast = $primaryRatio; expected >= 4.5",
        )
        val secondaryRatio = contrastRatio(MoonColors.SunAmber, MoonColors.SpaceBlack)
        assertTrue(
            secondaryRatio >= 4.5f,
            "SunAmber on SpaceBlack contrast = $secondaryRatio; expected >= 4.5",
        )
    }

    /**
     * WCAG 2.1 contrast ratio: `(L_lighter + 0.05) / (L_darker + 0.05)`.
     *
     * `relativeLuminance` per the WCAG formula:
     *   for each linear-RGB channel c: c_lin = c <= 0.03928 ? c/12.92 : ((c+0.055)/1.055)^2.4
     *   L = 0.2126·R_lin + 0.7152·G_lin + 0.0722·B_lin
     */
    private fun contrastRatio(a: Color, b: Color): Float {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        val lighter = maxOf(la, lb)
        val darker = minOf(la, lb)
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    private fun relativeLuminance(c: Color): Float {
        fun channel(v: Float): Float =
            if (v <= 0.03928f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
        return 0.2126f * channel(c.red) + 0.7152f * channel(c.green) + 0.0722f * channel(c.blue)
    }
}
