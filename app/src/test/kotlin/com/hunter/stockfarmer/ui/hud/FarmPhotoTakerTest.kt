package com.hunter.stockfarmer.ui.hud

import android.content.ContextWrapper
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.game.GameSession
import com.hunter.stockfarmer.ui.PhotoSaver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class FarmPhotoTakerTest {
    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setUp() {
        FarmPhotoTaker.isTakingPhoto.set(false)
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
        takeFarmPhoto(context, session) { message ->
            outcome = message
            latch.countDown()
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS))
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
        takeFarmPhoto(context, session) { message ->
            outcome = message
            latch.countDown()
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS))
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
        takeFarmPhoto(context, session) { message ->
            outcome = message
            latch.countDown()
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS))
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
        takeFarmPhoto(context, session) { message ->
            outcome = message
            latch.countDown()
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS))
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
