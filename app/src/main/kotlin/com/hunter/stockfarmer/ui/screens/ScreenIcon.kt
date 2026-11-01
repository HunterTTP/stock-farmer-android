package com.hunter.stockfarmer.ui.screens

import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun ScreenIcon(
    art: ScreenArt?,
    symbol: String = "✦",
    collected: Boolean = true,
) {
    val assets = LocalContext.current.assets
    val bitmap by produceState<ImageBitmap?>(null, art, assets) {
        value =
            art?.let {
                withContext(Dispatchers.IO) {
                    runCatching {
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        assets.open(it.path).use { stream -> BitmapFactory.decodeStream(stream, null, options) }
                        val width = it.frameWidth ?: (options.outWidth / it.columns)
                        val height = it.frameHeight ?: (options.outHeight / it.rows)
                        val x = it.column * width
                        val y = it.row * height
                        assets.open(it.path).use { stream ->
                            @Suppress("DEPRECATION")
                            val decoder = requireNotNull(BitmapRegionDecoder.newInstance(stream, false))
                            try {
                                decoder.decodeRegion(Rect(x, y, x + width, y + height), null).asImageBitmap()
                            } finally {
                                decoder.recycle()
                            }
                        }
                    }.getOrNull()
                }
            }
    }
    bitmap?.let {
        Image(
            it,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            filterQuality = FilterQuality.None,
            colorFilter = if (collected) null else ColorFilter.tint(Color.Black),
            alpha = if (collected) 1f else 0.45f,
        )
    } ?: Text(symbol, modifier = Modifier.size(48.dp), color = if (collected) FarmGreen else Color.Black)
}
