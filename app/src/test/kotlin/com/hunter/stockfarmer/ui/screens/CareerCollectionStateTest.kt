package com.hunter.stockfarmer.ui.screens

import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.Cat
import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.HomeUpgrade
import com.hunter.stockfarmer.core.catalog.Recipe
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.ValueSample
import com.hunter.stockfarmer.core.rules.CareerSeries
import com.hunter.stockfarmer.core.rules.GameEngine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class CareerCollectionStateTest {
    @Test
    fun `collections use catalog order and include uncollected items and every home upgrade`() {
        val engine = GameEngine(GameState())
        engine.state.career.plantedCropIDs += Crop.all[2].id
        val crops = CareerCollections.sections(engine, CareerCollection.CROPS).single()
        assertEquals(Crop.all.map { it.id }, crops.entries.map { it.id })
        assertEquals(listOf(Crop.all[2].id), crops.entries.filter { it.collected }.map { it.id })
        val buildings = CareerCollections.sections(engine, CareerCollection.BUILDINGS)
        assertEquals(Building.all.map { it.id }, buildings[0].entries.map { it.id })
        assertEquals(HomeUpgrade.all.size, buildings[1].entries.size)
        val recipes = CareerCollections.sections(engine, CareerCollection.RECIPES)
        assertEquals(Recipe.all.size, recipes.sumOf { it.entries.size })
        assertTrue(CareerCollections.sections(engine, CareerCollection.TOOLS).all { it.entries.first().collected })
    }

    @Test
    fun `fish records distinguish uncaught legacy catches and measured heaviest catches`() {
        val engine = GameEngine(GameState())
        engine.state.career.heaviestCatches["carp"] = 12.34
        val carp =
            CareerCollections.sections(engine, CareerCollection.FISH).flatMap { it.entries }.first {
                it.id ==
                    "carp"
            }
        assertEquals("Best 12.3 lb", carp.detail)
        assertTrue(carp.collected)
        engine.state.career.heaviestCatches["carp"] = 0.0
        assertEquals(
            "Caught",
            CareerCollections
                .sections(engine, CareerCollection.FISH)
                .flatMap { it.entries }
                .first { it.id == "carp" }
                .detail,
        )
    }

    @Test
    fun `cat display IDs are namespaced but collection uses the core breed ID`() {
        val engine = GameEngine(GameState())
        engine.state.career.workedAnimalIDs += Cat.starter.id
        val pets = CareerCollections.sections(engine, CareerCollection.ANIMALS).last()
        assertTrue(pets.entries.first { it.id == "cat-${Cat.starter.id}" }.collected)
    }

    @Test
    fun `search matches every word across section and entry and filters hide empty sections`() {
        val section =
            CareerSection(
                "Pine Trees",
                listOf(
                    CareerEntry("a", "Small Green", true),
                    CareerEntry("b", "Huge Blue", false),
                ),
            )
        assertEquals(listOf("a"), section.filtered("pine GREEN", CareerFilter.ALL)?.entries?.map { it.id })
        assertNull(section.filtered("green", CareerFilter.LOCKED))
        assertEquals(listOf("b"), section.filtered("  ", CareerFilter.LOCKED)?.entries?.map { it.id })
        assertNull(section.filtered("oak", CareerFilter.ALL))
    }

    @Test
    fun `career charts use engine history including now and total perfection uses all entries`() {
        val engine = GameEngine(GameState().apply { xp = 20 })
        engine.state.playTime = 600.0
        engine.state.career.incomeHistory = listOf(ValueSample(300.0, 1000))
        engine.state.career.grossIncomeCents = 2500
        val state = CareerState.from(engine)
        assertEquals(
            listOf(ValueSample(0.0, 0), ValueSample(300.0, 1000), ValueSample(600.0, 2500)),
            state.histories[CareerSeries.INCOME],
        )
        assertEquals(
            state.collections.values
                .flatten()
                .sumOf { it.entries.size },
            state.total,
        )
        assertEquals(state.collected * 100 / state.total, state.perfection)
        engine.state.career.plantedCropIDs += Crop.starter.id
        assertFalse(
            state.collections
                .getValue(CareerCollection.CROPS)
                .single()
                .entries
                .first()
                .collected,
        )
    }

    @Test
    fun `all collection asset paths exist in bundled iOS graphics`() {
        val engine = GameEngine(GameState())
        val assets = File("src/main/assets")
        CareerCollection.entries
            .flatMap { CareerCollections.sections(engine, it) }
            .flatMap { it.entries }
            .mapNotNull { it.art }
            .forEach { assertTrue(File(assets, it.path).isFile, it.path) }
    }
}
