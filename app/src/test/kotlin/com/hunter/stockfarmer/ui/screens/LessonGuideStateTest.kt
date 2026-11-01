package com.hunter.stockfarmer.ui.screens

import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.Lesson
import com.hunter.stockfarmer.core.model.PlayMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LessonGuideStateTest {
    @Test
    fun `plant lesson points to planting then invites dragging and grow never pulses`() {
        val pick = LessonGuideState.from(Lesson.plant, PlayMode.build, HarvestTool.scythe)
        assertEquals(PlayMode.plant, pick.toolTarget)
        assertEquals("Tap the circle and pick Plant", pick.message)
        assertTrue(pick.pulses)
        val plant = LessonGuideState.from(Lesson.plant, PlayMode.plant, HarvestTool.scythe)
        assertNull(plant.toolTarget)
        assertEquals("Drag from the circle over your farmland to plant Wheat Seeds", plant.message)
        assertTrue(plant.pulses)
        val grow = LessonGuideState.from(Lesson.grow, PlayMode.harvest, HarvestTool.scythe)
        assertEquals("Wait for your Wheat to finish growing", grow.message)
        assertFalse(grow.pulses)
    }

    @Test
    fun `harvest lessons distinguish picking harvest picking scythe and dragging it`() {
        for (lesson in listOf(Lesson.pickHarvest, Lesson.harvest)) {
            val pick = LessonGuideState.from(lesson, PlayMode.plant, HarvestTool.scythe)
            assertEquals(PlayMode.harvest, pick.toolTarget)
            assertEquals("Now get the Scythe", pick.message)
            assertTrue(pick.pulses)
            assertFalse(pick.needsScythe)
        }
        val scythe = LessonGuideState.from(Lesson.pickHarvest, PlayMode.harvest, HarvestTool.scythe)
        assertTrue(scythe.needsScythe)
        assertFalse(scythe.pulses)
        val wrong = LessonGuideState.from(Lesson.harvest, PlayMode.harvest, HarvestTool.axe)
        assertTrue(wrong.needsScythe)
        assertEquals("Now get the Scythe", wrong.message)
        val harvest = LessonGuideState.from(Lesson.harvest, PlayMode.harvest, HarvestTool.scythe)
        assertFalse(harvest.needsScythe)
        assertTrue(harvest.pulses)
        assertEquals("Drag the Scythe over the ripe Wheat to harvest it", harvest.message)
    }

    @Test
    fun `storage points at ring and remaining sale lessons leave circle quiet`() {
        val storage = LessonGuideState.from(Lesson.openStorage, PlayMode.harvest, HarvestTool.scythe)
        assertTrue(storage.storageTarget)
        assertEquals("Tap the circle, then Storage, to see your Wheat", storage.message)
        assertTrue(storage.pulses)
        for (lesson in Lesson.entries.dropWhile { it != Lesson.sellItem } + listOf(null)) {
            val state = LessonGuideState.from(lesson, PlayMode.harvest, HarvestTool.scythe)
            assertNull(state.message)
            assertFalse(state.pulses)
            assertFalse(state.storageTarget)
            assertEquals(lesson != null, state.isTeaching)
        }
    }

    @Test
    fun `guide reads first missing saved lesson without learning from a projection`() {
        val state = GameState()
        state.lessonsLearned.addAll(listOf(Lesson.plant, Lesson.grow, Lesson.harvest))
        val learned = state.lessonsLearned.toSet()
        assertEquals(Lesson.pickHarvest, LessonGuideState.from(state).lesson)
        assertEquals(learned, state.lessonsLearned)
        state.lessonsLearned.addAll(Lesson.entries)
        assertFalse(LessonGuideState.from(state).isTeaching)
        assertNull(LessonGuideState.from(state).message)
    }
}
