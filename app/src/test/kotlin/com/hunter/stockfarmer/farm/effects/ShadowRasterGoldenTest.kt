package com.hunter.stockfarmer.farm.effects

import com.hunter.stockfarmer.core.rules.SunShadow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * Pins [ShadowRaster]'s exact float bits, recorded before its loops were optimized, so a speed-up cannot move a pixel.
 * The offsets go through `Math.sin` and `Math.cos`, which another JVM may round differently; record again only from a
 * raster known to be right.
 */
internal class ShadowRasterGoldenTest {
    private val morning = SunShadow(-0.8, 0.6, 1.2, 1.0)
    private val noon = SunShadow(0.0, 1.0, 0.8, 1.0)
    private val evening = SunShadow(0.7, 0.7, 1.5, 0.6)
    private val flat = SunShadow(1.0, 0.0, 1.0, 1.0)
    private val still = SunShadow(0.0, 0.0, 0.0, 1.0)

    /** A round crown on a thin trunk, with a hole, so nearest-pixel sampling and upright orientation both show. */
    private val crown =
        AlphaMask(
            16,
            24,
            ByteArray(16 * 24) { index ->
                val x = index % 16 - 7.5
                val y = index / 16 - 8.0
                val inCrown = x * x + y * y < 50 && x * x + (y + 2) * (y + 2) > 4
                val inTrunk = index % 16 in 7..8 && index / 16 > 12
                (
                    if (inCrown) {
                        200 + index % 56
                    } else if (inTrunk) {
                        255
                    } else {
                        0
                    }
                ).toByte()
            },
        )

    private fun digest(image: ShadowImage?): String {
        if (image == null) return "null"
        val bytes = ByteBuffer.allocate(24 + image.alpha.size * 4)
        bytes.putInt(image.width).putInt(image.height)
        bytes.putLong(image.left.toRawBits()).putLong(image.bottom.toRawBits())
        for (value in image.alpha) bytes.putInt(value.toRawBits())
        return MessageDigest
            .getInstance("SHA-256")
            .digest(bytes.array())
            .take(12)
            .joinToString("") { "%02x".format(it) }
    }

    private val tree = ShadowShape(ShadowKind.TREE, 215.0, 130.0, "tree")
    private val rock = ShadowShape(ShadowKind.ROCK, 48.0, 36.0, "rock")
    private val statue = ShadowShape(ShadowKind.DECORATION, 96.0, 144.0, "statue")
    private val fence = ShadowShape(ShadowKind.FENCE, 96.0, 38.4, baseLeft = -48.0, baseHeight = 44.0)
    private val post = ShadowShape(ShadowKind.FENCE, 11.5, 38.4, baseLeft = -5.76, baseHeight = 11.5)

    private fun cases(): Map<String, ShadowImage?> =
        mapOf(
            "big building, morning" to
                ShadowRaster.render(
                    ShadowShape(ShadowKind.BUILDING, 200.0, 250.0),
                    morning,
                    0.5,
                ),
            "small building, evening, hard" to
                ShadowRaster.render(ShadowShape(ShadowKind.BUILDING, 96.0, 128.0), evening, 1.0),
            "building, noon, softest" to ShadowRaster.render(ShadowShape(ShadowKind.BUILDING, 300.0, 180.0), noon, 0.0),
            "building, flat sun" to ShadowRaster.render(ShadowShape(ShadowKind.BUILDING, 96.0, 96.0), flat, 0.5),
            "tree, morning" to ShadowRaster.render(tree, morning, 0.5, crown),
            "tree, noon" to ShadowRaster.render(tree, noon, 0.75, crown),
            "tree without its mask" to ShadowRaster.render(tree, morning, 0.5),
            "rock, evening" to ShadowRaster.render(rock, evening, 0.25, crown),
            "decoration, flat sun" to ShadowRaster.render(statue, flat, 0.5, crown),
            "decoration, still sun" to ShadowRaster.render(statue, still, 0.5, crown),
            "fence, noon" to ShadowRaster.render(fence, noon, 0.5),
            "fence post, evening" to ShadowRaster.render(post, evening, 0.5),
            "fence post, still sun" to ShadowRaster.render(post, still, 1.0),
            "nothing" to ShadowRaster.render(ShadowShape(ShadowKind.ROCK, 0.0, 10.0, "rock"), noon, 0.5, crown),
        )

    @Test
    fun `raster output is bit for bit the recorded one`() {
        assertEquals(GOLDEN, cases().mapValues { digest(it.value) })
    }

    private companion object {
        val GOLDEN =
            mapOf(
                "big building, morning" to "bc35129c17002a256b0456ed",
                "small building, evening, hard" to "0ae73005e318bd1ace39150e",
                "building, noon, softest" to "584edae25946c98177b2ad77",
                "building, flat sun" to "c344477443d4e5e0bf745ce6",
                "tree, morning" to "4d47e457b60f55cc9384278f",
                "tree, noon" to "d3292d6b5edd723dbd7eb99f",
                "tree without its mask" to "ff67fb3a34bdcf4114b9ad8c",
                "rock, evening" to "beb37f1228c38f61e119e173",
                "decoration, flat sun" to "3cdb78af37ef44f1c3bf5c3e",
                "decoration, still sun" to "8db69138601ff39aaad0bc6e",
                "fence, noon" to "7b514f8a747b120be3cdcb26",
                "fence post, evening" to "1bccda42ca9d1192d01745e2",
                "fence post, still sun" to "ff7c68ad9ac7849cf9fc517e",
                "nothing" to "null",
            )
    }
}
