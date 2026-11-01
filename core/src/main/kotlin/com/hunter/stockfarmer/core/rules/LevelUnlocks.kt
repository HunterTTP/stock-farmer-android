package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.Decoration
import com.hunter.stockfarmer.core.catalog.HomeUpgrade
import com.hunter.stockfarmer.core.catalog.Rarity
import com.hunter.stockfarmer.core.catalog.Recipe
import com.hunter.stockfarmer.core.catalog.TreeSpecies
import kotlin.math.max

/**
 * Everything that comes with reaching a level, or several in one go, worked out from the catalogs by their unlock levels
 * so the list never has to be kept by hand.
 */
data class LevelUnlocks(
    /** The levels reached. */
    val levels: IntRange,
    val crops: List<Crop>,
    val fruitTrees: List<Crop>,
    /** The farmland tiles these levels add, and how many the field can hold after them. */
    val farmlandTiles: Int,
    val farmlandTotal: Int,
    val buildings: List<Building>,
    val decorations: List<Decoration>,
    val trees: List<TreeSpecies>,
    /** Recipes that unlock later than the workshop that makes them. */
    val recipes: List<Recipe>,
    val toolUpgrades: List<ToolUpgrade>,
    val homeUpgrades: List<HomeUpgrade>,
    /** Kinds of fish that begin to appear. */
    val fishRarities: List<Rarity>,
    /** Whether the rarer fish become more likely. */
    val fishRatesShift: Boolean,
    val stockQuests: Boolean,
) {
    companion object {
        fun unlocked(
            before: Int,
            level: Int,
        ): LevelUnlocks {
            val levels = (before + 1)..max(before + 1, level)

            fun reached(unlockLevel: Int) = unlockLevel in levels
            val ratesShift =
                levels.any { step ->
                    Rarity.entries.any { it.spawnRate(step) != it.spawnRate(step - 1) }
                }
            return LevelUnlocks(
                levels = levels,
                crops = Crop.all.filter { !it.isFruitTree && reached(it.unlockLevel) },
                fruitTrees = Crop.all.filter { it.isFruitTree && reached(it.unlockLevel) },
                farmlandTiles = FarmlandRules.tilesUnlocked(levels.last) - FarmlandRules.tilesUnlocked(before),
                farmlandTotal = FarmlandRules.tilesUnlocked(levels.last),
                buildings = Building.all.filter { reached(it.unlockLevel) },
                decorations = Decoration.all.filter { !it.isRock && reached(it.unlockLevel) },
                trees = TreeSpecies.all.filter { reached(it.unlockLevel) },
                recipes =
                    Recipe.all.filter { recipe ->
                        reached(recipe.unlockLevel) &&
                            recipe.unlockLevel > (Building.named(recipe.buildingID)?.unlockLevel ?: 0)
                    },
                toolUpgrades = ToolUpgrade.all.filter { reached(it.unlockLevel) },
                homeUpgrades = HomeUpgrade.all.filter { reached(it.unlockLevel) },
                fishRarities = Rarity.entries.filter { reached(it.fishUnlockLevel) },
                fishRatesShift = ratesShift,
                stockQuests = reached(QuestRules.stockQuestLevel),
            )
        }
    }
}
