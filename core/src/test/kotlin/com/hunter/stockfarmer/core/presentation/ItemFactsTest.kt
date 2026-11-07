package com.hunter.stockfarmer.core.presentation

import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.Decoration
import com.hunter.stockfarmer.core.catalog.DecorationEffect
import com.hunter.stockfarmer.core.catalog.Fertilizer
import com.hunter.stockfarmer.core.catalog.GroundCover
import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.catalog.HomeUpgrade
import com.hunter.stockfarmer.core.catalog.LivestockWork
import com.hunter.stockfarmer.core.catalog.Recipe
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.catalog.Tree
import com.hunter.stockfarmer.core.catalog.TreeSpecies
import com.hunter.stockfarmer.core.model.Coop
import com.hunter.stockfarmer.core.model.Egg
import com.hunter.stockfarmer.core.model.ShopItem
import com.hunter.stockfarmer.core.model.compactDurationText
import com.hunter.stockfarmer.core.model.dollars
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.LevelRules
import com.hunter.stockfarmer.core.rules.QuestRules
import com.hunter.stockfarmer.core.rules.StockMarketRules
import com.hunter.stockfarmer.core.rules.ToolUpgrade
import com.hunter.stockfarmer.core.rules.requiredLevel
import com.hunter.stockfarmer.core.stateWith
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ItemFactsTest {
    private fun engine(level: Int = LevelRules.maxLevel) =
        GameEngine(stateWith(money = 0.0, xp = LevelRules.xpToReach(level), logs = 0))

    private fun rows(
        facts: ItemFacts,
        compact: Boolean = false,
    ): List<String> =
        facts.stats.map { stat -> listOfNotNull(stat.label, stat.value.text(compact), stat.unit).joinToString(" ") }

    private val shopItems: List<ShopItem> =
        Crop.all.map { ShopItem.Seed(it.id) } +
            listOf(ShopItem.Farmland, ShopItem.Water, ShopItem.Fence, ShopItem.Fertilizer) +
            GroundCover.entries.map { ShopItem.GroundCoverItem(it) } +
            TreeSpecies.all.map { ShopItem.Tree(it.id) } +
            Decoration.shopChoices.map { ShopItem.DecorationItem(it.id) } +
            Building.all.map { ShopItem.BuildingItem(it.id) }

    // Plant, Terrain, Fertilize and Decorate

    @Test fun `aCropShowsItsCostSaleTimeAndXP`() {
        val carrot = Crop.named("carrot")!!
        val facts = engine().facts(ShopItem.Seed(carrot.id))
        assertEquals(
            listOf(
                "Cost ${carrot.seedPrice.dollars} / tile",
                "Sells ${carrot.sellValue.dollars}",
                "Grows in ${carrot.growDuration.compactDurationText}",
                "XP +${LevelRules.harvestXP(carrot)}",
            ),
            rows(facts),
        )
        assertTrue(facts.notes.isEmpty())
    }

    @Test fun `aLockedItemLeadsWithTheLevelThatUnlocksIt`() {
        val carrot = Crop.named("carrot")!!
        val facts = engine(level = 1).facts(ShopItem.Seed(carrot.id))
        assertEquals(StatValue.Unlock(carrot.unlockLevel), facts.stats.first().value)
        assertEquals(StatKind.unlock, facts.stats.first().kind)
        assertEquals("Unlock Level ${carrot.unlockLevel}", rows(facts).first())
    }

    @Test fun `aFruitTreeIsBoughtOnceAndHasALimit`() {
        val apple = Crop.named("apple")!!
        val facts = engine().facts(ShopItem.Seed(apple.id))
        assertEquals(
            listOf(
                "Cost ${apple.seedPrice.dollars} once",
                "Sells ${apple.sellValue.dollars} / basket",
                "Ripens in ${apple.growDuration.compactDurationText}",
                "XP +${LevelRules.harvestXP(apple)}",
                "Limit ${Crop.MOST_FRUIT_TREES_OF_A_KIND}",
            ),
            rows(facts),
        )
    }

    @Test fun `aScarecrowSpeedsUpCropsNearIt`() {
        val scarecrow = Decoration.named("scarecrow")!!
        val reach = (scarecrow.effect as DecorationEffect.Guards).reach
        val facts = engine().facts(ShopItem.DecorationItem(scarecrow.id))
        assertEquals(listOf("Cost ${scarecrow.cost.dollars}", "Growth +5% within $reach tiles"), rows(facts))
        assertEquals(StatKind.boost, facts.stats.last().kind)
        assertTrue(facts.notes.isEmpty())
    }

    @Test fun `aDecorationThatDoesNothingIsJustForShow`() {
        val facts = engine().facts(ShopItem.DecorationItem("pumpkin"))
        assertEquals(listOf("Cost ${Decoration.named("pumpkin")!!.cost.dollars}"), rows(facts))
        assertEquals(listOf("Just for show"), facts.notes)
    }

    @Test fun `theTrunkHasALimit`() {
        assertEquals("Limit ${Decoration.TRUNK_LIMIT}", rows(engine().facts(ShopItem.DecorationItem("trunk"))).last())
    }

    @Test fun `waterIsFreeAndSpeedsUpFarmlandNextToIt`() {
        assertEquals(listOf("Cost Free", "Growth +5% nearby"), rows(engine().facts(ShopItem.Water)))
    }

    @Test fun `fertilizerCostsPerTileAndSpeedsUpGrowth`() {
        val facts = engine().facts(ShopItem.Fertilizer)
        assertEquals(listOf("Cost ${Fertilizer.price.dollars} / tile", "Growth +10%"), rows(facts))
        assertEquals(listOf("Used up when the crop is harvested"), facts.notes)
    }

    @Test fun `aTreeIsFreeAndComesInSizesAndColors`() {
        val pine = TreeSpecies.pine
        assertEquals(
            listOf("Cost Free", "Sizes ${pine.trees.size}", "Colors ${pine.colors.size}"),
            rows(engine().facts(ShopItem.Tree(pine.id))),
        )
    }

    @Test fun `theEraserIsFree`() {
        val facts = GameEngine.eraserFacts
        assertEquals(listOf("Cost Free"), rows(facts))
        assertTrue(facts.notes.isNotEmpty())
    }

    @Test fun `rowsUseCompactMoneyAndLevels`() {
        assertEquals("$14K", StatValue.Money(14_000.0).text(compact = true))
        assertEquals("$14,000", StatValue.Money(14_000.0).text(compact = false))
        assertEquals("Lv 7", StatValue.Level(7).text(compact = true))
        assertEquals("Level 7", StatValue.Level(7).text(compact = false))
        assertEquals("1h 30m", StatValue.Duration(90.0 * 60).text(compact = false))
        assertEquals("+3 XP", StatValue.XP(3).text(compact = true))
    }

    // Buildings

    @Test fun `aShedAddsStorageWithNoNotes`() {
        val shed = Building.named("shed")!!
        val facts = engine().facts(ShopItem.BuildingItem(shed.id))
        assertEquals(listOf("Storage +${shed.storageCapacity!!}", "Limit ${shed.placementLimit}"), rows(facts))
        assertTrue(facts.notes.isEmpty())
    }

    @Test fun `anAnimalHomeShowsWhoItHoldsAndHowFastTheyCome`() {
        val barn = Building.named("barn1")!!
        val home = barn.livestockHome!!
        val sheep = home.species[0]
        val wool = sheep.work as LivestockWork.Gives
        val facts = GameEngine.facts(barn)
        assertEquals(
            listOf(
                "Holds ${home.capacity} (${HomeUpgrade.capacity(
                    barn.id,
                    HomeUpgrade.maxLevel,
                )} at Lv ${HomeUpgrade.maxLevel})",
                "New lamb in ${home.birthInterval.compactDurationText}",
                "Grows up in ${sheep.growDuration.compactDurationText}",
                "Gives wool ${wool.item.sellValue.dollars}",
                "Limit 1",
            ),
            rows(facts),
        )
        assertEquals(wool.item, facts.stats[3].item)
        assertEquals(wool.tool, facts.stats[3].tool)
    }

    @Test fun `theCoopHatchesChicksThatLayEggs`() {
        val facts = GameEngine.facts(Building.named("chickenCoop")!!)
        assertEquals(
            listOf(
                "Holds ${HomeUpgrade.capacity("chickenCoop", 1)} " +
                    "(${HomeUpgrade.capacity("chickenCoop", HomeUpgrade.maxLevel)} at Lv ${HomeUpgrade.maxLevel})",
                "New chick in ${Coop.HATCH_INTERVAL.compactDurationText}",
                "Gives egg ${Egg.SALE_PRICE.dollars}",
                "Limit ${Building.named("chickenCoop")!!.placementLimit}",
            ),
            rows(facts),
        )
    }

    @Test fun `aWorkshopShowsItsRecipe`() {
        val recipe = Recipe.named("grainMachine")!!
        val facts = GameEngine.facts(Building.named("grainMachine")!!)
        assertEquals(
            listOf(
                "Makes ${recipe.inputCount} Wheat → ${recipe.outputCount} Flour",
                "Ready in ${recipe.duration.compactDurationText}",
                "Sells ${recipe.outputValue.dollars} each",
                "Limit 1",
            ),
            rows(facts),
        )
        assertTrue(Recipe.options(Building.named("grainMachine")!!).size > 1)
        assertEquals(listOf("Pick a recipe in its menu", "Collect with the Bucket"), facts.notes)
    }

    @Test fun `theMarketStandAndHouseCountWhatTheyHold`() {
        assertEquals(
            "Quests ${QuestRules.capacity("marketStand")}",
            rows(GameEngine.facts(Building.named("marketStand")!!)).first(),
        )
        assertEquals(
            "Quests ${QuestRules.capacity("marketStandLarge")}",
            rows(GameEngine.facts(Building.named("marketStandLarge")!!)).first(),
        )
        assertEquals(
            "Stocks ${StockMarketRules.BASE_SLOT_LIMIT}",
            rows(GameEngine.facts(Building.named("house1")!!)).first(),
        )
        assertEquals(
            "Stocks ${StockMarketRules.UPGRADED_SLOT_LIMIT}",
            rows(GameEngine.facts(Building.named("house2")!!)).first(),
        )
    }

    // Upgrades

    @Test fun `aHomeUpgradeShowsItsCostAndTheRoomItAdds`() {
        val upgrade = HomeUpgrade.upgrade("barn1", toLevel = 2)!!
        assertEquals(
            listOf(
                "Cost ${upgrade.cost.dollars}",
                "Holds ${HomeUpgrade.capacity("barn1", 1)} → ${HomeUpgrade.capacity("barn1", 2)}",
            ),
            rows(engine().facts(upgrade)),
        )
        assertEquals("Unlock Level ${upgrade.unlockLevel}", rows(engine(level = 1).facts(upgrade)).last())
    }

    @Test fun `theHouseAndMarketUpgradesShowWhatChanges`() {
        assertEquals(
            listOf(
                "Cost ${StockMarketRules.HOUSE_UPGRADE_COST.dollars}",
                "Stocks ${StockMarketRules.BASE_SLOT_LIMIT} → ${StockMarketRules.UPGRADED_SLOT_LIMIT}",
            ),
            rows(engine().houseUpgradeFacts),
        )
        assertEquals(
            listOf(
                "Cost ${QuestRules.UPGRADE_COST.dollars}",
                "Quests ${QuestRules.capacity("marketStand")} → ${QuestRules.capacity("marketStandLarge")}",
                "Needs ${QuestRules.QUESTS_TO_UPGRADE} quests",
            ),
            rows(GameEngine.marketUpgradeFacts),
        )
    }

    @Test fun `aToolUpgradeShowsWhatItChanges`() {
        val scythe = ToolUpgrade.all.first { it.tool == HarvestTool.scythe && it.level == 3 }
        assertEquals(listOf("Cost ${scythe.cost.dollars}", "Second drop 10% → 25%"), rows(engine().facts(scythe)))
        val axe = ToolUpgrade.all.first { it.tool == HarvestTool.axe && it.level == 2 }
        val hits = Tree.all.map { it.hitsToFell }
        assertEquals(
            listOf(
                "Cost ${axe.cost.dollars}",
                "Swings ${hits.min()}–${hits.max()} → ${(hits.min() + 1) / 2}–${(hits.max() + 1) / 2}",
            ),
            rows(engine().facts(axe)),
        )
        val rod = ToolUpgrade.all.first { it.tool == HarvestTool.rod && it.level == 2 }
        assertEquals("Circle 1× → 1.5×", rows(engine().facts(rod)).last())
    }

    // Tools and recipes

    @Test fun `theAxeGivesLogsAndListsItsUpgrades`() {
        val upgrades =
            ToolUpgrade.all
                .filter { it.tool == HarvestTool.axe }
                .map { it.unlockLevel }
                .sorted()
        val logs = Tree.all.map { it.logs }
        val facts = engine().facts(HarvestTool.axe)
        assertEquals(
            listOf(
                "Gives ${logs.min()}–${logs.max()} logs",
                "Upgrades ${upgrades.joinToString(" · ") { "Lv $it" }}",
            ),
            rows(facts),
        )
        assertEquals(StorageItem.Log, facts.stats[0].item)
        assertEquals(listOf("Fell trees for logs", "Planted trees give none"), facts.notes)
    }

    @Test fun `anUpgradedToolShowsItsSecondDrop`() {
        val engine = engine()
        engine.state.toolLevels[HarvestTool.scythe.rawValue] = 2
        assertEquals("Second drop 10%", rows(engine.facts(HarvestTool.scythe)).first())
    }

    @Test fun `aRecipeShowsWhatItMakesAndWhenItUnlocks`() {
        val recipe = Recipe.named("cottonCloth")!!
        assertEquals(
            listOf(
                "Makes ${recipe.inputCount} Cotton → ${recipe.outputCount} Cloth",
                "Ready in ${recipe.duration.compactDurationText}",
                "Sells ${recipe.outputValue.dollars} each",
            ),
            rows(engine().facts(recipe)),
        )
        assertEquals("Unlock Level ${recipe.unlockLevel}", rows(engine(level = 1).facts(recipe)).last())
        assertEquals(
            "Makes ${recipe.inputCount} → ${recipe.outputCount}",
            rows(engine().facts(recipe), compact = true).first(),
        )
    }

    // Every item

    private val everyFacts: List<ItemFacts>
        get() {
            val engine = engine()
            val fresh = engine(level = 1)
            return shopItems.map { engine.facts(it) } +
                shopItems.map { fresh.facts(it) } +
                HarvestTool.entries.map { engine.facts(it) } +
                HomeUpgrade.all.map { engine.facts(it) } +
                ToolUpgrade.all.map { engine.facts(it) } +
                Recipe.all.map { engine.facts(it) } +
                listOf(engine.houseUpgradeFacts, GameEngine.marketUpgradeFacts, GameEngine.eraserFacts)
        }

    @Test fun `everythingHasAStatAndAtMostTwoShortNotesWithoutNumbers`() {
        for (facts in everyFacts) {
            assertFalse(facts.stats.isEmpty())
            assertTrue(facts.notes.size <= 2)
            for (note in facts.notes) {
                assertTrue(note.none { it.isDigit() }, note)
                assertTrue(note.length <= 45, note)
            }
        }
    }

    @Test fun `onlyLockedItemsShowAnUnlockLevel`() {
        val unlocked = engine()
        val fresh = engine(level = 1)
        for (item in shopItems) {
            assertFalse(unlocked.facts(item).stats.any { it.kind == StatKind.unlock })
            val unlockStats = fresh.facts(item).stats.filter { it.kind == StatKind.unlock }
            assertEquals(
                fresh.requiredLevel(item)?.let { listOf(StatValue.Unlock(it)) } ?: emptyList<StatValue>(),
                unlockStats.map { it.value },
                "$item",
            )
        }
    }
}
