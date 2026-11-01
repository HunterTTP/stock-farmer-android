package com.hunter.stockfarmer.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Vector stand-ins for the SF Symbols the iOS HUD uses, drawn on a 24 unit grid and tinted at the call site. The pixel-art
 * pictures (crops, buildings, animals) are not here: they are sprite sheets, drawn by `ui.screens.ScreenIcon`.
 */
object HudIcons {
    private fun icon(
        name: String,
        build: ImageVector.Builder.() -> Unit,
    ): ImageVector = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(build).build()

    private fun ImageVector.Builder.filled(
        fillType: PathFillType = PathFillType.NonZero,
        block: PathBuilder.() -> Unit,
    ) = path(fill = SolidColor(Color.Black), pathFillType = fillType, pathBuilder = block)

    private fun ImageVector.Builder.stroked(
        width: Float,
        block: PathBuilder.() -> Unit,
    ) = path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = width,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = block,
    )

    private fun PathBuilder.circle(
        cx: Float,
        cy: Float,
        r: Float,
    ) {
        moveTo(cx - r, cy)
        arcTo(r, r, 0f, true, false, cx + r, cy)
        arcTo(r, r, 0f, true, false, cx - r, cy)
        close()
    }

    private fun PathBuilder.polygon(points: List<Pair<Float, Float>>) {
        moveTo(points.first().first, points.first().second)
        points.drop(1).forEach { lineTo(it.first, it.second) }
        close()
    }

    val minus =
        icon("minus") {
            stroked(2.4f) {
                moveTo(5f, 12f)
                lineTo(19f, 12f)
            }
        }

    val plus =
        icon("plus") {
            stroked(2.4f) {
                moveTo(5f, 12f)
                lineTo(19f, 12f)
                moveTo(12f, 5f)
                lineTo(12f, 19f)
            }
        }

    val chevronRight =
        icon("chevron.right") {
            stroked(2.4f) {
                moveTo(9f, 5f)
                lineTo(16f, 12f)
                lineTo(9f, 19f)
            }
        }

    val infoCircle =
        icon("info.circle") {
            stroked(1.8f) { circle(12f, 12f, 9.2f) }
            stroked(2.2f) {
                moveTo(12f, 11f)
                lineTo(12f, 17f)
            }
            filled { circle(12f, 7.6f, 1.3f) }
        }

    val checkmarkCircleFill =
        icon("checkmark.circle.fill") {
            filled(PathFillType.EvenOdd) {
                circle(12f, 12f, 10f)
                polygon(listOf(7f to 12.2f, 8.4f to 10.8f, 10.6f to 13f, 15.6f to 8f, 17f to 9.4f, 10.6f to 15.8f))
            }
        }

    val lockFill =
        icon("lock.fill") {
            filled {
                moveTo(6f, 10.5f)
                lineTo(18f, 10.5f)
                arcTo(1.8f, 1.8f, 0f, false, true, 19.8f, 12.3f)
                lineTo(19.8f, 19.2f)
                arcTo(1.8f, 1.8f, 0f, false, true, 18f, 21f)
                lineTo(6f, 21f)
                arcTo(1.8f, 1.8f, 0f, false, true, 4.2f, 19.2f)
                lineTo(4.2f, 12.3f)
                arcTo(1.8f, 1.8f, 0f, false, true, 6f, 10.5f)
                close()
            }
            stroked(2.2f) {
                moveTo(7.8f, 10.5f)
                lineTo(7.8f, 7.5f)
                arcTo(4.2f, 4.2f, 0f, false, true, 16.2f, 7.5f)
                lineTo(16.2f, 10.5f)
            }
        }

    val starFill =
        icon("star.fill") {
            filled {
                polygon(
                    (0 until 10).map {
                        val radius = if (it % 2 == 0) 10.5f else 4.4f
                        val angle = -PI / 2 + it * PI / 5
                        (12 + radius * cos(angle)).toFloat() to (12.8 + radius * sin(angle)).toFloat()
                    },
                )
            }
        }

    val bagFill =
        icon("bag.fill") {
            filled {
                polygon(listOf(5f to 8f, 19f to 8f, 20.4f to 20.5f, 3.6f to 20.5f))
            }
            stroked(2f) {
                moveTo(8.6f, 10f)
                lineTo(8.6f, 7f)
                arcTo(3.4f, 3.4f, 0f, false, true, 15.4f, 7f)
                lineTo(15.4f, 10f)
            }
        }

    val sparkle =
        icon("sparkle") {
            filled {
                moveTo(12f, 2f)
                quadTo(12.9f, 11.1f, 22f, 12f)
                quadTo(12.9f, 12.9f, 12f, 22f)
                quadTo(11.1f, 12.9f, 2f, 12f)
                quadTo(11.1f, 11.1f, 12f, 2f)
                close()
            }
        }
}

/** Draws a [HudIcons] vector in one color at a size. */
@Composable
fun HudIcon(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    contentDescription: String? = null,
) {
    Image(icon, contentDescription, modifier.size(size), colorFilter = ColorFilter.tint(tint))
}

private val butterflyPattern = listOf("WW...WW", "WWW.WWW", "WWWBWWW", ".WWBWW.", "..WBW..")
private val shiftingWings = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color(0.5f, 0f, 0.5f))

/** A butterfly like the ones on the farm: a black body and wings in one color, or all the hues for a [wing] of null. */
@Composable
fun ButterflyIcon(
    wing: Color?,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.aspectRatio(7f / 5f)) {
        val cell = minOf(size.width / 7, size.height / 5)
        val origin = Offset((size.width - cell * 7) / 2, (size.height - cell * 5) / 2)
        val wingBrush =
            wing?.let { SolidColor(it) }
                ?: Brush.linearGradient(shiftingWings, origin, Offset(origin.x + cell * 7, origin.y + cell * 5))
        butterflyPattern.forEachIndexed { row, line ->
            line.forEachIndexed { column, mark ->
                if (mark == '.') return@forEachIndexed
                val topLeft = Offset(origin.x + column * cell, origin.y + row * cell)
                if (mark == 'B') {
                    drawRect(Color(0.12f, 0.12f, 0.12f), topLeft, Size(cell, cell))
                } else {
                    drawRect(wingBrush, topLeft, Size(cell, cell))
                }
            }
        }
    }
}
