package org.jetbrains.moonexplorer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.jetbrains.moonexplorer.domain.MoonSite
import org.jetbrains.moonexplorer.domain.ScreenPos
import org.jetbrains.moonexplorer.domain.projectSiteToScreen

/**
 * 2D marker layer over the Filament-rendered Moon. T310 / 03-sites-and-flyto.
 *
 * For every site in [sites], runs `projectSiteToScreen` against the current camera state +
 * the captured viewport size. Visible (non-null) markers render as small circular dots; far-
 * side markers are filtered out by the projection. The Compose recomposition is triggered
 * automatically when any of the camera-state args or the viewport size changes — there's no
 * separate animation timer here, just per-frame state pull (ADR-0003).
 *
 * **Tap handling.** Each marker has a 48-dp invisible tap target around the visible dot
 * (Material's minimum touch target). The Box wrapping the markers has *no* pointerInput
 * modifier — Compose's pointer dispatch routes a tap to the topmost child that handles it,
 * and lets touches on bare overlay area fall through to whatever's underneath
 * (`MoonExplorerScreen`'s viewport gesture detector). Drag-from-marker may still get
 * claimed by `clickable` and not pan the camera; documented in `spec.md` § Edge Cases as
 * accepted-for-v1.
 */
@Composable
fun MarkerOverlay(
    sites: List<MoonSite>,
    cameraYawRad: Float,
    cameraPitchRad: Float,
    cameraDistance: Float,
    highlightedSiteId: String?,
    onMarkerTap: (siteId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var viewportSize: IntSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { viewportSize = it },
    ) {
        if (viewportSize.width <= 0 || viewportSize.height <= 0) return@Box

        val widthPx = viewportSize.width.toFloat()
        val heightPx = viewportSize.height.toFloat()

        for (site in sites) {
            val pos = projectSiteToScreen(
                latDeg = site.lat,
                lonDeg = site.lon,
                cameraYawRad = cameraYawRad,
                cameraPitchRad = cameraPitchRad,
                cameraDistance = cameraDistance,
                viewportWidthPx = widthPx,
                viewportHeightPx = heightPx,
            ) ?: continue
            MarkerDot(
                site = site,
                screenPos = pos,
                isHighlighted = site.id == highlightedSiteId,
                onTap = onMarkerTap,
            )
        }
    }
}

@Composable
private fun MarkerDot(
    site: MoonSite,
    screenPos: ScreenPos,
    isHighlighted: Boolean,
    onTap: (siteId: String) -> Unit,
) {
    val dotDp = if (isHighlighted) MARKER_DOT_HIGHLIGHTED_DP else MARKER_DOT_DEFAULT_DP
    val fill = if (isHighlighted) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.White
    }
    val borderColor = Color.Black.copy(alpha = 0.55f)

    // Outer 48-dp box: invisible tap target, centred on the projected screen position.
    Box(
        modifier = Modifier
            .offset {
                val halfBoxPx = MARKER_TAP_TARGET_DP.toPx() / 2f
                IntOffset(
                    x = (screenPos.xPx - halfBoxPx).toInt(),
                    y = (screenPos.yPx - halfBoxPx).toInt(),
                )
            }
            .size(MARKER_TAP_TARGET_DP)
            .clickable { onTap(site.id) },
        contentAlignment = Alignment.Center,
    ) {
        // Inner visible dot — limb-faded, circular, with a thin dark border for contrast on
        // light areas of the Moon.
        Box(
            modifier = Modifier
                .size(dotDp)
                .alpha(screenPos.limbAlpha)
                .clip(CircleShape)
                .background(fill)
                .border(width = 1.dp, color = borderColor, shape = CircleShape),
        )
    }
}

private val MARKER_DOT_DEFAULT_DP = 14.dp
private val MARKER_DOT_HIGHLIGHTED_DP = 22.dp
private val MARKER_TAP_TARGET_DP = 48.dp
