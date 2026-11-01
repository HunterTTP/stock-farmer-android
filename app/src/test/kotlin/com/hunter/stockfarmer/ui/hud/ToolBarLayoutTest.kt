package com.hunter.stockfarmer.ui.hud

import com.hunter.stockfarmer.ui.theme.Theme
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ToolBarLayoutTest {
    @Test
    fun `wrapping rows pack items until row width is exceeded then start a new row`() {
        val widths = listOf(100, 120, 80, 150)
        val rows = WrappingRows.rows(widths, available = 250, spacing = 10)
        assertEquals(2, rows.size)
        assertEquals(listOf(0, 1), rows[0])
        assertEquals(listOf(2, 3), rows[1])
    }

    @Test
    fun `wrapping rows handles single item wider than available width without infinite loop`() {
        val widths = listOf(300, 50)
        val rows = WrappingRows.rows(widths, available = 200, spacing = 10)
        assertEquals(2, rows.size)
        assertEquals(listOf(0), rows[0])
        assertEquals(listOf(1), rows[1])
    }

    @Test
    fun `wrapping rows returns single empty row for empty inputs`() {
        val rows = WrappingRows.rows(emptyList(), available = 200, spacing = 10)
        assertEquals(listOf(emptyList<Int>()), rows)
    }

    @Test
    fun `dropdown label shows danger color when stock is zero and primary when available`() {
        val emptyStock = ToolDropdown(id = "seed", value = "Wheat", choices = emptyList(), stock = 0)
        val emptyLabel = dropdownLabel(emptyStock)
        assertEquals("Wheat (x0)", emptyLabel.text)
        val emptyStyles = emptyLabel.spanStyles
        assertTrue(emptyStyles.any { it.item.color == Theme.danger })

        val inStock = ToolDropdown(id = "seed", value = "Wheat", choices = emptyList(), stock = 5)
        val inStockLabel = dropdownLabel(inStock)
        assertEquals("Wheat (x5)", inStockLabel.text)
        val inStockStyles = inStockLabel.spanStyles
        assertTrue(inStockStyles.any { it.item.color == Theme.textPrimary })

        val noStock = ToolDropdown(id = "tool", value = "Scythe", choices = emptyList(), stock = null)
        val noStockLabel = dropdownLabel(noStock)
        assertEquals("Scythe", noStockLabel.text)
        assertTrue(noStockLabel.spanStyles.isEmpty())
    }
}
