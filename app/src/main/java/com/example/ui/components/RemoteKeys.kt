package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.KeyBottom
import com.example.ui.theme.KeyBottomPressed
import com.example.ui.theme.KeyIcon
import com.example.ui.theme.KeyIconMuted
import com.example.ui.theme.KeyRim
import com.example.ui.theme.KeyTop
import com.example.ui.theme.KeyTopPressed
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

val KeyShape = RoundedCornerShape(28.dp)
val KeyHeight: Dp = 66.dp

/**
 * Press handling shared by all keys.
 * Normal keys fire on release; [repeatOnHold] keys fire on press and repeat while held
 * (volume, channel). A press that turns into a page scroll is cancelled and doesn't fire.
 */
private fun Modifier.keyPress(
    repeatOnHold: Boolean,
    onPressedChange: (Boolean) -> Unit,
    onTrigger: () -> Unit
): Modifier = pointerInput(repeatOnHold) {
    coroutineScope {
        awaitEachGesture {
            awaitFirstDown()
            onPressedChange(true)
            val repeatJob = if (repeatOnHold) {
                onTrigger()
                launch {
                    delay(420)
                    while (isActive) {
                        onTrigger()
                        delay(110)
                    }
                }
            } else null
            val up = waitForUpOrCancellation()
            repeatJob?.cancel()
            onPressedChange(false)
            if (!repeatOnHold && up != null) onTrigger()
        }
    }
}

private fun keyBrush(pressed: Boolean) = Brush.verticalGradient(
    if (pressed) listOf(KeyTopPressed, KeyBottomPressed) else listOf(KeyTop, KeyBottom)
)

private fun Modifier.keySurface(shape: Shape, pressed: Boolean, elevation: Dp = 8.dp): Modifier = this
    .shadow(
        elevation = if (pressed) elevation / 3 else elevation,
        shape = shape,
        ambientColor = Color.Black.copy(alpha = 0.30f),
        spotColor = Color.Black.copy(alpha = 0.45f)
    )
    .clip(shape)
    .background(keyBrush(pressed))
    .border(1.dp, KeyRim, shape)

/** A single graphite key. */
@Composable
fun RemoteKey(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    shape: Shape = KeyShape,
    repeatOnHold: Boolean = false,
    testTag: String = "",
    content: @Composable BoxScope.() -> Unit
) {
    val currentOnClick by rememberUpdatedState(onClick)
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, label = "keyScale")

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .keySurface(shape, pressed)
            .keyPress(repeatOnHold, { pressed = it }) { currentOnClick() }
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
                this.onClick(label = null, action = { currentOnClick(); true })
            }
            .testTag(testTag),
        contentAlignment = Alignment.Center,
        content = content
    )
}

/** Convenience: key with a single icon. */
@Composable
fun IconKey(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 26.dp,
    tint: Color = KeyIcon,
    shape: Shape = KeyShape,
    testTag: String = ""
) {
    RemoteKey(
        onClick = onClick,
        contentDescription = contentDescription,
        modifier = modifier,
        shape = shape,
        testTag = testTag
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/**
 * Two keys sharing one body (e.g. VOL + / −). Each half repeats while held.
 * [vertical] = true stacks first above second; false places first left of second.
 */
@Composable
fun RockerKey(
    firstIcon: ImageVector,
    firstDescription: String,
    onFirst: () -> Unit,
    secondIcon: ImageVector,
    secondDescription: String,
    onSecond: () -> Unit,
    modifier: Modifier = Modifier,
    vertical: Boolean = true,
    label: String? = null,
    firstTestTag: String = "",
    secondTestTag: String = ""
) {
    var firstPressed by remember { mutableStateOf(false) }
    var secondPressed by remember { mutableStateOf(false) }
    val anyPressed = firstPressed || secondPressed
    val scale by animateFloatAsState(if (anyPressed) 0.98f else 1f, label = "rockerScale")

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .keySurface(KeyShape, false),
        contentAlignment = Alignment.Center
    ) {
        if (vertical) {
            Column(Modifier.fillMaxWidth().fillMaxHeight()) {
                RockerHalf(firstIcon, firstDescription, onFirst, firstPressed, { firstPressed = it }, firstTestTag, Modifier.weight(1f).fillMaxWidth())
                RockerHalf(secondIcon, secondDescription, onSecond, secondPressed, { secondPressed = it }, secondTestTag, Modifier.weight(1f).fillMaxWidth())
            }
        } else {
            Row(Modifier.fillMaxWidth().fillMaxHeight()) {
                RockerHalf(firstIcon, firstDescription, onFirst, firstPressed, { firstPressed = it }, firstTestTag, Modifier.weight(1f).fillMaxHeight())
                RockerHalf(secondIcon, secondDescription, onSecond, secondPressed, { secondPressed = it }, secondTestTag, Modifier.weight(1f).fillMaxHeight())
            }
        }
        if (label != null) {
            Text(
                text = label,
                color = KeyIconMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
private fun RockerHalf(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    pressed: Boolean,
    onPressedChange: (Boolean) -> Unit,
    testTag: String,
    modifier: Modifier
) {
    val currentOnClick by rememberUpdatedState(onClick)
    Box(
        modifier = modifier
            .background(if (pressed) Color.White.copy(alpha = 0.08f) else Color.Transparent)
            .keyPress(repeatOnHold = true, onPressedChange = onPressedChange) { currentOnClick() }
            .semantics {
                role = Role.Button
                contentDescription = description
                this.onClick(label = null, action = { currentOnClick(); true })
            }
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = KeyIcon, modifier = Modifier.size(26.dp))
    }
}
