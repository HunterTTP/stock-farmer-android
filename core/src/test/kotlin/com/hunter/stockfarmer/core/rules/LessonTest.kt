package com.hunter.stockfarmer.core.rules

import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.HarvestTool
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.model.GridPoint
import com.hunter.stockfarmer.core.model.Lesson
import com.hunter.stockfarmer.core.model.PlayMode
import com.hunter.stockfarmer.core.util.advancedBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class LessonTest {
    private val planted = Instant.ofEpochSecond(100)
    private val ripe get() = planted.advancedBy(30.0)
    private val tile = GridPoint(24, 24)

    private fun plantedEngine() = GameEngine().also { it.tap(tile, planted) }

    @Test
    fun `a new game starts with planting`() {
        assertEquals(Lesson.plant, GameEngine().currentLesson)
        assertEquals(
            listOf(
                Lesson.plant,
                Lesson.grow,
                Lesson.pickHarvest,
                Lesson.harvest,
                Lesson.openStorage,
                Lesson.sellItem,
                Lesson.pickMax,
                Lesson.sell,
                Lesson.closeStorage,
            ),
            Lesson.entries,
        )
    }

    @Test
    fun `planting moves on to waiting for the wheat`() {
        assertEquals(Lesson.grow, plantedEngine().currentLesson)
    }

    @Test
    fun `the wheat ripening moves on to picking the harvest tool`() {
        val engine = plantedEngine()
        engine.learnWheatIsRipe(planted)
        assertEquals(Lesson.grow, engine.currentLesson)
        engine.learnWheatIsRipe(ripe)
        assertEquals(Lesson.pickHarvest, engine.currentLesson)
    }

    @Test
    fun `the scythe already in hand when the wheat ripens moves straight on to harvesting`() {
        val engine = plantedEngine()
        engine.setMode(PlayMode.harvest)
        assertEquals(Lesson.grow, engine.currentLesson)
        engine.learnWheatIsRipe(ripe)
        assertEquals(Lesson.harvest, engine.currentLesson)
        assertFalse(engine.isAnyCropRipe(planted))
        assertTrue(engine.isAnyCropRipe(ripe))
    }

    @Test
    fun `choosing the scythe moves on to harvesting`() {
        val engine = plantedEngine()
        engine.learnWheatIsRipe(ripe)
        engine.setMode(PlayMode.harvest)
        assertEquals(Lesson.pickHarvest, engine.currentLesson)
        engine.select(HarvestTool.scythe)
        assertEquals(Lesson.harvest, engine.currentLesson)
    }

    @Test
    fun `the axe or bucket in hand does not count as picking the scythe`() {
        val engine = plantedEngine()
        engine.learnWheatIsRipe(ripe)
        engine.select(HarvestTool.axe)
        assertEquals(Lesson.pickHarvest, engine.currentLesson)
        engine.select(HarvestTool.scythe)
        assertEquals(Lesson.harvest, engine.currentLesson)
    }

    @Test
    fun `picking the harvest tool before planting does not skip planting`() {
        val engine = GameEngine()
        engine.setMode(PlayMode.harvest)
        assertEquals(Lesson.plant, engine.currentLesson)
    }

    @Test
    fun `harvesting wheat moves on to opening storage`() {
        val engine = plantedEngine()
        engine.setMode(PlayMode.harvest)
        engine.useTool(tile, ripe)
        assertEquals(Lesson.openStorage, engine.currentLesson)
        assertEquals(StorageItem.CropItem(Crop.starter), engine.itemToLearnSellingWith)
    }

    @Test
    fun `tapping a ripe crop without the scythe harvests nothing and skips nothing`() {
        val engine = plantedEngine()
        assertEquals(listOf<GameEvent>(GameEvent.Rejected(Rejection.UseTheScythe)), engine.tap(tile, ripe))
        assertEquals(Lesson.grow, engine.currentLesson)
    }

    @Test
    fun `the selling steps cannot be skipped ahead`() {
        val engine = plantedEngine()
        engine.select(HarvestTool.scythe)
        engine.tap(tile, ripe)
        engine.learn(Lesson.sell)
        assertEquals(Lesson.openStorage, engine.currentLesson)
        for (lesson in Lesson.entries) engine.learn(lesson)
        assertNull(engine.currentLesson)
    }

    @Test
    fun `wheat is the example to sell`() {
        val engine = GameEngine()
        engine.state.storage = mutableMapOf(StorageItem.Log.id to 3, (Crop.named("carrot")?.id ?: "") to 2)
        assertNull(engine.itemToLearnSellingWith)
        engine.state.storage[Crop.starter.id] = 2
        assertEquals(StorageItem.CropItem(Crop.starter), engine.itemToLearnSellingWith)
    }

    @Test
    fun `closing storage is the last step`() {
        val engine = GameEngine()
        for (lesson in Lesson.entries.dropLast(1)) engine.learn(lesson)
        assertEquals(Lesson.closeStorage, engine.currentLesson)
        engine.learn(Lesson.closeStorage)
        assertNull(engine.currentLesson)
    }
}
