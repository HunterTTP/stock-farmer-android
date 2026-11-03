package com.hunter.stockfarmer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The layers run `core`, then these lower packages, then `farm` and `ui`, then the activity. Scans import lines so a
 * lower package cannot start depending on the presentation layers again.
 */
class PackageLayeringTest {
    private val sources = File("src/main/kotlin/com/hunter/stockfarmer")
    private val presentation = listOf("ui", "farm")

    /** Imports a lower package may still have, written as "path/File.kt imports package". None are expected. */
    private val allowed = emptySet<String>()

    private val lowerPackages = listOf("game", "audio", "settings", "diagnostics", "market", "haptics", "notifications")

    @Test
    fun `lower packages do not import ui or farm`() {
        assertTrue(File(sources, "game").walkTopDown().any { it.extension == "kt" }, "sources not found")
        val violations =
            lowerPackages.flatMap { pkg ->
                File(sources, pkg)
                    .walkTopDown()
                    .filter { it.extension == "kt" }
                    .flatMap { file -> importsOf(file).filter { it.startsWithAny(presentation) }.map { file to it } }
                    .map { (file, imported) -> "${file.relativeTo(sources).invariantSeparatorsPath} imports $imported" }
                    .filter { it !in allowed }
                    .toList()
            }
        assertEquals(emptyList<String>(), violations)
    }

    private fun importsOf(file: File): List<String> =
        file
            .readLines()
            .filter { it.startsWith("import ") }
            .map { it.removePrefix("import ").substringBefore(" as ").trim() }
            .filter { it.startsWith("$ROOT.") }
            .map { it.removePrefix("$ROOT.") }

    private fun String.startsWithAny(packages: List<String>) = packages.any { startsWith("$it.") }

    private companion object {
        const val ROOT = "com.hunter.stockfarmer"
    }
}
