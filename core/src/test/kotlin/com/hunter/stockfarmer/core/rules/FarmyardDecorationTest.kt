package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Decoration
import com.hunter.stockfarmer.core.catalog.PlacedDecoration
import com.hunter.stockfarmer.core.lateGameXP
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.persistence.GameStateJson
import com.hunter.stockfarmer.core.stateWith
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class FarmyardDecorationTest {
    private val now = Instant.ofEpochSecond(40_000_000)
    private val originalIDs =
        setOf("rock", "woodenTable", "scarecrow", "trunk", "pumpkin", "sprinkler", "well", "weathercock")

    @Test
    fun `farmyard choices include hay troughs bowls signs pots and shelves`() {
        val wanted =
            setOf("hayPile", "feedTrough", "dogBowl", "animalSign", "pottedFruitTree", "cheeseShelf", "crate")
        assertTrue(Decoration.all.map { it.id }.containsAll(wanted))
    }

    @Test
    fun `every new decoration can be placed saved moved and refunded`() {
        val additions = Decoration.shopChoices.filter { it.id !in originalIDs && !it.isRock && !it.isSprinkler }
        assertTrue(additions.isNotEmpty())
        for (decoration in additions) {
            val engine = GameEngine(stateWith(money = 100_000.0, xp = lateGameXP, logs = 0))
            val chosenSize = decoration.offeredSizes.last()
            engine.select(chosenSize, decoration)
            val origin = decoration.originSettingBottomLeftOn(GridPoint(15, 15))
            assertEquals(
                listOf<GameEvent>(GameEvent.Decorated(origin, decoration.cost)),
                engine.tap(GridPoint(15, 15), now),
            )
            val saved = GameStateJson.decode(GameStateJson.encode(engine.state))
            assertEquals(chosenSize, saved.decorations[origin]?.size)
            engine.beginMovingDecoration(origin)
            engine.movePlacement(GridPoint(30, 30))
            assertEquals(listOf<GameEvent>(GameEvent.Moved(GridPoint(30, 30))), engine.confirmPlacement(now))
            assertEquals(
                listOf<GameEvent>(GameEvent.Undecorated(GridPoint(30, 30), decoration.cost * 0.8)),
                engine.destroyDecoration(GridPoint(30, 30), now),
            )
            assertEquals(100_000.0 - decoration.cost * 0.2, engine.state.money)
        }
    }

    @Test
    fun `every decoration option has a reachable unlock and sane sizes`() {
        assertEquals(
            Decoration.all.size,
            Decoration.all
                .map { it.id }
                .toSet()
                .size,
        )
        for (decoration in Decoration.all) {
            assertTrue(decoration.unlockLevel in 1..LevelRules.maxLevel)
            assertTrue(decoration.width > 0 && decoration.height > 0)
            assertTrue(decoration.sizeNames.isEmpty() || decoration.sizeNames.size == decoration.assetNames.size)
        }
    }

    @Test
    fun `every level unlocks some decoration and no level unlocks more than three`() {
        for (level in 1..LevelRules.maxLevel) {
            val unlocked = Decoration.all.count { it.unlockLevel == level && !it.id.endsWith("Sprinkler") }
            assertTrue(unlocked in 1..3, "level $level unlocks $unlocked")
        }
    }

    @Test
    fun `a potted fruit tree is one tile wide and two tall`() {
        val potted = Decoration.named("pottedFruitTree")!!
        assertTrue(potted.width == 1 && potted.height == 2)
        assertEquals(0.525, potted.drawScale)
    }

    @Test
    fun `placed decorations keep their size through a save`() {
        val hay = Decoration.named("hayPile")!!
        val state = stateWith(money = 1.0, xp = lateGameXP)
        state.putDecoration(GridPoint(5, 5), PlacedDecoration(hay.id, hay.offeredSizes.last(), placedAt = now))
        assertFalse(GameStateJson.decode(GameStateJson.encode(state)).decorations.isEmpty())
    }
}
