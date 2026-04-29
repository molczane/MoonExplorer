package org.jetbrains.moonexplorer.actions

import kotlinx.serialization.Serializable
import org.jetbrains.moonexplorer.domain.MoonSite

/**
 * The single command surface for both UI button taps and (Phase 3) Koog tool calls. T210.
 *
 * Locked per ADR-0005 — eight methods covering search / current view / explain /
 * fly-to / lighting preset / sun direction / highlight / compare. Continuous gesture
 * input (drag/pinch from `pointerInput`) deliberately *isn't* in this interface; that
 * stays direct on `MoonViewModel.onDrag/onPinch`. The interface is for discrete commands.
 *
 * **ADR-0005 amendments:**
 *   - 04-sun-control (T430, 2026-04-29): `setLightingPreset` gains a non-breaking
 *     `durationMs: Long = 500` default arg, mirroring the precedent set by
 *     `flyToMoonLocation(id, durationMs = 1500)` from 03-sites-and-flyto.
 *
 * Implementation status of the discrete commands:
 *   - `setLightingPreset` is animated end-to-end as of 04-sun-control (T431). Defaults
 *     to a 500 ms cubic-eased transition; pass `durationMs = 0` for the snap path.
 *   - `setSunDirection` stays snap-only — animation is opt-in via `setLightingPreset`.
 *   - `flyToMoonLocation` animates over `durationMs` ms (default 1500); 03-sites-and-flyto
 *     graduated this from 01-shell's snap-only stub.
 *   - `compareLocations` returns the geodesic distance only; richer comparison notes
 *     await a follow-up spec.
 */
interface MoonExplorerActions {

    // --- Read / pure (parallel-safe) --------------------------------------------------

    suspend fun searchMoonLocations(query: String, limit: Int = 10): List<MoonSite>
    suspend fun getCurrentView(): CurrentView
    suspend fun explainCurrentView(): String

    // --- Side-effecting (Mutex-serialised in the impl per ADR-0005) -------------------

    suspend fun flyToMoonLocation(id: String, durationMs: Long = 1500): ActionAck
    suspend fun setLightingPreset(preset: LightingPreset, durationMs: Long = 500): ActionAck
    suspend fun setSunDirection(lat: Double, lon: Double): ActionAck
    suspend fun highlightLocation(id: String, on: Boolean = true): ActionAck

    // --- Hybrid (returns data, may have UI side effect) -------------------------------

    suspend fun compareLocations(id1: String, id2: String): ComparisonResult
}

/** Snapshot of what the renderer is currently showing. Returned by [MoonExplorerActions.getCurrentView]. */
@Serializable
data class CurrentView(
    val cameraLat: Double,
    val cameraLon: Double,
    val zoom: Float,
    val sunLat: Double,
    val sunLon: Double,
    val highlightedSiteId: String?,
)

/** Generic acknowledgement for side-effecting commands. `ok = false` carries a human-readable reason. */
@Serializable
data class ActionAck(
    val ok: Boolean,
    val message: String = "",
)

/** Pre-canned lighting moods. Implementation lands in a future spec; this enum is locked here. */
@Serializable
enum class LightingPreset { Day, Night, Terminator, HighContrast }

/** Result of [MoonExplorerActions.compareLocations] — two sites + great-circle distance + free-form notes. */
@Serializable
data class ComparisonResult(
    val a: MoonSite,
    val b: MoonSite,
    val distanceKm: Double,
    val notes: String,
)
