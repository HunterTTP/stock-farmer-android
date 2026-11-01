package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.catalog.RockSize
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.catalog.Tree
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.QuestGoal

/** How far a tool has been upgraded: 1 as bought. */
fun GameEngine.level(tool: HarvestTool): Int = state.toolLevels[tool.rawValue] ?: 1

/** The upgrade after the tool's current level, null once it has them all. */
fun GameEngine.nextUpgrade(tool: HarvestTool): ToolUpgrade? =
    ToolUpgrade.all.firstOrNull {
        it.tool == tool &&
            it.level == level(tool) + 1
    }

/** Why the tool can't be upgraded right now, or null if it can. */
fun GameEngine.upgradeRejection(tool: HarvestTool): Rejection? {
    val upgrade = nextUpgrade(tool) ?: return Rejection.ToolFullyUpgraded
    if (state.level < upgrade.unlockLevel) return Rejection.NeedLevel(upgrade.unlockLevel)
    if (state.money < upgrade.cost) return Rejection.NeedMoney(upgrade.cost)
    return null
}

/** Whether an upgrade is on offer: from the level it unlocks at, until it has been bought. */
fun GameEngine.offersUpgrade(tool: HarvestTool): Boolean =
    nextUpgrade(tool)?.let { state.level >= it.unlockLevel } ?: false

fun GameEngine.upgrade(tool: HarvestTool): List<GameEvent> {
    upgradeRejection(tool)?.let { return reject(it) }
    val upgrade = nextUpgrade(tool) ?: return emptyList()
    state.money -= upgrade.cost
    state.toolLevels[tool.rawValue] = upgrade.level
    return emptyList()
}

/** How many passes of a tool a thing takes, with what the tool has been upgraded to. */
private fun GameEngine.passes(
    base: Int,
    tool: HarvestTool,
): Int =
    when (ToolUpgrade.reaching(level(tool), tool)?.effect) {
        ToolEffect.HalvedPasses -> (base + 1) / 2
        ToolEffect.SinglePass -> 1
        else -> base
    }

fun GameEngine.hitsToFell(tree: Tree): Int = passes(tree.hitsToFell, HarvestTool.axe)

fun GameEngine.hitsToBreak(rock: RockSize): Int = passes(rock.hitsToBreak, HarvestTool.pickaxe)

/** How often the tool gathers a second item with what it has been upgraded to. */
private fun GameEngine.doubleDropChance(tool: HarvestTool): Double =
    (ToolUpgrade.reaching(level(tool), tool)?.effect as? ToolEffect.DoubleDrop)?.chance ?: 0.0

/** How many times as wide the rod's circle is in fishing mode, with what it has been upgraded to. */
val GameEngine.fishingReachFactor: Double
    get() =
        (ToolUpgrade.reaching(level(HarvestTool.rod), HarvestTool.rod)?.effect as? ToolEffect.WiderCircle)?.factor
            ?: 1.0

/** Gathers one item, and with the Scythe's or Bucket's upgrade sometimes a second, if it fits. */
internal fun GameEngine.gatherWithBonus(
    item: StorageItem,
    point: GridPoint,
    tool: HarvestTool,
    xp: Int = 0,
): List<GameEvent> {
    val gathered = gather(item, point, tool, xp)
    val chance = doubleDropChance(tool)
    if (chance <= 0 || !hasRoom(1, tool) || bonusRoll() >= chance) return listOf(gathered)
    if (item is StorageItem.CropItem) recordQuestProgress(QuestGoal.Harvest(item.crop.id))
    return listOf(gathered, gather(item, point, tool))
}
