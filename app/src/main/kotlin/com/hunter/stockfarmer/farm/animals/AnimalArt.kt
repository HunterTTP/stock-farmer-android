package com.hunter.stockfarmer.farm.animals

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.hunter.stockfarmer.farm.TextureLibrary

/**
 * Regions and alpha measurements share the TextureLibrary's ownership of their backing textures. One lives with the
 * farm's library and serves the live scene and its photos, both on the graphics thread, so sheets are measured once.
 */
internal class AnimalArt(
    private val textures: TextureLibrary,
) {
    private data class Sheet(
        val texture: Texture,
        val feet: Map<Facing, AnimalFootprint?>,
        val frames: MutableMap<SheetFrame, TextureRegion> = mutableMapOf(),
    )

    private val sheets = mutableMapOf<AnimalSprites, Sheet?>()

    fun region(
        look: AnimalSprites,
        frame: SheetFrame,
    ): TextureRegion? {
        val sheet = sheet(look) ?: return null
        return sheet.frames.getOrPut(frame) {
            val rect = look.sheet.rect(frame, sheet.texture.width, sheet.texture.height)
            TextureRegion(sheet.texture, rect.x, rect.y, rect.width, rect.height)
        }
    }

    fun footprint(
        look: AnimalSprites,
        facing: Facing,
    ): AnimalFootprint? = sheet(look)?.feet?.get(facing)

    private fun sheet(look: AnimalSprites): Sheet? =
        sheets.getOrPut(look) {
            val texture = textures.texture("animals/${look.folder}", look.asset) ?: return@getOrPut null
            val data = texture.textureData
            if (!data.isPrepared) data.prepare()
            val pixels = data.consumePixmap()
            try {
                val feet =
                    Facing.entries.associateWith { facing ->
                        val rect = look.sheet.rect(look.standing(facing), texture.width, texture.height)
                        AnimalFootprint.measure(rect.width, rect.height) { x, y ->
                            pixels.getPixel(rect.x + x, rect.y + y) and 0xff
                        }
                    }
                Sheet(texture, feet)
            } finally {
                if (data.disposePixmap()) pixels.dispose()
            }
        }
}
