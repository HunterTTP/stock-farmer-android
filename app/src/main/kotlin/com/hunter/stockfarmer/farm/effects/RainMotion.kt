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

/**
 * Falling rain or snow held as parallel arrays so a frame allocates nothing. After [update] the first [count]
 * entries of [x], [y], [size] and [alpha] are the drops to draw, in points from the bottom-left of the view.
 */
internal class RainMotion(
    private val random: RandomSource = SystemRandomSource,
) {
    private var capacity = 0
    private var dropX = DoubleArray(0)
    private var age = DoubleArray(0)
    private var life = DoubleArray(0)
    private var speed = DoubleArray(0)
    private var scale = DoubleArray(0)
    private var baseAlpha = DoubleArray(0)

    var x = FloatArray(0)
        private set
    var y = FloatArray(0)
        private set
    var size = FloatArray(0)
        private set
    var alpha = FloatArray(0)
        private set

    /** Degrees to turn each drop; zero for snow. */
    var rotation = 0.0
        private set
    var snow = false
        private set
    var count = 0
        private set
    private var width = 0.0
    private var height = 0.0
    private var built = false

    /** Advances every drop and returns how many are in flight. */
    fun update(
        seconds: Double,
        amount: Double,
        snowing: Boolean,
        pushesRight: Boolean,
        width: Double,
        height: Double,
    ): Int {
        if (amount <= 0 || width <= 0 || height <= 0) {
            count = 0
            built = false
            return 0
        }
        val fall = if (snowing) 120.0 else 800.0
        val slant = if (snowing) 0.12 else 0.3
        val lifeBase = (height + 80) / fall
        val drift = (height + 80) * tan(slant)
        if (!built || snow != snowing || this.width != width || this.height != height) {
            build(snowing, width, lifeBase, drift, fall)
        }
        snow = snowing
        this.width = width
        this.height = height
        val sign = if (pushesRight) 1 else -1
        rotation = if (snowing) 0.0 else sign * slant * 180 / Math.PI
        val eased = Weather.eased(amount)
        val sideways = sign * sin(slant)
        val downwards = cos(slant)
        val offset = sign * drift / 2
        for (i in 0 until count) {
            age[i] += seconds
            if (age[i] >= life[i]) respawn(i, false, snowing, width, lifeBase, drift, fall)
            x[i] = (dropX[i] - offset + sideways * speed[i] * age[i]).toFloat()
            y[i] = (height + 40 - downwards * speed[i] * age[i]).toFloat()
            size[i] = scale[i].toFloat()
            alpha[i] = (baseAlpha[i] * eased).toFloat()
        }
        return count
    }

    private fun build(
        snowing: Boolean,
        width: Double,
        lifeBase: Double,
        drift: Double,
        fall: Double,
    ) {
        count = ceil((if (snowing) 45 else 90) * lifeBase).toInt()
        if (count > capacity) {
            capacity = count
            dropX = DoubleArray(count)
            age = DoubleArray(count)
            life = DoubleArray(count)
            speed = DoubleArray(count)
            scale = DoubleArray(count)
            baseAlpha = DoubleArray(count)
            x = FloatArray(count)
            y = FloatArray(count)
            size = FloatArray(count)
            alpha = FloatArray(count)
        }
        for (i in 0 until count) respawn(i, true, snowing, width, lifeBase, drift, fall)
        built = true
    }

    private fun respawn(
        i: Int,
        prefill: Boolean,
        snowing: Boolean,
        width: Double,
        lifeBase: Double,
        drift: Double,
        fall: Double,
    ) {
        val range = if (snowing) 40.0 else 150.0
        val scaleBase = if (snowing) 0.8 else 0.9
        val scaleRange = if (snowing) 0.2 else 0.15
        dropX[i] = random.nextDouble(-drift / 2, width + drift / 2)
        age[i] = if (prefill) random.nextDouble(0.0, lifeBase) else 0.0
        life[i] = lifeBase + random.nextDouble(-0.05, 0.05)
        speed[i] = fall + random.nextDouble(-range / 2, range / 2)
        scale[i] = scaleBase + random.nextDouble(-scaleRange, scaleRange)
        baseAlpha[i] = (if (snowing) 0.85 else 0.3) + random.nextDouble(-0.075, 0.075)
    }
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
