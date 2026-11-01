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
import com.hunter.stockfarmer.ui.theme.Theme
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class CareerCollectionStateTest {
    @Test
    fun `switching filters partitions search matches and leaves the original collection intact`() {
        val section =
            CareerSection(
                "Pine Trees",
                listOf(
                    CareerEntry("a", "Small Green", true, detail = "Blue"),
                    CareerEntry("b", "Huge Green", false),
                    CareerEntry("c", "Small Blue", true),
                ),
                Theme.accent,
            )
        assertEquals(listOf("All", "Collected", "Not yet"), CareerFilter.entries.map { it.title })
        val expected =
            mapOf(
                CareerFilter.ALL to listOf("a", "b"),
                CareerFilter.COLLECTED to listOf("a"),
                CareerFilter.LOCKED to listOf("b"),
            )
        for ((filter, ids) in expected) {
            val result = requireNotNull(section.filtered("  GREEN   pInE  ", filter))
            assertEquals(ids, result.entries.map { it.id })
            assertEquals(section.title, result.title)
            assertEquals(section.tint, result.tint)
        }
        assertEquals(3, section.entries.size)
        assertEquals(2, section.collected)
        assertEquals(listOf("c"), section.filtered("blue", CareerFilter.ALL)?.entries?.map { it.id })
        assertNull(section.filtered("green blue", CareerFilter.ALL))
    }

    @Test
    fun `empty queries preserve entry order and empty collections vanish for every filter`() {
        val entries = listOf(CareerEntry("z", "Z", false), CareerEntry("a", "A", true))
        val section = CareerSection("Items", entries)
        assertEquals(section, section.filtered("", CareerFilter.ALL))
        assertEquals(section, section.filtered("    ", CareerFilter.ALL))
        for (filter in CareerFilter.entries) {
            assertNull(CareerSection("Empty", emptyList()).filtered("", filter))
        }
        assertNull(section.filtered("missing", CareerFilter.COLLECTED))
        assertEquals(listOf(entries[1]), section.filtered("ITEMS items", CareerFilter.COLLECTED)?.entries)
    }

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

    @Test
    fun `fish sections take their rarity tint and tool levels their level color`() {
        val engine = GameEngine(GameState())
        val fish = CareerCollections.sections(engine, CareerCollection.FISH)
        assertTrue(fish.all { it.tint != null })
        assertNull(CareerCollections.sections(engine, CareerCollection.CROPS).single().tint)
        val tools = CareerCollections.sections(engine, CareerCollection.TOOLS)
        assertTrue(tools.all { section -> section.entries.all { it.nameColor != null } })
        assertEquals(
            Theme.textPrimary,
            tools
                .first()
                .entries
                .first()
                .nameColor,
        )
        assertEquals(Theme.xp, tools.first().entries[1].nameColor)
        assertEquals(Theme.toolLevel3, tools.first().entries[2].nameColor)
    }
}
