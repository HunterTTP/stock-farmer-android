package com.hunter.stockfarmer.core.catalog

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class TreeGrowthTest {
    @Test fun `everyPassOfTheAxeIsOneLogAndEachSizeTakesOneMore`() {
        for (species in TreeSpecies.all) assertEquals(listOf(3, 4, 5, 6), species.trees.map { it.logs })
    }

    @Test fun `anOldSavedTreeWithoutAGrowthTimeStillLoads`() {
        val now = Instant.ofEpochSecond(30_000_000)
        val tree = Json.decodeFromString(PlacedTreeSerializer { now }, """{"treeID":"oak-big","colorID":"green"}""")
        assertEquals("oak-big", tree.treeID)
        assertEquals(now, tree.grewAt)
        assertTrue(tree.isNaturallyGenerated)
    }

    @Test fun `placed trees round trip and missing or null fields use migration defaults`() {
        val now = Instant.ofEpochSecond(30_000_000)
        val tree = PlacedTree(Tree.starter, TreeColor.green, now, false)
        assertEquals(tree, Json.decodeFromString<PlacedTree>(Json.encodeToString(tree)))
        val old =
            Json.decodeFromString(
                PlacedTreeSerializer {
                    now
                },
                """{"treeID":"missing","colorID":"missing","grewAt":null,"isNaturallyGenerated":null}""",
            )
        assertEquals(Tree.starter, old.tree)
        assertEquals(Tree.starter.species.colors[0], old.color)
        assertEquals(now, old.grewAt)
        assertTrue(old.isNaturallyGenerated)
    }
}
