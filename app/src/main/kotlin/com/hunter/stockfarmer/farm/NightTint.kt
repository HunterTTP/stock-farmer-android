package com.hunter.stockfarmer.farm

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch

private const val NIGHT_RED = 0.04f
private const val NIGHT_GREEN = 0.06f
private const val NIGHT_BLUE = 0.22f
private const val NIGHT_STRENGTH = 0.6

/** Darkens the whole view of [camera] toward night by [darkness], from 0 (day) to 1. */
fun SpriteBatch.drawNightTint(
    textures: TextureLibrary,
    camera: OrthographicCamera,
    darkness: Double,
) {
    if (darkness <= 0.0) return
    color = Color(NIGHT_RED, NIGHT_GREEN, NIGHT_BLUE, (darkness * NIGHT_STRENGTH).toFloat())
    draw(
        textures.white,
        camera.position.x - camera.viewportWidth * camera.zoom / 2,
        camera.position.y - camera.viewportHeight * camera.zoom / 2,
        camera.viewportWidth * camera.zoom,
        camera.viewportHeight * camera.zoom,
    )
    color = Color.WHITE
}
