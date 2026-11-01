package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Decoration
import com.hunter.stockfarmer.core.catalog.FenceColor
import com.hunter.stockfarmer.core.catalog.FishKind
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.catalog.WaterStyle
import com.hunter.stockfarmer.core.lateGameXP
import com.hunter.stockfarmer.core.model.FishPond
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.PlayMode
import com.hunter.stockfarmer.core.model.PondFish
import com.hunter.stockfarmer.core.persistence.GameStateJson
import com.hunter.stockfarmer.core.sameState
import com.hunter.stockfarmer.core.stateWith
import com.hunter.stockfarmer.core.util.SeededRandom
import com.hunter.stockfarmer.core.util.advancedBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class PlacedFishTest {
    private val now = Instant.ofEpochSecond(30_000_000)
    private val carp: StorageItem = StorageItem.Fish(FishKind.named("carp")!!)
    private val shore = GridPoint(30, 30)

    /** A four by four pond with some carp in storage and one of them equipped. */
    private fun engine(count: Int = 3): GameEngine {
        val engine = GameEngine(stateWith(money = 100.0, xp = lateGameXP))
        for (row in 20..23) for (col in 20..23) engine.state.water[GridPoint(row, col)] = WaterStyle.deep
        engine.state.storage[carp.id] = count
        engine.equip(carp)
        return engine
    }

    @Test
    fun `equipping a fish picks it up for the decorate tool`() {
        val engine = engine()
        assertTrue(
            engine.state.mode == PlayMode.decorate && engine.state.heldItemID == "fish-carp" &&
                engine.selectedDecoration == null,
        )
    }

    @Test
    fun `a fish dragged onto water swims there and leaves storage`() {
        val engine = engine()
        val tile = GridPoint(21, 21)
        assertEquals(listOf<GameEvent>(GameEvent.Placed(tile)), engine.useTool(tile, now))
        val fish = engine.state.fishPonds[tile]?.fish
        assertTrue(fish?.kindID == "carp" && fish.position == tile && !fish.isNaturallyGenerated && fish.weight != null)
        assertEquals(2, engine.state.storage[carp.id])
    }

    @Test
    fun `a fish only goes into water that is free`() {
        val engine = engine()
        val refused = listOf<GameEvent>(GameEvent.Rejected(Rejection.NeedWaterForFish))
        assertEquals(refused, engine.useTool(shore, now))
        val tile = GridPoint(21, 21)
        engine.useTool(tile, now)
        assertEquals(refused, engine.useTool(tile, now))
        assertEquals(2, engine.state.storage[carp.id])
    }

    @Test
    fun `no limit stops fish being placed and they do not count against fish that appear by themselves`() {
        val engine = engine(20)
        val limit = engine.animalLimitOnTheMap
        val placed = limit + 8
        for (index in 0 until placed) {
            val tile = GridPoint(20 + index / 4, 20 + index % 4)
            assertEquals(listOf<GameEvent>(GameEvent.Placed(tile)), engine.useTool(tile, now))
        }
        assertEquals(20 - placed, engine.state.storage[carp.id])
        val random = SeededRandom(3)
        engine.updateFish(now, using = random)
        engine.updateFish(now.advancedBy(24 * 60 * 60.0), using = random)
        val wild =
            engine.state.fishPonds.values
                .mapNotNull { it.fish }
                .filter { it.isNaturallyGenerated }
        assertTrue(wild.isNotEmpty() && wild.size <= limit)
    }

    @Test
    fun `with none left nothing is placed and it is only said once the last one has settled`() {
        val engine = engine(1)
        engine.useTool(GridPoint(21, 21), now)
        assertTrue(engine.useTool(GridPoint(22, 22), now.advancedBy(0.2)).isEmpty())
        assertEquals(1, engine.state.fishPonds.size)
        assertEquals(
            listOf<GameEvent>(GameEvent.Rejected(Rejection.NoneInStorage("Carp"))),
            engine.useTool(GridPoint(22, 22), now.advancedBy(2.0)),
        )
    }

    @Test
    fun `the tool lists stored things while it places from storage`() {
        val engine = engine()
        assertTrue(engine.isPlacingFromStorage && engine.isEquipped(carp))
        engine.select(Decoration.named("hayBale")!!)
        assertTrue(!engine.isPlacingFromStorage && !engine.isEquipped(carp))
    }

    @Test
    fun `picking a decoration or fence lets go of the fish`() {
        val engine = engine()
        engine.select(Decoration.named("hayBale")!!)
        assertNull(engine.state.heldItemID)
        engine.equip(carp)
        engine.select(FenceColor.starter)
        assertNull(engine.state.heldItemID)
    }

    @Test
    fun `a placed fish stays and gives no XP but a wild one does`() {
        val engine = engine()
        engine.state.xp = 0
        val tile = GridPoint(21, 21)
        val wild = GridPoint(22, 22)
        engine.useTool(tile, now)
        engine.state.fishPonds[wild] = FishPond(PondFish("carp", wild, 12.0, now), now)
        val random = SeededRandom(1)
        engine.updateFish(now.advancedBy(10 * FISH_LIFETIME), using = random)
        assertNotNull(engine.state.fishPonds[tile]?.fish)
        val xp = engine.state.xp
        val placedEvents = engine.landFish(tile, tile, now)
        assertTrue(engine.state.xp == xp && !placedEvents.containsRejection)
        engine.state.fishPonds[wild] = FishPond(PondFish("carp", wild, 12.0), now)
        engine.landFish(wild, wild, now)
        assertTrue(engine.state.xp > xp)
    }

    @Test
    fun `a placed fish stays through many days whatever else happens in the pond`() {
        val engine = engine()
        val tile = GridPoint(21, 21)
        engine.useTool(tile, now)
        val random = SeededRandom(5)
        for (day in 1..20) engine.updateFish(now.advancedBy(day * FISH_LIFETIME), using = random)
        assertTrue(
            engine.state.fishPonds.values
                .any { it.fish?.isNaturallyGenerated == false && it.fish?.position == tile },
        )
    }

    @Test
    fun `a fish let go on another fishs home tile keeps both`() {
        val engine = engine()
        val home = GridPoint(21, 21)
        val away = GridPoint(23, 23)
        engine.state.fishPonds[home] = FishPond(PondFish("carp", away, 12.0, now), now)
        assertEquals(listOf<GameEvent>(GameEvent.Placed(home)), engine.useTool(home, now))
        val fish =
            engine.state.fishPonds.values
                .mapNotNull { it.fish }
        assertTrue(fish.size == 2 && fish.map { it.position }.toSet() == setOf(home, away))
        assertEquals(listOf(home), fish.filter { !it.isNaturallyGenerated }.map { it.position })
    }

    @Test
    fun `the flag and held fish survive saves`() {
        val engine = engine()
        engine.useTool(GridPoint(21, 21), now)
        val loaded = GameStateJson.decode(GameStateJson.encode(engine.state))
        assertTrue(loaded.heldItemID == "fish-carp" && sameState(loaded, engine.state))
        assertNull(GameStateJson.decode("{}").heldItemID)
        assertFalse(loaded.fishPonds.isEmpty())
    }
}
