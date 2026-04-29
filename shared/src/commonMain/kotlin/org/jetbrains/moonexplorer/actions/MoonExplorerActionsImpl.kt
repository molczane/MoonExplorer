package org.jetbrains.moonexplorer.actions

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.moonexplorer.domain.MoonSite
import org.jetbrains.moonexplorer.domain.SiteCatalog
import org.jetbrains.moonexplorer.domain.Vec3
import org.jetbrains.moonexplorer.domain.greatCircleDistKm
import org.jetbrains.moonexplorer.domain.latLonToYawPitch
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
 * Deferred methods (per FR-007):
 *   - [setLightingPreset] returns `ActionAck(ok = false, …)` until the lighting work lands.
 *   - [compareLocations] returns the geodesic distance only; richer comparison deferred.
 *   - [flyToMoonLocation] ignores `durationMs` (snap-to). `03-sites-and-flyto` adds the lerp.
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
        // 01-app-shell ships the snap path; 03-sites-and-flyto reads `durationMs` for the lerp.
        val (yaw, pitch) = latLonToYawPitch(site.lat, site.lon)
        viewModel.setCameraTarget(yaw, pitch)
        ActionAck(ok = true, message = "centered on ${site.name}")
    }

    override suspend fun setLightingPreset(preset: LightingPreset): ActionAck =
        ActionAck(ok = false, message = "lighting preset ${preset.name} deferred to a future spec")

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
}
