package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.util.roundedHalfAwayFromZero
import java.time.Instant
import kotlin.math.floor

/** A repeatable wobble above the last real price, so every screen agrees between pulls. */
object PriceJitter {
    val interval = 1.0
    val maxSpread = 0.0005

    fun step(at: Instant): Long = floor((at.epochSecond.toDouble() + at.nano / 1_000_000_000.0) / interval).toLong()

    fun priceCents(
        ticker: String,
        baseCents: Long,
        step: Long,
    ): Long {
        if (baseCents <= 0) return baseCents
        val wobble = (noise(ticker, step) + noise(ticker, step - 1) + 2) / 4
        val bound = floor(baseCents.toDouble() * maxSpread)
        return baseCents + (wobble * bound).roundedHalfAwayFromZero().toLong()
    }

    private fun noise(
        ticker: String,
        step: Long,
    ): Double {
        var seed = 14_695_981_039_346_656_037uL
        for (byte in ticker.toByteArray(Charsets.UTF_8)) {
            seed =
                (seed xor byte.toUByte().toULong()) * 1_099_511_628_211uL
        }
        var z = seed + step.toULong() * 0x9E37_79B9_7F4A_7C15uL
        z = (z xor (z shr 30)) * 0xBF58_476D_1CE4_E5B9uL
        z = (z xor (z shr 27)) * 0x94D0_49BB_1331_11EBuL
        z = z xor (z shr 31)
        return (z shr 11).toDouble() / (1L shl 53).toDouble() * 2 - 1
    }
}
