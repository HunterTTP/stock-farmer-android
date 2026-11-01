package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.beginMoving
import com.hunter.stockfarmer.core.build
import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.lateGameXP
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.PlayMode
import com.hunter.stockfarmer.core.persistence.GameStateJson
import com.hunter.stockfarmer.core.stateWith
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class BuildingStyleTest {
    private val now = Instant.ofEpochSecond(30_000_000)

    private fun engine() =
        GameEngine(stateWith(money = 1_000_000.0, xp = lateGameXP, mode = PlayMode.build, logs = 1_000))

    @Test
    fun `the luxury barn comes in four colors and the tractor in four facings`() {
        val barn = Building.named("barn2")!!
        assertEquals(listOf("Red", "Blue", "Grey", "Yellow"), barn.styles.map { it.name })
        assertTrue(barn.styles.all { it.width == 10 && it.height == 5 })
        val tractor = Building.named("tractor1")!!
        assertEquals(listOf("Right", "Left", "Front", "Back"), tractor.styles.map { it.name })
        assertEquals(
            listOf(listOf(2, 1), listOf(2, 1), listOf(1, 2), listOf(1, 2)),
            tractor.styles.map {
                listOf(it.width, it.height)
            },
        )
    }

    @Test
    fun `the silo has styles but the market stand upgrade is not a free style`() {
        val silo = Building.named("silo")!!
        assertEquals(listOf("Tower", "Tower and Shed"), silo.styles.map { it.name })
        assertEquals(listOf(listOf(2, 5), listOf(3, 6)), silo.styles.map { listOf(it.width, it.height) })
        val small = Building.named("marketStand")!!
        val large = Building.named("marketStandLarge")!!
        assertEquals(small.styles.map { it.id }, large.styles.map { it.id })
        assertEquals(listOf(listOf(2, 2), listOf(3, 2)), listOf(small, large).map { listOf(it.width, it.height) })
    }

    @Test
    fun `the first style is the building as it is in the catalog`() {
        val barn = Building.named("barn2")!!
        assertEquals(barn, barn.styled(null))
        assertEquals(barn, barn.styled("red"))
        assertEquals(barn, barn.styled("nonsense"))
        assertEquals("barn_2_red", barn.assetName)
        val blue = barn.styled("blue")
        assertTrue(blue.assetName == "barn_2_blue" && blue.styleID == "blue" && blue.id == "barn2")
        assertEquals("Blue", blue.style?.name)
    }

    @Test
    fun `a styled building takes its styles footprint`() {
        val tractor = Building.named("tractor1")!!.styled("front")
        assertEquals(2, tractor.footprint(GridPoint(10, 10)).size)
        assertTrue(tractor.width == 1 && tractor.height == 2)
    }

    @Test
    fun `the chosen style is what gets built and is remembered`() {
        val engine = engine()
        val barn = Building.named("barn2")!!
        engine.select(barn.styles.first { it.id == "blue" }, barn)
        assertEquals("blue", engine.selectedBuilding.styleID)
        val events = engine.build(GridPoint(20, 20), now)
        val origin = events.filterIsInstance<GameEvent.Built>().first().point
        assertEquals(mapOf(origin to "blue"), engine.state.buildingStyles)
        assertEquals("barn_2_blue", engine.building(origin)?.building?.assetName)
        // Choosing the building again from the list keeps the color picked for it.
        engine.select(barn)
        assertEquals("blue", engine.selectedBuilding.styleID)
    }

    @Test
    fun `the first style is not written down and is what a building without one has`() {
        val engine = engine()
        engine.state.selectedBuildingID = "barn2"
        engine.build(GridPoint(20, 20), now)
        assertTrue(engine.state.buildingStyles.isEmpty())
        assertEquals(
            Building.named("barn2"),
            engine.state.placedBuildings
                .first()
                .building,
        )
    }

    @Test
    fun `a tractor facing front covers one by two`() {
        val engine = engine()
        val tractor = Building.named("tractor1")!!
        engine.select(tractor.styles.first { it.id == "front" }, tractor)
        engine.beginPlacing(GridPoint(20, 20))
        val placement = engine.buildingPlacement!!
        assertEquals(2, placement.footprint.size)
        assertTrue(
            placement.footprint
                .map { it.col }
                .toSet()
                .size == 1 && placement.footprint
                .map { it.row }
                .toSet()
                .size == 2,
        )
        engine.confirmPlacement(now)
        assertEquals("front", engine.building(placement.footprint[1])?.building?.styleID)
    }

    @Test
    fun `moving keeps the style and demolishing forgets it`() {
        val engine = engine()
        val barn = Building.named("barn2")!!
        engine.select(barn.styles.first { it.id == "yellow" }, barn)
        engine.build(GridPoint(20, 20), now)
        val placed = engine.state.placedBuildings.first()
        engine.beginMoving(placed, GridPoint(30, 30))
        val target = engine.buildingPlacement!!
        assertEquals("yellow", target.building.styleID)
        engine.confirmPlacement(now)
        assertEquals(mapOf(target.origin to "yellow"), engine.state.buildingStyles)
        engine.state.storage = mutableMapOf()
        engine.demolish(engine.state.placedBuildings.first())
        assertTrue(engine.state.buildingStyles.isEmpty())
    }

    @Test
    fun `styles survive a save`() {
        val engine = engine()
        val barn = Building.named("barn2")!!
        engine.select(barn.styles.first { it.id == "grey" }, barn)
        engine.build(GridPoint(20, 20), now)
        val loaded = GameStateJson.decode(GameStateJson.encode(engine.state))
        assertEquals(engine.state.buildingStyles, loaded.buildingStyles)
        assertEquals(
            "grey",
            loaded.placedBuildings
                .first()
                .building.styleID,
        )
    }
}
