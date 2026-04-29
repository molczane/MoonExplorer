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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.moonexplorer.domain.MoonSite
import org.jetbrains.moonexplorer.domain.SiteCatalog
import org.jetbrains.moonexplorer.domain.SiteType
import org.jetbrains.moonexplorer.state.MoonRenderState
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
    fun flyToMoonLocation_durationZero_snaps() = runBlocking {
        // FR-009 — durationMs = 0 takes the snap path. Same expectation as 01-shell's original
        // test (which used the default durationMs in a snap-only impl); 03-flyto's animated impl
        // preserves the snap path explicitly so callers + tests have an immediate-positioning
        // escape hatch.
        val (vm, actions) = newActions()
        val ack = actions.flyToMoonLocation("tycho", durationMs = 0L)
        assertTrue(ack.ok, "ack should be ok=true, got $ack")
        val expectedYaw = (-11.36 * PI / 180.0).toFloat()
        val expectedPitch = (-43.31 * PI / 180.0).toFloat()
        assertWithinTolerance(expectedYaw, vm.state.value.cameraYawRad, 1e-4f, "yaw")
        assertWithinTolerance(expectedPitch, vm.state.value.cameraPitchRad, 1e-4f, "pitch")
    }

    @Test
    fun flyToMoonLocation_animated_reachesTargetExactly() = runBlocking {
        val (vm, actions) = newActions()
        actions.flyToMoonLocation("tycho", durationMs = 50L)  // ~3 frames at 16 ms cadence
        // After completion, eased(1) = 1 → state must be exactly at target (within float tol).
        val expectedYaw = (-11.36 * PI / 180.0).toFloat()
        val expectedPitch = (-43.31 * PI / 180.0).toFloat()
        assertWithinTolerance(expectedYaw, vm.state.value.cameraYawRad, 1e-4f, "final yaw")
        assertWithinTolerance(expectedPitch, vm.state.value.cameraPitchRad, 1e-4f, "final pitch")
    }

    @Test
    fun flyToMoonLocation_animated_progressesMonotonically() = runBlocking {
        val (vm, actions) = newActions()
        // Apollo 11 at lat = +0.67°, lon = +23.47°. Start state (default MoonViewModel) is at
        // yaw = pitch = 0; deltas are both positive. Sample state mid-animation and assert
        // strict monotonic progression toward the target.
        val targetYaw = (23.47 * PI / 180.0).toFloat()
        val job = launch { actions.flyToMoonLocation("apollo_11", durationMs = 200L) }
        delay(80L)  // ~40% of duration; eased ≈ 0.35 with cubic curve
        val mid = vm.state.value
        job.join()
        val final = vm.state.value

        assertTrue(mid.cameraYawRad > 0f, "mid yaw should have advanced past 0, got ${mid.cameraYawRad}")
        assertTrue(mid.cameraYawRad < targetYaw, "mid yaw should be < target, got ${mid.cameraYawRad}")
        assertTrue(final.cameraYawRad > mid.cameraYawRad, "final yaw should be > mid, got mid=${mid.cameraYawRad} final=${final.cameraYawRad}")
        assertWithinTolerance(targetYaw, final.cameraYawRad, 1e-4f, "final yaw at target")
    }

    @Test
    fun flyToMoonLocation_cancelMidAnimation_leavesPartialState() = runBlocking {
        val (vm, actions) = newActions()
        val targetYaw = (23.47 * PI / 180.0).toFloat()
        val job = launch { actions.flyToMoonLocation("apollo_11", durationMs = 1000L) }
        delay(120L)  // ~12% through; cubic-eased progress ~0.07
        job.cancel()
        job.join()

        val finalYaw = vm.state.value.cameraYawRad
        assertTrue(finalYaw > 1e-4f, "yaw should have advanced past start (~0), got $finalYaw")
        assertTrue(finalYaw < targetYaw - 1e-3f, "yaw should NOT have reached target, got $finalYaw vs target $targetYaw")
    }

    @Test
    fun flyToMoonLocation_yawWrap_takesShortPath() = runBlocking {
        // Initial state at yaw ≈ +170°; target site at lon = -170° (= -2.967 rad). Shorter
        // angular path is +20° (eastward across the 180° wrap), not -340° (the long way).
        // Confirm intermediate yaw stays >= start (didn't go through 0°).
        val startYawRad = (170.0 * PI / 180.0).toFloat()
        val vm = MoonViewModel(MoonRenderState(cameraYawRad = startYawRad, cameraPitchRad = 0f))
        val catalog = SiteCatalog(
            listOf(
                MoonSite(
                    id = "far_west",
                    name = "Far West",
                    subtitle = null,
                    lat = 0.0, lon = -170.0,
                    type = SiteType.OTHER,
                    description = "Test site for yaw wrap.",
                ),
            ),
        )
        val actions = MoonExplorerActionsImpl(viewModel = vm, catalog = catalog)

        val job = launch { actions.flyToMoonLocation("far_west", durationMs = 200L) }
        delay(100L)  // mid-animation
        val midYaw = vm.state.value.cameraYawRad
        job.join()

        // Short path: start = +170°·rad, end = start + 20°·rad ≈ +3.316 rad (out of [-π, π]
        // but the renderer doesn't care — yaw is modular). Mid yaw should be >= start - tiny ε
        // (eased never goes below 0) and ≤ end + tiny ε.
        val endYawShortPath = startYawRad + (20.0 * PI / 180.0).toFloat()
        assertTrue(
            midYaw >= startYawRad - 1e-3f,
            "mid yaw should not regress past start (would mean long-way), got mid=$midYaw start=$startYawRad",
        )
        assertTrue(
            midYaw <= endYawShortPath + 1e-3f,
            "mid yaw should not exceed end (short-path), got mid=$midYaw end=$endYawShortPath",
        )
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
        // Both calls animate (durationMs = 30); the Mutex enforces serial state mutation, so
        // the second waits for the first to complete its full lerp before starting its own.
        // The final state must match one of the two targets exactly — no interleaved/torn write.
        val acks = coroutineScope {
            listOf(
                async(Dispatchers.Default) { actions.flyToMoonLocation("tycho", durationMs = 30L) },
                async(Dispatchers.Default) { actions.flyToMoonLocation("apollo_11", durationMs = 30L) },
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
