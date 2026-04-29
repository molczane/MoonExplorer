package org.jetbrains.moonexplorer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.jetbrains.moonexplorer.domain.Vec3

/**
 * 2D circular sun-joystick. T420 / 04-sun-control.
 *
 * The knob's render position is a function of [sunDirection] — projecting `(x, y)` onto
 * the disk in pixel space (the renderer-supplied unit vector's `x` and `y` are exactly
 * the disk-relative coordinates because the `joystickToSunDir` math defines them that
 * way). Y is flipped: Compose's screen Y grows downward while our world +Y is up.
 *
 * Drag handling uses `awaitEachGesture` + `awaitFirstDown` instead of `detectDragGestures`
 * so the knob jumps to the first touch position immediately — without the few-pixel drag
 * threshold a dragging detector would impose. Pointer events emit raw disk-relative
 * `(x, y)` to the caller; clamping outside the unit disk is the caller's job (it wraps
 * `onDrag` with `joystickToSunDir`, which clamps to the disk boundary with `z = 0`).
 *
 * The pad sits at the bottom of the screen and is finger-sized (120 dp). The knob is
 * 28 dp; the gesture math accounts for the knob radius so the knob's *edge* — not its
 * centre — meets the disk's edge at `|sunDirection| = 1`.
 */
@Composable
fun SunJoystick(
    sunDirection: Vec3,
    onDrag: (x: Float, y: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sizePx: IntSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .size(SUN_JOYSTICK_SIZE_DP)
            .onSizeChanged { sizePx = it }
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .border(width = 1.dp, color = Color.White.copy(alpha = 0.4f), shape = CircleShape)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    emitFromPosition(down.position, sizePx, onDrag)
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change: PointerInputChange =
                            event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        if (change.positionChanged()) {
                            emitFromPosition(change.position, sizePx, onDrag)
                            change.consume()
                        }
                    }
                }
            },
    ) {
        if (sizePx.width > 0 && sizePx.height > 0) {
            Box(
                modifier = Modifier
                    .offset {
                        val w = sizePx.width.toFloat()
                        val h = sizePx.height.toFloat()
                        val knobHalfPx = SUN_JOYSTICK_KNOB_DP.toPx() / 2f
                        // Knob centre travels in a disk of radius (diskRadius − knobRadius),
                        // so the knob's edge — not its centre — touches the outer disk edge
                        // when |sunDirection| = 1.
                        val travelR = (minOf(w, h) / 2f) - knobHalfPx
                        val knobCx = w / 2f + sunDirection.x * travelR
                        val knobCy = h / 2f - sunDirection.y * travelR  // Y-flip
                        IntOffset(
                            x = (knobCx - knobHalfPx).toInt(),
                            y = (knobCy - knobHalfPx).toInt(),
                        )
                    }
                    .size(SUN_JOYSTICK_KNOB_DP)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(
                        width = 1.dp,
                        color = Color.Black.copy(alpha = 0.55f),
                        shape = CircleShape,
                    ),
            )
        }
    }
}

/**
 * Convert a pointer position (in joystick-local pixels) to a disk-relative `(x, y)` in
 * approximately `[-1, 1]²` (saturating outside the disk; the caller clamps via
 * `joystickToSunDir`). The travel radius accounts for the knob's half-size so the user's
 * touch at the disk edge maps to `±1`, matching the knob's visual extreme.
 */
private fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.emitFromPosition(
    pos: Offset,
    sizePx: IntSize,
    onDrag: (Float, Float) -> Unit,
) {
    val w = sizePx.width.toFloat()
    val h = sizePx.height.toFloat()
    if (w <= 0f || h <= 0f) return
    val knobHalfPx = SUN_JOYSTICK_KNOB_DP.toPx() / 2f
    val travelR = (minOf(w, h) / 2f) - knobHalfPx
    if (travelR <= 0f) return
    val nx = (pos.x - w / 2f) / travelR
    val ny = -(pos.y - h / 2f) / travelR  // Y-flip: Compose y-down → our +Y up
    onDrag(nx, ny)
}

private val SUN_JOYSTICK_SIZE_DP = 120.dp
private val SUN_JOYSTICK_KNOB_DP = 28.dp
