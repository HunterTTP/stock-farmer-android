package com.hunter.stockfarmer.farm

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.util.RandomSource
import com.hunter.stockfarmer.core.util.SystemRandomSource
import com.hunter.stockfarmer.core.util.nextDouble
import com.hunter.stockfarmer.core.util.secondsSince
import com.hunter.stockfarmer.core.util.shuffled
import java.time.Instant
import kotlin.math.pow

/**
 * Now and then a gust leans the tip of some of the trees the way it blows, then they straighten, leaving their feet where they are.
 * Which trees a gust reaches, and how far each one bends, is chance; each is reached as the gust sweeps across the map. The picture
 * is drawn as horizontal strips whose edges shift, the top most, as the iOS warp grid does.
 */
class TreeLean(
    private val random: RandomSource = SystemRandomSource,
) {
    private class Lean(
        val start: Instant,
        val bend: Float,
    )

    private val leans = HashMap<GridPoint, Lean>()
    private var gust: Pair<Boolean, Instant>? = null
    private val strips = FloatArray(STRIPS * QUAD_FLOATS)

    /** Whether a gust has set off that has yet to be dealt out to the trees. */
    val isPending get() = gust != null

    /** A gust has set off, blowing towards the right of the map or not; [reach] says which trees it takes hold of. */
    fun gust(
        pushesRight: Boolean,
        at: Instant,
    ) {
        gust = pushesRight to at
    }

    /** Deals the pending gust out to a random share of the trees, given each tree's origin and the world x of the middle of its foot. */
    fun reach(trees: Map<GridPoint, Float>) {
        val (pushesRight, at) = gust ?: return
        gust = null
        leans.clear()
        if (trees.isEmpty()) return
        val reached =
            maxOf(1, (trees.size * random.nextDouble(SHARE_REACHED.start, SHARE_REACHED.endInclusive)).toInt())
        for (origin in trees.keys
            .toList()
            .shuffled(random)
            .take(reached)) {
            val bend = random.nextDouble(TIP_BEND.start, TIP_BEND.endInclusive).toFloat() * if (pushesRight) 1 else -1
            val delay = WeathercockTurn.gustDelay(trees.getValue(origin), pushesRight)
            leans[origin] = Lean(at.plusMillis((delay * 1000).toLong()), bend)
        }
    }

    /** How far the tip of the tree at [origin] is pushed just now, as a fraction of its width; 0 while it stands straight. */
    fun tipBend(
        origin: GridPoint,
        now: Instant,
    ): Float {
        val lean = leans[origin] ?: return 0f
        val age = now.secondsSince(lean.start)
        if (age >= DURATION) {
            leans.remove(origin)
            return 0f
        }
        return lean.bend * swing(age)
    }

    /** Draws a tree of [texture] whose bottom left is at [x] and [y], leaned to [bend]; straight, it is a single sprite. */
    fun draw(
        batch: SpriteBatch,
        texture: Texture,
        bend: Float,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
    ) {
        if (bend == 0f) {
            batch.draw(texture, x, y, width, height)
            return
        }
        val color = batch.packedColor
        val stripHeight = height / STRIPS
        for (strip in 0 until STRIPS) {
            val bottom = y + strip * stripHeight
            val low = rowShift(bend, strip) * width
            val high = rowShift(bend, strip + 1) * width
            val vBottom = 1f - strip.toFloat() / STRIPS
            val vTop = 1f - (strip + 1f) / STRIPS
            val first = strip * QUAD_FLOATS
            put(first, x + low, bottom, color, 0f, vBottom)
            put(first + VERTEX_FLOATS, x + high, bottom + stripHeight, color, 0f, vTop)
            put(first + 2 * VERTEX_FLOATS, x + width + high, bottom + stripHeight, color, 1f, vTop)
            put(first + 3 * VERTEX_FLOATS, x + width + low, bottom, color, 1f, vBottom)
        }
        batch.draw(texture, strips, 0, strips.size)
    }

    private fun put(
        at: Int,
        x: Float,
        y: Float,
        color: Float,
        u: Float,
        v: Float,
    ) {
        strips[at] = x
        strips[at + 1] = y
        strips[at + 2] = color
        strips[at + 3] = u
        strips[at + 4] = v
    }

    companion object {
        /** The strips a tree is cut into, which the lean shifts more the higher they are. */
        const val STRIPS = 4
        private const val VERTEX_FLOATS = 5
        private const val QUAD_FLOATS = 4 * VERTEX_FLOATS
        private const val PEAK = 1.3
        private const val DURATION = 2.8

        /** How far a gust pushes the tip of a tree, as a fraction of its width, and the share of the trees a gust reaches. */
        private val TIP_BEND = 0.03..0.075
        private val SHARE_REACHED = 0.25..0.6

        /** How much of its bend a tree has reached [age] seconds after the gust arrives: it leans for 1.3 seconds and straightens in 1.5 more. */
        fun swing(age: Double): Float =
            when {
                age <= 0 || age >= DURATION -> 0f
                age < PEAK -> (age / PEAK).toFloat()
                else -> ((DURATION - age) / (DURATION - PEAK)).toFloat()
            }

        /** How far the edge [row] strips up a tree, counting from 0 at its foot to [STRIPS] at its top, moves for a [bend] at the tip. */
        fun rowShift(
            bend: Float,
            row: Int,
        ): Float = bend * (row.toFloat() / STRIPS).pow(2.5f)
    }
}
