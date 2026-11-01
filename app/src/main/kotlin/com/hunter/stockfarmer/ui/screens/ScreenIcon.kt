package com.hunter.stockfarmer.ui.screens

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hunter.stockfarmer.ui.theme.Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun ScreenIcon(
    art: ScreenArt?,
    symbol: String = "✦",
    collected: Boolean = true,
    size: Dp = 48.dp,
) {
    val assets = LocalContext.current.assets
    val bitmap by produceState<ImageBitmap?>(null, art, assets) {
        value =
            art?.let {
                withContext(Dispatchers.IO) {
                    ScreenIconCache.load(assets, it)
                }
            }
    }
    bitmap?.let {
        Image(
            it,
            contentDescription = null,
            modifier = Modifier.size(size),
            filterQuality = FilterQuality.None,
            colorFilter = if (collected) null else ColorFilter.tint(Color.Black),
            alpha = if (collected) 1f else 0.45f,
        )
    } ?: Text(symbol, modifier = Modifier.size(size), color = if (collected) Theme.accent else Color.Black)
}

/** Icons are re-opened every time a list screen is entered, so decoded frames are kept in a small bounded cache. */
private object ScreenIconCache {
    private val cache =
        object : LruCache<ScreenArt, Bitmap>(2 * 1024 * 1024) {
            override fun sizeOf(
                key: ScreenArt,
                value: Bitmap,
            ): Int = value.allocationByteCount
        }

    fun load(
        assets: AssetManager,
        art: ScreenArt,
    ): ImageBitmap? {
        synchronized(cache) { cache.get(art) }?.let { return it.asImageBitmap() }
        val bitmap =
            runCatching {
                assets.open(art.path).use { stream ->
                    @Suppress("DEPRECATION")
                    val decoder = requireNotNull(BitmapRegionDecoder.newInstance(stream, false))
                    try {
                        val width = art.frameWidth ?: (decoder.width / art.columns)
                        val height = art.frameHeight ?: (decoder.height / art.rows)
                        val x = art.column * width
                        val y = art.row * height
                        decoder.decodeRegion(Rect(x, y, x + width, y + height), null)
                    } finally {
                        decoder.recycle()
                    }
                }
            }.getOrNull() ?: return null
        synchronized(cache) { cache.put(art, bitmap) }
        return bitmap.asImageBitmap()
    }
}
