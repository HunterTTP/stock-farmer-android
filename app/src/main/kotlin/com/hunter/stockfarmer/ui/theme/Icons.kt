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

    val chartLineUptrend =
        icon("chart.line.uptrend.xyaxis") {
            stroked(2f) {
                moveTo(3f, 3.5f)
                lineTo(3f, 20f)
                lineTo(21f, 20f)
            }
            stroked(2.2f) {
                moveTo(6.5f, 15.5f)
                lineTo(11f, 10.5f)
                lineTo(14f, 13.5f)
                lineTo(20f, 6.5f)
                moveTo(15.5f, 6.5f)
                lineTo(20f, 6.5f)
                lineTo(20f, 11f)
            }
        }

    val wifiSlash =
        icon("wifi.slash") {
            stroked(2f) {
                moveTo(2.5f, 9.2f)
                arcTo(14f, 14f, 0f, false, true, 8.2f, 5.7f)
                moveTo(21.5f, 9.2f)
                arcTo(14f, 14f, 0f, false, false, 15.8f, 5.7f)
                moveTo(5.6f, 12.8f)
                arcTo(9.5f, 9.5f, 0f, false, true, 8.6f, 10.8f)
                moveTo(18.4f, 12.8f)
                arcTo(9.5f, 9.5f, 0f, false, false, 15.4f, 10.8f)
                moveTo(9f, 16.2f)
                arcTo(4.8f, 4.8f, 0f, false, true, 11f, 15f)
                moveTo(15f, 16.2f)
                arcTo(4.8f, 4.8f, 0f, false, false, 13f, 15f)
                moveTo(3.5f, 3.5f)
                lineTo(20.5f, 20.5f)
            }
            filled { circle(12f, 19.4f, 1.5f) }
        }

    val magnifyingglass =
        icon("magnifyingglass") {
            stroked(2.2f) {
                circle(10.5f, 10.5f, 6.5f)
                moveTo(15.4f, 15.4f)
                lineTo(20.5f, 20.5f)
            }
        }

    val xmarkCircleFill =
        icon("xmark.circle.fill") {
            filled(PathFillType.EvenOdd) {
                circle(12f, 12f, 10f)
                polygon(
                    listOf(
                        8.1f to 9.5f,
                        9.5f to 8.1f,
                        12f to 10.6f,
                        14.5f to 8.1f,
                        15.9f to 9.5f,
                        13.4f to 12f,
                        15.9f to 14.5f,
                        14.5f to 15.9f,
                        12f to 13.4f,
                        9.5f to 15.9f,
                        8.1f to 14.5f,
                        10.6f to 12f,
                    ),
                )
            }
        }

    val forwardFill =
        icon("forward.fill") {
            filled {
                polygon(listOf(2.5f to 5.5f, 12f to 12f, 2.5f to 18.5f))
                polygon(listOf(12f to 5.5f, 21.5f to 12f, 12f to 18.5f))
            }
        }

    val xmark =
        icon("xmark") {
            stroked(3f) {
                moveTo(5.5f, 5.5f)
                lineTo(18.5f, 18.5f)
                moveTo(18.5f, 5.5f)
                lineTo(5.5f, 18.5f)
            }
        }

    val playFill =
        icon("play.fill") { filled { polygon(listOf(6f to 3.5f, 20f to 12f, 6f to 20.5f)) } }

    val pauseFill =
        icon("pause.fill") {
            filled {
                polygon(listOf(5f to 3.5f, 10f to 3.5f, 10f to 20.5f, 5f to 20.5f))
                polygon(listOf(14f to 3.5f, 19f to 3.5f, 19f to 20.5f, 14f to 20.5f))
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

    val musicNote =
        icon("music.note") {
            filled {
                circle(8f, 18f, 3.6f)
                polygon(listOf(10.6f to 3.5f, 12.6f to 3.5f, 12.6f to 18f, 10.6f to 18f))
                polygon(listOf(12.6f to 3.5f, 18.5f to 7f, 18.5f to 10.5f, 12.6f to 7.5f))
            }
        }

    val speakerWave2Fill =
        icon("speaker.wave.2.fill") {
            filled { polygon(listOf(2.5f to 9f, 6.5f to 9f, 12f to 4.5f, 12f to 19.5f, 6.5f to 15f, 2.5f to 15f)) }
            stroked(2f) {
                moveTo(15f, 9f)
                arcTo(4f, 4f, 0f, false, true, 15f, 15f)
                moveTo(17.8f, 6.2f)
                arcTo(7.5f, 7.5f, 0f, false, true, 17.8f, 17.8f)
            }
        }

    val leafFill =
        icon("leaf.fill") {
            filled {
                moveTo(4f, 20f)
                curveTo(4f, 10f, 10f, 4f, 20f, 4f)
                curveTo(20f, 14f, 14f, 20f, 4f, 20f)
                close()
            }
        }

    val pawprintFill =
        icon("pawprint.fill") {
            filled {
                circle(6.2f, 10.2f, 2.2f)
                circle(10f, 6f, 2.2f)
                circle(14f, 6f, 2.2f)
                circle(17.8f, 10.2f, 2.2f)
                moveTo(12f, 11.5f)
                curveTo(8.5f, 11.5f, 6f, 15f, 6.5f, 17.5f)
                curveTo(7f, 19.5f, 9.5f, 19f, 12f, 19f)
                curveTo(14.5f, 19f, 17f, 19.5f, 17.5f, 17.5f)
                curveTo(18f, 15f, 15.5f, 11.5f, 12f, 11.5f)
                close()
            }
        }

    val cloudRainFill =
        icon("cloud.rain.fill") {
            filled {
                circle(7.5f, 11f, 3.5f)
                circle(12.5f, 8.5f, 4.5f)
                circle(17.5f, 11f, 3.5f)
                polygon(listOf(7.5f to 7.5f, 17.5f to 7.5f, 17.5f to 14.5f, 7.5f to 14.5f))
            }
            stroked(1.7f) {
                moveTo(8f, 17.5f)
                lineTo(7f, 20.5f)
                moveTo(12.5f, 17.5f)
                lineTo(11.5f, 20.5f)
                moveTo(17f, 17.5f)
                lineTo(16f, 20.5f)
            }
        }

    val sunMaxFill =
        icon("sun.max.fill") {
            filled { circle(12f, 12f, 4.6f) }
            stroked(2f) {
                repeat(8) {
                    val angle = it * PI / 4
                    moveTo((12 + 7.6 * cos(angle)).toFloat(), (12 + 7.6 * sin(angle)).toFloat())
                    lineTo((12 + 10.6 * cos(angle)).toFloat(), (12 + 10.6 * sin(angle)).toFloat())
                }
            }
        }

    val grid =
        icon("grid") {
            stroked(1.8f) {
                polygon(listOf(4f to 4f, 20f to 4f, 20f to 20f, 4f to 20f))
                moveTo(9.3f, 4f)
                lineTo(9.3f, 20f)
                moveTo(14.7f, 4f)
                lineTo(14.7f, 20f)
                moveTo(4f, 9.3f)
                lineTo(20f, 9.3f)
                moveTo(4f, 14.7f)
                lineTo(20f, 14.7f)
            }
        }

    /** A ring with a dollar sign; the SF Symbol is a filled disc with the sign cut out. */
    val dollarsignCircle =
        icon("dollarsign.circle.fill") {
            stroked(1.8f) { circle(12f, 12f, 9.2f) }
            stroked(1.8f) {
                moveTo(14.8f, 9.2f)
                curveTo(14.8f, 7.8f, 13.6f, 7.2f, 12f, 7.2f)
                curveTo(10.2f, 7.2f, 9.2f, 8.1f, 9.2f, 9.3f)
                curveTo(9.2f, 10.6f, 10.4f, 11.2f, 12f, 11.7f)
                curveTo(13.8f, 12.2f, 14.9f, 12.9f, 14.9f, 14.5f)
                curveTo(14.9f, 15.8f, 13.6f, 16.8f, 12f, 16.8f)
                curveTo(10.4f, 16.8f, 9.2f, 16.1f, 9.1f, 14.8f)
                moveTo(12f, 5.8f)
                lineTo(12f, 7.2f)
                moveTo(12f, 16.8f)
                lineTo(12f, 18.2f)
            }
        }

    val arrowUpRight =
        icon("arrow.up.right") {
            stroked(2.4f) {
                moveTo(7f, 17f)
                lineTo(17f, 7f)
                moveTo(8.5f, 7f)
                lineTo(17f, 7f)
                lineTo(17f, 15.5f)
            }
        }

    val lineThreeHorizontalDecreaseCircle =
        icon("line.3.horizontal.decrease.circle") {
            stroked(1.8f) { circle(12f, 12f, 9.2f) }
            stroked(1.8f) {
                moveTo(7f, 9f)
                lineTo(17f, 9f)
                moveTo(9f, 12f)
                lineTo(15f, 12f)
                moveTo(11f, 15f)
                lineTo(13f, 15f)
            }
        }

    val lineThreeHorizontalDecreaseCircleFill =
        icon("line.3.horizontal.decrease.circle.fill") {
            filled(PathFillType.EvenOdd) {
                circle(12f, 12f, 10f)
                polygon(listOf(6.8f to 8.1f, 17.2f to 8.1f, 17.2f to 9.9f, 6.8f to 9.9f))
                polygon(listOf(8.8f to 11.1f, 15.2f to 11.1f, 15.2f to 12.9f, 8.8f to 12.9f))
                polygon(listOf(10.8f to 14.1f, 13.2f to 14.1f, 13.2f to 15.9f, 10.8f to 15.9f))
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
