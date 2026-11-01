package com.hunter.stockfarmer.ui.timelapse

import com.hunter.stockfarmer.core.persistence.TimelapseFrame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.IOException
import java.time.Instant
import java.util.UUID

class TimelapseImagesTest {
    private val frames = (1..5).map { TimelapseFrame(UUID(0, it.toLong()), it, 540, Instant.EPOCH, 1) }

    @Test
    fun `moving forward reuses neighbors and evicts distant decoded images`() =
        runBlocking {
            val loads = mutableListOf<Int>()
            val images = TimelapseImages(frames) { frame -> frame.day.also { loads.add(it) } }
            assertEquals(1, images.current(0))
            images.prefetch(0)
            assertEquals(listOf(1, 2), loads)
            assertEquals(2, images.current(1))
            images.prefetch(1)
            assertEquals(listOf(1, 2, 3), loads)
            images.current(2)
            images.prefetch(2)
            assertEquals(listOf(1, 2, 3, 4), loads)
            images.current(0)
            assertEquals(listOf(1, 2, 3, 4, 1), loads)
        }

    @Test
    fun `a missing neighbor does not hide the current image but fails when sought`() =
        runBlocking<Unit> {
            val images =
                TimelapseImages(frames) { frame ->
                    if (frame.day == 2) throw IOException("Missing")
                    frame.day
                }
            assertEquals(1, images.current(0))
            images.prefetch(0)
            assertEquals(1, images.current(0))
            assertThrows(IOException::class.java) { runBlocking { images.current(1) } }
        }

    @Test
    fun `prefetch preserves coroutine cancellation`() {
        val images = TimelapseImages(frames) { throw CancellationException("Closed") }
        assertThrows(CancellationException::class.java) { runBlocking { images.prefetch(0) } }
    }
}
