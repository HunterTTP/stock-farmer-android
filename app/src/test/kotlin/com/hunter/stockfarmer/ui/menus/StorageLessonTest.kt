package com.hunter.stockfarmer.ui.menus

import com.hunter.stockfarmer.core.catalog.Crop
import com.hunter.stockfarmer.core.catalog.StorageItem
import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.Lesson
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.core.rules.currentLesson
import com.hunter.stockfarmer.core.rules.learn
import com.hunter.stockfarmer.game.GameSession
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StorageLessonTest {
    @Test
    fun `stored row highlights wheat only when sellItem lesson is active`() {
        val state =
            GameState().apply {
                storage[Crop.starter.id] = 5
                storage[StorageItem.Egg.id] = 2
                lessonsLearned.addAll(
                    listOf(Lesson.plant, Lesson.grow, Lesson.pickHarvest, Lesson.harvest, Lesson.openStorage),
                )
            }
        val menuState = MenuState.from(state, Menu.Storage)
        assertEquals(Lesson.sellItem, menuState.currentLesson)
        val wheatRow = menuState.stored.first { it.item.id == Crop.starter.id }
        val eggRow = menuState.stored.first { it.item.id == StorageItem.Egg.id }
        assertTrue(wheatRow.isLesson)
        assertFalse(eggRow.isLesson)
    }

    @Test
    fun `stored row does not highlight wheat when other lessons are active`() {
        val state =
            GameState().apply {
                storage[Crop.starter.id] = 5
                lessonsLearned.addAll(listOf(Lesson.plant, Lesson.grow, Lesson.pickHarvest, Lesson.harvest))
            }
        val menuState = MenuState.from(state, Menu.Storage)
        assertEquals(Lesson.openStorage, menuState.currentLesson)
        val wheatRow = menuState.stored.first { it.item.id == Crop.starter.id }
        assertFalse(wheatRow.isLesson)
    }

    @Test
    fun `full storage tutorial progression advances through every step to completion`() {
        val engine =
            GameEngine(
                GameState().apply {
                    storage[Crop.starter.id] = 3
                    lessonsLearned.addAll(listOf(Lesson.plant, Lesson.grow, Lesson.pickHarvest, Lesson.harvest))
                },
            )
        val session = GameSession(engine)

        // Step 1: openStorage
        assertEquals(Lesson.openStorage, session.act { it.currentLesson })
        session.act { it.learn(Lesson.openStorage) }

        // Step 2: sellItem
        assertEquals(Lesson.sellItem, session.act { it.currentLesson })
        val menuAfterOpen = session.read { MenuState.from(it, Menu.Storage) }
        assertTrue(menuAfterOpen.stored.first { it.item.id == Crop.starter.id }.isLesson)
        session.act { it.learn(Lesson.sellItem) }

        // Step 3: pickMax
        assertEquals(Lesson.pickMax, session.act { it.currentLesson })
        session.act { it.learn(Lesson.pickMax) }

        // Step 4: sell
        assertEquals(Lesson.sell, session.act { it.currentLesson })
        session.act {
            it.learn(Lesson.sell)
            MenuActions.transact(it, StorageItem.CropItem(Crop.starter), 3, sell = true)
        }

        // Step 5: closeStorage
        assertEquals(Lesson.closeStorage, session.act { it.currentLesson })
        val completed =
            session.act {
                if (it.currentLesson == Lesson.closeStorage) {
                    it.learn(Lesson.closeStorage)
                    true
                } else {
                    false
                }
            }
        assertTrue(completed)
        assertNull(session.act { it.currentLesson })
    }

    @Test
    fun `session showToast emits completion message`() =
        runBlocking {
            val session = GameSession()
            val deferred = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { session.toasts.first() }
            session.showToast("Have fun building your farm!")
            val toast = deferred.await()
            assertEquals("Have fun building your farm!", toast.text)
            assertNull(toast.tool)
        }
}
