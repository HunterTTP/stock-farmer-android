package com.hunter.stockfarmer.core.persistence

import com.hunter.stockfarmer.core.catalog.Cat
import com.hunter.stockfarmer.core.catalog.ChickenVariant
import com.hunter.stockfarmer.core.catalog.Decoration
import com.hunter.stockfarmer.core.catalog.FenceColor
import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.catalog.LandscapeTool
import com.hunter.stockfarmer.core.catalog.LivestockSpecies
import com.hunter.stockfarmer.core.catalog.PlacedDecoration
import com.hunter.stockfarmer.core.catalog.PlacedTree
import com.hunter.stockfarmer.core.catalog.RockSize
import com.hunter.stockfarmer.core.catalog.Tree
import com.hunter.stockfarmer.core.catalog.TreeColor
import com.hunter.stockfarmer.core.catalog.WaterStyle
import com.hunter.stockfarmer.core.catalog.Workshop
import com.hunter.stockfarmer.core.model.Chicken
import com.hunter.stockfarmer.core.model.Coop
import com.hunter.stockfarmer.core.model.Egg
import com.hunter.stockfarmer.core.model.FishPond
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Herd
import com.hunter.stockfarmer.core.model.ItemSale
import com.hunter.stockfarmer.core.model.Lesson
import com.hunter.stockfarmer.core.model.Livestock
import com.hunter.stockfarmer.core.model.Pet
import com.hunter.stockfarmer.core.model.PlayMode
import com.hunter.stockfarmer.core.model.Plot
import com.hunter.stockfarmer.core.model.PondFish
import com.hunter.stockfarmer.core.stateWith
import com.hunter.stockfarmer.core.util.advancedBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

class SaveStoreTest {
    @TempDir
    lateinit var directory: Path

    private val savedAt = Instant.ofEpochSecond(14_000_000)

    private fun makeStore() = FileSaveStore(directory.resolve("game.json"))

    /** A game with something in every part of the state, so a field left out of saving shows up. */
    private fun fullyPlayedState(): GameState {
        val state = stateWith(money = 1234.5, xp = 80, mode = PlayMode.decorate)
        val spot = GridPoint(10, 10)
        state.plots[spot] = Plot("wheat", savedAt)
        state.storage["wheat"] = 4
        state.harvestTool = HarvestTool.axe
        state.fertilized = mutableSetOf(spot)
        state.water[GridPoint(3, 3)] = WaterStyle.deep
        state.putBuilding(GridPoint(20, 20), "petHouse", "blue")
        state.putBuilding(GridPoint(30, 30), "chickenCoop")
        state.pets[GridPoint(20, 20)] = Pet(breed = Cat.starter.id, name = "Mittens", position = GridPoint(21, 20))
        val chicken = Chicken.hatched(0, ChickenVariant.all[1], "Henny", GridPoint(31, 30), savedAt)
        state.coops[GridPoint(30, 30)] = Coop(chickens = listOf(chicken), nextChickAt = savedAt)
        state.putTree(GridPoint(40, 40), PlacedTree(Tree.all[3], TreeColor.darkGreen, savedAt))
        state.eggs = listOf(Egg(GridPoint(31, 30), 0.1, -0.1, GridPoint(30, 30), 0))
        state.fences[GridPoint(5, 5)] = FenceColor.starter.id
        state.selectedLandscape = LandscapeTool.water
        state.clockOffset = 1_234.0
        state.fertilizesWithSprinklers = true
        state.lastDecorationID = "scarecrow"
        state.selectedDecorationID = "well"
        state.selectedDecorationSizes = mutableMapOf("pumpkin" to 1)
        state.rocks[GridPoint(6, 6)] = RockSize.big
        state.putDecoration(GridPoint(8, 8), PlacedDecoration.of(Decoration.named("pumpkin")!!, 2))
        state.workshops[GridPoint(12, 12)] = Workshop(savedAt)
        state.lessonsLearned = mutableSetOf(Lesson.plant)
        state.farmland.add(GridPoint(26, 25))
        state.sellingTools = mutableSetOf(HarvestTool.bucket)
        state.selectedBuildingStyleIDs = mutableMapOf("barn" to "blue")
        val duck = LivestockSpecies.variant("duck:white")!!.second
        val herd = Herd(listOf(Livestock.born(2, duck, "Daisy", GridPoint(3, 3), savedAt)), savedAt)
        state.herds[GridPoint(3, 3)] = herd
        state.fishPonds[GridPoint(3, 3)] = FishPond(PondFish("carp", GridPoint(3, 3)), savedAt)
        state.hasRocks = true
        state.selectedTreeID = Tree.all[3].id
        state.selectedTreeColorID = TreeColor.darkGreen.id
        state.seenUnlocks = mutableSetOf("crop:carrot")
        state.selectedCropID = "carrot"
        state.selectedTerrain = LandscapeTool.grass
        state.selectedWaterStyle = WaterStyle.shallow
        state.selectedBuildingID = "chickenCoop"
        state.selectedFenceColorID = FenceColor.starter.id
        state.day = 4
        state.salesToday = mutableMapOf("wheat" to ItemSale("wheat", "Wheat", 3, 9.0))
        state.lingeringWater[spot] = savedAt.advancedBy(60.0)
        state.lastSavedAt = savedAt
        return state
    }

    @Test
    fun `load returns null when nothing saved`() {
        assertNull(makeStore().load())
    }

    @Test
    fun `a fully played game round trips`() {
        val store = makeStore()
        val state = fullyPlayedState()
        store.save(state)
        assertEquals(GameStateJson.encode(state), GameStateJson.encode(store.load()!!))
    }

    @Test
    fun `a save from a newer version of the game is refused rather than misread`() {
        val file = directory.resolve("newer.json")
        Files.writeString(file, """{"schemaVersion":999,"state":{"money":5}}""")
        assertThrows(NewerSaveVersionException::class.java) { FileSaveStore(file).load() }
    }
}
