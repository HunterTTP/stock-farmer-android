package com.hunter.stockfarmer.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

/** What a scroll bar needs to know about the list it sits beside, in pixels. */
data class ScrollMetrics(
    val offset: Float = 0f,
    val content: Float = 0f,
    val container: Float = 0f,
) {
    val overflows: Boolean get() = content > container + 1

    /** Length of the bar and how far down it sits. */
    fun bar(shortestBar: Float): Pair<Float, Float> {
        val length = max(shortestBar, container * container / content)
        val travel = max(0f, container - length)
        val progress = min(1f, max(0f, offset / max(1f, content - container)))
        return length to travel * progress
    }
}

object ScrollBar {
    val width = 4.dp
    val shortestBar = 36.dp
    val trailingPadding = 3.dp
    const val ALPHA = 0.5f
}

/**
 * A scrolling column with a thin bar down its right edge that stays up for as long as the list is too long to fit, instead of
 * fading a moment after scrolling like the system's.
 */
@Composable
fun BarScrollView(
    modifier: Modifier = Modifier,
    scrollsOnlyWhenNeeded: Boolean = false,
    state: ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    var container by remember { mutableIntStateOf(0) }
    val metrics = ScrollMetrics(state.value.toFloat(), (container + state.maxValue).toFloat(), container.toFloat())
    Box(modifier.onSizeChanged { container = it.height }) {
        Column(
            Modifier.verticalScroll(state, enabled = !scrollsOnlyWhenNeeded || metrics.overflows),
            content = content,
        )
        if (metrics.overflows) {
            Canvas(Modifier.fillMaxSize()) {
                val (length, top) = metrics.bar(ScrollBar.shortestBar.toPx())
                val width = ScrollBar.width.toPx()
                drawRoundRect(
                    Color.White.copy(alpha = ScrollBar.ALPHA),
                    Offset(size.width - width - ScrollBar.trailingPadding.toPx(), top),
                    Size(width, length),
                    CornerRadius(width / 2),
                )
            }
        }
    }
}
