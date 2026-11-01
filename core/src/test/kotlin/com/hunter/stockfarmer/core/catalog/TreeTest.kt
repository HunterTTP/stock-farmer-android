package com.hunter.stockfarmer.core.catalog

import com.hunter.stockfarmer.core.model.GridPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TreeTest {
    @Test fun `everyTreeIsFreeAndOpenFromTheFirstLevel`() {
        assertTrue(Tree.all.all { it.cost == 0.0 && it.unlockLevel == 1 })
        assertEquals(
            12,
            Tree.all
                .map { it.id }
                .toSet()
                .size,
        )
    }

    @Test fun `aTreeColorOutsideItsSpeciesFallsBackToTheFirst`() {
        assertEquals(TreeSpecies.oak.colors[0], Tree.named("oak-small")!!.color("blue"))
        assertEquals(TreeColor.blue, Tree.named("pine-small")!!.color("blue"))
    }

    @Test fun `tree pictures retain their exact tokens and doubled underscore`() {
        assertEquals(40, Tree.all.sumOf { it.species.colors.size })
        assertEquals("Tree_Pine_Blue__Green_Medium_48x48", Tree.named("pine-medium")!!.assetName(TreeColor.blueGreen))
        assertEquals("Tree_Pine_Blue_Green_Big_48x48", Tree.named("pine-big")!!.assetName(TreeColor.blueGreen))
    }

    @Test fun `growth preserves the trunk and ends at the largest size`() {
        val trunk = GridPoint(20, 20)
        for (species in TreeSpecies.all) {
            assertEquals(species.trees.first(), species.smallest)
            for ((index, tree) in species.trees.withIndex()) {
                assertEquals(trunk, tree.trunk(tree.origin(trunk)))
                assertEquals(tree.side * tree.side, tree.footprint(tree.origin(trunk)).size)
                assertEquals(species.trees.getOrNull(index + 1), tree.next)
            }
        }
    }
}
