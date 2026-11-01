package com.hunter.stockfarmer.farm

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.utils.Disposable
import com.hunter.stockfarmer.core.rules.Season

/** The farm's pictures, loaded from the bundled images the first time they are drawn and kept. */
class TextureLibrary : Disposable {
    private val cache = HashMap<String, Texture?>()
    private val generated = HashMap<String, Texture>()

    /** A single white pixel, tinted to draw plain colors. */
    val white: Texture = generated("white", 1, 1) { _, _ -> -1 }

    /**
     * The picture [name] in an images [folder]. Off-season pictures live under `seasons/<season>/<folder>` and win over
     * the spring one where they exist. Missing pictures give null so a gap in the art never stops the farm drawing.
     */
    fun texture(
        folder: String,
        name: String,
        extension: String = "png",
        season: Season = Season.spring,
        smooth: Boolean = false,
    ): Texture? {
        val seasonal = if (season == Season.spring) null else "images/seasons/${season.name}/$folder/$name.$extension"
        val key = "$seasonal|$folder|$name.$extension|$smooth"
        return cache.getOrPut(key) {
            val path =
                listOfNotNull(
                    seasonal,
                    "images/$folder/$name.$extension",
                ).firstOrNull { Gdx.files.internal(it).exists() }
            path?.let {
                Texture(Gdx.files.internal(it), smooth).apply {
                    val filter = if (smooth) Texture.TextureFilter.MipMapLinearLinear else Texture.TextureFilter.Nearest
                    setFilter(filter, if (smooth) Texture.TextureFilter.Linear else Texture.TextureFilter.Nearest)
                }
            }
        }
    }

    /** Procedural effect art shares the library's GL-thread lifetime, just like bundled pictures. */
    fun generated(
        key: String,
        width: Int,
        height: Int,
        smooth: Boolean = false,
        pixel: (Int, Int) -> Int,
    ): Texture =
        generated.getOrPut(key) {
            val pixmap = Pixmap(width, height, Pixmap.Format.RGBA8888)
            try {
                pixmap.setBlending(Pixmap.Blending.None)
                for (y in 0 until height) {
                    for (x in 0 until width) pixmap.drawPixel(x, y, pixel(x, y))
                }
                Texture(pixmap).apply {
                    val filter = if (smooth) Texture.TextureFilter.Linear else Texture.TextureFilter.Nearest
                    setFilter(filter, filter)
                }
            } finally {
                pixmap.dispose()
            }
        }

    fun releaseGenerated(key: String) {
        generated.remove(key)?.dispose()
    }

    override fun dispose() {
        cache.values.forEach { it?.dispose() }
        cache.clear()
        generated.values.forEach { it.dispose() }
        generated.clear()
    }
}
