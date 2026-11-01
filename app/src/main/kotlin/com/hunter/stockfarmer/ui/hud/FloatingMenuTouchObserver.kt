package com.hunter.stockfarmer.ui.hud

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The places on screen a touch counts as inside the open ring or list, so it leaves them open.
 * Touches on the farm close the menus through `GameSession.farmTouches`; this covers touches on the other HUD controls.
 */
@Stable
class MenuInteractionRegions {
    private val regions = mutableMapOf<Any, Rect>()

    fun set(
        key: Any,
        rect: Rect?,
    ) {
        if (rect == null) regions.remove(key) else regions[key] = rect
    }

    fun isOutside(point: Offset): Boolean = regions.values.none { it.contains(point) }
}

/** Registers this node's window bounds, grown by [padding], as inside the menus while [isEnabled]. */
fun Modifier.menuInteractionRegion(
    regions: MenuInteractionRegions,
    key: Any,
    padding: Dp = 0.dp,
    isEnabled: Boolean = true,
): Modifier =
    composed {
        val margin = LocalDensity.current.run { padding.toPx() }
        if (!isEnabled) regions.set(key, null)
        onGloballyPositioned { if (isEnabled) regions.set(key, it.boundsInWindow().inflate(margin)) }
    }

/** Calls [onOutsideTouch] when a finger goes down outside every region, without taking the touch away from the control. */
fun Modifier.observesOutsideTouches(
    isEnabled: Boolean,
    regions: MenuInteractionRegions,
    onOutsideTouch: () -> Unit,
): Modifier =
    composed {
        var origin by remember { mutableStateOf(Offset.Zero) }
        val latest by rememberUpdatedState(onOutsideTouch)
        onGloballyPositioned { origin = it.positionInWindow() }.pointerInput(isEnabled, regions) {
            if (!isEnabled) return@pointerInput
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val down = event.changes.firstOrNull { it.pressed && !it.previousPressed } ?: continue
                    if (regions.isOutside(origin + down.position)) latest()
                }
            }
        }
    }
