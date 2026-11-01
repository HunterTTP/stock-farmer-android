package com.hunter.stockfarmer.ui.sheets

import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.Cat
import com.hunter.stockfarmer.core.catalog.ChickenVariant
import com.hunter.stockfarmer.core.catalog.LivestockSpecies
import com.hunter.stockfarmer.core.model.Chicken
import com.hunter.stockfarmer.core.model.Coop
import com.hunter.stockfarmer.core.model.FishPond
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Herd
import com.hunter.stockfarmer.core.model.Livestock
import com.hunter.stockfarmer.core.model.Pet
import com.hunter.stockfarmer.core.model.PondFish
import com.hunter.stockfarmer.core.model.TractorFacing
import com.hunter.stockfarmer.core.model.TractorRun
import com.hunter.stockfarmer.core.model.tractorOccupiedTiles
import com.hunter.stockfarmer.core.rules.AnimalRef
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.buildingPlacement
import com.hunter.stockfarmer.game.GameSession
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant

class SheetTargetTest {
    private val now = Instant.ofEpochSecond(60_000_000)
    private val home = GridPoint(10, 10)
    private val spot = GridPoint(20, 20)

    @Test
    fun `every building and style resolves any tile of its footprint to its origin`() {
        (Building.all + Building.upgradedHouse + Building.upgradedMarketStand).forEach { building ->
            (listOf(building) + building.styles.map { building.styled(it.id) }).forEach { styled ->
                val engine = GameEngine()
                engine.state.putBuilding(home, styled.id, styled.styleID)
                val session = GameSession(engine)
                styled.footprint(home).forEach { assertEquals(SheetTarget.Building(home), session.targetAt(it)) }
            }
        }
    }

    @Test
    fun `animals are identified by their current positions including pond ducks`() {
        val engine = GameEngine()
        val session = GameSession(engine)
        engine.state.pets[home] = Pet(breed = Cat.starter.id, name = "Cat", position = spot)
        assertEquals(SheetTarget.Animal(AnimalRef.Pet(home)), session.targetAt(spot))
        engine.state.pets.clear()
        engine.state.coops[home] =
            Coop(
                chickens =
                    listOf(Chicken.hatched(7, ChickenVariant.all.first(), "Hen", spot, now)),
                nextChickAt = now,
            )
        assertEquals(SheetTarget.Animal(AnimalRef.Chicken(home, 7)), session.targetAt(spot))
        engine.state.coops.clear()
        engine.state.herds[home] =
            Herd(
                animals =
                    listOf(Livestock.born(9, LivestockSpecies.duck.variants.first(), "Duck", spot, now)),
                nextBirthAt = now,
            )
        assertEquals(SheetTarget.Animal(AnimalRef.Livestock(home, 9)), session.targetAt(spot))
        assertNull(session.targetAt(GridPoint(0, 0)))
    }

    @Test
    fun `a swimming fish uses its saved pond identity and empty water has no target`() {
        val engine = GameEngine()
        val session = GameSession(engine)
        engine.state.fishPonds[home] = FishPond(PondFish("carp", spot), now)
        assertEquals(SheetTarget.Fish(home), session.targetAt(spot))
        assertNull(session.targetAt(home))
        engine.state.fishPonds[home] = FishPond(nextFishAt = now)
        assertNull(session.targetAt(spot))
    }

    @Test
    fun `a driving tractor resolves to its building from both ends of a step`() {
        val engine = GameEngine()
        engine.state.putBuilding(home, "tractor1", null)
        engine.state.tractorRun =
            TractorRun(home, "wheat", spot, nextTile = GridPoint(20, 21), facing = TractorFacing.right)
        val session = GameSession(engine)
        engine.state.tractorOccupiedTiles.forEach { assertEquals(SheetTarget.Building(home), session.targetAt(it)) }
    }

    @Test
    fun `building classification takes priority over animals and does not mutate the farm`() {
        val engine = GameEngine()
        engine.state.putBuilding(home, "petHouse", null)
        engine.state.pets[home] = Pet(breed = Cat.starter.id, name = "Cat", position = home)
        val before = engine.state.copy()
        assertEquals(SheetTarget.Building(home), GameSession(engine).targetAt(home))
        assertEquals(before.pets, engine.state.pets)
        assertEquals(before.buildings, engine.state.buildings)
        assertNull(engine.buildingPlacement)
    }
}
