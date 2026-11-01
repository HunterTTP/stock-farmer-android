package com.hunter.stockfarmer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

internal class FarmHostMountTest {
    private val activity = File("src/main/kotlin/com/hunter/stockfarmer/MainActivity.kt").readText()

    @Test
    fun `the farm view is mounted outside the ready gate so a new farm never tears it down`() {
        val gate = activity.indexOf("if (persistence.isReady) {")
        val host = activity.indexOf("FarmHost()")
        assertTrue(gate >= 0 && host >= 0)
        assertTrue(host < gate, "FarmHost() must not sit inside if (persistence.isReady)")
        assertEquals(1, Regex("""FarmHost\(\)""").findAll(activity).count { it.range.first == host })
    }
}
