package com.hunter.stockfarmer.core.model

import kotlinx.serialization.Serializable

/** An egg a hen has laid, which floats over her until the Bucket takes it, and so is on the tile she stands on. */
@Serializable
data class Egg(
    /** The tile the hen stands on, which follows her. */
    val point: GridPoint,
    val offsetX: Double,
    val offsetY: Double,
    /** The coop and id of the hen that laid it, which lays no more while [MAX_PER_HEN] of its eggs lie uncollected. */
    val coop: GridPoint,
    val henID: Int,
) {
    companion object {
        const val SALE_PRICE = 5.0

        /** How far an egg may lie from the center of its tile, in tiles, each way. */
        const val MAX_OFFSET = 0.3

        /** How many of one hen's eggs may wait uncollected at once. */
        const val MAX_PER_HEN = 1

        /** On average, how long a grown hen takes to lay an egg, in seconds. */
        const val LAY_INTERVAL = 3.0 * 60
    }
}
