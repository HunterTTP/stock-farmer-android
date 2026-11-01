package com.hunter.stockfarmer.ui.menus

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hunter.stockfarmer.core.catalog.GroundCover
import com.hunter.stockfarmer.core.catalog.WaterStyle
import com.hunter.stockfarmer.core.rules.Butterflies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** Menus use spring artwork, like the iOS HUD. Decoding stays off the UI thread and the cache is bounded. */
internal class MenuAssetLoader(
    private val assets: AssetManager,
) {
    private val cache =
        object : LruCache<MenuPicture.Asset, Bitmap>(8 * 1024 * 1024) {
            override fun sizeOf(
                key: MenuPicture.Asset,
                value: Bitmap,
            ): Int = value.allocationByteCount
        }

    @Synchronized
    fun load(asset: MenuPicture.Asset): ImageBitmap? {
        cache.get(asset)?.let { return it.asImageBitmap() }
        val source =
            try {
                assets.open(asset.path).use { BitmapFactory.decodeStream(it) }
            } catch (_: IOException) {
                null
            } ?: return null
        val bitmap =
            if (asset.chickenFrame) {
                Bitmap.createBitmap(source, 3 * 48, 0, 48, source.height / 4).also {
                    if (it !== source) source.recycle()
                }
            } else {
                source
            }
        cache.put(asset, bitmap)
        return bitmap.asImageBitmap()
    }
}

@Composable
internal fun MenuImage(
    picture: MenuPicture,
    loader: MenuAssetLoader,
    modifier: Modifier = Modifier,
) {
    Box(modifier.size(44.dp), contentAlignment = Alignment.Center) {
        when (picture) {
            is MenuPicture.Asset -> {
                val bitmap = assetBitmap(picture, loader)
                if (bitmap != null) {
                    Image(
                        bitmap,
                        null,
                        Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        filterQuality = FilterQuality.None,
                    )
                }
            }

            is MenuPicture.Water -> {
                WaterImage(picture.style)
            }

            is MenuPicture.Ground -> {
                GroundImage(picture.cover, loader)
            }

            is MenuPicture.Symbol -> {
                Text(picture.text, fontSize = 28.sp)
            }

            is MenuPicture.Butterfly -> {
                ButterflyImage(picture.wings)
            }
        }
    }
}

@Composable
private fun assetBitmap(
    asset: MenuPicture.Asset,
    loader: MenuAssetLoader,
): ImageBitmap? {
    val bitmap by produceState<ImageBitmap?>(null, asset, loader) {
        value = withContext(Dispatchers.IO) { loader.load(asset) }
    }
    return bitmap
}

@Composable
internal fun rememberMenuAssetLoader(): MenuAssetLoader {
    val assets = LocalContext.current.applicationContext.assets
    return remember(assets) { MenuAssetLoader(assets) }
}

@Composable
private fun WaterImage(style: WaterStyle) {
    val colors =
        when (style) {
            WaterStyle.deep -> listOf(Color(16, 88, 136), Color(64, 148, 208))
            WaterStyle.medium -> listOf(Color(24, 112, 156), Color(82, 182, 222))
            WaterStyle.shallow -> listOf(Color(40, 132, 189), Color(108, 179, 224))
        }
    Canvas(Modifier.fillMaxSize()) {
        drawRoundRect(Brush.linearGradient(colors, Offset(0f, size.height), Offset(size.width, 0f)))
    }
}

/** Paths are generated from the grass image on iOS too; there are no path image files in the assets. */
@Composable
private fun GroundImage(
    cover: GroundCover,
    loader: MenuAssetLoader,
) {
    val grass = assetBitmap(MenuPictures.terrain("grass"), loader)
    Canvas(Modifier.fillMaxSize()) {
        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(Offset.Zero, size), Paint())
            if (cover == GroundCover.dirtPath) {
                grass?.let { drawImage(it, dstSize = IntSize(size.width.toInt(), size.height.toInt())) }
                drawRect(Color(0.52f, 0.32f, 0.16f), blendMode = BlendMode.Color)
                drawRect(Color(0.68f, 0.68f, 0.68f), blendMode = BlendMode.Multiply)
            } else {
                drawRect(Color(0.25f, 0.25f, 0.25f))
                scale(size.width / 48f, size.height / 48f, Offset.Zero) { cobblestones() }
                grass?.let {
                    drawImage(
                        it,
                        dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                        alpha = 0.16f,
                        blendMode = BlendMode.Luminosity,
                    )
                }
            }
            canvas.restore()
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.cobblestones() {
    val widths = listOf(listOf(15, 19, 14), listOf(20, 13, 15), listOf(14, 17, 17), listOf(17, 15, 16))
    widths.forEachIndexed { row, stones ->
        for (repeat in -1..1) {
            var x = (repeat * 48 + (row % 2) * 8).toFloat()
            stones.forEachIndexed { column, width ->
                val left = x + 1
                val top = row * 12f + 1
                val right = x + width
                val bottom = top + 11
                val path =
                    Path().apply {
                        moveTo(left + 2, top)
                        lineTo(right - 3, top)
                        lineTo(right, top + 2)
                        lineTo(right - 1, bottom - 2)
                        lineTo(right - 3, bottom)
                        lineTo(left + 1, bottom - 1)
                        lineTo(left, top + 3)
                        close()
                    }
                val shade = 0.45f + ((row * 2 + column * 3) % 5) * 0.035f
                drawPath(path, Color(shade, shade, shade))
                drawLine(
                    Color(shade + 0.12f, shade + 0.12f, shade + 0.12f),
                    Offset(left + 3, top + 1),
                    Offset(
                        right - 4,
                        top + 1,
                    ),
                )
                x += width
            }
        }
    }
}

@Composable
private fun ButterflyImage(wings: Butterflies.Wings) {
    val pattern = listOf("WW...WW", "WWW.WWW", "WWWBWWW", ".WWBWW.", "..WBW..")
    val colors =
        when (wings) {
            is Butterflies.Wings.Color -> {
                wings.rarity.color.let {
                    listOf(Color(it.red.toFloat(), it.green.toFloat(), it.blue.toFloat()))
                }
            }

            Butterflies.Wings.Shifting -> {
                listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Magenta)
            }
        }
    Canvas(Modifier.fillMaxSize()) {
        val cell = minOf(size.width / 7, size.height / 5)
        val origin = Offset((size.width - cell * 7) / 2, (size.height - cell * 5) / 2)
        pattern.forEachIndexed { row, line ->
            line.forEachIndexed { column, mark ->
                val point = origin + Offset(column * cell, row * cell)
                if (mark == 'B') drawRect(Color(0.12f, 0.12f, 0.12f), point, Size(cell, cell))
                if (mark == 'W') {
                    if (colors.size == 1) {
                        drawRect(colors.first(), point, Size(cell, cell))
                    } else {
                        drawRect(
                            Brush.linearGradient(colors, origin, origin + Offset(7 * cell, 5 * cell)),
                            point,
                            Size(cell, cell),
                        )
                    }
                }
            }
        }
    }
}
