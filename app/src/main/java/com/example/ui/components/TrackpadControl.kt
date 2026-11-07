package com.example.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.KeyBottom
import com.example.ui.theme.KeyBottomPressed
import com.example.ui.theme.KeyIconMuted
import com.example.ui.theme.KeyRim
import com.example.ui.theme.KeyTop
import com.example.ui.theme.KeyTopPressed
import kotlin.math.roundToInt

private val PadShape = RoundedCornerShape(36.dp)

/**
 * Paints the trackpad as one big graphite key (same face gradient as [RemoteKey]) that is
 * "inflated": the centre bulges up and catches the light, while all four sides slope down
 * into shadow, the bottom slope deepest since the light comes from above.
 */
private fun DrawScope.drawPillowSurface(pressed: Boolean) {
    val w = size.width
    val h = size.height
    val bevel = size.minDimension * 0.24f

    // Key face, identical to the keys below the pad
    drawRect(
        Brush.verticalGradient(
            if (pressed) listOf(KeyTopPressed, KeyBottomPressed) else listOf(KeyTop, KeyBottom)
        )
    )

    // Dome highlight: the crown of the cushion, slightly above centre
    drawRect(
        Brush.radialGradient(
            0f to Color.White.copy(alpha = if (pressed) 0.10f else 0.16f),
            0.45f to Color.White.copy(alpha = 0.05f),
            1f to Color.Transparent,
            center = Offset(w / 2f, h * 0.44f),
            radius = size.minDimension * 0.58f
        )
    )

    // Sides falling away from the crown
    drawRect(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.34f), Color.Transparent), startX = 0f, endX = bevel))
    drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.34f)), startX = w - bevel, endX = w))
    drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.18f), Color.Transparent), startY = 0f, endY = bevel))
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.46f)), startY = h - bevel, endY = h))

    // Thin specular rim along the top edge, like light grazing a rounded key
    drawRect(
        Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.14f), Color.Transparent),
            startY = 0f,
            endY = 6.dp.toPx()
        )
    )
}

/**
 * Full-size Magic Remote trackpad:
 *  - one-finger drag = move the TV pointer
 *  - tap             = click
 *  - two-finger drag = scroll
 */
@Composable
fun TrackpadControl(
    onPointerMove: (dx: Float, dy: Float) -> Unit,
    onPointerClick: () -> Unit,
    onScroll: (dy: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val pointerMove by rememberUpdatedState(onPointerMove)
    val pointerClick by rememberUpdatedState(onPointerClick)
    val scroll by rememberUpdatedState(onScroll)

    var pressed by remember { mutableStateOf(false) }

    val elevation by animateDpAsState(if (pressed) 10.dp else 22.dp, label = "padElevation")

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .shadow(
                elevation = elevation,
                shape = PadShape,
                ambientColor = Color.Black.copy(alpha = 0.30f),
                spotColor = Color.Black.copy(alpha = 0.45f)
            )
            .clip(PadShape)
            .drawBehind { drawPillowSurface(pressed) }
            .border(1.dp, KeyRim, PadShape)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    val slop = viewConfiguration.touchSlop
                    val scrollStep = 6.dp.toPx()
                    var total = Offset.Zero
                    var dragging = false
                    var scrolling = false
                    var scrollAcc = 0f

                    while (true) {
                        val event = awaitPointerEvent()
                        val active = event.changes.filter { it.pressed }

                        if (active.isEmpty()) {
                            if (!dragging && !scrolling) pointerClick()
                            break
                        }

                        if (active.size >= 2) {
                            // Two fingers: scroll by the average vertical movement
                            scrolling = true
                            val dy = active.map { it.positionChange().y }.average().toFloat()
                            active.forEach { it.consume() }
                            scrollAcc += dy
                            if (kotlin.math.abs(scrollAcc) >= scrollStep) {
                                val amount = scrollAcc.roundToInt()
                                scroll(amount.toFloat())
                                scrollAcc -= amount
                            }
                            continue
                        }

                        // Once a gesture became a scroll, lifting one finger doesn't turn it into a move.
                        if (scrolling) continue

                        val change = active.firstOrNull { it.id == down.id } ?: active.first()
                        val delta = change.positionChange()
                        total += delta
                        if (!dragging && total.getDistance() > slop) dragging = true
                        if (dragging && (delta.x != 0f || delta.y != 0f)) {
                            change.consume()
                            pointerMove(delta.x, delta.y)
                        }
                    }
                    pressed = false
                }
            }
            .semantics {
                contentDescription =
                    "Trackpad. Drag to move the pointer, tap to click, drag with two fingers to scroll."
            }
            .testTag("trackpad"),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Outlined.TouchApp,
                contentDescription = null,
                tint = KeyIconMuted.copy(alpha = 0.75f),
                modifier = Modifier.size(34.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Drag to move · Tap to click\nTwo fingers to scroll",
                color = KeyIconMuted.copy(alpha = 0.85f),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
    }
}
