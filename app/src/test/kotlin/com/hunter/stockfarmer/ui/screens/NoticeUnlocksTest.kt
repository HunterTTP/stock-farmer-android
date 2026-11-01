package com.hunter.stockfarmer.ui.screens

import androidx.compose.ui.graphics.Color
import com.hunter.stockfarmer.core.catalog.Building
import com.hunter.stockfarmer.core.rules.LevelUnlocks
import com.hunter.stockfarmer.ui.hud.ToolPalette
import com.hunter.stockfarmer.ui.menus.MenuPicture
import com.hunter.stockfarmer.ui.menus.MenuPictures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class NoticeUnlocksTest {
    @Test
    fun `multi level notice sections follow the iOS category order`() {
        val sections = LevelUnlocks.unlocked(0, 50).noticeSections()
        assertEquals(
            listOf(
                "Crops",
                "Farmland",
                "Buildings",
                "Decorations",
                "Trees",
                "Recipes",
                "Upgrades",
                "Fishing",
                "Quests",
            ),
            sections.map { it.title },
        )
        assertTrue(sections.all { it.rows.isNotEmpty() })
        val unlocks = LevelUnlocks.unlocked(0, 50)
        assertEquals(
            unlocks.crops.map { it.plantingName } + unlocks.fruitTrees.map { it.name },
            sections.first().rows.map { it.text },
        )
    }

    @Test
    fun `zero farmland and disabled fishing and quests omit their notice sections`() {
        val unlocks =
            LevelUnlocks.unlocked(0, 50).copy(
                farmlandTiles = 0,
                fishRarities = emptyList(),
                fishRatesShift = false,
                stockQuests = false,
            )
        assertEquals(
            listOf("Crops", "Buildings", "Decorations", "Trees", "Recipes", "Upgrades"),
            unlocks.noticeSections().map { it.title },
        )
    }

    @Test
    fun `single and combined level notices preserve section order and have bundled row art`() {
        val ranges = (1..50).map { it - 1 to it } + listOf(0 to 50)
        ranges.forEach { (before, level) ->
            val unlocks = LevelUnlocks.unlocked(before, level)
            val sections = unlocks.noticeSections()
            assertEquals(unlocks.sections().map { it.title }, sections.map { it.title })
            sections.forEach { section ->
                if (section.title != "Farmland") {
                    assertEquals(
                        unlocks.sections().first { it.title == section.title }.rows,
                        section.rows.map { it.text },
                    )
                }
                section.rows.forEach { row ->
                    if (section.title !in listOf("Fishing", "Quests")) {
                        assertTrue(row.picture != null, "${section.title}: ${row.text}")
                    }
                    (row.picture as? MenuPicture.Asset)?.let {
                        assertTrue(File("src/main/assets", it.path).isFile, it.path)
                    }
                }
            }
        }
    }

    @Test
    fun `upgrade art follows tools then homes in the same order as the labels`() {
        val unlocks = LevelUnlocks.unlocked(0, 50)
        val rows = unlocks.noticeSections().first { it.title == "Upgrades" }.rows
        assertEquals(
            unlocks.toolUpgrades.map { ToolPalette.harvestPicture(it.tool) } +
                unlocks.homeUpgrades.map { MenuPictures.building(requireNotNull(Building.named(it.buildingID))) },
            rows.map { it.picture },
        )
    }

    @Test
    fun `farmland counts are grouped and fishing symbols follow the first unlocked rarity tint`() {
        val unlocks = LevelUnlocks.unlocked(9, 10).copy(farmlandTiles = 1234, farmlandTotal = 5678)
        val sections = unlocks.noticeSections()
        assertEquals(
            "1,234 more tiles, 5,678 in all",
            sections
                .first { it.title == "Farmland" }
                .rows
                .single()
                .text,
        )
        val fishing = sections.first { it.title == "Fishing" }.rows
        assertEquals(NoticeSymbol.FISH, fishing.first().symbol)
        val tint = unlocks.fishRarities.first().color
        assertEquals(Color(tint.red.toFloat(), tint.green.toFloat(), tint.blue.toFloat()), fishing.first().tint)
        assertEquals(NoticeSymbol.ARROW, fishing.last().symbol)
        val ratesOnly =
            LevelUnlocks
                .unlocked(4, 5)
                .noticeSections()
                .first { it.title == "Fishing" }
                .rows
        assertEquals(NoticeSymbol.ARROW, ratesOnly.single().symbol)
    }
}
