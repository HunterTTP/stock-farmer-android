package com.hunter.stockfarmer.ui.fishing

import com.hunter.stockfarmer.core.catalog.FishKind
import com.hunter.stockfarmer.core.catalog.WaterStyle
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.PI

class FishingVisualsTest {
    @Test
    fun `phone bounds reserve the safe areas meter and tool circle in screen points`() {
        val area = FishingArea(400.0, 850.0, left = 10.0, top = 24.0, right = 12.0, bottom = 20.0)
        val bounds = area.bounds
        assertEquals(78.0, bounds.minX)
        assertEquals(320.0, bounds.maxX)
        assertEquals(220.0, bounds.minY)
        assertEquals(660.0, bounds.maxY)
        val tablet = area.copy(controlScale = 1.3).bounds
        assertEquals(258.4, tablet.minY, 0.000001)
        assertEquals(629.4, tablet.maxY, 0.000001)
    }

    @Test
    fun `compact windows still leave a nonempty visible swimming area`() {
        val area = FishingArea(120.0, 200.0, top = 24.0, bottom = 24.0)
        val bounds = area.bounds
        assertTrue(bounds.minX >= 0 && bounds.maxX <= area.width)
        assertTrue(bounds.minY >= 0 && bounds.maxY <= area.height)
        assertTrue(bounds.minX < bounds.maxX && bounds.minY < bounds.maxY)
    }

    @Test
    fun `sideways fish mirror on rightward headings and stay upright on leftward headings`() {
        assertEquals(FishingFishPose(true, 0f), fishingFishPose("carp", 0.0))
        assertEquals(FishingFishPose(false, 0f), fishingFishPose("carp", PI))
        val downRight = fishingFishPose("carp", PI / 4)
        assertTrue(downRight.mirrored)
        assertEquals(45f, downRight.degrees)
        val downLeft = fishingFishPose("carp", PI * 0.75)
        assertFalse(downLeft.mirrored)
        assertEquals(-45f, downLeft.degrees)
    }

    @Test
    fun `upright species rotate their heads along the swimming direction without mirroring`() {
        for (kind in listOf("jellyfish", "lobster", "octopus", "squid")) {
            assertEquals(FishingFishPose(false, 0f), fishingFishPose(kind, -PI / 2))
            assertEquals(FishingFishPose(false, 90f), fishingFishPose(kind, 0.0))
            assertEquals(FishingFishPose(false, 180f), fishingFishPose(kind, PI / 2))
        }
    }

    @Test
    fun `water loops in five seconds and neighboring tiles use different phase groups`() {
        assertEquals(0, FishingWater.frame(0.0, 0, 0))
        assertEquals(59, FishingWater.frame(4.99, 0, 0))
        assertEquals(0, FishingWater.frame(5.0, 0, 0))
        assertEquals(10, FishingWater.frame(0.0, 1, 0))
        assertEquals(50, FishingWater.frame(0.0, 0, 1))
        for (style in WaterStyle.entries) {
            val water = FishingWater(style)
            assertArrayEquals(water.pixels(0), water.pixels(60))
            assertEquals(256, water.pixels(0).size)
            assertTrue(water.pixels(0).all { it ushr 24 == 255 })
            assertTrue(water.pixels(0, glowing = true).all { it ushr 24 in 0..102 })
            assertArrayEquals(water.pixels(17), FishingWater(style).pixels(17))
        }
    }

    @Test
    fun `every fishing species has decodable bundled picture art`() {
        FishKind.all.forEach { kind ->
            val path = "images/animals/Fish/${kind.assetName}.png"
            val file = File("src/main/assets", path)
            assertTrue(file.isFile, path)
            val image = ImageIO.read(file)
            assertTrue(image.width > 0 && image.height > 0, path)
        }
    }
}
