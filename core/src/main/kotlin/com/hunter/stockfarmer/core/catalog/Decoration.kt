package com.hunter.stockfarmer.core.catalog

import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.util.InstantSerializer
import kotlinx.serialization.Serializable
import java.time.Instant
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** What a decoration does besides standing there. */
sealed interface DecorationEffect {
    data object None : DecorationEffect

    /** Farmland within this many tiles counts as being near water, which makes crops grow a little faster. */
    data class Waters(
        val reach: Int,
    ) : DecorationEffect

    /** Crops on farmland within this many tiles grow a little faster. */
    data class Guards(
        val reach: Int,
    ) : DecorationEffect
}

/** A decoration that is set down on bare grass like a fence, on one or more tiles. */
data class Decoration(
    val id: String,
    val name: String,
    /** The picture for each size it comes in; just the one for a decoration that comes in one size. */
    val assetNames: List<String>,
    /** What each size is called, which is empty for a decoration that comes in one size. */
    val sizeNames: List<String>,
    val width: Int,
    val height: Int,
    val cost: Double,
    val unlockLevel: Int,
    val effect: DecorationEffect,
    /** How much of the picture's natural size it is drawn at; the pictures are 48 pixels to a tile. */
    val drawScale: Double,
) {
    val isSprinkler: Boolean get() = id == SPRINKLER_ID || id == BIG_SPRINKLER_ID

    /** A rock is set down in the Decorate tool but stands on the map as a real rock, which the Pickaxe breaks for stones. */
    val isRock: Boolean get() = id == ROCK_ID
    val rockSizes: List<RockSize> get() = RockSize.stoneSizes

    /** Where its pictures are, in `Images`. */
    val imageFolder: String get() = if (isRock) "rocks" else "decorations"

    /** How many tiles out from its ground it waters farmland, if it does. */
    val waterReach: Int? get() = (effect as? DecorationEffect.Waters)?.reach

    /** The tiles it stands on when its bottom left tile is [point], which is where it is tapped or dragged to. */
    fun originSettingBottomLeftOn(point: GridPoint): GridPoint = GridPoint(point.row - (height - 1), point.col)

    fun footprint(origin: GridPoint): List<GridPoint> = origin.rectangle(rows = height, cols = width)

    fun clampedSize(size: Int): Int = min(max(0, size), assetNames.size - 1)

    /** Keep saved variant numbers stable while removing duplicate pictures from new purchases. */
    val offeredSizes: List<Int>
        get() = assetNames.indices.filter { it !in (duplicateSizes[id] ?: emptySet()) }

    fun offeredSize(preferred: Int?): Int {
        if (preferred != null && preferred in offeredSizes) return preferred
        return offeredSizes.firstOrNull() ?: 0
    }

    companion object {
        /** A sprinkler sprays for this long when it is set down, and only waters the farmland around it once it has finished. */
        const val SPRAY_DURATION = 12.0

        /** Every sprinkler sprays together this many real seconds apart, on one schedule counted from the epoch. */
        const val SPRAY_INTERVAL = 60.0

        /** A scarecrow makes crops near it grow this much faster, as water does. */
        const val GUARD_MULTIPLIER = 1.05

        /** The most trunks that can be placed, which is as much storage as a barn. */
        const val TRUNK_LIMIT = 15

        /** Sprinklers are what the Fertilize tool holds besides fertilizer, not part of the Decorate tool. */
        const val SPRINKLER_ID = "sprinkler"

        /** The larger sprinkler sold alongside the standard one. */
        const val BIG_SPRINKLER_ID = "bigSprinkler"
        const val ROCK_ID = "rock"

        /** How many shared sprays have come due by a moment, so it is the same however the game was opened or put away. */
        fun spraysDone(now: Instant): Int = floor(max(0.0, epochSeconds(now)) / SPRAY_INTERVAL).toInt()

        /** How long until the sprinklers next spray together. */
        fun timeUntilNextSpray(now: Instant): Double = SPRAY_INTERVAL - max(0.0, epochSeconds(now)) % SPRAY_INTERVAL

        private fun epochSeconds(now: Instant): Double = now.epochSecond + now.nano / 1e9

        val all: List<Decoration> by lazy {
            listOf(
                Decoration(
                    id = ROCK_ID,
                    name = "Rock",
                    assetNames = RockSize.stoneSizes.map { it.assetName },
                    sizeNames = RockSize.stoneSizes.map { it.displayName },
                    width = 1,
                    height = 1,
                    cost = 30.0,
                    unlockLevel = 1,
                    effect = DecorationEffect.None,
                    drawScale = 1.0,
                ),
                single("woodenTable", "Wooden Table", "Wooden_Table_48x48", 2, 1, 60.0, 5, DecorationEffect.None, 1.0),
                single("scarecrow", "Scarecrow", "Scarecrow_48x48", 1, 1, 150.0, 6, DecorationEffect.Guards(3), 0.5),
                single("trunk", "Trunk", "Trunk_Closed_2_48x48", 1, 1, 300.0, 7, DecorationEffect.None, 0.7),
                Decoration(
                    id = "pumpkin",
                    name = "Pumpkin",
                    assetNames = listOf("Pumpkin_Small_48x48", "Pumpkin_Medium_48x48", "Pumpkin_Big_48x48"),
                    sizeNames = listOf("Small", "Medium", "Big"),
                    width = 1,
                    height = 1,
                    cost = 20.0,
                    unlockLevel = 8,
                    effect = DecorationEffect.None,
                    drawScale = 0.8,
                ),
                single(SPRINKLER_ID, "Sprinkler", "Sprinkler_48x48", 1, 1, 400.0, 9, DecorationEffect.Waters(2), 0.5),
                single("well", "Well", "Well_Usable_No_Handle_48x48", 2, 2, 1_200.0, 11, DecorationEffect.None, 1.0),
                single(
                    "weathercock",
                    "Weathercock",
                    "Weathercock_Base_Roof_Farmer_House_48x48",
                    1,
                    1,
                    500.0,
                    13,
                    DecorationEffect.None,
                    0.6,
                ),
            ) + farmyardDecorations + moduleDecorations +
                single(
                    BIG_SPRINKLER_ID,
                    "Big Sprinkler",
                    "Sprinkler_48x48",
                    1,
                    1,
                    1_600.0,
                    12,
                    DecorationEffect.Waters(3),
                    0.6,
                )
        }

        fun named(id: String): Decoration? = all.firstOrNull { it.id == id }

        val shopChoices: List<Decoration> by lazy {
            UnlockOrder.sorted(all.filter { it.offeredSizes.isNotEmpty() }) { it.unlockLevel }
        }

        private fun single(
            id: String,
            name: String,
            assetName: String,
            width: Int,
            height: Int,
            cost: Double,
            unlockLevel: Int,
            effect: DecorationEffect,
            drawScale: Double,
        ) = Decoration(id, name, listOf(assetName), emptyList(), width, height, cost, unlockLevel, effect, drawScale)

        private val duplicateSizes =
            mapOf(
                "feedTrough" to setOf(1),
                "waterTrough" to setOf(1, 7),
                "tailorDisplay" to setOf(0, 1),
                "cheeseShelf" to setOf(0, 6),
                "craftingSupplies" to setOf(4, 5),
                "woodworkSupplies" to setOf(11, 12),
                "nest" to setOf(0),
            )
    }
}

/** A decoration standing on the map, keyed by its top left tile. */
@Serializable
data class PlacedDecoration(
    val decorationID: String,
    /** Which of its sizes, counting from the smallest, if it comes in more than one. */
    val size: Int = 0,
    /** For a decoration that waters the land, when its first spray ends and it waters all it reaches; null once it has. */
    @Serializable(with = InstantSerializer::class) val wateringStartsAt: Instant? = null,
    /** The farmland its first spray has already watered, which grows as the spray goes on and is dropped once it is over. */
    val wateredTiles: Set<GridPoint> = emptySet(),
    /** When it was set down, which tells a sprinkler just set down (to spray at once) from one already standing. */
    @Serializable(with = InstantSerializer::class) val placedAt: Instant? = null,
) {
    val decoration: Decoration get() = Decoration.named(decorationID) ?: Decoration.all[0]

    companion object {
        fun of(
            decoration: Decoration,
            size: Int = 0,
            wateringStartsAt: Instant? = null,
            placedAt: Instant? = null,
        ) = PlacedDecoration(decoration.id, decoration.clampedSize(size), wateringStartsAt, emptySet(), placedAt)
    }
}
