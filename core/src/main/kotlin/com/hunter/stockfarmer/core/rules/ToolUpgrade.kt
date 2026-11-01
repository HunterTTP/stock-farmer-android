package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.HarvestTool

/** What reaching an upgrade does for its tool. */
sealed interface ToolEffect {
    /** Fells or breaks in half as many passes, rounded up. */
    data object HalvedPasses : ToolEffect

    /** Fells or breaks in one pass. */
    data object SinglePass : ToolEffect

    /** Gathers a second item this often. */
    data class DoubleDrop(
        val chance: Double,
    ) : ToolEffect

    /** The Fishing Rod's circle in fishing mode is this many times as wide. */
    data class WiderCircle(
        val factor: Double,
    ) : ToolEffect
}

/**
 * One step up for a Harvest implement. Every tool has its own steps, each unlocking at its own player level, spread from
 * 5 to 30, with its own effect, and costing about two-fifths of what a building unlocking at that level does, except the
 * Fishing Rod, which costs half that.
 */
data class ToolUpgrade(
    val tool: HarvestTool,
    /** The level it buys. */
    val level: Int,
    /** The player level it unlocks at. */
    val unlockLevel: Int,
    val cost: Double,
    val effect: ToolEffect,
) {
    companion object {
        val all: List<ToolUpgrade> =
            listOf(
                ToolUpgrade(HarvestTool.axe, 2, 5, 400.0, ToolEffect.HalvedPasses),
                ToolUpgrade(HarvestTool.scythe, 2, 8, 1_500.0, ToolEffect.DoubleDrop(0.1)),
                ToolUpgrade(HarvestTool.pickaxe, 2, 11, 3_000.0, ToolEffect.HalvedPasses),
                ToolUpgrade(HarvestTool.bucket, 2, 14, 5_500.0, ToolEffect.DoubleDrop(0.15)),
                ToolUpgrade(HarvestTool.axe, 3, 15, 6_000.0, ToolEffect.SinglePass),
                ToolUpgrade(HarvestTool.rod, 2, 17, 4_000.0, ToolEffect.WiderCircle(1.5)),
                ToolUpgrade(HarvestTool.scythe, 3, 20, 12_000.0, ToolEffect.DoubleDrop(0.25)),
                ToolUpgrade(HarvestTool.rod, 3, 23, 7_750.0, ToolEffect.WiderCircle(1.5 * 1.5)),
                ToolUpgrade(HarvestTool.pickaxe, 3, 25, 18_000.0, ToolEffect.SinglePass),
                ToolUpgrade(HarvestTool.bucket, 3, 30, 26_000.0, ToolEffect.DoubleDrop(0.35)),
            )

        val maxLevel: Int = all.maxOf { it.level }

        /** The upgrade that brought a tool to a level, null for the level it is bought at. */
        internal fun reaching(
            level: Int,
            tool: HarvestTool,
        ): ToolUpgrade? = all.firstOrNull { it.tool == tool && it.level == level }
    }
}
