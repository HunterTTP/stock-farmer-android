package com.hunter.stockfarmer.farm.effects

import com.hunter.stockfarmer.core.catalog.Rarity
import com.hunter.stockfarmer.core.util.RandomSource
import com.hunter.stockfarmer.core.util.SystemRandomSource
import com.hunter.stockfarmer.core.util.nextBoolean
import com.hunter.stockfarmer.core.util.nextDouble
import com.hunter.stockfarmer.core.util.nextInt
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** World coordinates, with y increasing upward, in FarmRenderer units. */
data class FireworkArea(
    val left: Double,
    val bottom: Double,
    val width: Double,
    val height: Double,
)

internal data class FireworkPixel(
    val point: Point,
    val size: Double,
    val color: RGB,
    val alpha: Double,
    val glowSize: Double = 0.0,
)

internal class FireworkMotion(
    private val random: RandomSource = SystemRandomSource,
) {
    private data class Spark(
        val control: Point,
        val end: Point,
        val color: RGB,
    )

    private data class Rocket(
        val start: Point,
        val born: Double,
        val climb: Point,
        val duration: Double,
        val sparks: List<Spark>,
        val color: RGB,
        val glows: Boolean,
    )

    private val rockets = mutableListOf<Rocket>()
    var time = 0.0
        private set
    private val colors =
        Rarity.entries.map {
            RGB(
                (it.color.red * 255).toInt(),
                (it.color.green * 255).toInt(),
                (
                    it.color.blue *
                        255
                ).toInt(),
            )
        }
    val count get() = rockets.size

    fun reset() {
        rockets.clear()
    }

    fun launch(
        area: FireworkArea,
        count: Int,
        darkness: Double,
    ) {
        require(area.width >= 0 && area.height >= 0)
        repeat(count) { index ->
            val color = colors[random.nextInt(colors.indices)]
            val inner = if (random.nextBoolean()) colors[random.nextInt(colors.indices)] else null
            val sparks =
                List(28) { spark ->
                    val isInner = inner != null && spark % 3 == 0
                    val angle = spark / 28.0 * 2 * PI + random.nextDouble(-0.12, 0.12)
                    val distance = random.nextDouble(60.0, 170.0) * if (isInner) 0.55 else 1.0
                    Spark(
                        Point(cos(angle) * distance * 0.8, sin(angle) * distance + 30),
                        Point(
                            cos(angle) * distance,
                            sin(angle) * distance - random.nextDouble(50.0, 110.0),
                        ),
                        if (isInner) inner!! else color,
                    )
                }
            rockets +=
                Rocket(
                    Point(
                        random.nextDouble(area.left, area.left + area.width),
                        random.nextDouble(
                            area.bottom,
                            area.bottom + area.height,
                        ),
                    ),
                    time + 3.5 * index / count + random.nextDouble(0.0, 0.25),
                    Point(random.nextDouble(-30.0, 30.0), random.nextDouble(3.5, 6.0) * TILE),
                    random.nextDouble(0.7, 1.0),
                    sparks,
                    color,
                    darkness > 0.15,
                )
        }
    }

    fun advance(seconds: Double): List<FireworkPixel> {
        time += seconds
        rockets.removeAll { time >= it.born + it.duration + 1.25 }
        return rockets.flatMap { rocket ->
            val age = time - rocket.born
            if (age < 0) return@flatMap emptyList()

            fun rising(time: Double): Point {
                val progress = (time / rocket.duration).coerceIn(0.0, 1.0)
                val eased = 1 - (1 - progress) * (1 - progress)
                return Point(rocket.start.x + rocket.climb.x * eased, rocket.start.y + rocket.climb.y * eased)
            }
            val pixels = mutableListOf<FireworkPixel>()
            if (age < rocket.duration) pixels += FireworkPixel(rising(age), 6.0, RGB(255, 255, 255), 1.0)
            for (index in 0 until (rocket.duration / 0.035).toInt()) {
                val then = index * 0.035
                val trailAge = age - then
                if (trailAge in
                    0.0..0.35
                ) {
                    pixels += FireworkPixel(rising(then), 4.0, RGB(255, 217, 128), 1 - trailAge / 0.35)
                }
            }
            val burstAge = age - rocket.duration
            if (burstAge >= 0) {
                val center = rising(rocket.duration)
                if (burstAge <
                    0.5
                ) {
                    pixels += FireworkPixel(center, 0.0, rocket.color, 0.8 * (1 - burstAge / 0.5), 340.0)
                }
                val progress = (burstAge / 1.25).coerceIn(0.0, 1.0)
                val t = 1 - (1 - progress) * (1 - progress)
                val alpha = if (progress <= 0.45) 1.0 else (1 - progress) / 0.55
                for (spark in rocket.sparks) {
                    val point =
                        Point(
                            center.x + 2 * (1 - t) * t * spark.control.x + t * t * spark.end.x,
                            center.y + 2 * (1 - t) * t * spark.control.y + t * t * spark.end.y,
                        )
                    pixels += FireworkPixel(point, 6.0, spark.color, alpha, if (rocket.glows) 36.0 else 0.0)
                }
            }
            pixels
        }
    }
}
