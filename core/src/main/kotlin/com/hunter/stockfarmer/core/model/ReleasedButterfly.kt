package com.hunter.stockfarmer.core.model

import com.hunter.stockfarmer.core.rules.Butterflies
import com.hunter.stockfarmer.core.util.UuidSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * A butterfly the player let go on the farm: it stays and flutters about until it is caught, and starts from the tile
 * it was let go on.
 */
@Serializable
data class ReleasedButterfly(
    @Serializable(with = UuidSerializer::class) val id: UUID = UUID.randomUUID(),
    val wings: Butterflies.Wings,
    val home: GridPoint,
)
