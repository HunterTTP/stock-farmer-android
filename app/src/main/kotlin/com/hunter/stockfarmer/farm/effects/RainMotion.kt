package com.hunter.stockfarmer.farm.effects

import com.hunter.stockfarmer.core.rules.Season
import com.hunter.stockfarmer.core.rules.Weather
import com.hunter.stockfarmer.core.util.RandomSource
import com.hunter.stockfarmer.core.util.SystemRandomSource
import com.hunter.stockfarmer.core.util.nextDouble
import com.hunter.stockfarmer.core.util.secondsSince
import java.time.Instant
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

internal data class Precipitation(
    val point: Point,
    val size: Double,
    val alpha: Double,
    val rotation: Double,
)

internal class RainMotion(
    private val random: RandomSource = SystemRandomSource,
) {
    private data class Drop(
        val x: Double,
        val age: Double,
        val life: Double,
        val speed: Double,
        val scale: Double,
        val alpha: Double,
    )

    private val drops = mutableListOf<Drop>()
    private var snow: Boolean? = null
    private var width = 0.0
    private var height = 0.0

    fun update(
        seconds: Double,
        amount: Double,
        snowing: Boolean,
        pushesRight: Boolean,
        width: Double,
        height: Double,
    ): List<Precipitation> {
        if (amount <= 0 || width <= 0 || height <= 0) {
            drops.clear()
            return emptyList()
        }
        val speed = if (snowing) 120.0 else 800.0
        val range = if (snowing) 40.0 else 150.0
        val slant = if (snowing) 0.12 else 0.3
        val life = (height + 80) / speed
        val drift = (height + 80) * tan(slant)
        val count = ceil((if (snowing) 45 else 90) * life).toInt()
        val scaleBase = if (snowing) 0.8 else 0.9
        val scaleRange = if (snowing) 0.2 else 0.15

        fun drop(prefill: Boolean): Drop =
            Drop(
                random.nextDouble(-drift / 2, width + drift / 2),
                if (prefill) random.nextDouble(0.0, life) else 0.0,
                life + random.nextDouble(-0.05, 0.05),
                speed + random.nextDouble(-range / 2, range / 2),
                scaleBase + random.nextDouble(-scaleRange, scaleRange),
                (if (snowing) 0.85 else 0.3) + random.nextDouble(-0.075, 0.075),
            )
        if (snow != snowing || this.width != width || this.height != height || drops.isEmpty()) {
            drops.clear()
            repeat(count) { drops += drop(true) }
        }
        snow = snowing
        this.width = width
        this.height = height
        val sign = if (pushesRight) 1 else -1
        return drops.indices.map { index ->
            var d = drops[index].copy(age = drops[index].age + seconds)
            if (d.age >= d.life) d = drop(false)
            drops[index] = d
            Precipitation(
                Point(
                    d.x - sign * drift / 2 + sign * sin(slant) * d.speed * d.age,
                    height + 40 - cos(slant) * d.speed * d.age,
                ),
                d.scale,
                d.alpha * Weather.eased(amount),
                if (snowing) 0.0 else sign * slant * 180 / Math.PI,
            )
        }
    }

    val count get() = drops.size
}

internal data class SkyColor(
    val red: Float,
    val green: Float,
    val blue: Float,
)

internal fun Season.skyRGB(): SkyColor =
    when (this) {
        Season.spring -> SkyColor(0.07f, 0.09f, 0.07f)
        Season.fall -> SkyColor(0.09f, 0.065f, 0.035f)
        Season.winter -> SkyColor(0.04f, 0.06f, 0.12f)
    }

internal fun snowAlpha(radius: Double): Double = if (radius <= 0.35) 1.0 else ((1 - radius) / 0.65).coerceIn(0.0, 1.0)

internal data class WeatherLook(
    val amount: Double,
    val snow: Boolean,
    val pushesRight: Boolean,
)

/** Mirrors WeatherScene's rain tolerance and once-a-second wind sampling. */
internal class WeatherPresentation {
    private var look = WeatherLook(-1.0, false, false)
    private var windAt: Instant? = null

    fun update(
        now: Instant,
        amount: Double,
        snow: Boolean,
        pushesRight: Boolean,
    ): WeatherLook {
        if (windAt == null || now.secondsSince(windAt!!) >= 1) {
            windAt = now
            look = look.copy(pushesRight = pushesRight)
        }
        if (abs(amount - look.amount) > 0.002 || (amount == 0.0) != (look.amount == 0.0) || snow != look.snow) {
            look = look.copy(amount = amount, snow = snow)
        }
        return look
    }
}
