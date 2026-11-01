package com.hunter.stockfarmer.farm.effects

import com.hunter.stockfarmer.core.catalog.WaterStyle
import com.hunter.stockfarmer.core.rules.Season
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

internal class WeatherMotionTest {
    @Test
    fun `season sky keeps reference components without eight bit quantization and snow has a solid center`() {
        assertEquals(SkyColor(0.07f, 0.09f, 0.07f), Season.spring.skyRGB())
        assertEquals(SkyColor(0.09f, 0.065f, 0.035f), Season.fall.skyRGB())
        assertEquals(SkyColor(0.04f, 0.06f, 0.12f), Season.winter.skyRGB())
        assertEquals(1.0, snowAlpha(0.0))
        assertEquals(1.0, snowAlpha(0.35))
        assertEquals(0.5, snowAlpha(0.675), 1e-9)
        assertEquals(0.0, snowAlpha(1.0))
    }

    @Test
    fun `weather tolerates small rain changes but always stops at zero and samples wind once per second`() {
        val weather = WeatherPresentation()
        val now = Instant.EPOCH
        assertEquals(WeatherLook(0.5, false, true), weather.update(now, 0.5, false, true))
        assertEquals(WeatherLook(0.5, false, true), weather.update(now.plusMillis(500), 0.501, false, false))
        assertEquals(WeatherLook(0.501, true, false), weather.update(now.plusSeconds(1), 0.501, true, false))
        weather.update(now.plusSeconds(2), 0.001, true, false)
        assertEquals(0.0, weather.update(now.plusSeconds(3), 0.0, true, false).amount)
    }

    @Test
    fun `ambient time stops on first frame and limits resume jumps`() {
        val clock = EffectClock()
        val now = Instant.EPOCH
        assertEquals(0.0, clock.advance(now))
        assertEquals(0.05, clock.advance(now.plusMillis(50)), 1e-9)
        assertEquals(0.1, clock.advance(now.plusSeconds(60)), 1e-9)
        assertEquals(0.0, clock.advance(now.plusSeconds(60)))
        assertEquals(0.0, clock.advance(now))
        assertEquals(0.15, clock.time, 1e-9)
    }

    @Test
    fun `a shower starts full and stops without lingering particles`() {
        val rain = RainMotion(seeded(4))
        val drops = rain.update(0.0, 1.0, false, true, 1000.0, 800.0)
        assertEquals(100, drops.size)
        assertTrue(drops.any { it.point.y < 400 })
        assertTrue(drops.all { it.alpha in 0.225..0.375 })
        assertTrue(rain.update(0.1, 0.0, false, true, 1000.0, 800.0).isEmpty())
        assertEquals(0, rain.count)
    }

    @Test
    fun `rain leans downwind and intensity eases while snow stays upright`() {
        val right = RainMotion(seeded(9)).update(0.0, 0.5, false, true, 800.0, 1000.0)
        val left = RainMotion(seeded(9)).update(0.0, 1.0, false, false, 800.0, 1000.0)
        assertEquals(left.size, right.size)
        right.zip(left).forEach { (r, l) ->
            assertEquals(r.alpha * 2, l.alpha, 1e-9)
            assertEquals(r.rotation, -l.rotation)
        }
        val snow = RainMotion(seeded(9)).update(0.0, 1.0, true, false, 800.0, 1000.0)
        assertTrue(snow.all { it.rotation == 0.0 && it.alpha in 0.775..0.925 })
        assertTrue(snow.size > right.size)
    }

    @Test
    fun `changing precipitation kind rebuilds the emitter and resizing fills the new view`() {
        val motion = RainMotion(seeded(2))
        motion.update(0.0, 1.0, false, true, 100.0, 100.0)
        val snow = motion.update(0.0, 1.0, true, true, 1000.0, 1000.0)
        assertEquals(405, snow.size)
        assertTrue(snow.any { it.point.x > 500 && it.point.y > 500 })
    }

    @Test
    fun `water frames loop with six evenly spaced phase groups`() {
        assertEquals(0, WaterAnimation.frame(0.0, 0))
        assertEquals(10, WaterAnimation.frame(0.0, 1))
        assertEquals(50, WaterAnimation.frame(0.0, 5))
        assertEquals(WaterAnimation.frame(0.2, 3), WaterAnimation.frame(5.2, 3))
        for (style in WaterStyle.entries) {
            val (low, high) = WaterPalette.colors(style)
            val pixels = WaterAnimation.pixels(style, 0)
            assertEquals(256, pixels.size)
            assertTrue(
                pixels.all { pixel ->
                    (pixel ushr 24) in low.red..high.red &&
                        ((pixel ushr 16) and 255) in low.green..high.green &&
                        ((pixel ushr 8) and 255) in low.blue..high.blue && (pixel and 255) == 255
                },
            )
            assertTrue(pixels.contentEquals(WaterAnimation.pixels(style, 60)))
            assertFalse(pixels.contentEquals(WaterAnimation.pixels(style, 5)))
        }
    }

    @Test
    fun `water glow uses squared brightness and drops share the water palette`() {
        val glows = WaterAnimation.pixels(WaterStyle.deep, 3, glow = true)
        assertTrue(glows.all { (it and 255) in 0..102 })
        assertTrue(glows.any { (it and 255) > 90 })
        assertTrue(glows.any { (it and 255) < 5 })
        assertEquals(6, WaterDrops.colors.size)
        WaterDrops.colors.zip(WaterDrops.spray).forEach { (water, spray) ->
            assertTrue(spray.red > water.red && spray.green > water.green && spray.blue > water.blue)
        }
        assertEquals(TILE / 16, WaterDrops.SIZE)
    }
}
