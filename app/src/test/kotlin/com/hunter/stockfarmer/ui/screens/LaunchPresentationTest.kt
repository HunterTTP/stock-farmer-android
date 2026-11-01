package com.hunter.stockfarmer.ui.screens

import com.hunter.stockfarmer.core.presentation.LaunchSequence
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LaunchPresentationTest {
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

    @Test
    fun `launch stages follow the loader and then the renderer`() {
        val stage = LaunchSequence.Stage.entries.associateBy { it.name }
        assertEquals(stage["loadingFarm"], LaunchPresentation.stage(false, true, false, 0))
        assertEquals(stage["failed"], LaunchPresentation.stage(false, false, false, 0))
        assertEquals(stage["buildingScene"], LaunchPresentation.stage(true, false, false, 0))
        assertEquals(stage["loadingTextures"], LaunchPresentation.stage(true, false, true, 0))
        assertEquals(stage["waitingForFrame"], LaunchPresentation.stage(true, false, true, 1))
        assertEquals(stage["ready"], LaunchPresentation.stage(true, false, true, 2))
    }
}
