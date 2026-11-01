package com.hunter.stockfarmer.core.persistence

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

class TimelapseStoreTest {
    @TempDir lateinit var directory: Path
    private val now = Instant.ofEpochSecond(1_000_000)
    private val first = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val second = UUID.fromString("00000000-0000-0000-0000-000000000002")

    private fun store(files: SaveFileSystem = LocalSaveFileSystem()): TimelapseStore {
        var id = 100L
        return TimelapseStore(directory, files) { UUID(0, id++) }
    }

    @Test fun `images are separate from saves and history survives reopening`() {
        val store = store()
        val empty = store.archive(first)
        val data = byteArrayOf(1, 2, 3, 4)
        val archive = requireNotNull(store.append(data, first, empty.generation, 3, 540, now))
        assertEquals(1, archive.frames.size)
        assertEquals(4L, archive.totalBytes)
        val frame = archive.frames.first()
        assertArrayEquals(data, store.image(frame, first))
        val reopened = store()
        assertEquals(archive, reopened.archive(first))
        assertTrue(reopened.archive(second).frames.isEmpty())
        val duplicate = reopened.append(data, first, archive.generation, 3, 600, now)
        assertEquals(1, duplicate?.frames?.size)
    }

    @Test fun `clearing invalidates pending writes and does not affect other farms`() {
        val store = store()
        val before = store.archive(first)
        val other = store.archive(second)
        val saved = requireNotNull(store.append(byteArrayOf(1), first, before.generation, 1, 540, now))
        store.append(byteArrayOf(2, 3), second, other.generation, 1, 540, now)
        val cleared = store.clear(first)
        assertTrue(cleared.frames.isEmpty())
        assertEquals(0L, cleared.totalBytes)
        assertNotEquals(before.generation, cleared.generation)
        assertNull(store.image(saved.frames.first(), first))
        assertNull(store.append(byteArrayOf(4), first, before.generation, 2, 540, now))
        assertTrue(
            requireNotNull(store.append(byteArrayOf(4), first, cleared.generation, 1, 540, now)).frames.isEmpty(),
        )
        assertEquals(2L, store.archive(second).totalBytes)
    }

    @Test fun `a failed index write does not leave an uncounted image`() {
        val normal = store()
        val archive = normal.archive(first)
        val failing = store(FailedTimelapseIndex())
        assertThrows(IOException::class.java) {
            failing.append(byteArrayOf(1), first, archive.generation, 1, 540, now)
        }
        assertTrue(normal.archive(first).frames.isEmpty())
        Files.list(directory.resolve(first.toString().uppercase())).use { files ->
            assertTrue(files.noneMatch { it.fileName.toString().endsWith(".png") })
        }
    }

    @Test fun `clearing also removes interrupted writes and can reset a damaged index`() {
        val store = store()
        val archive = store.archive(first)
        val folder = directory.resolve(first.toString().uppercase())
        val orphan = folder.resolve("interrupted.png")
        Files.write(orphan, byteArrayOf(1))
        store.clear(first)
        assertFalse(Files.exists(orphan))
        Files.writeString(folder.resolve("index.json"), "damaged")
        val reset = store.clear(first)
        assertTrue(reset.frames.isEmpty())
        assertNotEquals(archive.generation, reset.generation)
        assertEquals(reset, store.archive(first))
    }

    private class FailedTimelapseIndex : SaveFileSystem {
        private val local = LocalSaveFileSystem()

        override fun read(url: Path): ByteArray? = local.read(url)

        override fun createDirectory(url: Path) = local.createDirectory(url)

        override fun remove(url: Path) = local.remove(url)

        override fun write(
            data: ByteArray,
            to: Path,
        ) {
            if (to.fileName.toString() == "index.json") throw IOException("Injected index failure")
            local.write(data, to)
        }
    }
}
