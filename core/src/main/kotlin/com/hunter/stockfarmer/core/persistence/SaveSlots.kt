package com.hunter.stockfarmer.core.persistence

import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.model.cleanedName
import com.hunter.stockfarmer.core.util.InstantSerializer
import com.hunter.stockfarmer.core.util.secondsSince
import kotlinx.serialization.Serializable
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** What the load menu shows about one saved game. */
data class SaveSlotInfo(
    val slot: Int,
    val name: String,
    val lastPlayed: Instant,
    val level: Int,
    val money: Double,
    /** Real time spent playing it. */
    val playTime: Double = 0.0,
) {
    val id: Int get() = slot
}

/**
 * Ten places to keep a game each, with a name and a last-played time, and one of them being the game in play. Everything
 * lives in a folder: a manifest that also summarises each game, and one save file per slot. What it remembers between
 * calls only spares work: every memo is checked against the bytes on the disk or forgotten when the slot changes.
 */
class SaveSlots(
    private val directory: Path,
    private val files: SaveFileSystem = LocalSaveFileSystem(),
) {
    @Serializable
    private data class Record(
        val name: String,
        @Serializable(with = InstantSerializer::class) val lastPlayed: Instant,
        val level: Int,
        val money: Double,
        /** When the older backup last advanced; null until a backup exists. */
        @Serializable(with = InstantSerializer::class) val lastBackupShiftAt: Instant? = null,
        /** The farm's day when the daily backup last advanced. */
        val dailyBackupDay: Int? = null,
        /** Real time spent playing. */
        val playTime: Double? = null,
    )

    private enum class BackupRotation { SCHEDULED, ALWAYS, NEVER }

    @Serializable
    private data class Manifest(
        val active: Int = 0,
        val records: Map<Int, Record> = emptyMap(),
    )

    private class KnownManifest(
        val bytes: ByteArray,
        val manifest: Manifest,
    )

    private val stores = List(COUNT) { FileSaveStore(fileURL(it), files) }

    /** Keyed by the manifest's bytes, so one written by anyone else simply misses it and is decoded. */
    @Volatile private var knownManifest: KnownManifest? = null

    /** What [saveIfChanged] last put in each slot, by [GameStateJson.contentHash]. */
    private val writtenContent = ConcurrentHashMap<Int, ByteArray>()

    // MARK: Reading

    /** The slot being played. */
    val activeSlot: Int get() = manifest().active

    /** Each slot's summary, or null where nothing is saved. */
    fun slots(): List<SaveSlotInfo?> {
        val manifest = manifest()
        return (0 until COUNT).map { slot ->
            manifest.records[slot]?.let {
                SaveSlotInfo(slot, it.name, it.lastPlayed, it.level, it.money, it.playTime ?: 0.0)
            }
        }
    }

    /** Null means an empty slot. Unreadable and unsupported saves throw and remain untouched. */
    fun load(slot: Int): GameState? = if (isValid(slot)) store(slot).load() else null

    // MARK: Writing

    fun setActive(slot: Int) {
        if (!isValid(slot)) return
        write(manifest().copy(active = slot))
    }

    /** Saves a game into a slot, keeping its name or giving it the default one. */
    fun save(
        state: GameState,
        slot: Int,
        now: Instant = Instant.now(),
    ) {
        if (!isValid(slot)) return
        writtenContent.remove(slot)
        // Before any backup moves: a game that could not be reopened must never push out one that can.
        state.validate()
        createDirectory()
        val manifest = manifest()
        val existing =
            manifest.records[slot]
                ?: Record(defaultName(slot), now, state.level, state.money, dailyBackupDay = state.day)
        val record =
            existing.copy(
                lastPlayed = now,
                level = state.level,
                money = state.money,
                playTime = state.playTime,
            )
        val store = store(slot)
        val original = files.read(fileURL(slot))
        if (original != null) {
            // What becomes a long-kept backup is read back whatever the hash says, so no fault can outlast every backup.
            if (isOlderBackupDue(record, now) || record.dailyBackupDay != state.day) {
                store.load()
            } else {
                store.requireOpens(original)
            }
        }
        changingSlot(slot, manifest.withRecord(slot, record), now, BackupRotation.SCHEDULED, original, state.day) {
            store.replace(state)
        }
    }

    /**
     * Saves unless the game holds nothing new since the last time this did, and says whether it wrote. As on iOS, time
     * played alone is not worth a write: it goes along with the next one, and pause and stop flush with [save].
     */
    fun saveIfChanged(
        state: GameState,
        slot: Int,
        now: Instant = Instant.now(),
    ): Boolean {
        if (!isValid(slot)) return false
        // Forgotten first, so that any failure from here on makes the next autosave write and report.
        val written = writtenContent.remove(slot)
        val content = GameStateJson.contentHash(state)
        if (written?.contentEquals(content) == true) {
            writtenContent[slot] = written
            return false
        }
        save(state, slot, now)
        writtenContent[slot] = content
        return true
    }

    /** Starts a new game in a slot under a name, replacing anything saved there. */
    fun create(
        slot: Int,
        name: String,
        state: GameState = GameState(),
        now: Instant = Instant.now(),
    ) {
        if (!isValid(slot)) return
        writtenContent.remove(slot)
        state.validate()
        createDirectory()
        val record = Record(cleaned(name, slot), now, state.level, state.money, playTime = state.playTime)
        val original = files.read(fileURL(slot))
        original?.let { preserve(it, "slot-${slot + 1}") }
        changingSlot(
            slot,
            manifest().withRecord(slot, record),
            now,
            BackupRotation.NEVER,
            original,
        ) { store(slot).replace(state) }
    }

    fun rename(
        slot: Int,
        name: String,
    ) {
        val manifest = manifest()
        val record = manifest.records[slot]?.takeIf { isValid(slot) } ?: return
        write(manifest.withRecord(slot, record.copy(name = cleaned(name, slot))))
    }

    fun delete(slot: Int) {
        if (!isValid(slot)) return
        writtenContent.remove(slot)
        val manifest = manifest()
        val original = files.read(fileURL(slot))
        original?.let { preserve(it, "slot-${slot + 1}") }
        changingSlot(
            slot,
            manifest.copy(records = manifest.records - slot),
            Instant.now(),
            BackupRotation.NEVER,
            original,
        ) { files.remove(fileURL(slot)) }
    }

    // MARK: Backups

    /** The backups of a slot that can still be opened, latest first. */
    fun backups(slot: Int): List<SaveBackup> {
        if (!isValid(slot)) return emptyList()
        return SaveBackup.Kind.entries.mapNotNull { kind ->
            val state = runCatching { loadBackup(kind, slot) }.getOrNull() ?: return@mapNotNull null
            SaveBackup(slot, kind, state.lastSavedAt, state.level, state.money)
        }
    }

    /**
     * Makes a backup the slot's game and returns it. The game it replaces is kept as the latest backup, so a restore can
     * be undone.
     */
    fun restore(
        backup: SaveBackup,
        now: Instant = Instant.now(),
    ): GameState {
        val slot = backup.slot
        val state = (if (isValid(slot)) loadBackup(backup.kind, slot) else null) ?: throw SaveRestoreException()
        writtenContent.remove(slot)
        val manifest = manifest()
        val existing = manifest.records[slot] ?: Record(defaultName(slot), now, state.level, state.money)
        val record =
            existing.copy(
                lastPlayed = now,
                level = state.level,
                money = state.money,
                playTime = state.playTime,
                dailyBackupDay = state.day,
            )
        val original = files.read(fileURL(slot))
        original?.let { preserve(it, "slot-${slot + 1}") }
        // A game that cannot be opened must not push a good backup out of the way.
        val keepsCurrent = original != null && runCatching { store(slot).load() }.getOrNull() != null
        val rotation = if (keepsCurrent) BackupRotation.ALWAYS else BackupRotation.NEVER
        changingSlot(slot, manifest.withRecord(slot, record), now, rotation, original) { store(slot).replace(state) }
        return state
    }

    private fun loadBackup(
        kind: SaveBackup.Kind,
        slot: Int,
    ): GameState? = FileSaveStore(backupURL(kind, slot), files).load()

    // MARK: Files

    private fun Manifest.withRecord(
        slot: Int,
        record: Record,
    ) = copy(records = records + (slot to record))

    private fun fileURL(slot: Int): Path = directory.resolve("slot-${slot + 1}.json")

    private fun backupURL(
        kind: SaveBackup.Kind,
        slot: Int,
    ): Path = directory.resolve("slot-${slot + 1}.${kind.rawValue}.json")

    private val manifestURL: Path get() = directory.resolve("slots.json")

    private fun store(slot: Int) = stores[slot]

    private fun createDirectory() = files.createDirectory(directory)

    private fun decodedManifest(data: ByteArray): Manifest {
        knownManifest?.takeIf { it.bytes.contentEquals(data) }?.let { return it.manifest }
        return json
            .decodeFromString<Manifest>(data.toString(Charsets.UTF_8))
            .also { knownManifest = KnownManifest(data, it) }
    }

    private fun manifest(): Manifest {
        val saved = runCatching { files.read(manifestURL)?.let(::decodedManifest) }
        var result = saved.getOrNull() ?: Manifest()
        if (!isValid(result.active)) result = result.copy(active = 0)
        // The manifest is an index, not the only way to discover a farm after an interrupted write.
        val records = result.records.toMutableMap()
        for (slot in 0 until COUNT) {
            if (slot in records || !files.exists(fileURL(slot))) continue
            val state = runCatching { load(slot) }.getOrNull()
            if (state != null) {
                records[slot] =
                    Record(
                        defaultName(slot),
                        state.lastSavedAt ?: Instant.EPOCH,
                        state.level,
                        state.money,
                        playTime = state.playTime,
                    )
            } else if (runCatching { files.read(fileURL(slot)) }.getOrNull() != null) {
                records[slot] = Record(defaultName(slot), Instant.EPOCH, 1, 0.0)
            }
        }
        return result.copy(records = records)
    }

    private fun write(manifest: Manifest) {
        createDirectory()
        val original = files.read(manifestURL)
        if (original != null && runCatching { decodedManifest(original) }.isFailure) preserve(original, "manifest")
        val data = json.encodeToString(manifest).toByteArray(Charsets.UTF_8)
        files.write(data, manifestURL)
        knownManifest = KnownManifest(data, manifest)
    }

    private fun preserve(
        data: ByteArray,
        name: String,
    ) {
        val recovery = directory.resolve("Recovery")
        files.createDirectory(recovery)
        files.write(data, recovery.resolve("$name-${UUID.randomUUID().toString().uppercase()}.json"))
    }

    /**
     * Retains the original as the latest backup before changing either file, moving the one it replaces to the older
     * backup when that one is due, and also as the daily backup when the farm's day has rolled over. A failed manifest
     * write rolls the slot back.
     *
     * The latest backup is a whole new file, never the old save moved aside: between a move and the next write the slot
     * would have no file, and a crash there reads as an empty slot, which startup answers with a new farm.
     */
    private fun changingSlot(
        slot: Int,
        manifest: Manifest,
        now: Instant,
        rotation: BackupRotation,
        original: ByteArray?,
        newDay: Int? = null,
        change: () -> Unit,
    ) {
        var current = manifest
        val url = fileURL(slot)
        if (original != null && rotation != BackupRotation.NEVER) {
            val latest = backupURL(SaveBackup.Kind.latest, slot)
            if (rotation == BackupRotation.ALWAYS || isOlderBackupDue(current.records[slot], now)) {
                files.read(latest)?.let { files.write(it, backupURL(SaveBackup.Kind.older, slot)) }
                current = current.updating(slot) { it.copy(lastBackupShiftAt = now) }
            }
            files.write(original, latest)
        }
        // The last save of the day before is kept when the farm's day first changes, whatever else is rotated.
        if (original != null && newDay != null && rotation != BackupRotation.NEVER &&
            current.records[slot]?.dailyBackupDay != newDay
        ) {
            files.write(original, backupURL(SaveBackup.Kind.daily, slot))
            current = current.updating(slot) { it.copy(dailyBackupDay = newDay) }
        }
        try {
            change()
            write(current)
        } catch (failure: Exception) {
            try {
                if (original != null) files.write(original, url) else files.remove(url)
            } catch (_: Exception) {
                // The previous file remains available even when the storage failure prevents rollback.
                original?.let { runCatching { preserve(it, "slot-${slot + 1}") } }
            }
            throw failure
        }
    }

    private fun isOlderBackupDue(
        record: Record?,
        now: Instant,
    ): Boolean = record?.lastBackupShiftAt?.let { now.secondsSince(it) >= OLDER_BACKUP_INTERVAL } ?: true

    private fun Manifest.updating(
        slot: Int,
        change: (Record) -> Record,
    ): Manifest = records[slot]?.let { copy(records = records + (slot to change(it))) } ?: this

    companion object {
        const val COUNT = 10
        const val MAX_NAME_LENGTH = 24

        /** The older backup trails the latest one by at least this long, so a run of autosaves cannot overwrite both. */
        const val OLDER_BACKUP_INTERVAL = 10.0 * 60

        private val json = GameStateJson.json

        fun defaultName(slot: Int): String = "Farm ${slot + 1}"

        private fun isValid(slot: Int): Boolean = slot in 0 until COUNT

        private fun cleaned(
            name: String,
            slot: Int,
        ): String = name.cleanedName(MAX_NAME_LENGTH, defaultName(slot))
    }
}
