package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.Decoration
import com.hunter.stockfarmer.core.catalog.PlacedDecoration
import com.hunter.stockfarmer.core.catalog.Recipe
import com.hunter.stockfarmer.core.catalog.UnlockOrder
import com.hunter.stockfarmer.core.lateGameXP
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.ShopItem
import com.hunter.stockfarmer.core.persistence.GameStateJson
import com.hunter.stockfarmer.core.sameState
import com.hunter.stockfarmer.core.stateWith
import com.hunter.stockfarmer.core.util.advancedBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class MenuProgressionTest {
    @Test
    fun `decorations and recipes use unlock order with stable ties`() {
        val decorations = Decoration.shopChoices
        assertEquals(decorations.map { it.unlockLevel }.sorted(), decorations.map { it.unlockLevel })
        assertTrue(decorations.any { it.id == Decoration.BIG_SPRINKLER_ID })
        for (building in Building.all) {
            val recipes = Recipe.options(building)
            assertEquals(recipes.map { it.unlockLevel }.sorted(), recipes.map { it.unlockLevel })
        }
        val crops = listOf(ShopItem.Seed("coffee"), ShopItem.Seed("wheat"), ShopItem.Seed("carrot"))
        assertEquals(
            listOf(ShopItem.Seed("wheat"), ShopItem.Seed("carrot"), ShopItem.Seed("coffee")),
            UnlockOrder.sorted(crops) { it.unlockLevel },
        )
        val ties = listOf(ShopItem.Water, ShopItem.Farmland, ShopItem.Fertilizer)
        assertEquals(ties, UnlockOrder.sorted(ties) { it.unlockLevel })
    }

    @Test
    fun `storage lists early materials and produce before later unlocks`() {
        val engine = GameEngine()
        engine.state.storage =
            mutableMapOf("coffee" to 1, "wheat" to 1, "egg" to 1, "milk" to 1, "log" to 1, "cotton" to 1, "cloth" to 1)
        assertEquals(
            listOf("wheat", "log", "egg", "cloth", "milk", "cotton", "coffee"),
            engine.storedItems.map { it.first.id },
        )
    }

    @Test
    fun `the pet bowl keeps its saved identity and unlocks with the pet house`() {
        val bowl = Decoration.named("dogBowl")!!
        assertEquals("Pet Bowl", bowl.name)
        assertEquals(Building.named("petHouse")?.unlockLevel, bowl.unlockLevel)
        assertTrue(bowl.sizeNames.all { it.startsWith("Pet Bowl") })
        val state = stateWith()
        state.putDecoration(GridPoint(5, 5), PlacedDecoration(bowl.id, 3))
        val loaded = GameStateJson.decode(GameStateJson.encode(state)).decorations[GridPoint(5, 5)]!!
        assertTrue(loaded.decorationID == "dogBowl" && loaded.size == 3)
    }

    @Test
    fun `a big sprinkler is bought directly and sprays its full reach`() {
        val now = Instant.ofEpochSecond(60_000_000)
        val point = GridPoint(20, 20)
        val big = Decoration.named(Decoration.BIG_SPRINKLER_ID)!!
        val engine = GameEngine(stateWith(money = big.cost, xp = lateGameXP))
        engine.select(big)
        engine.beginPlacingSelectedDecoration(point)
        assertEquals(listOf<GameEvent>(GameEvent.Decorated(point, 1_600.0)), engine.confirmPlacement(now))
        assertEquals(0.0, engine.state.money)
        val far = GridPoint(23, 23)
        engine.state.farmland = mutableSetOf(far, GridPoint(24, 24))
        engine.activateSprinklers(now.advancedBy(Decoration.SPRAY_DURATION))
        assertEquals(setOf(far), engine.state.saturatedFarmland)
        val loaded = GameStateJson.decode(GameStateJson.encode(engine.state))
        assertEquals(big.id, loaded.decorations[point]?.decorationID)
        assertEquals(big.id, loaded.lastSprinklerID)
    }

    @Test
    fun `buying a big sprinkler requires its level and full price`() {
        val big = Decoration.named(Decoration.BIG_SPRINKLER_ID)!!
        val locked = GameEngine(stateWith(money = 10_000.0))
        val before = locked.state.copy()
        locked.select(big)
        assertTrue(sameState(before, locked.state))
        assertEquals(12, locked.requiredLevel(ShopItem.DecorationItem(big.id)))
        val poor = GameEngine(stateWith(money = 1_599.0, xp = lateGameXP))
        poor.select(big)
        assertEquals(Rejection.NeedMoney(1_600.0), poor.cannotStartPlacingSelectedDecoration)
        poor.beginPlacingSelectedDecoration(GridPoint(20, 20))
        assertEquals(
            listOf<GameEvent>(GameEvent.Rejected(Rejection.NeedMoney(1_600.0))),
            poor.confirmPlacement(Instant.ofEpochSecond(60_000_000)),
        )
        assertTrue(poor.state.money == 1_599.0 && poor.state.decorations.isEmpty())
    }

    @Test
    fun `the fertilize tool remembers its sprinkler through other tools and saving`() {
        var engine = GameEngine(stateWith(xp = lateGameXP))
        engine.select(Decoration.named(Decoration.BIG_SPRINKLER_ID)!!)
        engine.select(Decoration.named("scarecrow")!!)
        engine = GameEngine(GameStateJson.decode(GameStateJson.encode(engine.state)))
        engine.selectFertilize()
        assertEquals(Decoration.BIG_SPRINKLER_ID, engine.selectedDecoration?.id)
        assertEquals(Decoration.BIG_SPRINKLER_ID, engine.state.lastSprinklerID)
    }

    @Test
    fun `new saves default to the standard sprinkler`() {
        assertEquals(Decoration.SPRINKLER_ID, GameStateJson.decode("{}").lastSprinklerID)
    }
}
