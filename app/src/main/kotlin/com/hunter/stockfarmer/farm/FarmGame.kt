package com.hunter.stockfarmer.farm

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import kotlin.math.sin

/** The farm scene. For now it only clears the screen, to prove libGDX renders inside the Compose screen. */
class FarmGame : ApplicationAdapter() {
    private var elapsed = 0f

    override fun render() {
        elapsed += Gdx.graphics.deltaTime
        val pulse = 0.5f + 0.1f * sin(elapsed)
        Gdx.gl.glClearColor(0.25f, pulse, 0.3f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
    }
}
