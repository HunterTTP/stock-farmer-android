package com.hunter.stockfarmer.ui.sheets

import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.Recipe
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.model.QuestGoal
import com.hunter.stockfarmer.ui.screens.ScreenArt
import com.hunter.stockfarmer.ui.screens.iconSymbol
import com.hunter.stockfarmer.ui.screens.item
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class MarketQuestSectionTest {
    @Test
    fun `workshop objectives show the building followed by its default recipe output`() {
        Recipe.all.map { it.buildingID }.distinct().forEach { id ->
            val building = requireNotNull(Building.named(id))
            val output = requireNotNull(Recipe.of(building)).output
            assertEquals(
                listOf(
                    QuestObjectiveIcon(ScreenArt.image("buildings", building.assetName), width = 40),
                    QuestObjectiveIcon(
                        ScreenArt.item(output),
                        output.iconSymbol,
                        width = 28,
                        height = 32,
                        isFlour = output == StorageItem.Flour,
                    ),
                ),
                questObjectiveIcons(QuestGoal.Workshop(id)),
            )
        }
    }

    @Test
    fun `feeding shows wheat before the animal home`() {
        val icons = questObjectiveIcons(QuestGoal.Feed("chickenCoop"))
        assertEquals(2, icons.size)
        assertEquals(QuestObjectiveIcon(ScreenArt.crop(Crop.starter), width = 28, height = 32), icons.first())
        assertEquals(40, icons.last().width)
    }

    @Test
    fun `tree planting shows a tree and a ripe fruit tree instead of a fruit basket`() {
        val icons = questObjectiveIcons(QuestGoal.PlantTrees)
        assertEquals(listOf(32, 36), icons.map { it.width })
        assertEquals(listOf(44, 44), icons.map { it.height })
        assertTrue(requireNotNull(icons.last().art).path.contains("_Ripe_"))
    }

    @Test
    fun `harvest and collect use storage artwork including fruit baskets`() {
        Crop.all.forEach { crop ->
            assertEquals(
                listOf(
                    QuestObjectiveIcon(
                        ScreenArt.item(StorageItem.CropItem(crop)),
                        StorageItem.CropItem(crop).iconSymbol,
                    ),
                ),
                questObjectiveIcons(QuestGoal.Harvest(crop.id)),
            )
        }
        assertEquals(
            listOf(QuestObjectiveIcon(ScreenArt.item(StorageItem.Egg), StorageItem.Egg.iconSymbol)),
            questObjectiveIcons(QuestGoal.Collect(StorageItem.Egg.id)),
        )
    }

    @Test
    fun `stock goals share the small house icon and every quest bitmap exists`() {
        assertEquals(questObjectiveIcons(QuestGoal.StockProfit), questObjectiveIcons(QuestGoal.StockValue))
        assertEquals(40, questObjectiveIcons(QuestGoal.StockProfit).single().width)
        val goals =
            Crop.all.map { QuestGoal.Harvest(it.id) } +
                Recipe.all.map { QuestGoal.Workshop(it.buildingID) } +
                listOf(QuestGoal.PlantTrees, QuestGoal.StockProfit, QuestGoal.Feed("chickenCoop"))
        val assets = File("src/main/assets").takeIf { it.isDirectory } ?: File("app/src/main/assets")
        goals.flatMap(::questObjectiveIcons).mapNotNull { it.art }.forEach {
            assertTrue(File(assets, it.path).isFile, it.path)
        }
    }
}
