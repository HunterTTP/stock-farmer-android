package com.hunter.stockfarmer.farm

import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.rules.Season
import com.hunter.stockfarmer.core.util.secondsSince
import com.hunter.stockfarmer.farm.effects.NightLights
import com.hunter.stockfarmer.farm.effects.Point
import com.hunter.stockfarmer.farm.effects.additive
import com.hunter.stockfarmer.farm.effects.glow
import com.hunter.stockfarmer.farm.effects.preserve
import java.time.Instant

/** Explicitly receives wild growth; loading a farm or placing an object never implies a reveal. */
class GrowthReveal {
    private data class Key(
        val origin: GridPoint,
        val tree: Boolean,
    )

    private data class Reveal(
        val start: Instant,
        val season: Season,
    )

    private val reveals = mutableMapOf<Key, Reveal>()

    internal fun prune(
        trees: Set<GridPoint>,
        rocks: Set<GridPoint>,
        now: Instant,
    ) {
        reveals.entries.removeAll { (key, reveal) ->
            now.secondsSince(reveal.start) >= 7 || key.origin !in (if (key.tree) trees else rocks)
        }
    }

    fun reveal(
        trees: Set<GridPoint>,
        rocks: Set<GridPoint>,
        season: Season,
        now: Instant,
    ) {
        trees.forEach { reveals[Key(it, true)] = Reveal(now, season) }
        rocks.forEach { reveals[Key(it, false)] = Reveal(now, season) }
    }

    internal fun collect(
        batch: SpriteBatch,
        textures: TextureLibrary,
        origin: GridPoint,
        tree: Boolean,
        now: Instant,
        depth: Float,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        out: MutableList<Standing>,
    ): Float {
        val key = Key(origin, tree)
        val reveal = reveals[key] ?: return 1f
        val age = now.secondsSince(reveal.start)
        if (age >= 7) {
            reveals.remove(key)
            return 1f
        }
        val color = NightLights.pulseColor(reveal.season)
        out +=
            Standing(depth - 0.3f) {
                batch.preserve {
                    batch.additive()
                    val alpha = VisualTiming.growthGlow(age)
                    batch.setColor(color.red / 255f, color.green / 255f, color.blue / 255f, alpha)
                    batch.glow(
                        textures,
                        Point((x + width / 2).toDouble(), (y + height / 2).toDouble()),
                        maxOf(width, height) * 1.7,
                    )
                }
            }
        return VisualTiming.growthAlpha(age)
    }
}
