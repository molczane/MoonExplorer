package org.jetbrains.moonexplorer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.moonexplorer.ui.theme.MoonModalBottomSheet

/**
 * Settings sheet. T222 (01-app-shell) shipped the entry affordance; T730 / 07-celestial-
 * background turns the placeholder body into its first real job — toggles for the
 * celestial backdrop (`showStars` and `showSun`). Both flags are session-only; persistence
 * across app restarts is documented as out-of-scope for v1.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    showStars: Boolean,
    showSun: Boolean,
    onShowStarsChange: (Boolean) -> Unit,
    onShowSunChange: (Boolean) -> Unit,
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
            Text(text = "Settings", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(20.dp))

            Text(
                text = "Celestial background",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            ToggleRow(
                label = "Show stars",
                checked = showStars,
                onCheckedChange = onShowStarsChange,
            )
            ToggleRow(
                label = "Show sun",
                checked = showSun,
                onCheckedChange = onShowSunChange,
            )

            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
