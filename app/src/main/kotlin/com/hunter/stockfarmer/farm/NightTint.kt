package com.hunter.stockfarmer.farm

import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.hunter.stockfarmer.farm.effects.preserve

/** The colour the whole farm is multiplied by at night, a deep blue; daylight multiplies by white, which changes nothing. */
object NightShade {
    const val DEEPEST_RED = 0.42
    const val DEEPEST_GREEN = 0.49
    const val DEEPEST_BLUE = 0.72

    /** One channel of the tint at [darkness], from 1 by day (0) down to [deepest] at full night (1). */
    fun channel(
        deepest: Double,
        darkness: Double,
    ) = 1 + (deepest - 1) * darkness
}

/**
 * Darkens the whole view of [camera] toward night by [darkness], from 0 (day) to 1. The farm is multiplied by the tint,
 * so blacks stay black and colours keep their depth instead of being washed over with a flat blue.
 */
fun SpriteBatch.drawNightTint(
    textures: TextureLibrary,
    camera: OrthographicCamera,
    darkness: Double,
) {
    if (darkness <= 0.0) return
    preserve {
        setBlendFunction(GL20.GL_DST_COLOR, GL20.GL_ZERO)
        setColor(
            NightShade.channel(NightShade.DEEPEST_RED, darkness).toFloat(),
            NightShade.channel(NightShade.DEEPEST_GREEN, darkness).toFloat(),
            NightShade.channel(NightShade.DEEPEST_BLUE, darkness).toFloat(),
            1f,
        )
        draw(
            textures.white,
            camera.position.x - camera.viewportWidth * camera.zoom / 2,
            camera.position.y - camera.viewportHeight * camera.zoom / 2,
            camera.viewportWidth * camera.zoom,
            camera.viewportHeight * camera.zoom,
        )
    }
}
