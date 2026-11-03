package com.hunter.stockfarmer.core.persistence

import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.stateWith
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

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

    private val start = Instant.ofEpochSecond(40_000_000)

    private fun minutes(count: Long): Instant = start.plusSeconds(count * 60)

    private fun game(
        money: Double,
        day: Int = 1,
    ) = stateWith(money = money).also { it.day = day }

    /** Two saves, so the next one, twenty minutes on and a day later, moves every backup and then both files. */
    private fun farmAboutToRotateEverything() {
        slots.save(game(1.0), 0, start)
        slots.save(game(2.0), 0, minutes(1))
    }

    private fun saveThatRotatesEverything(on: SaveSlots) = on.save(game(3.0, day = 2), 0, minutes(20))

    @ParameterizedTest
    @MethodSource("writeSteps")
    fun `a crash at any step of a save leaves a game that opens and a working fallback`(
        file: String,
        step: SaveStep,
    ) {
        farmAboutToRotateEverything()
        val crashing = SaveSlots(directory, StepFaults(file, step) { SimulatedCrash() })
        assertThrows(SimulatedCrash::class.java) { saveThatRotatesEverything(crashing) }

        // What a relaunch finds: nothing rolled back, as nothing ran after the crash.
        val relaunched = SaveSlots(directory)
        val reachedNewGame =
            file == "slots.json" || (file == "slot-1.json" && step == SaveStep.AFTER_MOVE)
        assertEquals(if (reachedNewGame) 3.0 else 2.0, relaunched.load(0)?.money)
        assertNotNull(relaunched.slots()[0])
        assertTrue(relaunched.backups(0).isNotEmpty(), "a backup can still be restored")
        assertTrue(relaunched.backups(0).all { it.money in listOf(1.0, 2.0) })

        relaunched.save(game(4.0, day = 2), 0, minutes(21))
        assertEquals(4.0, SaveSlots(directory).load(0)?.money)
    }

    @ParameterizedTest
    @MethodSource("failingSteps")
    fun `a storage failure at any step of a save puts the game back as it was`(
        file: String,
        step: SaveStep,
    ) {
        farmAboutToRotateEverything()
        val original = Files.readAllBytes(directory.resolve("slot-1.json"))
        val failing = SaveSlots(directory, StepFaults(file, step) { InjectedFailure() })
        assertThrows(InjectedFailure::class.java) { saveThatRotatesEverything(failing) }

        assertArrayEquals(original, Files.readAllBytes(directory.resolve("slot-1.json")))
        assertEquals(2.0, failing.load(0)?.money)
        assertEquals(2.0, slots.slots()[0]?.money)
        // The same instance recovers once the storage does.
        failing.save(game(5.0), 0, minutes(21))
        assertEquals(5.0, slots.load(0)?.money)
    }

    @Test
    fun `when the rollback fails too the previous game is still in the latest backup and in recovery`() {
        farmAboutToRotateEverything()
        val original = Files.readAllBytes(directory.resolve("slot-1.json"))
        val failing = SaveSlots(directory, FailingAfterGameWrite())
        assertThrows(InjectedFailure::class.java) { failing.save(game(3.0), 0, minutes(2)) }
        assertEquals(2.0, slots.backups(0).first().money)
        val recovered = Files.list(directory.resolve("Recovery")).use { it.toList() }
        assertTrue(recovered.any { Files.readAllBytes(it).contentEquals(original) })
    }

    @Test
    fun `temporary files left by a crash do not disturb the farm`() {
        farmAboutToRotateEverything()
        Files.writeString(directory.resolve(".save-123.tmp"), """{"schemaVersion":2,"sta""")
        assertEquals(2.0, slots.load(0)?.money)
        assertEquals(2.0, slots.slots()[0]?.money)
        slots.save(game(3.0), 0, minutes(2))
        assertEquals(3.0, slots.load(0)?.money)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "damaged",
            "",
            """{"schemaVersion":999,"state":{}}""",
            """{"schemaVersion":2,"state":{"money":-1}}""",
        ],
    )
    fun `a save damaged between saves is detected and kept by the instance that wrote it`(damage: String) {
        val saving = SaveSlots(directory)
        saving.save(game(1.0), 0, start)
        saving.save(game(2.0), 0, minutes(1))
        val file = directory.resolve("slot-1.json")
        val latest = Files.readAllBytes(directory.resolve("slot-1.previous.json"))
        Files.writeString(file, damage)

        assertThrows(Exception::class.java) { saving.save(game(3.0), 0, minutes(2)) }
        assertEquals(damage, Files.readString(file))
        assertArrayEquals(latest, Files.readAllBytes(directory.resolve("slot-1.previous.json")))
    }

    @Test
    fun `damage that keeps the length and the modified time is still detected`() {
        val saving = SaveSlots(directory)
        saving.save(game(1.0), 0, start)
        saving.save(game(2.0), 0, minutes(1))
        val file = directory.resolve("slot-1.json")
        val modified = Files.getLastModifiedTime(file)
        val damaged = Files.readAllBytes(file).also { it[0] = '['.code.toByte() }
        Files.write(file, damaged)
        Files.setLastModifiedTime(file, modified)

        assertThrows(Exception::class.java) { saving.save(game(3.0), 0, minutes(2)) }
        assertArrayEquals(damaged, Files.readAllBytes(file))
    }

    @Test
    fun `a save changed from outside but still whole is accepted after a read back`() {
        val saving = SaveSlots(directory)
        saving.save(game(1.0), 0, start)
        saving.save(game(2.0), 0, minutes(1))
        val file = directory.resolve("slot-1.json")
        slots.save(game(9.0), 0, minutes(2))
        saving.save(game(3.0), 0, minutes(3))
        assertEquals(9.0, slots.backups(0).first().money)
        assertEquals(3.0, slots.load(0)?.money)
        assertTrue(Files.size(file) > 0)
    }

    @Test
    fun `a game that could not be reopened never reaches the disk or moves a backup`() {
        slots.save(game(1.0), 0, start)
        slots.save(game(2.0), 0, minutes(1))
        val before = Files.readAllBytes(directory.resolve("slot-1.json"))
        val latest = Files.readAllBytes(directory.resolve("slot-1.previous.json"))

        // Twenty minutes on, so without the check first the older backup would already have moved.
        assertThrows(InvalidSaveException::class.java) { slots.save(game(-1.0), 0, minutes(20)) }

        assertArrayEquals(before, Files.readAllBytes(directory.resolve("slot-1.json")))
        assertArrayEquals(latest, Files.readAllBytes(directory.resolve("slot-1.previous.json")))
        assertFalse(Files.exists(directory.resolve("slot-1.older.json")))
    }

    @Test
    fun `an unchanged save is read once and the empty slots not at all`() {
        val counting = CountingFiles()
        val saving = SaveSlots(directory, counting)
        saving.save(game(1.0), 0, start)
        saving.save(game(2.0), 0, minutes(1))
        counting.reads.clear()

        saving.save(game(3.0), 0, minutes(2))

        assertEquals(1, counting.reads["slot-1.json"])
        assertEquals(setOf("slot-1.json", "slots.json"), counting.reads.keys)
    }

    @Test
    fun `a save about to become a long-kept backup is read back whatever its hash says`() {
        val counting = CountingFiles()
        val saving = SaveSlots(directory, counting)
        saving.save(game(1.0), 0, start)
        saving.save(game(2.0), 0, minutes(1))

        counting.reads.clear()
        saving.save(game(3.0), 0, minutes(20))
        assertEquals(2, counting.reads["slot-1.json"], "the older backup was due")

        counting.reads.clear()
        saving.save(game(4.0, day = 2), 0, minutes(21))
        assertEquals(2, counting.reads["slot-1.json"], "the daily backup was due")

        // The hash matches the first read, so only the read back can find this.
        val latest = Files.readAllBytes(directory.resolve("slot-1.previous.json"))
        counting.reads.clear()
        counting.damageSecondRead = true
        assertThrows(Exception::class.java) { saving.save(game(5.0), 0, minutes(40)) }
        assertArrayEquals(latest, Files.readAllBytes(directory.resolve("slot-1.previous.json")))
    }

    @Test
    fun `a write puts the data in place and leaves nothing behind, syncing only then moving`() {
        val steps = mutableListOf<String>()
        val files =
            object : LocalSaveFileSystem() {
                override fun writeDurably(
                    data: ByteArray,
                    temporary: Path,
                ) {
                    steps += "data"
                    super.writeDurably(data, temporary)
                }

                override fun moveIntoPlace(
                    temporary: Path,
                    destination: Path,
                ) {
                    steps += "move"
                    super.moveIntoPlace(temporary, destination)
                }

                override fun syncDirectory(directory: Path) {
                    steps += "directory"
                    super.syncDirectory(directory)
                }
            }
        val file = directory.resolve("file.json")
        files.write("first".toByteArray(), file)
        files.write("second".toByteArray(), file)
        assertEquals("second", Files.readString(file))
        assertEquals(listOf("data", "move", "directory", "data", "move", "directory"), steps)
        assertEquals(
            listOf("file.json"),
            Files.list(directory).use { paths ->
                paths.map { it.fileName.toString() }.toList()
            },
        )
        assertTrue(files.exists(file))
        assertFalse(files.exists(directory.resolve("missing.json")))
    }

    companion object {
        private val filesOfASave =
            listOf("slot-1.older.json", "slot-1.previous.json", "slot-1.daily.json", "slot-1.json", "slots.json")

        @JvmStatic
        fun writeSteps(): List<Arguments> =
            filesOfASave.flatMap { file -> SaveStep.entries.map { Arguments.of(file, it) } }

        /** Only a crash can stop a write after its move: a failed directory sync is not reported as a failed save. */
        @JvmStatic
        fun failingSteps(): List<Arguments> =
            filesOfASave.flatMap { file ->
                SaveStep.entries.filter { it != SaveStep.AFTER_MOVE }.map { Arguments.of(file, it) }
            }
    }
}

enum class SaveStep { TEMPORARY_WRITE, PARTIAL_TEMPORARY, MOVE, AFTER_MOVE }

/** Not an [Exception]: like a real crash, it runs through every rollback without stopping to tidy anything. */
private class SimulatedCrash : Error("crash")

/** Fails one step of one file's write, once. */
private class StepFaults(
    private val file: String,
    private val step: SaveStep,
    private val fault: () -> Throwable,
) : LocalSaveFileSystem() {
    private var armed = false
    private var spent = false

    private fun failAt(reached: SaveStep) {
        if (armed && !spent && reached == step) {
            spent = true
            throw fault()
        }
    }

    override fun write(
        data: ByteArray,
        to: Path,
    ) {
        armed = to.fileName.toString() == file
        super.write(data, to)
    }

    override fun writeDurably(
        data: ByteArray,
        temporary: Path,
    ) {
        failAt(SaveStep.TEMPORARY_WRITE)
        if (armed && !spent && step == SaveStep.PARTIAL_TEMPORARY) {
            Files.write(temporary, data.copyOf(data.size / 2))
            failAt(SaveStep.PARTIAL_TEMPORARY)
        }
        super.writeDurably(data, temporary)
    }

    override fun moveIntoPlace(
        temporary: Path,
        destination: Path,
    ) {
        failAt(SaveStep.MOVE)
        super.moveIntoPlace(temporary, destination)
    }

    override fun syncDirectory(directory: Path) {
        failAt(SaveStep.AFTER_MOVE)
        super.syncDirectory(directory)
    }
}

/** The game's file can be written once, then neither the manifest nor the game's file can be: no save, no rollback. */
private class FailingAfterGameWrite : LocalSaveFileSystem() {
    private var gameWritten = false

    override fun write(
        data: ByteArray,
        to: Path,
    ) {
        val name = to.fileName.toString()
        if (name == "slots.json" || (name == "slot-1.json" && gameWritten)) throw InjectedFailure()
        super.write(data, to)
        if (name == "slot-1.json") gameWritten = true
    }
}

private class CountingFiles : LocalSaveFileSystem() {
    val reads = mutableMapOf<String, Int>()
    var damageSecondRead = false

    override fun read(url: Path): ByteArray? {
        val name = url.fileName.toString()
        reads[name] = (reads[name] ?: 0) + 1
        if (damageSecondRead && name == "slot-1.json" && reads[name] == 2) return "damaged".toByteArray()
        return super.read(url)
    }
}
