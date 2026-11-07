package com.example.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.model.Direction
import com.example.model.RemoteMode
import com.example.ui.theme.KeyBottom
import com.example.ui.theme.KeyRim
import com.example.ui.theme.KeyTop
import com.example.ui.theme.PadBottom
import com.example.ui.theme.PadEdge
import com.example.ui.theme.PadTop
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max

/** Inner trackpad diameter as a fraction of the outer ring diameter. */
private const val INNER_RATIO = 0.70f

/**
 * Circular remote control in the style of a modern TV remote:
 *  - Outer ring: tap a side (marked by a dot) to send UP/DOWN/LEFT/RIGHT; hold to repeat.
 *  - Inner trackpad:
 *      [RemoteMode.DPAD]     swipe = arrow keys (one step per ~44dp), tap = OK
 *      [RemoteMode.TOUCHPAD] drag  = move the Magic Remote pointer,   tap = click
 */
@Composable
fun TrackpadControl(
    mode: RemoteMode,
    onDirection: (Direction) -> Unit,
    onSelect: () -> Unit,
    onPointerMove: (dx: Float, dy: Float) -> Unit,
    onPointerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentMode by rememberUpdatedState(mode)
    val direction by rememberUpdatedState(onDirection)
    val select by rememberUpdatedState(onSelect)
    val pointerMove by rememberUpdatedState(onPointerMove)
    val pointerClick by rememberUpdatedState(onPointerClick)

    var activeDirection by remember { mutableStateOf<Direction?>(null) }
    var padPressed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .shadow(
                elevation = 22.dp,
                shape = CircleShape,
                ambientColor = Color.Black.copy(alpha = 0.35f),
                spotColor = Color.Black.copy(alpha = 0.55f)
            )
            .drawBehind {
                val outerR = size.minDimension / 2f
                val innerR = outerR * INNER_RATIO
                val midR = (outerR + innerR) / 2f
                val ringWidth = outerR - innerR
                val c = center

                // Outer ring
                drawCircle(
                    brush = Brush.verticalGradient(
                        colors = listOf(KeyTop, KeyBottom),
                        startY = c.y - outerR,
                        endY = c.y + outerR
                    ),
                    radius = outerR,
                    center = c
                )
                drawCircle(
                    color = KeyRim,
                    radius = outerR - 0.5.dp.toPx(),
                    center = c,
                    style = Stroke(width = 1.dp.toPx())
                )

                // Highlight the quadrant being pressed / swiped
                activeDirection?.let { dir ->
                    drawArc(
                        color = Color.White.copy(alpha = 0.08f),
                        startAngle = arcStart(dir),
                        sweepAngle = 90f,
                        useCenter = false,
                        topLeft = Offset(c.x - midR, c.y - midR),
                        size = Size(midR * 2f, midR * 2f),
                        style = Stroke(width = ringWidth)
                    )
                }

                // Inner trackpad (darker at the top so it reads as recessed)
                drawCircle(
                    brush = Brush.verticalGradient(
                        colors = listOf(PadTop, PadBottom),
                        startY = c.y - innerR,
                        endY = c.y + innerR
                    ),
                    radius = innerR,
                    center = c
                )
                if (padPressed) {
                    drawCircle(color = Color.White.copy(alpha = 0.05f), radius = innerR, center = c)
                }
                drawCircle(
                    color = PadEdge,
                    radius = innerR,
                    center = c,
                    style = Stroke(width = 1.5.dp.toPx())
                )

                // Direction dots
                val dotR = 3.dp.toPx()
                Direction.entries.forEach { dir ->
                    val active = dir == activeDirection
                    drawCircle(
                        color = if (active) Color.White else Color.White.copy(alpha = 0.85f),
                        radius = if (active) dotR * 1.6f else dotR,
                        center = c + unitVector(dir) * midR
                    )
                }
            }
            .pointerInput(Unit) {
                coroutineScope {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val c = Offset(size.width / 2f, size.height / 2f)
                        val outerR = minOf(size.width, size.height) / 2f
                        val innerR = outerR * INNER_RATIO
                        val fromCenter = down.position - c
                        val dist = fromCenter.getDistance()
                        if (dist > outerR) return@awaitEachGesture

                        if (dist > innerR) {
                            // ---- Ring: directional key, repeats while held ----
                            val dir = directionOf(fromCenter)
                            activeDirection = dir
                            direction(dir)
                            val repeat = launch {
                                delay(420)
                                while (isActive) {
                                    direction(dir)
                                    delay(120)
                                }
                            }
                            waitForUpOrCancellation()
                            repeat.cancel()
                            activeDirection = null
                            return@awaitEachGesture
                        }

                        // ---- Inner trackpad ----
                        padPressed = true
                        val slop = viewConfiguration.touchSlop
                        val swipeStep = 44.dp.toPx()
                        var total = Offset.Zero
                        var swipe = Offset.Zero
                        var dragging = false

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break

                            if (!change.pressed) {
                                if (!dragging) {
                                    if (currentMode == RemoteMode.TOUCHPAD) pointerClick() else select()
                                }
                                break
                            }

                            val delta = change.positionChange()
                            total += delta
                            if (!dragging && total.getDistance() > slop) dragging = true
                            if (!dragging) continue

                            change.consume()
                            if (currentMode == RemoteMode.TOUCHPAD) {
                                if (delta.x != 0f || delta.y != 0f) pointerMove(delta.x, delta.y)
                            } else {
                                swipe += delta
                                val ax = abs(swipe.x)
                                val ay = abs(swipe.y)
                                if (max(ax, ay) >= swipeStep) {
                                    val dir = if (ax > ay) {
                                        if (swipe.x > 0) Direction.RIGHT else Direction.LEFT
                                    } else {
                                        if (swipe.y > 0) Direction.DOWN else Direction.UP
                                    }
                                    activeDirection = dir
                                    direction(dir)
                                    swipe = Offset.Zero
                                }
                            }
                        }
                        padPressed = false
                        activeDirection = null
                    }
                }
            }
            .semantics {
                contentDescription = if (mode == RemoteMode.TOUCHPAD) {
                    "Trackpad. Drag to move the pointer, tap to click. Tap the ring edges for arrow keys."
                } else {
                    "Trackpad. Swipe for arrow keys, tap to select. Tap the ring edges for arrow keys."
                }
            }
            .testTag("trackpad")
    )
}

private fun directionOf(v: Offset): Direction {
    // Screen coordinates: y grows downwards, so angles run clockwise from 3 o'clock.
    val angle = Math.toDegrees(atan2(v.y.toDouble(), v.x.toDouble()))
    return when {
        angle >= -45 && angle < 45 -> Direction.RIGHT
        angle >= 45 && angle < 135 -> Direction.DOWN
        angle >= -135 && angle < -45 -> Direction.UP
        else -> Direction.LEFT
    }
}

private fun unitVector(dir: Direction): Offset = when (dir) {
    Direction.UP -> Offset(0f, -1f)
    Direction.DOWN -> Offset(0f, 1f)
    Direction.LEFT -> Offset(-1f, 0f)
    Direction.RIGHT -> Offset(1f, 0f)
}

private fun arcStart(dir: Direction): Float = when (dir) {
    Direction.RIGHT -> -45f
    Direction.DOWN -> 45f
    Direction.LEFT -> 135f
    Direction.UP -> 225f
}
