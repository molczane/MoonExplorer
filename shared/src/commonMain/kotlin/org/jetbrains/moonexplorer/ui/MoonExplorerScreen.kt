package org.jetbrains.moonexplorer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.jetbrains.moonexplorer.actions.MoonExplorerActions
import org.jetbrains.moonexplorer.actions.MoonExplorerActionsImpl
import org.jetbrains.moonexplorer.assets.MoonAssetLoader
import org.jetbrains.moonexplorer.assets.StorageDir
import org.jetbrains.moonexplorer.assets.createMoonHttpClient
import org.jetbrains.moonexplorer.domain.DEFAULT_FOV_Y_RAD
import org.jetbrains.moonexplorer.domain.MoonSite
import org.jetbrains.moonexplorer.domain.SiteCatalog
import org.jetbrains.moonexplorer.render.MoonViewport
import org.jetbrains.moonexplorer.state.MoonViewModel

/**
 * Top-level Moon Explorer screen. Hosts the platform renderer, gestures, sun control, and —
 * since 01-app-shell — the search affordance, location info sheet, About / Settings stack.
 *
 * Two flow rules from FR-005:
 *   - **Continuous gesture inputs** (`pointerInput { detectTransformGestures { ... } }`) call
 *     `viewModel.onDrag/onPinch` directly. They're not commands; ADR-0005's
 *     `MoonExplorerActions` deliberately doesn't include them.
 *   - **Discrete commands** (search result tap, "Center on this site") flow through
 *     `MoonExplorerActions`. The impl serialises side-effecting calls with a Mutex so a
 *     concurrent Phase-3 Koog tool dispatch can't race the UI.
 *
 * Sheet state machine: About → AboutSheet. AboutSheet's "Settings" row closes About and
 * opens SettingsSheet (sequential, never stacked). LocationInfoSheet is independent —
 * triggered by a search-result tap.
 */
@Composable
fun MoonExplorerScreen(
    storage: StorageDir,
    modifier: Modifier = Modifier,
) {
    val viewModel = remember { MoonViewModel() }
    val http = remember { createMoonHttpClient() }
    val loader = remember(storage, http, viewModel) {
        MoonAssetLoader(storage = storage, http = http, viewModel = viewModel)
    }
    LaunchedEffect(loader) { loader.loadInto() }

    // T224 — site catalog + actions surface (ADR-0005). loadBundled is suspending, so the
    // action impl flips from null to non-null once the JSON parse completes (a frame or two
    // after first composition). Search/Center handlers null-guard until then.
    var actions: MoonExplorerActions? by remember { mutableStateOf(null) }
    LaunchedEffect(viewModel) {
        val catalog = SiteCatalog.loadBundled()
        actions = MoonExplorerActionsImpl(viewModel = viewModel, catalog = catalog)
    }
    val scope = rememberCoroutineScope()

    val state = viewModel.state.collectAsState().value
    var viewportHeightPx by remember { mutableStateOf(0) }

    var aboutSheetVisible by remember { mutableStateOf(false) }
    var settingsSheetVisible by remember { mutableStateOf(false) }

    var searchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf(emptyList<MoonSite>()) }
    var infoSheetSite: MoonSite? by remember { mutableStateOf(null) }

    // Re-run search whenever the query changes (or when actions becomes non-null after the
    // catalog finishes loading). MoonExplorerActionsImpl.searchMoonLocations is read-only
    // and skips the Mutex, so this can fire freely on every keystroke.
    LaunchedEffect(searchQuery, actions) {
        searchResults = actions?.searchMoonLocations(searchQuery) ?: emptyList()
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        MoonViewport(
            state = state,
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { size -> viewportHeightPx = size.height }
                .pointerInput(Unit) {
                    detectTransformGestures(panZoomLock = false) { _, pan, zoom, _ ->
                        if (pan != Offset.Zero && viewportHeightPx > 0) {
                            viewModel.onDrag(
                                dxPx = pan.x,
                                dyPx = pan.y,
                                viewportHpx = viewportHeightPx,
                                fovYRad = DEFAULT_FOV_Y_RAD,
                            )
                        }
                        if (zoom != 1f) {
                            viewModel.onPinch(zoom)
                        }
                    }
                },
        )
        SunControl(
            value = state.sunDirection.x,
            onValueChange = { x -> viewModel.setSunDirection(joystickToHemisphereDir(x)) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp),
        )
        IconButton(
            onClick = { aboutSheetVisible = true },
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(8.dp),
        ) {
            Text(
                text = "ⓘ",
                color = Color.White,
                fontSize = 28.sp,
                textAlign = TextAlign.Center,
            )
        }
        SearchBar(
            expanded = searchExpanded,
            onExpandedChange = { value ->
                searchExpanded = value
                if (!value) searchQuery = ""  // collapse clears the field per the edge-cases section
            },
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            results = searchResults,
            onResultTap = { site ->
                infoSheetSite = site
                searchExpanded = false
                searchQuery = ""
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(8.dp),
        )
    }

    val site = infoSheetSite
    if (site != null) {
        LocationInfoSheet(
            site = site,
            onCenterClick = {
                actions?.let { a ->
                    // flyToMoonLocation is suspend; dispatch through the screen's scope so
                    // the click handler returns immediately. The Mutex inside the impl
                    // serialises against any other concurrent action.
                    scope.launch { a.flyToMoonLocation(site.id) }
                }
            },
            onDismissRequest = { infoSheetSite = null },
        )
    }

    if (aboutSheetVisible) {
        AboutSheet(
            onDismissRequest = { aboutSheetVisible = false },
            onSettingsClick = {
                aboutSheetVisible = false
                settingsSheetVisible = true
            },
        )
    }

    if (settingsSheetVisible) {
        SettingsSheet(onDismissRequest = { settingsSheetVisible = false })
    }
}
