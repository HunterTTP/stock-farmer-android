package com.hunter.stockfarmer.core.catalog

import com.hunter.stockfarmer.core.rules.LevelRules
import com.hunter.stockfarmer.core.util.InstantSerializer
import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant
import kotlin.math.max

/** What a workshop makes: it takes some of one thing from storage, works for a while, and gives something worth more. */
data class Recipe(
    val id: String,
    val buildingID: String,
    val input: StorageItem,
    val inputCount: Int,
    val output: StorageItem,
    val outputCount: Int,
    val duration: Double,
    /** What one of what it makes sells for, when it is not simply the output's own value. */
    private val fixedOutputValue: Double? = null,
) {
    val outputValue: Double get() = fixedOutputValue ?: output.sellValue

    val unlockLevel: Int
        get() {
            val buildingLevel = Building.named(buildingID)?.unlockLevel ?: LevelRules.maxLevel
            return when (input) {
                is StorageItem.Ore -> max(buildingLevel, 18)
                is StorageItem.CropItem -> max(buildingLevel, input.crop.unlockLevel)
                else -> buildingLevel
            }
        }

    companion object {
        /**
         * The grain machine mills wheat into flour, the stone oven bakes flour into bread, the woodwork bench saws logs into
         * planks, the tailor table spins wool into cloth and the DIY crafting table fires stones into bricks.
         */
        val all: List<Recipe> by lazy {
            listOf(
                recipe("grainMachine", Crop.starter.item(), 10, StorageItem.Flour, 1, 3.0, 8.0),
                recipe("stoneOven", StorageItem.Flour, 3, StorageItem.Bread, 2, 5.0, 20.0),
                recipe("woodworkBench", StorageItem.Log, 5, StorageItem.Planks, 1, 4.0, 15.0),
                recipe("tailorTable", StorageItem.Wool, 3, StorageItem.Cloth, 1, 8.0, 150.0),
                recipe("diyTable", StorageItem.Stone, 6, StorageItem.Bricks, 2, 6.0, 25.0),
                recipe("dairy", StorageItem.Milk, 3, StorageItem.Cheese, 1, 10.0, 270.0),
                recipe(
                    "tailorTable",
                    Crop("cotton", "Cotton").item(),
                    3,
                    StorageItem.Cloth,
                    2,
                    12.0,
                    150.0,
                    id = "cottonCloth",
                ),
            ) +
                ArtisanGood.entries.map { good ->
                    Recipe(
                        id = good.rawValue,
                        buildingID = good.buildingID,
                        input = good.input,
                        inputCount = good.inputCount,
                        output = StorageItem.Artisan(good),
                        outputCount = 1,
                        duration = good.duration,
                    )
                }
        }

        fun named(id: String): Recipe? = all.firstOrNull { it.id == id }

        fun options(building: Building): List<Recipe> =
            UnlockOrder.sorted(all.filter { it.buildingID == building.id }) { it.unlockLevel }

        fun of(building: Building): Recipe? = all.firstOrNull { it.buildingID == building.id }

        private fun Crop.item() = StorageItem.CropItem(this)

        private fun recipe(
            buildingID: String,
            input: StorageItem,
            inputCount: Int,
            output: StorageItem,
            outputCount: Int,
            minutes: Double,
            outputValue: Double,
            id: String = buildingID,
        ) = Recipe(id, buildingID, input, inputCount, output, outputCount, minutes * 60, outputValue)
    }
}

/** What a workshop is doing: a batch started at some moment, which finishes when its recipe's time has passed. */
@Serializable
data class Workshop(
    @Serializable(with = InstantSerializer::class) val startedAt: Instant,
    val recipeID: String? = null,
) {
    fun isFinished(
        recipe: Recipe,
        now: Instant,
    ): Boolean = elapsedSeconds(now) >= recipe.duration

    fun timeLeft(
        recipe: Recipe,
        now: Instant,
    ): Double = max(0.0, recipe.duration - elapsedSeconds(now))

    private fun elapsedSeconds(now: Instant): Double = Duration.between(startedAt, now).toNanos() / 1e9
}
