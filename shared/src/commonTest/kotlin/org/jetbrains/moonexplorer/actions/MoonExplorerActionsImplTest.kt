package org.jetbrains.moonexplorer.actions

import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.jetbrains.moonexplorer.domain.MoonSite
import org.jetbrains.moonexplorer.domain.SiteCatalog
import org.jetbrains.moonexplorer.domain.SiteType
import org.jetbrains.moonexplorer.state.MoonViewModel

/**
 * T212 — exercises [MoonExplorerActionsImpl] against a real [MoonViewModel] + a hardcoded
 * [SiteCatalog]. Verifies the full action surface that 01-app-shell implements (deferred
 * methods are tested for their stub behaviour) plus the Mutex's serialisation guarantee.
 */
class MoonExplorerActionsImplTest {

    private fun newActions(): Pair<MoonViewModel, MoonExplorerActionsImpl> {
        val vm = MoonViewModel()
        val catalog = SiteCatalog(SAMPLE_SITES)
        return vm to MoonExplorerActionsImpl(viewModel = vm, catalog = catalog)
    }

    @Test
    fun searchMoonLocations_findsBySubstring() = runBlocking {
        val (_, actions) = newActions()
        val results = actions.searchMoonLocations("tych")
        assertEquals(1, results.size)
        assertEquals("tycho", results[0].id)
    }

    @Test
    fun flyToMoonLocation_advancesCameraToSiteCoords() = runBlocking {
        val (vm, actions) = newActions()
        val ack = actions.flyToMoonLocation("tycho")
        assertTrue(ack.ok, "ack should be ok=true, got $ack")
        // Tycho: lat = -43.31°, lon = -11.36° → pitch = lat·rad, yaw = lon·rad.
        val expectedYaw = (-11.36 * PI / 180.0).toFloat()
        val expectedPitch = (-43.31 * PI / 180.0).toFloat()
        assertWithinTolerance(expectedYaw, vm.state.value.cameraYawRad, 1e-4f, "yaw")
        assertWithinTolerance(expectedPitch, vm.state.value.cameraPitchRad, 1e-4f, "pitch")
    }

    @Test
    fun flyToMoonLocation_unknownIdReturnsAckFalse() = runBlocking {
        val (vm, actions) = newActions()
        val initialYaw = vm.state.value.cameraYawRad
        val ack = actions.flyToMoonLocation("garbage-id")
        assertFalse(ack.ok)
        assertTrue("garbage-id" in ack.message, "message should mention the bad id, got '${ack.message}'")
        // State unchanged — the early-return path doesn't touch the viewmodel.
        assertEquals(initialYaw, vm.state.value.cameraYawRad)
    }

    @Test
    fun getCurrentView_roundTripsAfterFlyTo() = runBlocking {
        val (_, actions) = newActions()
        actions.flyToMoonLocation("tycho")
        val view = actions.getCurrentView()
        assertWithinTolerance(-43.31, view.cameraLat, 1e-3, "cameraLat")
        assertWithinTolerance(-11.36, view.cameraLon, 1e-3, "cameraLon")
    }

    @Test
    fun setSunDirection_writesUnitVector() = runBlocking {
        val (vm, actions) = newActions()
        val ack = actions.setSunDirection(lat = 0.0, lon = 90.0)  // east horizon
        assertTrue(ack.ok)
        val sun = vm.state.value.sunDirection
        // (lat=0, lon=90°) → (cos(0)*sin(90°), sin(0), cos(0)*cos(90°)) = (1, 0, 0).
        assertWithinTolerance(1.0f, sun.x, 1e-5f, "sun.x")
        assertWithinTolerance(0.0f, sun.y, 1e-5f, "sun.y")
        assertWithinTolerance(0.0f, sun.z, 1e-5f, "sun.z")
    }

    @Test
    fun highlightLocation_updatesAndClearsState() = runBlocking {
        val (vm, actions) = newActions()
        actions.highlightLocation("tycho", on = true)
        assertEquals("tycho", vm.state.value.highlightedSiteId)
        actions.highlightLocation("tycho", on = false)
        assertEquals(null, vm.state.value.highlightedSiteId)
    }

    @Test
    fun setLightingPreset_returnsDeferredStub() = runBlocking {
        val (_, actions) = newActions()
        val ack = actions.setLightingPreset(LightingPreset.Day)
        assertFalse(ack.ok, "deferred stub should return ok=false")
        assertTrue("deferred" in ack.message, "message should mention deferral")
    }

    @Test
    fun compareLocations_computesGeodesicDistance() = runBlocking {
        val (_, actions) = newActions()
        val result = actions.compareLocations("tycho", "apollo_11")
        assertEquals("tycho", result.a.id)
        assertEquals("apollo_11", result.b.id)
        // Tycho (-43.31°, -11.36°) ↔ Apollo 11 (0.67°, 23.47°): rough geodesic ≈ 1700 km on the Moon.
        assertTrue(
            result.distanceKm > 1500.0 && result.distanceKm < 2000.0,
            "expected ~1700 km, got ${result.distanceKm}",
        )
    }

    @Test
    fun concurrentFlyTo_serializesViaMutex() = runBlocking {
        val (vm, actions) = newActions()
        // Both calls run on a worker pool; the Mutex enforces serial state mutation. Order is
        // unspecified, but both must complete with ok=true and the final state must match one
        // of the two targets exactly (no torn write).
        val acks = coroutineScope {
            listOf(
                async(Dispatchers.Default) { actions.flyToMoonLocation("tycho") },
                async(Dispatchers.Default) { actions.flyToMoonLocation("apollo_11") },
            ).awaitAll()
        }
        assertTrue(acks.all { it.ok }, "both flyTo calls should succeed: $acks")

        val finalYaw = vm.state.value.cameraYawRad
        val tychoYaw = (-11.36 * PI / 180.0).toFloat()
        val apolloYaw = (23.47 * PI / 180.0).toFloat()
        assertTrue(
            withinTolerance(finalYaw, tychoYaw, 1e-4f) ||
                withinTolerance(finalYaw, apolloYaw, 1e-4f),
            "final yaw should match Tycho or Apollo 11 exactly, got $finalYaw",
        )
    }
}

private fun assertWithinTolerance(expected: Float, actual: Float, tol: Float, label: String) {
    assertTrue(withinTolerance(expected, actual, tol), "$label: expected $expected ± $tol, got $actual")
}

private fun assertWithinTolerance(expected: Double, actual: Double, tol: Double, label: String) {
    assertTrue(abs(expected - actual) <= tol, "$label: expected $expected ± $tol, got $actual")
}

private fun withinTolerance(a: Float, b: Float, tol: Float): Boolean = abs(a - b) <= tol

private val SAMPLE_SITES: List<MoonSite> = listOf(
    MoonSite(
        id = "tycho",
        name = "Tycho",
        subtitle = null,
        lat = -43.31, lon = -11.36,
        type = SiteType.CRATER,
        description = "Bright ray crater.",
    ),
    MoonSite(
        id = "apollo_11",
        name = "Apollo 11",
        subtitle = "Tranquillity Base",
        lat = 0.67, lon = 23.47,
        type = SiteType.LANDING_SITE,
        description = "First crewed lunar landing.",
    ),
    MoonSite(
        id = "mare_imbrium",
        name = "Mare Imbrium",
        subtitle = "Sea of Showers",
        lat = 32.8, lon = -15.6,
        type = SiteType.MARE,
        description = "Largest visible mare.",
    ),
)
