package com.hunter.stockfarmer.ui.fishing

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.hunter.stockfarmer.core.catalog.WaterStyle
import com.hunter.stockfarmer.core.rules.DayNightCycle
import com.hunter.stockfarmer.core.rules.Season
import com.hunter.stockfarmer.core.rules.Weather
import com.hunter.stockfarmer.farm.LocalFarmWeather
import com.hunter.stockfarmer.farm.NightShade
import com.hunter.stockfarmer.farm.drawFarmWeather
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
    mode: FishingMode,
    modifier: Modifier,
) {
    val assets = LocalContext.current.applicationContext.assets
    val fishing = mode.fishing
    val area = mode.area
    val cast = fishing.cast
    val art by produceState<FishingArt?>(null, assets, cast) {
        value = withContext(Dispatchers.IO) { loadFishingArt(assets, cast) }
    }
    val clip = remember { Path() }
    val weather = LocalFarmWeather.current
    Canvas(
        modifier.pointerInput(Unit) {
            // The water is over the farm, which is not touched through it.
            awaitEachGesture { awaitFirstDown().consume() }
        },
    ) {
        val seconds = mode.seconds
        val caughtAt = mode.caughtAt
        val sky =
            session.read { state ->
                val now = Instant.now()
                FishingSky(
                    DayNightCycle.darkness(now, state.clockOffset),
                    Weather.eased(Weather.rainAmount(now, state.clockOffset)),
                    state.season == Season.winter,
                )
            }
        clip.reset()
        val radius = hypot(size.width, size.height) / 2 * mode.reveal.value
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
                        // The tiles are laid from the bottom of the screen up, so the overhang is at the top.
                        val offset = IntOffset((column * tile).toInt(), (size.height - (row + 1) * tile).toInt())
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
                    val caught = mode.isCaught
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
            drawFarmWeather(weather.value)
        }
    }
}

private data class FishingSky(
    val darkness: Double,
    val rain: Double,
    val snowing: Boolean,
) {
    val tint: Color
        get() {
            val rainy = if (snowing) doubleArrayOf(0.9, 0.93, 1.0) else doubleArrayOf(0.6, 0.67, 0.8)
            val night = doubleArrayOf(NightShade.DEEPEST_RED, NightShade.DEEPEST_GREEN, NightShade.DEEPEST_BLUE)
            val channels =
                FloatArray(3) { (NightShade.channel(night[it], darkness) * (1 + (rainy[it] - 1) * rain)).toFloat() }
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
    val (water, glow) = waterFrames(cast.waterStyle)
    return FishingArt(fish, water, glow)
}

/** The shimmer depends only on the water style, so each style's 120 frames are built once. */
private val waterFrameCache = mutableMapOf<WaterStyle, Pair<List<ImageBitmap>, List<ImageBitmap>>>()

private fun waterFrames(style: WaterStyle): Pair<List<ImageBitmap>, List<ImageBitmap>> =
    synchronized(waterFrameCache) {
        waterFrameCache.getOrPut(style) {
            val water = FishingWater(style)

            fun frames(glowing: Boolean): List<ImageBitmap> =
                List(60) { frame ->
                    Bitmap.createBitmap(water.pixels(frame, glowing), 16, 16, Bitmap.Config.ARGB_8888).asImageBitmap()
                }
            frames(false) to frames(true)
        }
    }
