package org.jetbrains.moonexplorer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * About / Credits sheet (T130). Material3 ModalBottomSheet — drag down or scrim tap dismisses
 * (the host's `onDismissRequest` flips the visibility flag in `MoonExplorerScreen`).
 *
 * Carries the verbatim NASA SVS attribution string from ADR-0004 — keeping it verbatim
 * matters for FR-005 / NASA media-usage compliance. Also surfaces the renderer + asset-format
 * decisions via short ADR pointers so a curious user can dig into how the Moon got rendered
 * the way it did.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding(),
        ) {
            Text(
                text = "Moon Explorer",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = "$APP_VERSION  ·  Filament $FILAMENT_VERSION",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))

            // Verbatim per ADR-0004 / FR-005. Do not paraphrase — NASA media usage compliance.
            Text(
                text = "Lunar surface imagery",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = ATTRIBUTION_NASA_SVS,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(20.dp))

            Text(
                text = "Renderer",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Built with Google Filament — a real-time PBR renderer (Apache 2.0). " +
                    "Android via the filament-android JNI bindings; iOS via the Filament CocoaPods " +
                    "subspecs (filament + ktxreader + uberz). The Moon is a procedural UV sphere " +
                    "lit by a single directional sun light.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(20.dp))

            Text(
                text = "Architecture decisions",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "ADR-0001 — Filament as the renderer\n" +
                    "ADR-0004 — Asset strategy: NASA SVS, KTX2/Basis, bundled + CDN\n" +
                    "ADR-0010 — HD assets on GitHub Releases\n" +
                    "ADR-0011 — Android HD KTX2 deferred (bundled tier ships PNG)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

// Verbatim attribution string from ADR-0004 / spec.md FR-005. The NASA insignia / logotype /
// worm are deliberately not used (per ADR-0004 §"Pitfalls to avoid").
private const val ATTRIBUTION_NASA_SVS: String =
    "Lunar surface imagery: NASA's Scientific Visualization Studio, " +
        "\"CGI Moon Kit\" (Ernie Wright / Noah Petro), derived from LRO LROC and " +
        "LOLA data. Public domain. https://svs.gsfc.nasa.gov/4720"

// Hand-rolled version literals for the spike — BuildKonfig-generated build numbers can land
// in a polish task. These are honest about what's running.
private const val APP_VERSION: String = "v1 (02-moon-renderer-mvp)"
private const val FILAMENT_VERSION: String = "1.71.1"
