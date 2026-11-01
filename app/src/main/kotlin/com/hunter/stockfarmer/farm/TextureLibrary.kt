package com.hunter.stockfarmer.farm

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.utils.Disposable
import com.hunter.stockfarmer.core.rules.Season

/** The farm's pictures, loaded from the bundled images the first time they are drawn and kept. */
class TextureLibrary : Disposable {
    private val cache = HashMap<String, Texture?>()

    /** A single white pixel, tinted to draw plain colors. */
    val white: Texture =
        Texture(
            Pixmap(1, 1, Pixmap.Format.RGBA8888).apply {
                setColor(1f, 1f, 1f, 1f)
                fill()
            },
        )

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
        val key = "$seasonal|$folder|$name.$extension"
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

    override fun dispose() {
        cache.values.forEach { it?.dispose() }
        cache.clear()
        white.dispose()
    }
}
