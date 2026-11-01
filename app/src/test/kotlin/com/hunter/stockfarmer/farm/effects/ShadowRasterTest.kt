package com.hunter.stockfarmer.farm.effects

import com.hunter.stockfarmer.core.rules.Sun
import com.hunter.stockfarmer.core.rules.SunShadow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ShadowRasterTest {
    @Test
    fun `shadows stay behind the caster and are soft at the edge`() {
        val shape = ShadowShape(ShadowKind.BUILDING, 200.0, 250.0)
        val image = ShadowRaster.render(shape, Sun.shadow(720.0), 0.5)!!
        assertTrue(image.bottom >= -4)
        assertTrue(image.alpha.all { it in 0f..1f })
        assertTrue(image.alpha.any { it > 0.5f })
        assertTrue(image.alpha.any { it > 0f && it < 0.1f })
        assertTrue(image.alpha.take(image.width).max() < 0.1f)
    }

    @Test
    fun `sun throws shadows toward opposite sides in morning and evening`() {
        val shape = ShadowShape(ShadowKind.BUILDING, 100.0, 200.0)
        val morning = ShadowRaster.render(shape, Sun.shadow(420.0), 0.5)!!
        val evening = ShadowRaster.render(shape, Sun.shadow(1020.0), 0.5)!!

        fun center(image: ShadowImage): Double {
            val total = image.alpha.sum()
            return image.alpha.indices.sumOf { index ->
                (image.left + (index % image.width + 0.5) / ShadowRaster.RESOLUTION) * image.alpha[index]
            } / total
        }
        assertTrue(center(morning) < 0)
        assertTrue(center(evening) > 0)
    }

    @Test
    fun `picture shadows follow sprite alpha and missing art casts nothing`() {
        val shape = ShadowShape(ShadowKind.TREE, 100.0, 200.0, "tree")
        val sun = SunShadow(0.0, 1.0, 1.2, 1.0)
        val clear = AlphaMask(2, 2, ByteArray(4))
        val solid = AlphaMask(2, 2, ByteArray(4) { 255.toByte() })
        val empty = ShadowRaster.render(shape, sun, 0.5, clear)!!
        val full = ShadowRaster.render(shape, sun, 0.5, solid)!!
        assertTrue(empty.alpha.all { it == 0f })
        assertTrue(full.alpha.any { it > 0f })
        assertNotNull(ShadowRaster.render(ShadowShape(ShadowKind.FENCE, 12.0, 40.0, baseHeight = 12.0), sun, 0.5))
        assertNull(ShadowRaster.render(shape.copy(width = 0.0), sun, 0.5))
    }

    @Test
    fun `sprite mask keeps its upright orientation`() {
        val mask = AlphaMask(2, 2, byteArrayOf(255.toByte(), 0, 0, 0))
        assertEquals(1.0, mask.at(0.25, 0.75))
        assertEquals(0.0, mask.at(0.75, 0.75))
        assertEquals(0.0, mask.at(0.25, 0.25))
        assertEquals(0.0, mask.at(1.1, 0.5))
    }
}
