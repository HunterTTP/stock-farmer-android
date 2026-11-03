package com.hunter.stockfarmer.ui.hud

import android.content.ContextWrapper
import com.hunter.stockfarmer.core.feedback.HapticCue
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.game.GameSession
import com.hunter.stockfarmer.game.HapticRequest
import com.hunter.stockfarmer.game.collectingHaptics
import com.hunter.stockfarmer.ui.PhotoSaver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class FarmPhotoTakerTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `a request lost with its farm view does not leave photos stuck`() {
        val session = GameSession(GameEngine())
        session.photos.handler = { _, _ -> } // The view queues the work and is destroyed before running it.
        val context = FakePhotoContext(externalDir = tempDir.resolve("Lost").toFile())
        val outcomes = LinkedBlockingQueue<String>()

        takeFarmPhoto(context, session) { outcomes.add(it) }
        assertTrue(session.photos.isTakingPhoto.get())
        takeFarmPhoto(context, session) { outcomes.add("second tap") }
        assertNull(outcomes.poll())

        val png = byteArrayOf(1, 2)
        session.photos.handler = { _, done -> done(png) } // The next farm view.

        assertEquals("Farm photo saved", outcomes.poll(5, TimeUnit.SECONDS))
        assertFalse(session.photos.isTakingPhoto.get())
    }

    @Test
    fun `losing the farm view with none to follow reports failure and frees photos`() {
        val session = GameSession(GameEngine())
        session.photos.handler = { _, _ -> }
        val outcomes = LinkedBlockingQueue<String>()

        takeFarmPhoto(FakePhotoContext(externalDir = tempDir.toFile()), session) { outcomes.add(it) }
        session.photos.handler = null

        assertEquals("The farm photo could not be created. Please try again.", outcomes.poll(5, TimeUnit.SECONDS))
        assertFalse(session.photos.isTakingPhoto.get())
    }

    @Test
    fun `each session keeps its own photo flag`() {
        val stuck = GameSession(GameEngine())
        stuck.photos.isTakingPhoto.set(true)
        assertFalse(GameSession(GameEngine()).photos.isTakingPhoto.get())
    }

    @Test
    fun `photo request succeeds at 4096px and saves photo`() {
        val session = GameSession(GameEngine())
        val fakePng = byteArrayOf(1, 2, 3, 4)
        session.photos.handler = { request, done ->
            if (request.pixels == 4096) done(fakePng) else done(null)
        }

        val picturesDir = tempDir.resolve("Pictures").toFile()
        val context = FakePhotoContext(externalDir = picturesDir)

        val latch = CountDownLatch(1)
        var outcome = ""
        val haptics =
            collectingHaptics(session) {
                takeFarmPhoto(context, session) { message ->
                    outcome = message
                    latch.countDown()
                }
                assertTrue(latch.await(5, TimeUnit.SECONDS))
            }
        assertEquals(listOf(HapticRequest.Cue(HapticCue.Success)), haptics)
        assertEquals("Farm photo saved", outcome)
        assertTrue(File(picturesDir, "Stock Farmer.png").exists())
    }

    @Test
    fun `photo request falls back to 2048px when 4096px returns null`() {
        val session = GameSession(GameEngine())
        val fakePng = byteArrayOf(5, 6, 7, 8)
        val requestedSizes = mutableListOf<Int>()
        session.photos.handler = { request, done ->
            requestedSizes += request.pixels
            if (request.pixels == 2048) done(fakePng) else done(null)
        }

        val picturesDir = tempDir.resolve("PicturesFallback").toFile()
        val context = FakePhotoContext(externalDir = picturesDir)

        val latch = CountDownLatch(1)
        var outcome = ""
        val haptics =
            collectingHaptics(session) {
                takeFarmPhoto(context, session) { message ->
                    outcome = message
                    latch.countDown()
                }
                assertTrue(latch.await(5, TimeUnit.SECONDS))
            }
        assertEquals(listOf(HapticRequest.Cue(HapticCue.Success)), haptics)
        assertEquals("Farm photo saved", outcome)
        assertEquals(listOf(4096, 2048), requestedSizes)
        assertTrue(File(picturesDir, "Stock Farmer.png").exists())
    }

    @Test
    fun `photo request alerts failure when both sizes return null`() {
        val session = GameSession(GameEngine())
        session.photos.handler = { _, done -> done(null) }

        val context = FakePhotoContext(externalDir = tempDir.toFile())
        val latch = CountDownLatch(1)
        var outcome = ""
        val haptics =
            collectingHaptics(session) {
                takeFarmPhoto(context, session) { message ->
                    outcome = message
                    latch.countDown()
                }
                assertTrue(latch.await(5, TimeUnit.SECONDS))
            }
        assertTrue(haptics.isEmpty())
        assertEquals("The farm photo could not be created. Please try again.", outcome)
    }

    @Test
    fun `save failure alerts that photo could not be saved`() {
        val session = GameSession(GameEngine())
        session.photos.handler = { _, done -> done(byteArrayOf(1, 2, 3)) }

        val context =
            object : ContextWrapper(null) {
                override fun getExternalFilesDir(type: String?): File = throw RuntimeException("Storage unavailable")
            }

        val latch = CountDownLatch(1)
        var outcome = ""
        val haptics =
            collectingHaptics(session) {
                takeFarmPhoto(context, session) { message ->
                    outcome = message
                    latch.countDown()
                }
                assertTrue(latch.await(5, TimeUnit.SECONDS))
            }
        assertTrue(haptics.isEmpty())
        assertEquals("The farm photo could not be saved", outcome)
    }

    @Test
    fun `photo saver saves to app folder and handles missing external storage`() {
        val filesDir = tempDir.resolve("InternalFiles").toFile()
        val context = FakePhotoContext(externalDir = null, internalDir = filesDir)

        val result = PhotoSaver.save(context, byteArrayOf(9, 10))
        assertTrue(result)
        assertTrue(File(filesDir, "Pictures/Stock Farmer.png").exists())
    }

    private class FakePhotoContext(
        private val externalDir: File?,
        private val internalDir: File = File("."),
    ) : ContextWrapper(null) {
        override fun getExternalFilesDir(type: String?): File? = externalDir

        override fun getFilesDir(): File = internalDir
    }
}
