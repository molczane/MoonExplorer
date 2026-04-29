package org.jetbrains.moonexplorer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.moonexplorer.domain.MoonSite

/**
 * T220 — Search affordance + results list. Collapsed it's the 🔍 icon at TopEnd; tapping
 * expands to a Material3 Card with an OutlinedTextField + up to 10 result rows.
 *
 * State is fully hoisted so the host (`MoonExplorerScreen`) drives the search through
 * `MoonExplorerActions.searchMoonLocations` and reacts to result taps. The composable
 * only renders + dispatches events.
 */
@Composable
fun SearchBar(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<MoonSite>,
    onResultTap: (MoonSite) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!expanded) {
        IconButton(onClick = { onExpandedChange(true) }, modifier = modifier) {
            Text(
                text = "🔍",
                color = Color.White,
                fontSize = 24.sp,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Card(
        modifier = modifier.widthIn(min = 240.dp, max = 360.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text("Search the Moon") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                )
                IconButton(onClick = { onExpandedChange(false) }) {
                    Text("✕", fontSize = 18.sp)
                }
            }

            if (query.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                if (results.isEmpty()) {
                    Text(
                        text = "No matches",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    results.forEach { site ->
                        ResultRow(site = site, onClick = { onResultTap(site) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(site: MoonSite, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Text(
            text = site.name,
            style = MaterialTheme.typography.bodyMedium,
        )
        site.subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
