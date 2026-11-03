package com.hunter.stockfarmer.farm.animals

import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.rules.DayNightCycle
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.Sun
import com.hunter.stockfarmer.core.rules.SunShadow
import com.hunter.stockfarmer.core.rules.needsShelter
import com.hunter.stockfarmer.core.util.secondsSince
import com.hunter.stockfarmer.farm.FarmRenderer
import com.hunter.stockfarmer.farm.ProduceBadge
import com.hunter.stockfarmer.farm.Standing
import com.hunter.stockfarmer.farm.StandingSource
import com.hunter.stockfarmer.farm.TextureLibrary
import com.hunter.stockfarmer.farm.VisualTiming
import com.hunter.stockfarmer.farm.effects.WaterDrops
import com.hunter.stockfarmer.farm.label
import com.hunter.stockfarmer.game.AnimalSceneGestures
import com.hunter.stockfarmer.ui.screens.PerformanceWork
import java.time.Instant
import kotlin.math.sqrt

/** Add these to FarmRenderer's sources, using the renderer's TextureLibrary on the GL thread. */
internal fun animalSources(
    textures: TextureLibrary,
    gestures: AnimalSceneGestures? = null,
    art: AnimalArt,
): List<StandingSource> = listOf(AnimalSource(textures, gestures, art), FishSource(textures))

private class AnimalSource(
    private val textures: TextureLibrary,
    private val gestures: AnimalSceneGestures?,
    private val art: AnimalArt,
) : StandingSource {
    override val work = PerformanceWork.animals

    private data class Node(
        val look: AnimalSprites,
        val motion: AnimalMotion,
        val animation: AnimalAnimation,
        val wake: DuckWake,
        val shelter: AnimalShelter = AnimalShelter(),
        val hearts: AnimalHearts = AnimalHearts(),
        var badge: StorageItem? = null,
        var badgeAt: Instant = Instant.EPOCH,
        var shadowWidth: Float? = null,
    )

    private val nodes = mutableMapOf<AnimalKey, Node>()
    private val badges = ProduceBadge(textures)
    private val heartTexture by lazy { textures.label("❤️", 28f) }

    override fun collect(
        batch: SpriteBatch,
        state: GameState,
        now: Instant,
        out: MutableList<Standing>,
    ) {
        val wanted = mutableSetOf<AnimalKey>()
        val targets = mutableListOf<AnimalHitTarget>()
        val petting = gestures?.drainPetting().orEmpty().groupBy { it.animal }
        val nudging = gestures?.drainNudging().orEmpty().toSet()
        val sheltering = GameEngine(state).needsShelter(now)
        val minute = DayNightCycle.minuteOfDay(now, state.clockOffset)
        val daylight = (1 - 2 * DayNightCycle.darkness(minute)).coerceAtLeast(0.0).toFloat()
        val sun = Sun.shadow(minute)
        for (animal in AnimalSnapshot.all(state, now)) {
            val (key, point, look) = animal
            wanted += key
            if (nodes[key]?.look?.copy(sheared = false) != look.copy(sheared = false)) nodes.remove(key)
            val node =
                nodes.getOrPut(key) {
                    Node(
                        look,
                        AnimalMotion(point, look.defaultFacing, now),
                        AnimalAnimation(key.hashCode()),
                        DuckWake(key.hashCode()),
                    )
                }
            petting[key.ref]?.forEach { node.hearts.pet(it.at) }
            if (key.ref in nudging) node.animation.restart()
            val moving = node.motion.update(point, now)
            val sheltered = node.shelter.update(moving, animal.entrance, sheltering, now)
            val pose = sheltered.pose
            if (node.badge != animal.badge) {
                node.badge = animal.badge
                node.badgeAt = now
            }
            if (sheltered.alpha <= 0) {
                targets +=
                    AnimalHitTarget(
                        key.ref,
                        (pose.col + 0.5f) * FarmRenderer.TILE,
                        (GameState.GRID_SIZE - pose.row - 0.5f) * FarmRenderer.TILE,
                        inside = true,
                    )
                continue
            }
            val region = art.region(look, node.animation.frame(look, pose)) ?: continue
            val feet = art.footprint(look, pose.facing)
            val width = look.drawWidth
            if (node.shadowWidth == null) {
                node.shadowWidth = art.footprint(look, look.defaultFacing)?.let { width * it.width * 0.85f }
            }
            val height = width * region.regionHeight / region.regionWidth
            val waterline = look.waterline(pose.facing)
            val center = feet?.center(waterline, look.duck && pose.facing == Facing.UP) ?: (0.5f to 0.5f)
            val mirror = look.kind == AnimalKind.CAT && pose.mirrored
            val centerX = (pose.col + 0.5f) * FarmRenderer.TILE
            val centerY = (GameState.GRID_SIZE - pose.row - 0.5f) * FarmRenderer.TILE
            targets +=
                AnimalHitTarget(
                    key.ref,
                    centerX,
                    centerY,
                    sheltered.inside,
                    if (pose.walking) (0.5 - pose.elapsed).coerceAtLeast(0.0) else 0.0,
                )
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
            val hearts = node.hearts.hearts(now)
            val badgeY =
                y + height * 0.82f +
                    (if (animal.badge == StorageItem.Egg) 10 else 0) + VisualTiming.bob(now.secondsSince(node.badgeAt))
            out +=
                Standing(point.row + 1f + 0.4f + if (look.baby) 0f else 0.05f) {
                    val oldColor = batch.packedColor
                    try {
                        for (pixel in wake) {
                            val rgb = WaterDrops.wakeRgb
                            batch.setColor(
                                rgb[pixel.tint * 3],
                                rgb[pixel.tint * 3 + 1],
                                rgb[pixel.tint * 3 + 2],
                                pixel.alpha,
                            )
                            batch.draw(textures.white, pixel.x, pixel.y, pixel.size, pixel.size)
                        }
                        if (waterline == null && feet != null) {
                            drawShadow(
                                batch,
                                x,
                                y,
                                width,
                                height,
                                feet,
                                mirror,
                                sun,
                                daylight * sheltered.alpha,
                                node.shadowWidth ?: width * feet.width * 0.85f,
                            )
                        }
                        batch.setColor(1f, 1f, 1f, sheltered.alpha)
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
                        animal.badge?.let { badges.draw(batch, it, centerX, badgeY) }
                        for (heart in hearts) {
                            batch.setColor(1f, 1f, 1f, heart.alpha * sheltered.alpha)
                            batch.draw(
                                heartTexture,
                                centerX - heartTexture.width / 2,
                                centerY + 30 + heart.rise - heartTexture.height / 2,
                            )
                        }
                    } finally {
                        batch.packedColor = oldColor
                    }
                }
        }
        nodes.keys.retainAll(wanted)
        gestures?.targets = targets.toList()
    }

    private fun drawShadow(
        batch: SpriteBatch,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        feet: AnimalFootprint,
        mirror: Boolean,
        sun: SunShadow,
        daylight: Float,
        originalWidth: Float,
    ) {
        val shadowWidth = originalWidth * (1 + 0.4 * kotlin.math.abs(sun.dx) * sun.length).toFloat()
        val shadowHeight = originalWidth * 0.35f
        val centerX =
            x + width * (if (mirror) 1 - feet.centerX else feet.centerX) +
                (sun.dx * originalWidth * 0.25 * sun.length).toFloat()
        val centerY = y + feet.bottom * height + shadowHeight * 0.3f
        batch.setColor(0f, 0f, 0f, 0.16f * daylight)
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
