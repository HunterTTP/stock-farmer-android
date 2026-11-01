package com.hunter.stockfarmer.core.util

/** A small seeded generator, so tests that use randomness give the same answer every run. */
class SeededRandom(
    seed: Long,
) : RandomSource {
    private var state = seed

    override fun nextLong(): Long {
        state += -0x61c8864680b583ebL
        var z = state
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }
}
