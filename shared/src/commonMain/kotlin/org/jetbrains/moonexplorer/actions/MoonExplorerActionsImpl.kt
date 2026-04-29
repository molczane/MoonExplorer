package org.jetbrains.moonexplorer.actions

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.time.TimeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.moonexplorer.domain.MoonSite
import org.jetbrains.moonexplorer.domain.SiteCatalog
import org.jetbrains.moonexplorer.domain.Vec3
import org.jetbrains.moonexplorer.domain.easeInOutCubic
import org.jetbrains.moonexplorer.domain.greatCircleDistKm
import org.jetbrains.moonexplorer.domain.latLonToYawPitch
import org.jetbrains.moonexplorer.domain.lerpSunDirection
import org.jetbrains.moonexplorer.domain.lightingPresetSunDir
import org.jetbrains.moonexplorer.domain.shortestYawDelta
import org.jetbrains.moonexplorer.domain.yawPitchToLatLon
import org.jetbrains.moonexplorer.state.MoonViewModel

/**
 * Default `MoonExplorerActions` impl. T211 / 01-app-shell.
 *
 * Side-effecting methods are serialised by a single [Mutex] per ADR-0005 — Phase-3 Koog's
 * `toParallelToolCallsRaw` can dispatch multiple state-mutating tool calls in parallel,
 * and we don't want to trust the LLM to serialise on our behalf. Read-only methods
 * (`searchMoonLocations`, `getCurrentView`, `explainCurrentView`) are parallel-safe and
 * skip the lock.
 *
 * Animation is opt-in via `durationMs > 0`:
 *   - [flyToMoonLocation] animates camera yaw/pitch over `durationMs` ms with cubic ease-in-out
 *     (T321 / 03-sites-and-flyto). `durationMs = 0` keeps the snap escape hatch.
 *   - [setLightingPreset] animates `sunDirection` to the preset target via `lerpSunDirection`
 *     (lat/lon lerp + `shortestYawDelta`) with cubic ease-in-out, default 500 ms (T431 /
 *     04-sun-control). `durationMs = 0` snaps. The deferred-stub `ok = false` from 01-shell
 *     is gone.
 *   - [setSunDirection] is snap-only — animation is opt-in via the preset path.
 *   - [compareLocations] returns the geodesic distance only; richer comparison deferred.
 */
class MoonExplorerActionsImpl(
    private val viewModel: MoonViewModel,
    private val catalog: SiteCatalog,
) : MoonExplorerActions {

    private val mutex = Mutex()

    override suspend fun searchMoonLocations(query: String, limit: Int): List<MoonSite> =
        catalog.search(query, limit)

    override suspend fun getCurrentView(): CurrentView {
        val s = viewModel.state.value
        val (cameraLat, cameraLon) = yawPitchToLatLon(s.cameraYawRad, s.cameraPitchRad)
        val sd = s.sunDirection
        // Sun direction is a unit vector in the same selenographic frame as latLonToCartesian:
        // x = cos(lat) sin(lon), y = sin(lat), z = cos(lat) cos(lon).
        val sunLat = asin(sd.y.toDouble().coerceIn(-1.0, 1.0)) * 180.0 / PI
        val sunLon = atan2(sd.x.toDouble(), sd.z.toDouble()) * 180.0 / PI
        return CurrentView(
            cameraLat = cameraLat,
            cameraLon = cameraLon,
            zoom = s.cameraDistance,
            sunLat = sunLat,
            sunLon = sunLon,
            highlightedSiteId = s.highlightedSiteId,
        )
    }

    override suspend fun explainCurrentView(): String {
        val v = getCurrentView()
        val nearest = catalog.all.minByOrNull {
            greatCircleDistKm(v.cameraLat, v.cameraLon, it.lat, it.lon)
        }
        val nearestLabel = nearest?.let { "near ${it.name}" } ?: "over unmarked terrain"
        return "Camera $nearestLabel · sun lon ${formatDeg(v.sunLon)}"
    }

    override suspend fun flyToMoonLocation(id: String, durationMs: Long): ActionAck = mutex.withLock {
        val site = catalog.byId(id)
            ?: return@withLock ActionAck(ok = false, message = "no such site: $id")
        val (targetYaw, targetPitch) = latLonToYawPitch(site.lat, site.lon)

        if (durationMs <= 0L) {
            // Snap path (FR-009) — preserves T212's snap-path expectations and gives callers an
            // immediate-positioning escape hatch.
            viewModel.setCameraTarget(targetYaw, targetPitch)
            return@withLock ActionAck(ok = true, message = "centered on ${site.name}")
        }

        // T321 / 03-sites-and-flyto — animated path. Cubic ease-in-out, shorter-yaw-path.
        // The loop's delay() is cancellable; the screen-level `currentFlyJob.cancel()` is what
        // interrupts a fly-to mid-animation so a subsequent fly can start cleanly. The Mutex's
        // withLock guarantees we either finish or unwind via the cancellation finally before
        // the next caller acquires.
        val start = viewModel.state.value
        val startYaw = start.cameraYawRad
        val startPitch = start.cameraPitchRad
        val yawDelta = shortestYawDelta(startYaw, targetYaw)
        val pitchDelta = targetPitch - startPitch

        val source = TimeSource.Monotonic.markNow()
        while (true) {
            val elapsedMs = source.elapsedNow().inWholeMilliseconds
            val t = (elapsedMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            val eased = easeInOutCubic(t)
            viewModel.setCameraTarget(
                yawRad = startYaw + yawDelta * eased,
                pitchRad = startPitch + pitchDelta * eased,
            )
            if (t >= 1f) break
            delay(FRAME_MS)
        }
        ActionAck(ok = true, message = "centered on ${site.name}")
    }

    override suspend fun setLightingPreset(
        preset: LightingPreset,
        durationMs: Long,
    ): ActionAck = mutex.withLock {
        val target = lightingPresetSunDir(preset)

        if (durationMs <= 0L) {
            // Snap path — preserves a test escape hatch and gives external callers an
            // immediate-positioning option.
            viewModel.setSunDirection(target)
            return@withLock ActionAck(ok = true, message = "lighting set to ${preset.name}")
        }

        // T431 / 04-sun-control — animated path. lat/lon lerp + cubic ease-in-out, identical
        // control flow to flyToMoonLocation. The screen-level `currentLightingJob.cancel()` is
        // what interrupts a preset animation mid-flight; the loop's delay() is cancellable so
        // the unwind is clean and the next preset call acquires the Mutex from the current
        // (interrupted) state.
        val start = viewModel.state.value.sunDirection
        val source = TimeSource.Monotonic.markNow()
        while (true) {
            val elapsedMs = source.elapsedNow().inWholeMilliseconds
            val t = (elapsedMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            val eased = easeInOutCubic(t)
            viewModel.setSunDirection(lerpSunDirection(start, target, eased))
            if (t >= 1f) break
            delay(FRAME_MS)
        }
        ActionAck(ok = true, message = "lighting set to ${preset.name}")
    }

    override suspend fun setSunDirection(lat: Double, lon: Double): ActionAck = mutex.withLock {
        val phi = lat * PI / 180.0
        val lambda = lon * PI / 180.0
        val cp = cos(phi)
        viewModel.setSunDirection(
            Vec3(
                x = (cp * sin(lambda)).toFloat(),
                y = sin(phi).toFloat(),
                z = (cp * cos(lambda)).toFloat(),
            ),
        )
        ActionAck(ok = true, message = "sun moved to ${formatDeg(lat)}, ${formatDeg(lon)}")
    }

    override suspend fun highlightLocation(id: String, on: Boolean): ActionAck = mutex.withLock {
        val site = catalog.byId(id)
            ?: return@withLock ActionAck(ok = false, message = "no such site: $id")
        viewModel.highlightLocation(if (on) id else null)
        val verb = if (on) "highlighted" else "cleared highlight on"
        ActionAck(ok = true, message = "$verb ${site.name}")
    }

    override suspend fun compareLocations(id1: String, id2: String): ComparisonResult {
        val a = catalog.byId(id1)
            ?: error("no such site: $id1")
        val b = catalog.byId(id2)
            ?: error("no such site: $id2")
        return ComparisonResult(
            a = a,
            b = b,
            distanceKm = greatCircleDistKm(a.lat, a.lon, b.lat, b.lon),
            notes = "great-circle distance only — richer comparison deferred to a future spec",
        )
    }

    private fun formatDeg(value: Double): String {
        // One decimal place, no float-format jvm-only calls so this works on K/N too.
        val tenths = (value * 10.0).toInt()
        val whole = tenths / 10
        val frac = if (tenths < 0) -tenths % 10 else tenths % 10
        return "$whole.$frac°"
    }

    private companion object {
        /** ~60 FPS frame budget for the animated fly-to loop. T321. */
        const val FRAME_MS: Long = 16L
    }
}
