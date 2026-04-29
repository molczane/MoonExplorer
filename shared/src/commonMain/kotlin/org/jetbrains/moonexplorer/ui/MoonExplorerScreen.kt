package org.jetbrains.moonexplorer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import kotlinx.coroutines.Job
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
import org.jetbrains.moonexplorer.domain.joystickToSunDir
import org.jetbrains.moonexplorer.render.MoonViewport
import org.jetbrains.moonexplorer.state.MoonViewModel
import org.jetbrains.moonexplorer.ui.theme.MoonExplorerTheme
// MarkerOverlay is in the same package — no import needed.

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
    MoonExplorerTheme {
        MoonExplorerScreenContent(storage = storage, modifier = modifier)
    }
}

/**
 * Inner content split out from [MoonExplorerScreen] so the [MoonExplorerTheme] wrapper is
 * a pure pass-through composable — every descendant reads `MaterialTheme.colorScheme.*` etc.
 * already wired by the wrapper. T506 / 05-modern-theme.
 */
@Composable
private fun MoonExplorerScreenContent(
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
    // catalog and action impl flip from null to non-null once the JSON parse completes (a
    // frame or two after first composition). Search/Center handlers + MarkerOverlay (T311)
    // null-guard until then. The catalog is hoisted alongside `actions` so MarkerOverlay can
    // read `catalog.all` directly without round-tripping through MoonExplorerActions.
    var catalog: SiteCatalog? by remember { mutableStateOf(null) }
    var actions: MoonExplorerActions? by remember { mutableStateOf(null) }
    LaunchedEffect(viewModel) {
        val c = SiteCatalog.loadBundled()
        catalog = c
        actions = MoonExplorerActionsImpl(viewModel = viewModel, catalog = c)
    }
    val scope = rememberCoroutineScope()

    val state = viewModel.state.collectAsState().value
    var viewportHeightPx by remember { mutableStateOf(0) }

    var aboutSheetVisible by remember { mutableStateOf(false) }
    var settingsSheetVisible by remember { mutableStateOf(false) }

    // T323 — track the in-flight fly-to so a new tap can cancel a prior animation cleanly.
    // Each cancel() lets the actions impl unwind via cancellable delay() inside the Mutex'd
    // loop; the new fly-to then acquires the Mutex and starts from the *current* state, which
    // gives the user the "fluid hand-off" feel from US2 instead of waiting for the prior 1.5 s
    // animation to finish.
    var currentFlyJob: Job? by remember { mutableStateOf<Job?>(null) }

    // T433 / 04-sun-control — the same cancel-then-launch tracker for the animated
    // setLightingPreset path. Declared here so the screen owns the Job lifetime; the
    // SunPanel's onPresetTap callback calls cancel + relaunch. Independent of
    // currentFlyJob so a fly-to and a sun-preset transition can run concurrently —
    // each cancels only its own track on a new tap.
    var currentLightingJob: Job? by remember { mutableStateOf<Job?>(null) }

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
        // T311 — site markers. Overlay sits above the viewport but only the marker dots
        // consume taps; bare overlay area falls through to the viewport's gesture detector.
        // While the catalog is still loading, the let-block doesn't render anything.
        catalog?.let { c ->
            MarkerOverlay(
                sites = c.all,
                cameraYawRad = state.cameraYawRad,
                cameraPitchRad = state.cameraPitchRad,
                cameraDistance = state.cameraDistance,
                highlightedSiteId = state.highlightedSiteId,
                onMarkerTap = { id -> infoSheetSite = c.byId(id) },
                modifier = Modifier.fillMaxSize(),
            )
        }
        // T440 / 04-sun-control. Replaces 01-shell's 1-axis SunControl slider with the
        // 2D joystick + 4-preset grid. Two side-effect tracks per spec.md / plan.md:
        //   • Joystick drag = continuous gesture → viewModel.setSunDirection direct
        //     (mirrors onDrag/onPinch from 02-mvp; bypasses MoonExplorerActions because
        //     gestures aren't commands).
        //   • Preset tap = discrete command → MoonExplorerActions.setLightingPreset
        //     animates over 500 ms; currentLightingJob.cancel-then-launch interrupts a
        //     prior in-flight animation cleanly so the new tap starts from the *current*
        //     state (US3's "fluid hand-off" feel).
        SunPanel(
            sunDirection = state.sunDirection,
            onJoystickDrag = { x, y ->
                viewModel.setSunDirection(joystickToSunDir(x, y))
            },
            onPresetTap = { preset ->
                actions?.let { a ->
                    currentLightingJob?.cancel()
                    currentLightingJob = scope.launch { a.setLightingPreset(preset) }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
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
                    // T323 — cancel any in-flight fly-to before kicking off the new one. The
                    // Mutex inside the impl still serialises against any other concurrent
                    // action, but cancellation makes the hand-off snappy: the prior delay()
                    // throws CancellationException, the loop unwinds, the Mutex unlocks, and
                    // the new fly-to acquires it without waiting out the full 1.5 s.
                    currentFlyJob?.cancel()
                    currentFlyJob = scope.launch { a.flyToMoonLocation(site.id) }
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
        // T732 / 07-celestial-background — the SettingsSheet placeholder from 01-shell
        // finally gets its first real toggles. Both bind directly to MoonRenderState
        // flags via viewModel setters; the renderer hosts read these per frame and
        // attach/detach the Skybox + sun Renderable + bloom config accordingly.
        SettingsSheet(
            showStars = state.showStars,
            showSun = state.showSun,
            onShowStarsChange = viewModel::setShowStars,
            onShowSunChange = viewModel::setShowSun,
            onDismissRequest = { settingsSheetVisible = false },
        )
    }
}
