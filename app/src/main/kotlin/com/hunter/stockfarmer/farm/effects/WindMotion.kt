package com.hunter.stockfarmer.farm.effects

import com.hunter.stockfarmer.core.rules.Season
import com.hunter.stockfarmer.core.util.RandomSource
import com.hunter.stockfarmer.core.util.SystemRandomSource
import com.hunter.stockfarmer.core.util.nextDouble
import com.hunter.stockfarmer.core.util.nextInt
import com.hunter.stockfarmer.core.util.roundedHalfAwayFromZero
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal object WindWisp {
    fun path(
        phase: Double,
        height: Double,
        radius: Double,
        right: Boolean,
    ): List<Point> {
        val points =
            (0 until 70)
                .map { step ->
                    val along = step / 69.0
                    Point(along * 300, height * sin(along * 2 * PI * 1.1 + phase))
                }.toMutableList()
        val end = points.last()
        for (step in 1..40) {
            val turn = step * 0.17
            val ring = radius * (1 - step / 42.0)
            points += Point(end.x + ring * sin(turn), end.y + radius - ring * cos(turn))
        }
        val sign = if (right) 1 else -1
        return points
            .map {
                Point(
                    (it.x * sign / 4).roundedHalfAwayFromZero() * 4,
                    (it.y / 4).roundedHalfAwayFromZero() * 4,
                )
            }.distinct()
    }

    fun alpha(
        age: Double,
        index: Int,
        strength: Double,
    ): Double {
        val time = age - index * 0.012
        return when {
            time < 0 -> 0.0
            time < 0.12 -> strength * time / 0.12
            time < 0.67 -> strength
            else -> strength * (1 - (time - 0.67) / 0.4).coerceAtLeast(0.0)
        }
    }
}

internal data class Wisp(
    val start: Point,
    val born: Double,
    val points: List<Point>,
    val right: Boolean,
    val strength: Double,
    val drift: Point,
) {
    val life get() = points.size * 0.012 + 1.07

    fun point(
        index: Int,
        age: Double,
    ): Point {
        val progress = (age / life).coerceIn(0.0, 1.0)
        val eased = 1 - (1 - progress) * (1 - progress)
        return Point(start.x + points[index].x + drift.x * eased, start.y + points[index].y + drift.y * eased)
    }
}

internal data class Leaf(
    val born: Double,
    val color: String,
    val side: Double,
    val right: Boolean,
    val startY: Double,
    val duration: Double,
    val sink: Double,
    val bob: Double,
    val frameTime: Double,
) {
    fun point(
        age: Double,
        width: Double,
    ): Point {
        val sign = if (right) 1 else -1
        return Point(
            -sign * (width / 2 + side) + sign * (width + side * 2) * age / duration,
            startY - sink * age / duration + bob * (0.5 - 0.5 * cos(PI * age / 0.9)),
        )
    }
}

internal class WindMotion(
    private val random: RandomSource = SystemRandomSource,
) {
    val wisps = mutableListOf<Wisp>()
    val leaves = mutableListOf<Leaf>()
    var time = 0.0
        private set
    private var nextGust = 30.0

    /** How many gusts have set off, and which way the latest blew. */
    var gusts = 0
        private set
    var gustRight = true
        private set
    private var nextLeaves = 0.0
    private var season: Season? = null

    fun update(
        seconds: Double,
        season: Season,
        raining: Boolean,
        right: Boolean,
        worldView: View,
        screen: View,
    ) {
        time += seconds
        if (this.season != season) {
            this.season = season
            nextLeaves = time + 60
            leaves.clear()
        }
        if (raining) leaves.clear()
        wisps.removeAll { time - it.born >= it.life }
        leaves.removeAll { time - it.born >= it.duration }
        if (time < nextGust) return
        nextGust = time + 30
        gusts++
        gustRight = right
        gust(right, worldView)
        if (season != Season.fall || raining || time < nextLeaves || screen.width <= 0) return
        nextLeaves = time + 60
        repeat(random.nextInt(5..10)) {
            leaves +=
                Leaf(
                    time + 1 + random.nextDouble(0.0, 2.5),
                    listOf("orange", "red", "yellow")[random.nextInt(0..2)],
                    random.nextDouble(56.0, 84.0),
                    right,
                    random.nextDouble(-screen.height * 0.4, screen.height * 0.5),
                    random.nextDouble(6.0, 10.0),
                    random.nextDouble(60.0, 200.0),
                    random.nextDouble(15.0, 40.0),
                    random.nextDouble(0.07, 0.13),
                )
        }
    }

    private fun gust(
        right: Boolean,
        view: View,
    ) {
        val left = maxOf(0.0, view.left)
        val bottom = maxOf(0.0, view.bottom)
        val width = minOf(WORLD, view.right) - left
        val height = minOf(WORLD, view.top) - bottom
        if (width <= 0 || height <= 0) return
        repeat(4) { slot ->
            val share = random.nextDouble(0.0, 1.0).let { it * it * 0.85 }
            val x = if (right) left + share * width else left + width - share * width
            val start = Point(x, bottom + height / 4 * (slot + random.nextDouble(0.1, 0.9)))
            wisps +=
                Wisp(
                    start,
                    time + random.nextDouble(0.0, 5.0),
                    WindWisp.path(
                        random.nextDouble(0.0, 2 * PI),
                        random.nextDouble(8.0, 14.0),
                        random.nextDouble(15.0, 26.0),
                        right,
                    ),
                    right,
                    random.nextDouble(0.56, 0.8),
                    Point((if (right) 1 else -1) * random.nextDouble(131.0, 219.0), random.nextDouble(6.0, 22.0)),
                )
        }
    }
}
