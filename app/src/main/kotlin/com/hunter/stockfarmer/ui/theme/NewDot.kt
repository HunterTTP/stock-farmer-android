package com.hunter.stockfarmer.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

private val dotDiameter = 10.dp

private fun DrawScope.drawDot(center: Offset) {
    val radius = dotDiameter.toPx() / 2
    drawCircle(Color.Red, radius, center)
    drawCircle(Theme.panelBorder, radius - 0.5.dp.toPx(), center, style = Stroke(1.dp.toPx()))
}

/** The little red dot marking something unlocked but not yet picked. */
@Composable
fun NewDot(modifier: Modifier = Modifier) {
    Canvas(modifier.size(dotDiameter).semantics { contentDescription = "New" }) { drawDot(center) }
}

/** Overlays a [NewDot] on the content, at [alignment] nudged by [offset]. */
fun Modifier.newDot(
    isShown: Boolean,
    alignment: Alignment = Alignment.TopEnd,
    offset: DpOffset = DpOffset.Zero,
): Modifier =
    if (!isShown) {
        this
    } else {
        drawWithContent {
            drawContent()
            val diameter = dotDiameter.toPx().toInt()
            val place =
                alignment.align(
                    IntSize(diameter, diameter),
                    IntSize(size.width.toInt(), size.height.toInt()),
                    LayoutDirection.Ltr,
                )
            drawDot(Offset(place.x + diameter / 2f + offset.x.toPx(), place.y + diameter / 2f + offset.y.toPx()))
        }
    }
