package com.hunter.stockfarmer.farm

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Affine2

/** A SpriteBatch that counts the sprites drawn since [startCount], the Android equivalent of SpriteKit's node count. */
class CountingSpriteBatch : SpriteBatch() {
    var sprites = 0
        private set

    fun startCount() {
        sprites = 0
    }

    override fun draw(
        texture: Texture,
        x: Float,
        y: Float,
        originX: Float,
        originY: Float,
        width: Float,
        height: Float,
        scaleX: Float,
        scaleY: Float,
        rotation: Float,
        srcX: Int,
        srcY: Int,
        srcWidth: Int,
        srcHeight: Int,
        flipX: Boolean,
        flipY: Boolean,
    ) {
        sprites++
        super.draw(
            texture,
            x,
            y,
            originX,
            originY,
            width,
            height,
            scaleX,
            scaleY,
            rotation,
            srcX,
            srcY,
            srcWidth,
            srcHeight,
            flipX,
            flipY,
        )
    }

    override fun draw(
        texture: Texture,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        srcX: Int,
        srcY: Int,
        srcWidth: Int,
        srcHeight: Int,
        flipX: Boolean,
        flipY: Boolean,
    ) {
        sprites++
        super.draw(texture, x, y, width, height, srcX, srcY, srcWidth, srcHeight, flipX, flipY)
    }

    override fun draw(
        texture: Texture,
        x: Float,
        y: Float,
        srcX: Int,
        srcY: Int,
        srcWidth: Int,
        srcHeight: Int,
    ) {
        sprites++
        super.draw(texture, x, y, srcX, srcY, srcWidth, srcHeight)
    }

    override fun draw(
        texture: Texture,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        u: Float,
        v: Float,
        u2: Float,
        v2: Float,
    ) {
        sprites++
        super.draw(texture, x, y, width, height, u, v, u2, v2)
    }

    override fun draw(
        texture: Texture,
        x: Float,
        y: Float,
    ) {
        sprites++
        super.draw(texture, x, y)
    }

    override fun draw(
        texture: Texture,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
    ) {
        sprites++
        super.draw(texture, x, y, width, height)
    }

    override fun draw(
        texture: Texture,
        spriteVertices: FloatArray,
        offset: Int,
        count: Int,
    ) {
        sprites += count / SPRITE_FLOATS
        super.draw(texture, spriteVertices, offset, count)
    }

    override fun draw(
        region: TextureRegion,
        x: Float,
        y: Float,
    ) {
        sprites++
        super.draw(region, x, y)
    }

    override fun draw(
        region: TextureRegion,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
    ) {
        sprites++
        super.draw(region, x, y, width, height)
    }

    override fun draw(
        region: TextureRegion,
        x: Float,
        y: Float,
        originX: Float,
        originY: Float,
        width: Float,
        height: Float,
        scaleX: Float,
        scaleY: Float,
        rotation: Float,
    ) {
        sprites++
        super.draw(region, x, y, originX, originY, width, height, scaleX, scaleY, rotation)
    }

    override fun draw(
        region: TextureRegion,
        x: Float,
        y: Float,
        originX: Float,
        originY: Float,
        width: Float,
        height: Float,
        scaleX: Float,
        scaleY: Float,
        rotation: Float,
        clockwise: Boolean,
    ) {
        sprites++
        super.draw(region, x, y, originX, originY, width, height, scaleX, scaleY, rotation, clockwise)
    }

    override fun draw(
        region: TextureRegion,
        width: Float,
        height: Float,
        transform: Affine2,
    ) {
        sprites++
        super.draw(region, width, height, transform)
    }

    private companion object {
        const val SPRITE_FLOATS = 20
    }
}
