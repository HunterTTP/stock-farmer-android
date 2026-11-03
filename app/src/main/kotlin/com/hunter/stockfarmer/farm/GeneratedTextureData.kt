package com.hunter.stockfarmer.farm

import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.TextureData
import com.badlogic.gdx.utils.GdxRuntimeException
import java.nio.ByteBuffer

/**
 * Procedural pixels libGDX can ask for again. Being managed, the texture is reloaded after a GL context loss by running
 * [fill] once more, so no copy of the pixels has to stay in memory between uploads.
 */
internal class GeneratedTextureData(
    private val width: Int,
    private val height: Int,
    private val fill: (ByteBuffer) -> Unit,
) : TextureData {
    private var pixmap: Pixmap? = null

    override fun getType() = TextureData.TextureDataType.Pixmap

    override fun isPrepared() = pixmap != null

    override fun prepare() {
        if (pixmap != null) throw GdxRuntimeException("Already prepared")
        val made = Pixmap(width, height, Pixmap.Format.RGBA8888)
        try {
            fill(made.pixels)
        } catch (failure: Throwable) {
            made.dispose()
            throw failure
        }
        pixmap = made
    }

    override fun consumePixmap(): Pixmap {
        val made = pixmap ?: throw GdxRuntimeException("Call prepare() before consumePixmap()")
        pixmap = null
        return made
    }

    override fun disposePixmap() = true

    override fun consumeCustomData(target: Int): Unit = throw GdxRuntimeException("Generated textures hold a pixmap")

    override fun getWidth() = width

    override fun getHeight() = height

    override fun getFormat() = Pixmap.Format.RGBA8888

    override fun useMipMaps() = false

    override fun isManaged() = true
}

/** Lays out `0xRRGGBBAA` colors, row by row, as the R, G, B, A byte order of an RGBA8888 pixmap's memory. */
internal inline fun ByteBuffer.fillRgba(
    width: Int,
    height: Int,
    pixel: (Int, Int) -> Int,
) {
    var at = 0
    for (y in 0 until height) {
        for (x in 0 until width) {
            val color = pixel(x, y)
            put(at, (color ushr 24).toByte())
            put(at + 1, (color ushr 16).toByte())
            put(at + 2, (color ushr 8).toByte())
            put(at + 3, color.toByte())
            at += 4
        }
    }
}
