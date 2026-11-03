package com.hunter.stockfarmer.farm

import com.badlogic.gdx.graphics.Texture
import com.hunter.stockfarmer.core.catalog.GroundCover
import kotlin.math.roundToInt

internal class TerrainTextures(
    private val textures: TextureLibrary,
) {
    private val cache = mutableMapOf<GroundCover, Texture?>()

    fun cover(cover: GroundCover): Texture? =
        cache.getOrPut(cover) {
            val grass = textures.texture("terrain", "grass", "jpg") ?: return@getOrPut null
            val cobble = cover == GroundCover.cobblestonePath
            val width = if (cobble) 48 else grass.width
            val height = if (cobble) 48 else grass.height
            textures.generatedPixels("terrain/${cover.assetName}", width, height, smooth = !cobble) { pixels ->
                val data = grass.textureData
                if (!data.isPrepared) data.prepare()
                val source = data.consumePixmap()
                try {
                    pixels.fillRgba(width, height) { x, y ->
                        val color = source.getPixel(x * source.width / width, y * source.height / height)
                        val light =
                            (
                                (color ushr 24) * 0.3 + ((color ushr 16) and 255) * 0.59 +
                                    ((color ushr 8) and 255) * 0.11
                            ) / 255
                        if (cobble) {
                            CobblestonePath.pixel(x, y, light)
                        } else {
                            // Color blending retains the grass's luminosity under the dirt tint.
                            val shift = light - (0.52 * 0.3 + 0.32 * 0.59 + 0.16 * 0.11)
                            val channels = doubleArrayOf(0.52 + shift, 0.32 + shift, 0.16 + shift)
                            val lum = light
                            val low = channels.min()
                            if (low < 0) {
                                for (i in channels.indices) {
                                    channels[i] = lum + (channels[i] - lum) * lum / (lum - low)
                                }
                            }
                            val high = channels.max()
                            if (high > 1) {
                                for (i in channels.indices) {
                                    channels[i] = lum +
                                        (channels[i] - lum) * (1 - lum) / (high - lum)
                                }
                            }
                            val rgb = channels.map { (it * 0.68 * 255).roundToInt().coerceIn(0, 255) }
                            (rgb[0] shl 24) or (rgb[1] shl 16) or (rgb[2] shl 8) or 255
                        }
                    }
                } finally {
                    if (data.disposePixmap()) source.dispose()
                }
            }
        }
}
