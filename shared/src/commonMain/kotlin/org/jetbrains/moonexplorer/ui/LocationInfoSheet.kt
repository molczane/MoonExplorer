package org.jetbrains.moonexplorer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.moonexplorer.domain.MoonSite
import org.jetbrains.moonexplorer.domain.SiteType
import org.jetbrains.moonexplorer.ui.theme.MoonModalBottomSheet

/**
 * T221 — Location info sheet. Material3 ModalBottomSheet shows the chosen [site] with name,
 * optional subtitle, a type chip, formatted coordinates, and the description. The "Center
 * on this site" button calls [onCenterClick] but does NOT dismiss the sheet — per US3 the
 * user can read the description while the camera is centered, then dismiss when ready.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationInfoSheet(
    site: MoonSite,
    onCenterClick: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState()
    MoonModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding(),
        ) {
            Text(text = site.name, style = MaterialTheme.typography.headlineSmall)
            site.subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistChip(
                    onClick = {},
                    label = { Text(formatType(site.type)) },
                    colors = AssistChipDefaults.assistChipColors(),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = formatCoords(site.lat, site.lon),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(text = site.description, style = MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(20.dp))

            Button(onClick = onCenterClick, modifier = Modifier.fillMaxWidth()) {
                Text("Center on this site")
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun formatType(type: SiteType): String = when (type) {
    SiteType.MARE -> "Mare"
    SiteType.CRATER -> "Crater"
    SiteType.LANDING_SITE -> "Landing site"
    SiteType.OTHER -> "Region"
}

private fun formatCoords(lat: Double, lon: Double): String {
    val latDir = if (lat >= 0.0) "N" else "S"
    val lonDir = if (lon >= 0.0) "E" else "W"
    return "${formatAbsDeg(lat)}° $latDir, ${formatAbsDeg(lon)}° $lonDir"
}

// Single-decimal absolute-value formatter — String.format isn't multiplatform on K/N, so we
// build the digits manually. Round-half-up via the `+ 0.5` trick.
private fun formatAbsDeg(value: Double): String {
    val abs = if (value < 0.0) -value else value
    val tenths = (abs * 10.0 + 0.5).toInt()
    val whole = tenths / 10
    val frac = tenths % 10
    return "$whole.$frac"
}
