package com.hunter.stockfarmer.ui.hud

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.hunter.stockfarmer.ui.screens.lessonPulse
import com.hunter.stockfarmer.ui.theme.LocalHudScale
import com.hunter.stockfarmer.ui.theme.darkGlass
import com.hunter.stockfarmer.ui.theme.newDot
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The round tool icon: tap it to change tools, or drag it over the farm to use the tool (iOS `ToolCircle`). All positions the
 * callbacks see are window coordinates in dp.
 */
@Composable
fun ToolCircle(
    title: String,
    tool: ToolCircleTool,
    pulses: Boolean,
    isNew: Boolean,
    throbTrigger: Int,
    showsTile: (Offset) -> Boolean,
    onTap: () -> Unit,
    onMove: (to: Offset, phase: ToolDragPhase) -> Unit,
    onDrag: (from: Offset, to: Offset) -> Unit,
    onHold: () -> Unit,
    onDragEnded: () -> Unit,
    modifier: Modifier = Modifier,
    onDraggingChanged: (Boolean) -> Unit = {},
    icon: @Composable () -> Unit,
) {
    val scale = LocalHudScale.current
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    val currentTool by rememberUpdatedState(tool)
    val currentScale by rememberUpdatedState(scale)
    val currentShowsTile by rememberUpdatedState(showsTile)
    val currentTap by rememberUpdatedState(onTap)
    val currentMove by rememberUpdatedState(onMove)
    val currentDrag by rememberUpdatedState(onDrag)
    val currentHold by rememberUpdatedState(onHold)
    val currentEnded by rememberUpdatedState(onDragEnded)
    val gesture =
        remember {
            ToolCircleGesture(
                scale = { currentScale },
                tool = { currentTool },
                showsTile = { currentShowsTile(it) },
                schedule = { millis, action ->
                    val job: Job =
                        scope.launch {
                            delay(millis)
                            action()
                        }
                    return@ToolCircleGesture { job.cancel() }
                },
                onTap = { currentTap() },
                onMove = { to, phase -> currentMove(to, phase) },
                onDrag = { from, to -> currentDrag(from, to) },
                onHold = { currentHold() },
                onDragEnded = { currentEnded() },
            )
        }
    DisposableEffect(gesture) { onDispose { gesture.cancel() } }

    var restOrigin by remember { mutableStateOf(Offset.Zero) }
    var restCenter by remember { mutableStateOf<Offset?>(null) }
    val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var isDragging by remember { mutableStateOf(false) }
    var shownThrob by remember { mutableStateOf(throbTrigger) }
    if (!isDragging && shownThrob != throbTrigger) shownThrob = throbTrigger
    val currentDragging by rememberUpdatedState(onDraggingChanged)
    LaunchedEffect(isDragging) { currentDragging(isDragging) }

    Box(
        modifier
            .zIndex(1f)
            .size((ToolCircleMetrics.DIAMETER * scale).dp)
            .onGloballyPositioned {
                restOrigin = it.positionInWindow()
                restCenter = (restOrigin + Offset(it.size.width / 2f, it.size.height / 2f)) / density
            }.pointerInput(gesture) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val start = (restOrigin + down.position) / density
                    var first = true
                    while (true) {
                        val change = awaitPointerEventChange(down.id) ?: break
                        if (change.changedToUpIgnoreConsumed()) {
                            gesture.end()
                            isDragging = false
                            scope.launch { offset.animateTo(Offset.Zero, spring(1f, 438f)) }
                            break
                        }
                        if (!change.pressed) {
                            gesture.cancel()
                            isDragging = false
                            scope.launch { offset.animateTo(Offset.Zero, spring(1f, 438f)) }
                            break
                        }
                        val location = (restOrigin + change.position) / density
                        if (gesture.change(start, location)) {
                            change.consume()
                            isDragging = true
                            // The circle centers under the finger, even if it was grabbed by its edge.
                            val target = location - (restCenter ?: start)
                            if (first) {
                                first = false
                                scope.launch { offset.animateTo(target, tween(100, easing = LinearOutSlowInEasing)) }
                            } else {
                                scope.launch { offset.snapTo(target) }
                            }
                        }
                    }
                }
            }.semantics {
                contentDescription = title
                role = Role.Button
                onClick("Change tools") {
                    onTap()
                    true
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size((ToolCircleMetrics.DIAMETER * scale).dp)
                .graphicsLayer {
                    translationX = offset.value.x * density
                    translationY = offset.value.y * density
                    val grown = if (isDragging) ToolCircleMetrics.DRAGGED_SCALE else 1f
                    scaleX = grown
                    scaleY = grown
                    alpha = if (isDragging) ToolCircleMetrics.DRAGGED_OPACITY else 1f
                }.throb(shownThrob)
                .lessonPulse(pulses && !isDragging)
                .newDot(isNew, Alignment.TopEnd, DpOffset((-4).dp, 4.dp))
                .then(
                    if (isDragging) {
                        Modifier
                    } else {
                        Modifier.shadow(
                            ToolCircleMetrics.SHADOW_RADIUS.dp,
                            CircleShape,
                            clip = false,
                            ambientColor = Color.Black.copy(alpha = ToolCircleMetrics.SHADOW_ALPHA),
                            spotColor = Color.Black.copy(alpha = ToolCircleMetrics.SHADOW_ALPHA),
                        )
                    },
                ).darkGlass(CircleShape)
                .padding((ToolCircleMetrics.ICON_PADDING * scale).dp),
            contentAlignment = Alignment.Center,
        ) { icon() }
    }
}

private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.awaitPointerEventChange(
    id: androidx.compose.ui.input.pointer.PointerId,
) = awaitPointerEvent().changes.firstOrNull { it.id == id }
