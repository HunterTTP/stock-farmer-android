package com.hunter.stockfarmer.core.presentation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.max

class LaunchSequenceTest {
    @Test fun `instant load has a brief introduction then reveals`() {
        var sequence = LaunchSequence(stage = LaunchSequence.Stage.ready)
        sequence = sequence.advance(0.34, true, false)
        assertEquals(0.0, sequence.revealProgress)
        sequence = sequence.advance(0.13, true, false)
        assertEquals(0.0, sequence.contentOpacity)
        assertTrue(sequence.revealProgress < 0.0001)
        sequence = sequence.advance(0.275, true, false)
        assertTrue(abs(sequence.revealProgress - 0.5) < 0.0001)
        assertFalse(sequence.isComplete)
        sequence = sequence.advance(0.275, true, false)
        assertTrue(sequence.isComplete)
    }

    @Test fun `slow loads never reveal before the farm is ready`() {
        var sequence = LaunchSequence().advance(20.0, true, false)
        assertTrue(sequence.showsLoadingMessage)
        assertEquals(0.0, sequence.revealProgress)
        sequence = sequence.copy(stage = LaunchSequence.Stage.waitingForFrame).advance(20.0, true, false)
        assertTrue(sequence.progress < 1)
        assertEquals(1.0, sequence.contentOpacity)
        sequence = sequence.copy(stage = LaunchSequence.Stage.ready).advance(0.12, true, false)
        assertEquals(0.0, sequence.revealProgress)
        sequence = sequence.advance(0.55, true, false)
        assertTrue(sequence.isComplete)
    }

    @Test fun `background time does not advance the introduction or reveal`() {
        var sequence = LaunchSequence(stage = LaunchSequence.Stage.ready).advance(100.0, false, false)
        assertEquals(1.0, sequence.contentOpacity)
        assertEquals(0.0, sequence.revealProgress)
        sequence = sequence.advance(0.6, true, false)
        val before = sequence
        sequence = sequence.advance(100.0, false, false)
        assertEquals(before, sequence)
        sequence = sequence.advance(0.42, true, false)
        assertTrue(sequence.isComplete)
    }

    @Test fun `reduced motion uses a short fade`() {
        assertTrue(LaunchSequence(stage = LaunchSequence.Stage.ready).advance(0.62, true, true).isComplete)
    }

    @Test fun `failure does not complete or pretend to be loading`() {
        val sequence = LaunchSequence(stage = LaunchSequence.Stage.failed).advance(20.0, true, false)
        assertFalse(sequence.isComplete)
        assertFalse(sequence.showsLoadingMessage)
        assertEquals(1.0, sequence.contentOpacity)
    }

    @Test fun `the bar glides toward each stage instead of jumping`() {
        var sequence = LaunchSequence(stage = LaunchSequence.Stage.buildingScene).advance(0.016, true, false)
        assertTrue(sequence.displayedProgress > 0)
        assertTrue(sequence.displayedProgress < sequence.progress / 2)
        sequence = sequence.advance(5.0, true, false)
        assertTrue(abs(sequence.displayedProgress - sequence.progress) < 0.001)
    }

    @Test fun `the bar never goes back and never passes the stage`() {
        var sequence = LaunchSequence()
        var last = 0.0
        for (stage in listOf(
            LaunchSequence.Stage.buildingScene,
            LaunchSequence.Stage.loadingTextures,
            LaunchSequence.Stage.waitingForFrame,
            LaunchSequence.Stage.failed,
            LaunchSequence.Stage.ready,
        )) {
            sequence = sequence.copy(stage = stage)
            repeat(30) {
                sequence = sequence.advance(0.016, true, false)
                assertTrue(sequence.displayedProgress >= last)
                assertTrue(sequence.displayedProgress <= max(sequence.progress, last))
                last = sequence.displayedProgress
            }
        }
    }

    @Test fun `the bar fills quickly once the farm is ready`() {
        val sequence =
            LaunchSequence(stage = LaunchSequence.Stage.waitingForFrame)
                .advance(5.0, true, false)
                .copy(stage = LaunchSequence.Stage.ready)
                .advance(0.3, true, false)
        assertTrue(sequence.displayedProgress > 0.99)
    }

    @Test fun `with reduce motion the bar simply shows the stage`() {
        val sequence = LaunchSequence(stage = LaunchSequence.Stage.loadingTextures).advance(0.016, true, true)
        assertEquals(sequence.progress, sequence.displayedProgress)
    }
}
