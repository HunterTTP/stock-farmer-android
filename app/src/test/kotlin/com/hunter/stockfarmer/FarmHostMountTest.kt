package com.hunter.stockfarmer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

internal class FarmHostMountTest {
    private val root = File("src/main/kotlin/com/hunter/stockfarmer/GameRoot.kt").readText()
    private val activity = File("src/main/kotlin/com/hunter/stockfarmer/MainActivity.kt").readText()

    @Test
    fun `the farm view is mounted outside the ready gate so a new farm never tears it down`() {
        val gate = root.indexOf("if (persistence.isReady) {")
        val host = root.indexOf("farmHost()")
        assertTrue(gate >= 0 && host >= 0)
        assertTrue(host < gate, "farmHost() must not sit inside if (persistence.isReady)")
        assertEquals(1, Regex("""farmHost\(\)""").findAll(root).count { it.range.first == host })
        assertTrue("GameRoot(model, sound, farmHost = { FarmHost() })" in activity)
    }
}
