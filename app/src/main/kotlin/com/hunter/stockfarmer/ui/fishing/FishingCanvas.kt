package com.hunter.stockfarmer.ui.fishing

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.hunter.stockfarmer.core.rules.DayNightCycle
import com.hunter.stockfarmer.core.rules.Season
import com.hunter.stockfarmer.core.rules.Weather
import com.hunter.stockfarmer.game.GameSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Instant
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot

private data class FishingArt(
    val fish: ImageBitmap?,
    val water: List<ImageBitmap>,
    val glow: List<ImageBitmap>,
)

@Composable
internal fun FishingCanvas(
    session: GameSession,
    fishing: FishingSession,
    area: FishingArea,
    seconds: Double,
    caughtAt: Double,
    reveal: Float,
    modifier: Modifier,
) {
    val assets = LocalContext.current.applicationContext.assets
    val cast = fishing.cast
    val art by produceState<FishingArt?>(null, assets, cast) {
        value = withContext(Dispatchers.IO) { loadFishingArt(assets, cast) }
    }
    val sky =
        session.read { state ->
            val now = Instant.now()
            FishingSky(
                DayNightCycle.darkness(now, state.clockOffset),
                Weather.eased(Weather.rainAmount(now, state.clockOffset)),
                state.season == Season.winter,
            )
        }
    val clip = remember { Path() }
    Canvas(modifier) {
        clip.reset()
        val radius = hypot(size.width, size.height) / 2 * reveal
        clip.addOval(
            androidx.compose.ui.geometry
                .Rect(center, radius),
        )
        clipPath(clip) {
            drawRect(Color(24, 112, 156))
            val picture = art
            if (picture != null) {
                val tile = 80.dp.toPx()
                for (row in 0 until ceil(size.height / tile).toInt()) {
                    for (column in 0 until ceil(size.width / tile).toInt()) {
                        val frame = FishingWater.frame(seconds, column, row)
                        val offset = IntOffset((column * tile).toInt(), (row * tile).toInt())
                        val tileSize = IntSize(ceil(tile).toInt(), ceil(tile).toInt())
                        drawImage(
                            picture.water[frame],
                            dstOffset = offset,
                            dstSize = tileSize,
                            filterQuality = FilterQuality.None,
                        )
                        if (sky.darkness > 0) {
                            drawImage(
                                picture.glow[frame],
                                dstOffset = offset,
                                dstSize = tileSize,
                                alpha = sky.darkness.toFloat(),
                                blendMode = BlendMode.Plus,
                                filterQuality = FilterQuality.None,
                            )
                        }
                    }
                }
                picture.fish?.let { fish ->
                    val caught = fishing.progress >= 1
                    val x = if (caught) size.width / 2 else (fishing.fish.x / area.width * size.width).toFloat()
                    val y =
                        if (caught) {
                            size.height * 0.44f - (4.5 * (1 - cos(PI * (seconds - caughtAt) / 0.9))).dp.toPx()
                        } else {
                            (fishing.fish.y / area.height * size.height).toFloat()
                        }
                    val pose = fishingFishPose(cast.kind.id, fishing.heading)
                    val width = ((if (caught) 190 else 84) * cast.sizeFactor).dp.toPx()
                    val height = width * fish.height / fish.width
                    translate(x, y) {
                        rotate(if (caught) 0f else pose.degrees, Offset.Zero) {
                            scale(if (!caught && pose.mirrored) -1f else 1f, 1f, Offset.Zero) {
                                drawImage(
                                    fish,
                                    dstOffset = IntOffset((-width / 2).toInt(), (-height / 2).toInt()),
                                    dstSize = IntSize(width.toInt(), height.toInt()),
                                    alpha = if (caught) 1f else 0.35f,
                                    colorFilter = if (caught) null else ColorFilter.tint(Color.Black),
                                    filterQuality = FilterQuality.None,
                                )
                            }
                        }
                    }
                }
            }
            drawRect(sky.tint, blendMode = BlendMode.Multiply)
        }
        if (reveal >= 1f && fishing.progress < 1 && !fishing.isFinished) {
            val rod = fishing.rod
            val point =
                if (rod != null) {
                    Offset((rod.x / area.width * size.width).toFloat(), (rod.y / area.height * size.height).toFloat())
                } else {
                    Offset(size.width / 2, size.height - ((area.bottom + 55) / area.height * size.height).toFloat())
                }
            if (rod != null) {
                drawCircle(
                    if (fishing.isOverFish) FishingGreen else Color.White.copy(alpha = 0.5f),
                    (fishing.reach / area.width * size.width).toFloat(),
                    point,
                    style = Stroke(3.dp.toPx()),
                )
            }
            drawRod(point, if (rod == null) 1f else 0.35f)
        }
    }
}

internal val FishingGreen = Color(0xFF87FF85)

private data class FishingSky(
    val darkness: Double,
    val rain: Double,
    val snowing: Boolean,
) {
    val tint: Color
        get() {
            val rainy = if (snowing) doubleArrayOf(0.9, 0.93, 1.0) else doubleArrayOf(0.6, 0.67, 0.8)
            val night = doubleArrayOf(0.42, 0.49, 0.72)
            val channels = FloatArray(3) { ((1 + (night[it] - 1) * darkness) * (1 + (rainy[it] - 1) * rain)).toFloat() }
            return Color(channels[0], channels[1], channels[2])
        }
}

private fun loadFishingArt(
    assets: AssetManager,
    cast: FishingCast,
): FishingArt {
    val fish =
        try {
            assets.open(cast.picturePath).use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
        } catch (_: IOException) {
            null
        }
    val water = FishingWater(cast.waterStyle)

    fun frames(glowing: Boolean): List<ImageBitmap> =
        List(60) { frame ->
            Bitmap.createBitmap(water.pixels(frame, glowing), 16, 16, Bitmap.Config.ARGB_8888).asImageBitmap()
        }
    return FishingArt(fish, frames(false), frames(true))
}

private fun DrawScope.drawRod(
    point: Offset,
    alpha: Float,
) {
    val radius = 39.dp.toPx()
    drawCircle(Color(0xDD303030).copy(alpha = 0.85f * alpha), radius, point)
    drawCircle(Color.White.copy(alpha = 0.2f * alpha), radius, point, style = Stroke(1.dp.toPx()))
    translate(point.x - 22.dp.toPx(), point.y - 24.dp.toPx()) {
        val unit = 1.dp.toPx()
        drawLine(Color.White.copy(alpha = alpha), Offset(5 * unit, 46 * unit), Offset(32 * unit, 3 * unit), 4 * unit)
        drawLine(Color.White.copy(alpha = alpha), Offset(32 * unit, 3 * unit), Offset(42 * unit, 32 * unit), unit)
        drawArc(
            Color.White.copy(alpha = alpha),
            0f,
            180f,
            false,
            Offset(34 * unit, 28 * unit),
            Size(8 * unit, 9 * unit),
            style = Stroke(2 * unit),
        )
    }
}
