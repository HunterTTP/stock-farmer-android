package com.hunter.stockfarmer.ui.screens

import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.model.ChangelogDay
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.Lesson
import com.hunter.stockfarmer.core.model.PlayMode
import com.hunter.stockfarmer.core.presentation.LaunchSequence
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class RemainingScreenStateTest {
    private val days = (20 downTo 1).map { ChangelogDay("2026-10-%02d".format(it), listOf("Wheat $it", "Farm $it")) }

    @Test
    fun `changelog opens newest day and pages seven recorded days at a time`() {
        val state = ChangelogState(days)
        assertTrue(state.isExpanded(days.first().date))
        assertFalse(state.isExpanded(days[1].date))
        assertEquals(3, state.pageCount)
        assertEquals(days.take(7), state.shown)
        assertEquals(days.drop(7).take(7), state.goToPage(1).shown)
        assertEquals(days.takeLast(6), state.goToPage(99).shown)
        assertEquals(0, state.goToPage(-1).page)
    }

    @Test
    fun `search matches only lines ignoring case and spaces and expands results without changing folds`() {
        val state = ChangelogState(days).toggle(days[1].date).goToPage(2).search("  wHeAt  ")
        assertEquals(0, state.page)
        assertTrue(state.shown.all { it.lines.size == 1 && it.lines.single().startsWith("Wheat") })
        assertTrue(state.isExpanded(days[2].date))
        assertTrue(state.toggle(days[2].date).search("").isExpanded(days[1].date))
        assertFalse(state.toggle(days[2].date).search("").isExpanded(days[2].date))
        assertEquals(1, state.search("no match").pageCount)
        assertTrue(state.search("2026").shown.isEmpty())
        assertEquals(days.take(7), state.search("   ").shown)
    }

    @Test
    fun `calendar includes every day between earliest and latest selections including gaps`() {
        val picked = setOf(LocalDate.parse("2026-10-03"), LocalDate.parse("2026-10-06"))
        val state = ChangelogState(days).goToPage(2).pick(picked)
        assertEquals(0, state.page)
        assertEquals(listOf("2026-10-06", "2026-10-05", "2026-10-04", "2026-10-03"), state.shown.map { it.date })
        assertEquals(
            listOf("Wheat 5"),
            state
                .search("Wheat 5")
                .shown
                .single()
                .lines,
        )
        assertEquals(
            "2026-10-03",
            state
                .pick(setOf(picked.min()))
                .shown
                .single()
                .date,
        )
        assertEquals(days.take(7), state.pick(emptySet()).shown)
        assertEquals(1, ChangelogState(emptyList()).pageCount)
    }

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

    @Test
    fun `launch failure presents preserved save message while slow loading has delayed text`() {
        val loading = LaunchPresentation.from(LaunchSequence())
        assertNull(loading.error)
        assertFalse(loading.showsLoadingMessage)
        val slow = LaunchSequence().advance(2.0, true, false)
        assertTrue(LaunchPresentation.from(slow).showsLoadingMessage)
        val failed = LaunchSequence(stage = LaunchSequence.Stage.failed)
        assertEquals(
            "This farm could not be opened. Its save has been kept safe.",
            LaunchPresentation.from(failed).error,
        )
        assertEquals("Disk unavailable", LaunchPresentation.from(failed, "Disk unavailable").error)
        assertFalse(LaunchPresentation.from(failed).showsLoadingMessage)
        assertEquals("Farm ready", LaunchPresentation.from(LaunchSequence(stage = LaunchSequence.Stage.ready)).label)
    }

    @Test
    fun `launch stage changes preserve progress and retry starts a fresh introduction`() {
        val loading = LaunchSequence().advance(2.0, true, false)
        val textures = LaunchPresentation.transition(loading, LaunchSequence.Stage.loadingTextures)
        assertEquals(loading.displayedProgress, textures.displayedProgress)
        assertTrue(textures.showsLoadingMessage)
        val failed = LaunchPresentation.transition(textures, LaunchSequence.Stage.failed)
        assertFalse(failed.isComplete)
        assertEquals(LaunchSequence(), LaunchPresentation.transition(failed, LaunchSequence.Stage.loadingFarm))
    }
}
