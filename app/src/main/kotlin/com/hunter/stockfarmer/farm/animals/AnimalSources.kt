package com.hunter.stockfarmer.farm.animals

import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.util.secondsSince
import com.hunter.stockfarmer.farm.FarmRenderer
import com.hunter.stockfarmer.farm.Standing
import com.hunter.stockfarmer.farm.StandingSource
import com.hunter.stockfarmer.farm.TextureLibrary
import java.time.Instant
import kotlin.math.sqrt

/** Add these to FarmRenderer's sources, using the renderer's TextureLibrary on the GL thread. */
fun animalSources(textures: TextureLibrary): List<StandingSource> = listOf(AnimalSource(textures), FishSource(textures))

private class AnimalSource(
    private val textures: TextureLibrary,
) : StandingSource {
    private data class Node(
        val motion: AnimalMotion,
        val animation: AnimalAnimation,
        val wake: DuckWake,
    )

    private val art = AnimalArt(textures)
    private val nodes = mutableMapOf<AnimalKey, Node>()

    override fun collect(
        batch: SpriteBatch,
        state: GameState,
        now: Instant,
        out: MutableList<Standing>,
    ) {
        val wanted = mutableSetOf<AnimalKey>()
        for (animal in AnimalSnapshot.all(state, now)) {
            val (key, point, look, egg) = animal
            wanted += key
            val node =
                nodes.getOrPut(key) {
                    Node(
                        AnimalMotion(point, look.defaultFacing, now),
                        AnimalAnimation(key.hashCode()),
                        DuckWake(key.hashCode()),
                    )
                }
            val pose = node.motion.update(point, now)
            val region = art.region(look, node.animation.frame(look, pose)) ?: continue
            val feet = art.footprint(look, pose.facing)
            val width = look.drawWidth
            val height = width * region.regionHeight / region.regionWidth
            val waterline = look.waterline(pose.facing)
            val center = feet?.center(waterline, look.duck && pose.facing == Facing.UP) ?: (0.5f to 0.5f)
            val mirror = look.kind == AnimalKind.CAT && pose.mirrored
            val centerX = (pose.col + 0.5f) * FarmRenderer.TILE
            val centerY = (GameState.GRID_SIZE - pose.row - 0.5f) * FarmRenderer.TILE
            val x = centerX - width * (if (mirror) 1 - center.first else center.first)
            val y = centerY - height * center.second
            val surface = y + height * (1 - (waterline ?: 1f))
            node.wake.halfWidth = width * 0.28f
            val wake =
                if (look.duck) {
                    node.wake.update(now.secondsSince(Instant.EPOCH), centerX, surface, pose.facing, pose.walking)
                } else {
                    emptyList()
                }
            out +=
                Standing(point.row + 1f + 0.0004f + if (look.baby) 0f else 0.00005f) {
                    val oldColor = batch.packedColor
                    try {
                        for (pixel in wake) {
                            if (pixel.spray) {
                                batch.setColor(0.7f, 0.9f, 1f, pixel.alpha)
                            } else {
                                batch.setColor(0.9f, 0.97f, 1f, pixel.alpha)
                            }
                            batch.draw(textures.white, pixel.x, pixel.y, pixel.size, pixel.size)
                        }
                        if (waterline == null && feet != null) {
                            drawShadow(batch, x, y, width, height, feet, mirror)
                        }
                        batch.setColor(1f, 1f, 1f, 1f)
                        drawBody(
                            batch,
                            region,
                            x,
                            y,
                            width,
                            height,
                            mirror,
                            waterline,
                            look.duck && pose.facing == Facing.UP,
                        )
                        if (egg) drawEgg(batch, centerX, y + height * 0.82f + 10, now)
                    } finally {
                        batch.packedColor = oldColor
                    }
                }
        }
        nodes.keys.retainAll(wanted)
    }

    private fun drawEgg(
        batch: SpriteBatch,
        x: Float,
        y: Float,
        now: Instant,
    ) {
        val texture = textures.texture("animals/Chickens", "Egg_Big_48x48") ?: return
        val phase = (now.secondsSince(Instant.EPOCH) % 1.2).toFloat()
        val bob = if (phase < 0.6f) phase * 10 else (1.2f - phase) * 10
        val size = 100 * 0.34f * 0.54f
        batch.draw(texture, x - size / 2, y + bob - size / 2, size, size)
    }

    private fun drawShadow(
        batch: SpriteBatch,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        feet: AnimalFootprint,
        mirror: Boolean,
    ) {
        val shadowWidth = width * feet.width * 0.85f
        val shadowHeight = shadowWidth * 0.35f
        val centerX = x + width * if (mirror) 1 - feet.centerX else feet.centerX
        val centerY = y + feet.bottom * height + shadowHeight * 0.3f
        batch.setColor(0f, 0f, 0f, 0.16f)
        repeat(12) { row ->
            val dy = (row + 0.5f) / 6 - 1
            val strip = shadowWidth * sqrt(1 - dy * dy)
            batch.draw(
                textures.white,
                centerX - strip / 2,
                centerY - shadowHeight / 2 + row * shadowHeight / 12,
                strip,
                shadowHeight / 12,
            )
        }
    }

    private fun drawBody(
        batch: SpriteBatch,
        region: TextureRegion,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        mirror: Boolean,
        waterline: Float?,
        tail: Boolean,
    ) {
        if (waterline == null) {
            batch.draw(region, if (mirror) x + width else x, y, if (mirror) -width else width, height)
            return
        }
        val visible = (region.regionHeight * waterline).toInt().coerceIn(1, region.regionHeight)
        val top = TextureRegion(region, 0, 0, region.regionWidth, visible)
        val visibleHeight = height * visible / region.regionHeight
        batch.draw(top, x, y + height - visibleHeight, width, visibleHeight)
        if (tail && visible < region.regionHeight) {
            val tailWidth = (region.regionWidth * 0.16f).toInt().coerceAtLeast(1)
            val tailLeft = (region.regionWidth - tailWidth) / 2
            val strip = TextureRegion(region, tailLeft, visible, tailWidth, region.regionHeight - visible)
            batch.draw(
                strip,
                x + width * tailLeft / region.regionWidth,
                y,
                width * tailWidth / region.regionWidth,
                height - visibleHeight,
            )
        }
    }
}
