package com.hunter.stockfarmer.core.model

import com.hunter.stockfarmer.core.catalog.FishKind
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.util.InstantSerializer
import com.hunter.stockfarmer.core.util.advancedBy
import kotlinx.serialization.Serializable
import java.time.Instant

/** The fish swimming in a pond, which is seen only as a shadow until the rod catches it. */
@Serializable
data class PondFish(
    val kindID: String,
    val position: GridPoint,
    /** What it weighs in pounds, settled when it appears. */
    val weight: Double? = null,
    /** When it appeared, on the farm's clock. */
    @Serializable(with = InstantSerializer::class) val appearedAt: Instant? = null,
    /**
     * False for a fish the player put into the water: it stays until caught, is not counted against how many fish appear
     * by themselves, and earns no XP when caught.
     */
    val isNaturallyGenerated: Boolean = true,
) {
    val kind: FishKind? get() = FishKind.named(kindID)

    /** How big its picture is drawn against an average one of its kind, from its weight. */
    val sizeFactor: Double
        get() {
            val kind = kind ?: return 1.0
            val weight = weight ?: return 1.0
            return kind.sizeFactor(weight)
        }
}

/**
 * One of a pond's two fish homes: its fish, if present, and its independent respawn timer. The saved home tile
 * identifies this fish through swimming and catching.
 */
@Serializable
data class FishPond(
    val fish: PondFish? = null,
    @Serializable(with = InstantSerializer::class) val nextFishAt: Instant,
    @Serializable(with = InstantSerializer::class) val waitStartedAt: Instant =
        nextFishAt.advancedBy(-GameEngine.FISH_INTERVAL),
)
