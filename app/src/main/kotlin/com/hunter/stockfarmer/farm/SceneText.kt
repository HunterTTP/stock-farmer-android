package com.hunter.stockfarmer.farm

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.badlogic.gdx.graphics.Texture

/** Android only supplies glyph rasterization; positions, sizes and lifetimes belong to the pure scene models. */
internal fun TextureLibrary.label(
    text: String,
    size: Float,
    black: Boolean = false,
): Texture {
    val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            color = if (black) Color.BLACK else Color.WHITE
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        }
    val width =
        kotlin.math
            .ceil(paint.measureText(text).toDouble())
            .toInt()
            .coerceAtLeast(1)
    val metrics = paint.fontMetrics
    val height =
        kotlin.math
            .ceil((metrics.descent - metrics.ascent).toDouble())
            .toInt()
            .coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    try {
        Canvas(bitmap).drawText(text, 0f, -metrics.ascent, paint)
        return generated("label/$text/$size/$black", width, height, smooth = true) { x, y ->
            val argb = bitmap.getPixel(x, y)
            (argb shl 8) or (argb ushr 24)
        }
    } finally {
        bitmap.recycle()
    }
}
