package com.hunter.stockfarmer.farm.animals

import com.hunter.stockfarmer.core.model.Chicken
import com.hunter.stockfarmer.core.model.Coop
import com.hunter.stockfarmer.core.model.Egg
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.GridPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

internal class AnimalSnapshotTest {
    private val now = Instant.parse("2026-01-01T12:00:00Z")

    @Test
    fun `eggs follow their own hen by home and id rather than saved egg offsets`() {
        val state = GameState()
        val first = GridPoint(5, 5)
        val second = GridPoint(15, 15)
        val hen = Chicken(0, "hen-white", "Hen", GridPoint(8, 8), hatchedAt = Instant.EPOCH)
        state.coops[first] = Coop(chickens = listOf(hen), nextChickAt = now)
        state.coops[second] = Coop(chickens = listOf(hen.copy(position = GridPoint(18, 18))), nextChickAt = now)
        state.eggs = listOf(Egg(GridPoint(7, 7), 0.2, -0.2, first, hen.id))
        val before = state.copy()
        val snapshots = AnimalSnapshot.all(state, now)
        assertEquals(2, snapshots.map { it.key }.distinct().size)
        assertTrue(snapshots.first { it.key.home == first }.egg)
        assertFalse(snapshots.first { it.key.home == second }.egg)
        assertEquals(hen.position, snapshots.first { it.key.home == first }.point)
        assertEquals(before.coops, state.coops)
        assertEquals(before.eggs, state.eggs)

        state.eggs = emptyList()
        assertTrue(AnimalSnapshot.all(state, now).none { it.egg })
        state.coops.clear()
        assertTrue(AnimalSnapshot.all(state, now).isEmpty())
    }
}
