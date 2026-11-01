package com.hunter.stockfarmer.core.persistence

import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.stateWith
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

private class InjectedFailure : Exception("injected")

/** A file system that refuses one write by file name, or every removal. */
private class FailingSaveFiles(
    private val deniedWrite: String? = null,
    private val deniesRemoval: Boolean = false,
) : SaveFileSystem {
    private val local = LocalSaveFileSystem()

    override fun read(url: Path) = local.read(url)

    override fun createDirectory(url: Path) = local.createDirectory(url)

    override fun write(
        data: ByteArray,
        to: Path,
    ) {
        if (to.fileName.toString() == deniedWrite) throw InjectedFailure()
        local.write(data, to)
    }

    override fun remove(url: Path) {
        if (deniesRemoval) throw InjectedFailure()
        local.remove(url)
    }
}

class SaveSafetyTest {
    @TempDir
    lateinit var directory: Path

    private val slots get() = SaveSlots(directory)

    @Test
    fun `unsupported save cannot be loaded or overwritten`() {
        val file = directory.resolve("slot-1.json")
        val original = """{"schemaVersion":999,"state":{"money":12345}}""".toByteArray()
        Files.write(file, original)
        assertThrows(Exception::class.java) { slots.load(0) }
        assertThrows(Exception::class.java) { slots.save(GameState(), 0) }
        assertArrayEquals(original, Files.readAllBytes(file))
    }

    @Test
    fun `damaged save is not an empty slot`() {
        val file = directory.resolve("slot-1.json")
        val original = "damaged".toByteArray()
        Files.write(file, original)
        assertThrows(Exception::class.java) { slots.load(0) }
        assertThrows(Exception::class.java) { slots.save(GameState(), 0) }
        assertArrayEquals(original, Files.readAllBytes(file))
    }

    @Test
    fun `missing and broken manifest recover existing slot files`() {
        slots.save(stateWith(money = 1234.0), 2)
        val manifest = directory.resolve("slots.json")
        Files.writeString(manifest, "broken")
        assertEquals(1234.0, slots.slots()[2]?.money)
        Files.delete(manifest)
        assertEquals(1234.0, slots.slots()[2]?.money)
        assertEquals(1234.0, slots.load(2)?.money)
    }

    @Test
    fun `invalid active slot falls back within range`() {
        Files.writeString(directory.resolve("slots.json"), """{"active":999,"records":{}}""")
        assertTrue(slots.activeSlot in 0 until SaveSlots.COUNT)
    }

    @Test
    fun `manifest failure rolls back the game and keeps its backup`() {
        slots.save(stateWith(money = 1234.0), 0)
        val file = directory.resolve("slot-1.json")
        val original = Files.readAllBytes(file)
        val failing = SaveSlots(directory, FailingSaveFiles(deniedWrite = "slots.json"))
        assertThrows(InjectedFailure::class.java) { failing.save(stateWith(money = 2.0), 0) }
        assertArrayEquals(original, Files.readAllBytes(file))
        assertArrayEquals(original, Files.readAllBytes(directory.resolve("slot-1.previous.json")))
        assertEquals(1234.0, slots.slots()[0]?.money)
    }

    @Test
    fun `failed deletion keeps the game and manifest`() {
        slots.save(stateWith(money = 99.0), 0)
        val failing = SaveSlots(directory, FailingSaveFiles(deniesRemoval = true))
        assertThrows(InjectedFailure::class.java) { failing.delete(0) }
        assertEquals(99.0, slots.load(0)?.money)
        assertEquals(99.0, slots.slots()[0]?.money)
    }

    @Test
    fun `failed game write does not publish a new summary`() {
        slots.save(stateWith(money = 99.0), 0)
        val failing = SaveSlots(directory, FailingSaveFiles(deniedWrite = "slot-1.json"))
        assertThrows(InjectedFailure::class.java) { failing.save(stateWith(money = 0.0), 0) }
        assertEquals(99.0, slots.load(0)?.money)
        assertEquals(99.0, slots.slots()[0]?.money)
    }

    @Test fun `a damaged field is reported so the original save can be preserved`() {
        assertThrows(Exception::class.java) {
            GameStateJson.decode("""{"money":42,"mode":"nonsense","selectedWaterStyle":7}""")
        }
    }
}
