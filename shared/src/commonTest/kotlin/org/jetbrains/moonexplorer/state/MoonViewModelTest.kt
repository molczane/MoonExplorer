package org.jetbrains.moonexplorer.state

import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.jetbrains.moonexplorer.domain.PITCH_LIMIT_RAD

/**
 * Behavior checks on MoonViewModel's gesture handlers. State mutations go
 * through `MutableStateFlow.update`, which is synchronous, so reading
 * `state.value` immediately after a call gives the new state.
 */
class MoonViewModelTest {

    // ---- T022: onPinch zoom + clamp ----

    @Test
    fun onPinch_zoomInDecreasesDistance() {
        val vm = MoonViewModel(MoonRenderState(cameraDistance = 10f))
        vm.onPinch(2f) // fingers spread → smaller distance
        assertEquals(5f, vm.state.value.cameraDistance)
    }

    @Test
    fun onPinch_zoomOutIncreasesDistance() {
        val vm = MoonViewModel(MoonRenderState(cameraDistance = 5f))
        vm.onPinch(0.5f) // fingers pinch → larger distance
        assertEquals(10f, vm.state.value.cameraDistance)
    }

    @Test
    fun onPinch_clampsAtMin() {
        val vm = MoonViewModel(MoonRenderState(cameraDistance = 5f))
        repeat(50) { vm.onPinch(2f) }
        assertEquals(MoonViewModel.MIN_DIST, vm.state.value.cameraDistance)
    }

    @Test
    fun onPinch_clampsAtMax() {
        val vm = MoonViewModel(MoonRenderState(cameraDistance = 5f))
        repeat(50) { vm.onPinch(0.5f) }
        assertEquals(MoonViewModel.MAX_DIST, vm.state.value.cameraDistance)
    }

    @Test
    fun onPinch_invalidScaleIsNoop() {
        val vm = MoonViewModel(MoonRenderState(cameraDistance = 5f))
        vm.onPinch(0f)
        vm.onPinch(-1f)
        assertEquals(5f, vm.state.value.cameraDistance)
    }

    // ---- T023: onDrag yaw / pitch + clamp ----

    @Test
    fun onDrag_rightDecreasesYaw() {
        val vm = MoonViewModel(MoonRenderState(cameraYawRad = 0f))
        vm.onDrag(dxPx = 100f, dyPx = 0f, viewportHpx = VIEWPORT_H, fovYRad = FOV_Y)
        assertTrue(
            vm.state.value.cameraYawRad < 0f,
            "yaw should decrease for rightward drag, got ${vm.state.value.cameraYawRad}",
        )
    }

    @Test
    fun onDrag_downIncreasesPitch() {
        val vm = MoonViewModel(MoonRenderState(cameraPitchRad = 0f))
        vm.onDrag(dxPx = 0f, dyPx = 100f, viewportHpx = VIEWPORT_H, fovYRad = FOV_Y)
        assertTrue(
            vm.state.value.cameraPitchRad > 0f,
            "pitch should increase for downward drag, got ${vm.state.value.cameraPitchRad}",
        )
    }

    @Test
    fun onDrag_pitchClampsAtPositiveLimit() {
        val vm = MoonViewModel(MoonRenderState(cameraPitchRad = 0f))
        repeat(100) {
            vm.onDrag(dxPx = 0f, dyPx = 1000f, viewportHpx = VIEWPORT_H, fovYRad = FOV_Y)
        }
        val pitch = vm.state.value.cameraPitchRad
        assertTrue(
            pitch in (PITCH_LIMIT_RAD - 1e-4f)..(PITCH_LIMIT_RAD + 1e-4f),
            "pitch should clamp at +PITCH_LIMIT_RAD (~89.4°), got $pitch",
        )
    }

    @Test
    fun onDrag_pitchClampsAtNegativeLimit() {
        val vm = MoonViewModel(MoonRenderState(cameraPitchRad = 0f))
        repeat(100) {
            vm.onDrag(dxPx = 0f, dyPx = -1000f, viewportHpx = VIEWPORT_H, fovYRad = FOV_Y)
        }
        val pitch = vm.state.value.cameraPitchRad
        assertTrue(
            pitch in (-PITCH_LIMIT_RAD - 1e-4f)..(-PITCH_LIMIT_RAD + 1e-4f),
            "pitch should clamp at -PITCH_LIMIT_RAD (~-89.4°), got $pitch",
        )
    }

    @Test
    fun onDrag_invalidViewportOrFovIsNoop() {
        val initial = MoonRenderState(cameraYawRad = 0.5f, cameraPitchRad = 0.3f)
        val vm = MoonViewModel(initial)
        vm.onDrag(100f, 100f, viewportHpx = 0, fovYRad = FOV_Y)
        vm.onDrag(100f, 100f, viewportHpx = -1, fovYRad = FOV_Y)
        vm.onDrag(100f, 100f, viewportHpx = VIEWPORT_H, fovYRad = 0f)
        assertEquals(0.5f, vm.state.value.cameraYawRad)
        assertEquals(0.3f, vm.state.value.cameraPitchRad)
    }

    // ---- T705 / 07-celestial-background: setShowStars ----

    @Test
    fun setShowStars_defaultIsTrue() {
        val vm = MoonViewModel()
        assertTrue(vm.state.value.showStars, "default showStars should be true")
    }

    @Test
    fun setShowStars_togglesState() {
        val vm = MoonViewModel()
        vm.setShowStars(false)
        assertFalse(vm.state.value.showStars, "after setShowStars(false), state.showStars should be false")
        vm.setShowStars(true)
        assertTrue(vm.state.value.showStars, "after setShowStars(true), state.showStars should be true")
    }

    // ---- T715 / 07-celestial-background: setShowSun ----

    @Test
    fun setShowSun_defaultIsTrue() {
        val vm = MoonViewModel()
        assertTrue(vm.state.value.showSun, "default showSun should be true")
    }

    @Test
    fun setShowSun_togglesState() {
        val vm = MoonViewModel()
        vm.setShowSun(false)
        assertFalse(vm.state.value.showSun, "after setShowSun(false), state.showSun should be false")
        vm.setShowSun(true)
        assertTrue(vm.state.value.showSun, "after setShowSun(true), state.showSun should be true")
    }

    companion object {
        private const val VIEWPORT_H: Int = 1000
        private val FOV_Y: Float = (PI / 3).toFloat() // 60°
    }
}
