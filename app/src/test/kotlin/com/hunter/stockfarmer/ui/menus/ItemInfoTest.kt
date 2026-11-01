package com.hunter.stockfarmer.ui.menus

import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.Decoration
import com.hunter.stockfarmer.core.catalog.GroundCover
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.catalog.TreeSpecies
import com.hunter.stockfarmer.core.model.ShopItem
import com.hunter.stockfarmer.core.model.dollars
import com.hunter.stockfarmer.core.model.durationText
import com.hunter.stockfarmer.core.persistence.GameStateJson
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.LevelRules
import com.hunter.stockfarmer.ui.theme.EffectLine
import com.hunter.stockfarmer.ui.theme.EffectLine.Segment.Tint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal fun EffectLine.text(): String = segments.joinToString("") { it.text }

class ItemInfoTest {
    private val engine = GameEngine()

    private fun lines(item: ShopItem) = ItemInfo.from(item, engine).effects.map { it.text() }

    @Test
    fun `seed text has exact sale time and XP with semantic colors`() {
        val crop = Crop.starter
        val info = ItemInfo.from(ShopItem.Seed(crop.id), engine)
        assertEquals("Planted", info.placedLabel)
        assertEquals(
            listOf(
                "Free to plant",
                "Sells for ${crop.sellValue.dollars} when harvested",
                "Grows in ${crop.growDuration.durationText}",
                "Harvesting earns ${LevelRules.harvestXP(crop)} XP",
            ),
            info.effects.map { it.text() },
        )
        assertEquals(Tint.Money, info.effects[1].segments[1].tint)
        assertEquals(Tint.Blue, info.effects[2].segments[1].tint)
        assertEquals(Tint.Purple, info.effects[3].segments[1].tint)
    }

    @Test
    fun `locked fruit trees describe a recurring basket and planting once`() {
        val crop = Crop.all.first { it.isFruitTree }
        val text = lines(ShopItem.Seed(crop.id))
        assertEquals("Unlocks at Level ${crop.unlockLevel}", text.first())
        assertEquals("Costs ${crop.seedPrice.dollars} once: it never needs replanting", text[1])
        assertEquals(
            "Stands on four free grass tiles and bears a basket worth ${crop.sellValue.dollars} again and again",
            text[2],
        )
        assertEquals("Ripens in ${crop.growDuration.durationText}", text[3])
    }

    @Test
    fun `terrain and fertilizer describe the actual boosts and clearing behavior`() {
        assertEquals(listOf("Free to place", "Farmland next to it grows 5% faster"), lines(ShopItem.Water))
        assertEquals(
            listOf("Crops can only be planted on farmland", "Every level unlocks more tiles"),
            lines(ShopItem.Farmland),
        )
        assertEquals("Crops planted on farmland with fertilizer grow 10% faster", lines(ShopItem.Fertilizer)[1])
        assertEquals("It is used up when the crop on it is harvested", lines(ShopItem.Fertilizer)[2])
        assertEquals(
            "Clear with Grass, Farmland or Water",
            lines(ShopItem.GroundCoverItem(GroundCover.dirtPath)).last(),
        )
        assertEquals("Tap a gate to open or close it; hold it to sell", lines(ShopItem.Fence).last())
        assertTrue(lines(ShopItem.Tree(TreeSpecies.pine.id)).any { it.startsWith("Starts small and grows through ") })
    }

    @Test
    fun `rock skips the decoration refund promise and functional decorations include reach`() {
        assertEquals(
            "Break it with the Pickaxe for 2, 4, 6 stones, by size",
            lines(ShopItem.DecorationItem("rock")).last(),
        )
        assertFalse(lines(ShopItem.DecorationItem("rock")).any { it.contains("80%") })
        engine.state.xp = LevelRules.xpToReach(34)
        assertEquals(
            "Farmland within 2 tiles grows 5% faster, as it does by water",
            lines(ShopItem.DecorationItem(Decoration.SPRINKLER_ID))[1],
        )
        assertEquals("Crops within 3 tiles grow 5% faster", lines(ShopItem.DecorationItem("scarecrow"))[1])
        assertEquals(
            "Tap one to destroy it, and get 80% of its price back",
            lines(ShopItem.DecorationItem("scarecrow")).last(),
        )
    }

    @Test
    fun `special buildings share exact descriptions with their placed sheets`() {
        val tractor = ItemInfo.descriptionLines(Building.named("tractor1")!!).map { it.text() }
        assertEquals(
            listOf(
                "Shows planted crops, their ready times and gathered cargo",
                "Harvests crops and brings them home; tap Store or Sell to unload",
                "Only one can be placed",
            ),
            tractor,
        )
        val house = ItemInfo.descriptionLines(Building.named("house1")!!).map { it.text() }
        assertEquals("Tap it to open the Stock Market: buy and sell shares of up to 5 stocks", house.first())
        assertEquals("At Level 24, upgrade it for $50,000 to own up to 10", house[1])
        val market = ItemInfo.descriptionLines(Building.named("marketStandLarge")!!).map { it.text() }
        assertEquals("6 harder, two-part quests reward XP scaled to your level", market.first())
        val coop = ItemInfo.descriptionLines(Building.named("chickenCoop")!!).map { it.text() }
        assertEquals("Hens eat ripe Wheat from the field to lay twice as fast for five minutes", coop.last())
        assertTrue(coop.first().contains("a coop, and 6 when upgraded"))
        Building.all.forEach { assertTrue(ItemInfo.descriptionLines(it).isNotEmpty(), it.id) }
    }

    @Test
    fun `detail requirements include zero costs affordability and locked stats stay hidden`() {
        val item = ShopItem.BuildingItem("petHouse")
        val locked = ItemDetailState.from(engine, item)
        assertTrue(locked.stats.isEmpty())
        assertEquals(listOf("logs", "stones", "money"), locked.requirements.map { it.id })
        assertFalse(locked.requirements.last().enough)
        val building = Building.named("petHouse")!!
        engine.state.xp = LevelRules.xpToReach(34)
        engine.state.money = building.cost
        engine.state.storage[StorageItem.Log.id] = building.logCost
        engine.state.storage[StorageItem.Stone.id] = building.stoneCost
        val ready = ItemDetailState.from(engine, item)
        assertTrue(ready.requirements.all { it.enough })
        assertEquals("Placed", ready.stats.single().title)
        assertEquals(
            listOf("Unlocked", "Placed"),
            ItemDetailState.from(engine, ShopItem.Farmland).stats.map { it.title },
        )
        assertEquals(false, ItemDetailState.from(engine, ShopItem.DecorationItem(Decoration.SPRINKLER_ID)).fountain)
    }

    @Test
    fun `building and item builders do not mutate the farm`() {
        val before = GameStateJson.encode(engine.state)
        Building.all.forEach { ItemInfo.from(ShopItem.BuildingItem(it.id), engine) }
        Crop.all.forEach { ItemInfo.from(ShopItem.Seed(it.id), engine) }
        Decoration.all.forEach { ItemInfo.from(ShopItem.DecorationItem(it.id), engine) }
        assertEquals(before, GameStateJson.encode(engine.state))
    }
}
