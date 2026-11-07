@file:Suppress("ktlint:standard:enum-entry-name-case")

package com.hunter.stockfarmer.core.presentation

import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.Decoration
import com.hunter.stockfarmer.core.catalog.DecorationEffect
import com.hunter.stockfarmer.core.catalog.Fertilizer
import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.catalog.HomeUpgrade
import com.hunter.stockfarmer.core.catalog.LivestockWork
import com.hunter.stockfarmer.core.catalog.Recipe
import com.hunter.stockfarmer.core.catalog.RockSize
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.catalog.Tree
import com.hunter.stockfarmer.core.catalog.TreeSpecies
import com.hunter.stockfarmer.core.model.Coop
import com.hunter.stockfarmer.core.model.ShopItem
import com.hunter.stockfarmer.core.model.compactDollars
import com.hunter.stockfarmer.core.model.compactDurationText
import com.hunter.stockfarmer.core.model.dollars
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.GrowthConditions
import com.hunter.stockfarmer.core.rules.LevelRules
import com.hunter.stockfarmer.core.rules.QuestRules
import com.hunter.stockfarmer.core.rules.StockMarketRules
import com.hunter.stockfarmer.core.rules.ToolEffect
import com.hunter.stockfarmer.core.rules.ToolUpgrade
import com.hunter.stockfarmer.core.rules.level
import com.hunter.stockfarmer.core.rules.placementLimit
import com.hunter.stockfarmer.core.rules.price
import com.hunter.stockfarmer.core.rules.requiredLevel
import com.hunter.stockfarmer.core.util.roundedHalfAwayFromZero
import java.text.NumberFormat
import java.util.Locale

/** What a stat is about, which sets the color and symbol it is shown with. */
enum class StatKind { money, xp, unlock, level, time, boost, count }

/** A number on an item's page, kept typed so the menus can format and color it. */
sealed interface StatValue {
    data class Money(
        val amount: Double,
    ) : StatValue

    data object Free : StatValue

    data class Duration(
        val seconds: Double,
    ) : StatValue

    data class XP(
        val xp: Int,
    ) : StatValue

    /** The player level something unlocks at, while it is still locked. */
    data class Unlock(
        val level: Int,
    ) : StatValue

    data class Level(
        val level: Int,
    ) : StatValue

    data class Levels(
        val levels: List<Int>,
    ) : StatValue

    /** How many, and what an upgrade to a level takes it to. */
    data class Count(
        val count: Int,
        val upgradedTo: Int? = null,
        val atLevel: Int? = null,
    ) : StatValue

    /** How many it adds, such as storage room. */
    data class Added(
        val count: Int,
    ) : StatValue

    data class Range(
        val low: Int,
        val high: Int,
    ) : StatValue

    /** How much faster something grows, as a fraction: 0.05 is "+5%". */
    data class Boost(
        val fraction: Double,
    ) : StatValue

    /** How often something happens, as a fraction: 0.1 is "10%". */
    data class Chance(
        val fraction: Double,
    ) : StatValue

    data class Multiplier(
        val factor: Double,
    ) : StatValue

    data class Conversion(
        val input: StorageItem,
        val inputCount: Int,
        val output: StorageItem,
        val outputCount: Int,
    ) : StatValue

    data class Change(
        val from: StatValue,
        val to: StatValue,
    ) : StatValue

    val kind: StatKind
        get() =
            when (this) {
                is Money, Free -> StatKind.money
                is Duration -> StatKind.time
                is XP -> StatKind.xp
                is Unlock -> StatKind.unlock
                is Level, is Levels -> StatKind.level
                is Count, is Added, is Range, is Conversion -> StatKind.count
                is Boost, is Chance, is Multiplier -> StatKind.boost
                is Change -> to.kind
            }

    /** The value as shown: compact money ("$14K") and levels ("Lv 7") in dropdown rows, in full on pages. */
    fun text(compact: Boolean): String =
        when (this) {
            is Money -> {
                if (compact) amount.compactDollars else amount.dollars
            }

            Free -> {
                "Free"
            }

            is Duration -> {
                seconds.compactDurationText
            }

            is XP -> {
                if (compact) "+$xp XP" else "+$xp"
            }

            is Unlock -> {
                if (compact) "Lv $level" else "Level $level"
            }

            is Level -> {
                if (compact) "Lv $level" else "Level $level"
            }

            is Levels -> {
                levels.joinToString(" · ") { "Lv $it" }
            }

            is Count -> {
                if (upgradedTo != null && atLevel != null) "$count ($upgradedTo at Lv $atLevel)" else "$count"
            }

            is Added -> {
                "+$count"
            }

            is Range -> {
                if (low == high) "$low" else "$low–$high"
            }

            is Boost -> {
                "+${percent(fraction)}"
            }

            is Chance -> {
                percent(fraction)
            }

            is Multiplier -> {
                "${NumberFormat.getNumberInstance(Locale.US).format(factor)}×"
            }

            is Conversion -> {
                if (compact) "$inputCount → $outputCount" else "$inputCount ${input.name} → $outputCount ${output.name}"
            }

            is Change -> {
                "${from.text(compact)} → ${to.text(compact)}"
            }
        }

    private fun percent(fraction: Double) = "${(fraction * 100).roundedHalfAwayFromZero().toInt()}%"
}

/** One row of an item's stat card: a short label and its value, with what it is counted in after it. */
data class ItemStat(
    val label: String,
    val value: StatValue,
    /** Plain text after the value, such as "/ tile" or "each". */
    val unit: String? = null,
    /** The storage item the value is about, shown in place of the kind's symbol. */
    val item: StorageItem? = null,
    /** The tool that gathers it. */
    val tool: HarvestTool? = null,
) {
    val kind: StatKind get() = value.kind
}

/** What an item's page says about it: its numbers as stats, and at most two short plain notes for what they can't say. */
data class ItemFacts(
    val stats: List<ItemStat>,
    val notes: List<String> = emptyList(),
)

// Plant, Terrain, Fertilize, Decorate and Build items

fun GameEngine.facts(item: ShopItem): ItemFacts {
    val facts = baseFacts(item)
    val level = requiredLevel(item) ?: return facts
    return ItemFacts(listOf(ItemStat("Unlock", StatValue.Unlock(level))) + facts.stats, facts.notes)
}

/** The Terrain tool's Grass, which clears farmland and water. */
val GameEngine.Companion.eraserFacts: ItemFacts
    get() =
        ItemFacts(
            listOf(ItemStat("Cost", StatValue.Free)),
            listOf("Clears farmland and water to grass", "Not where crops are growing"),
        )

private fun GameEngine.baseFacts(item: ShopItem): ItemFacts {
    val price = price(item)
    return when (item) {
        is ShopItem.Seed -> {
            val crop = Crop.named(item.cropID) ?: Crop.starter
            val xp = ItemStat("XP", StatValue.XP(LevelRules.harvestXP(crop)))
            if (!crop.isFruitTree) {
                ItemFacts(
                    listOf(
                        cost(price, "/ tile"),
                        ItemStat("Sells", StatValue.Money(crop.sellValue)),
                        ItemStat("Grows in", StatValue.Duration(crop.growDuration)),
                        xp,
                    ),
                )
            } else {
                ItemFacts(
                    listOf(
                        cost(price, "once"),
                        ItemStat("Sells", StatValue.Money(crop.sellValue), unit = "/ basket"),
                        ItemStat("Ripens in", StatValue.Duration(crop.growDuration)),
                        xp,
                        ItemStat("Limit", StatValue.Count(Crop.MOST_FRUIT_TREES_OF_A_KIND)),
                    ),
                    listOf("Pick it with the Bucket"),
                )
            }
        }

        is ShopItem.GroundCoverItem -> {
            ItemFacts(listOf(cost(price, "/ tile")))
        }

        ShopItem.Farmland -> {
            ItemFacts(listOf(cost(price)), listOf("Crops grow only on farmland", "Each level unlocks more tiles"))
        }

        ShopItem.Water -> {
            ItemFacts(listOf(cost(price), growth(GrowthConditions.SATURATION_MULTIPLIER, "nearby")))
        }

        ShopItem.Fence -> {
            ItemFacts(listOf(cost(price, "each")), listOf("Animals can't walk through"))
        }

        is ShopItem.Tree -> {
            val species = TreeSpecies.named(item.id) ?: TreeSpecies.pine
            ItemFacts(
                listOf(
                    cost(price),
                    ItemStat("Sizes", StatValue.Count(species.trees.size)),
                    ItemStat("Colors", StatValue.Count(species.colors.size)),
                ),
                listOf("Grows bigger over time", "Gives no logs when felled"),
            )
        }

        is ShopItem.DecorationItem -> {
            decorationFacts(Decoration.named(item.id) ?: Decoration.all.first(), item)
        }

        ShopItem.Fertilizer -> {
            ItemFacts(
                listOf(cost(price, "/ tile"), growth(Fertilizer.growthMultiplier)),
                listOf("Used up when the crop is harvested"),
            )
        }

        is ShopItem.BuildingItem -> {
            GameEngine.facts(Building.named(item.id) ?: Building.starter)
        }
    }
}

private fun GameEngine.decorationFacts(
    decoration: Decoration,
    item: ShopItem,
): ItemFacts {
    val stats = mutableListOf(cost(decoration.cost))
    val notes = mutableListOf<String>()
    when (val effect = decoration.effect) {
        is DecorationEffect.Waters -> {
            stats += growth(GrowthConditions.SATURATION_MULTIPLIER, "within ${effect.reach} tiles")
        }

        is DecorationEffect.Guards -> {
            stats += growth(Decoration.GUARD_MULTIPLIER, "within ${effect.reach} tiles")
        }

        DecorationEffect.None -> {
            notes += if (decoration.isRock) "Gives no stones when broken" else "Just for show"
        }
    }
    placementLimit(item)?.let { stats += ItemStat("Limit", StatValue.Count(it)) }
    return ItemFacts(stats, notes)
}

internal fun cost(
    price: Double,
    unit: String? = null,
): ItemStat = if (price == 0.0) ItemStat("Cost", StatValue.Free) else ItemStat("Cost", StatValue.Money(price), unit)

private fun growth(
    multiplier: Double,
    unit: String? = null,
): ItemStat = ItemStat("Growth", StatValue.Boost(multiplier - 1), unit)

// Buildings

/** What a building does, without its price, which its page shows with the logs and stones it takes. */
fun GameEngine.Companion.facts(building: Building): ItemFacts {
    var stats = mutableListOf<ItemStat>()
    var notes = emptyList<String>()
    val home = building.livestockHome
    val recipe = Recipe.of(building)
    when {
        building.id == "tractor1" -> {
            notes = listOf("Harvests crops and brings them home", "Shows when crops are ready")
        }

        building.isPetHouse -> {
            notes = listOf("Home for a cat or a dog", "Its pet may ripen crops")
        }

        building.isHouse -> {
            val slots =
                if (building.isUpgradedHouse) StockMarketRules.UPGRADED_SLOT_LIMIT else StockMarketRules.BASE_SLOT_LIMIT
            stats = mutableListOf(ItemStat("Stocks", StatValue.Count(slots)))
            notes = listOf("Opens the Stock Market")
        }

        building.isChickenCoop -> {
            stats =
                mutableListOf(
                    holds(building),
                    ItemStat("New chick in", StatValue.Duration(Coop.HATCH_INTERVAL)),
                    gives(StorageItem.Egg, HarvestTool.bucket),
                )
            notes = listOf("Hens lay faster after eating Wheat")
        }

        home != null -> {
            val baby =
                if (home.species.size == 1) {
                    home.species
                        .first()
                        .babyName
                        .lowercase()
                } else {
                    "baby"
                }
            stats = mutableListOf(holds(building), ItemStat("New $baby in", StatValue.Duration(home.birthInterval)))
            home.species.minOfOrNull { it.growDuration }?.let {
                stats += ItemStat("Grows up in", StatValue.Duration(it))
            }
            home.species.mapNotNull { it.work as? LivestockWork.Gives }.forEach { stats += gives(it.item, it.tool) }
        }

        recipe != null -> {
            stats = recipeStats(recipe).toMutableList()
            notes =
                (if (Recipe.options(building).size > 1) listOf("Pick a recipe in its menu") else emptyList()) +
                "Collect with the Bucket"
        }

        QuestRules.capacity(building.id) > 0 -> {
            stats = mutableListOf(ItemStat("Quests", StatValue.Count(QuestRules.capacity(building.id))))
            notes =
                listOf(if (building.isUpgradedMarketStand) "Harder two-part quests for more XP" else "Quests give XP")
        }

        building.storageCapacity != null -> {
            stats = mutableListOf(ItemStat("Storage", StatValue.Added(building.storageCapacity!!)))
        }
    }
    stats += ItemStat("Limit", StatValue.Count(building.placementLimit))
    return ItemFacts(stats, notes)
}

private fun holds(building: Building): ItemStat =
    ItemStat(
        "Holds",
        StatValue.Count(
            HomeUpgrade.capacity(building.id, 1),
            upgradedTo = HomeUpgrade.capacity(building.id, HomeUpgrade.maxLevel),
            atLevel = HomeUpgrade.maxLevel,
        ),
    )

private fun gives(
    item: StorageItem,
    tool: HarvestTool,
): ItemStat = ItemStat("Gives ${item.name.lowercase()}", StatValue.Money(item.sellValue), item = item, tool = tool)

private fun recipeStats(recipe: Recipe): List<ItemStat> =
    listOf(
        ItemStat(
            "Makes",
            StatValue.Conversion(recipe.input, recipe.inputCount, recipe.output, recipe.outputCount),
        ),
        ItemStat("Ready in", StatValue.Duration(recipe.duration)),
        ItemStat("Sells", StatValue.Money(recipe.outputValue), unit = "each"),
    )

/** One of a workshop's recipes, as its recipe list shows it. */
fun GameEngine.facts(recipe: Recipe): ItemFacts = ItemFacts(recipeStats(recipe) + unlock(recipe.unlockLevel))

/** The level something unlocks at, while the player hasn't reached it. */
private fun GameEngine.unlock(level: Int): List<ItemStat> =
    if (state.level < level) listOf(ItemStat("Unlock", StatValue.Unlock(level))) else emptyList()

// Upgrades

fun GameEngine.facts(upgrade: HomeUpgrade): ItemFacts =
    ItemFacts(
        listOf(
            cost(upgrade.cost),
            ItemStat(
                "Holds",
                StatValue.Change(
                    StatValue.Count(HomeUpgrade.capacity(upgrade.buildingID, upgrade.level - 1)),
                    StatValue.Count(HomeUpgrade.capacity(upgrade.buildingID, upgrade.level)),
                ),
            ),
        ) + unlock(upgrade.unlockLevel),
    )

/** The House's upgrade to the Luxury House. */
val GameEngine.houseUpgradeFacts: ItemFacts
    get() =
        ItemFacts(
            listOf(
                cost(StockMarketRules.HOUSE_UPGRADE_COST),
                ItemStat(
                    "Stocks",
                    StatValue.Change(
                        StatValue.Count(StockMarketRules.BASE_SLOT_LIMIT),
                        StatValue.Count(StockMarketRules.UPGRADED_SLOT_LIMIT),
                    ),
                ),
            ) + unlock(StockMarketRules.HOUSE_UPGRADE_LEVEL),
        )

/** The Market Stand's upgrade to the large stand. */
val GameEngine.Companion.marketUpgradeFacts: ItemFacts
    get() =
        ItemFacts(
            listOf(
                cost(QuestRules.UPGRADE_COST),
                ItemStat(
                    "Quests",
                    StatValue.Change(
                        StatValue.Count(QuestRules.capacity("marketStand")),
                        StatValue.Count(QuestRules.capacity("marketStandLarge")),
                    ),
                ),
                ItemStat("Needs", StatValue.Count(QuestRules.QUESTS_TO_UPGRADE), unit = "quests"),
            ),
            listOf("Harder two-part quests for more XP"),
        )

fun GameEngine.facts(upgrade: ToolUpgrade): ItemFacts {
    val before = ToolUpgrade.reaching(upgrade.level - 1, upgrade.tool)?.effect
    val change =
        when (val effect = upgrade.effect) {
            is ToolEffect.DoubleDrop -> {
                ItemStat(
                    "Second drop",
                    StatValue.Change(StatValue.Chance(doubleDropChance(before)), StatValue.Chance(effect.chance)),
                )
            }

            is ToolEffect.WiderCircle -> {
                ItemStat(
                    "Circle",
                    StatValue.Change(StatValue.Multiplier(circleFactor(before)), StatValue.Multiplier(effect.factor)),
                )
            }

            ToolEffect.HalvedPasses, ToolEffect.SinglePass -> {
                ItemStat("Swings", StatValue.Change(swings(upgrade.tool, before), swings(upgrade.tool, effect)))
            }
        }
    return ItemFacts(listOf(cost(upgrade.cost), change) + unlock(upgrade.unlockLevel))
}

private fun swings(
    tool: HarvestTool,
    effect: ToolEffect?,
): StatValue {
    val bases = if (tool == HarvestTool.axe) Tree.all.map { it.hitsToFell } else RockSize.entries.map { it.hitsToBreak }
    return StatValue.Range(
        ToolUpgrade.passes(bases.minOrNull() ?: 1, effect),
        ToolUpgrade.passes(bases.maxOrNull() ?: 1, effect),
    )
}

private fun doubleDropChance(effect: ToolEffect?): Double = (effect as? ToolEffect.DoubleDrop)?.chance ?: 0.0

private fun circleFactor(effect: ToolEffect?): Double = (effect as? ToolEffect.WiderCircle)?.factor ?: 1.0

// Harvest tools

fun GameEngine.facts(tool: HarvestTool): ItemFacts {
    val stats = mutableListOf<ItemStat>()
    when (tool) {
        HarvestTool.axe -> {
            val logs = Tree.all.map { it.logs }
            stats +=
                ItemStat(
                    "Gives",
                    StatValue.Range(logs.minOrNull() ?: 0, logs.maxOrNull() ?: 0),
                    "logs",
                    StorageItem.Log,
                )
        }

        HarvestTool.pickaxe -> {
            val stones = RockSize.stoneSizes.map { it.stones }
            stats +=
                ItemStat(
                    "Gives",
                    StatValue.Range(stones.minOrNull() ?: 0, stones.maxOrNull() ?: 0),
                    "stones",
                    StorageItem.Stone,
                )
        }

        HarvestTool.scythe, HarvestTool.bucket, HarvestTool.rod -> {}
    }
    when (val effect = ToolUpgrade.reaching(level(tool), tool)?.effect) {
        is ToolEffect.DoubleDrop -> {
            stats += ItemStat("Second drop", StatValue.Chance(effect.chance))
        }

        is ToolEffect.WiderCircle -> {
            stats += ItemStat("Circle", StatValue.Multiplier(effect.factor))
        }

        else -> {}
    }
    val upgrades =
        ToolUpgrade.all
            .filter { it.tool == tool }
            .map { it.unlockLevel }
            .sorted()
    if (upgrades.isNotEmpty()) stats += ItemStat("Upgrades", StatValue.Levels(upgrades))
    return ItemFacts(stats, notes(tool))
}

private fun notes(tool: HarvestTool): List<String> =
    when (tool) {
        HarvestTool.scythe -> listOf("Cuts ripe crops and takes wool", "Fruit trees need the Bucket")
        HarvestTool.axe -> listOf("Fell trees for logs", "Planted trees give none")
        HarvestTool.pickaxe -> listOf("Break rocks for stones", "Placed rocks give none")
        HarvestTool.bucket -> listOf("Takes eggs, milk, fertilizer and fruit", "Catches butterflies too")
        HarvestTool.rod -> listOf("Catch fish from a pond", "Rarer fish are harder to keep")
    }
