package com.hunter.stockfarmer.ui.timelapse

import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.persistence.TimelapseArchive
import com.hunter.stockfarmer.core.persistence.TimelapseStore
import com.hunter.stockfarmer.core.rules.DayNightCycle
import com.hunter.stockfarmer.core.rules.TimelapseSchedule
import com.hunter.stockfarmer.core.rules.recordTimelapseCapture
import com.hunter.stockfarmer.core.rules.setTimelapseCrossfade
import com.hunter.stockfarmer.core.rules.setTimelapseEnabled
import com.hunter.stockfarmer.core.rules.setTimelapseMinute
import com.hunter.stockfarmer.game.GameSession
import com.hunter.stockfarmer.game.PhotoRequest
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Instant
import java.util.UUID
import kotlin.coroutines.resume

data class TimelapseRecordingState(
    val farm: UUID? = null,
    val archive: TimelapseArchive? = null,
    val isClearing: Boolean = false,
    val error: String? = null,
)

/** Call tick once a second during active, save-ready play; reset when its lifecycle coroutine stops. */
class TimelapseRecorder(
    private val session: GameSession,
    val store: TimelapseStore,
) {
    private val changes = MutableStateFlow(TimelapseRecordingState())
    val state = changes.asStateFlow()
    private val operations = Mutex()
    private val lock = Any()
    private var schedule = TimelapseSchedule()
    private var generation = 0L
    private var viewing = false
    private var observedState: GameState? = null
    private var observedFarm: UUID? = null
    private var pendingPhoto: CancellableContinuation<ByteArray?>? = null

    /** Invalidates suspended work as well as the schedule, including when returning from the background. */
    fun reset() = synchronized(lock) { resetLocked() }

    fun setViewing(value: Boolean) =
        synchronized(lock) {
            if (viewing != value) {
                viewing = value
                resetLocked()
            }
        }

    fun setEnabled(enabled: Boolean) =
        synchronized(lock) {
            session.act { it.setTimelapseEnabled(enabled) }
            resetLocked()
        }

    fun setMinute(minute: Int) =
        synchronized(lock) {
            session.act { it.setTimelapseMinute(minute) }
            resetLocked()
        }

    fun setCrossfades(enabled: Boolean) = session.act { it.setTimelapseCrossfade(enabled) }

    suspend fun refresh() {
        operations.withLock {
            val source = source() ?: return@withLock
            try {
                loadArchive(source)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                report(source, "Timelapse screenshots could not be read. Please try again.")
            }
        }
    }

    suspend fun tick(
        now: Instant,
        active: Boolean = true,
    ) {
        if (!active) {
            reset()
            return
        }
        if (!operations.tryLock()) return
        var source: Source? = null
        try {
            source = source() ?: return
            synchronized(lock) { if (viewing || changes.value.isClearing) return }
            val archive =
                if (changes.value.farm != source.farm || changes.value.archive == null) {
                    loadArchive(source) ?: return
                } else {
                    changes.value.archive ?: return
                }
            val snapshot =
                synchronized(lock) {
                    if (!isCurrent(source)) return
                    session.read { state ->
                        val minute = DayNightCycle.minuteOfDay(now, state.clockOffset)
                        if (!schedule.observe(state.day, minute, state.timelapse, active = true)) return@read null
                        state.copy()
                    } ?: return
                }
            val data = photo(source, snapshot, now) ?: throw IOException("Farm photo unavailable")
            currentCoroutineContext().ensureActive()
            if (!canSave(source, snapshot)) return
            val saved =
                withContext(Dispatchers.IO) {
                    currentCoroutineContext().ensureActive()
                    if (!canSave(source, snapshot)) return@withContext null
                    store.append(
                        data,
                        source.farm,
                        archive.generation,
                        snapshot.day,
                        DayNightCycle.minuteOfDay(now, snapshot.clockOffset).toInt(),
                        now,
                    )
                } ?: return
            synchronized(lock) {
                if (canSave(source, snapshot)) publish(source, saved)
            }
        } catch (_: CaptureInvalidated) {
            currentCoroutineContext().ensureActive()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            source?.let {
                report(it, "The last timelapse screenshot could not be saved. Check available storage.")
            }
        } finally {
            operations.unlock()
        }
    }

    suspend fun clear() {
        val source =
            synchronized(lock) {
                if (changes.value.isClearing) return
                resetLocked()
                val source = source() ?: return
                changes.value = changes.value.copy(isClearing = true)
                source
            }
        try {
            operations.withLock {
                val archive = withContext(Dispatchers.IO) { store.clear(source.farm) }
                synchronized(lock) {
                    if (isCurrent(source)) publish(source, archive)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            report(source, "Timelapse could not be cleared. Please try again.")
        } finally {
            synchronized(lock) {
                changes.value = changes.value.copy(isClearing = false)
                resetLocked()
            }
        }
    }

    private data class Source(
        val identity: GameState,
        val farm: UUID,
        val generation: Long,
    )

    private fun source(): Source? =
        synchronized(lock) {
            session.read { state ->
                if (observedState !== state || observedFarm != state.timelapse.historyID) {
                    observedState = state
                    observedFarm = state.timelapse.historyID
                    resetLocked()
                    changes.value = TimelapseRecordingState(isClearing = changes.value.isClearing)
                }
                state.timelapse.historyID?.let { Source(state, it, generation) }
            }
        }

    private fun resetLocked() {
        generation++
        schedule = TimelapseSchedule()
        pendingPhoto?.cancel(CaptureInvalidated())
        pendingPhoto = null
    }

    private fun isCurrent(source: Source): Boolean =
        source.generation == generation &&
            session.read { it === source.identity && it.timelapse.historyID == source.farm }

    private fun canSave(
        source: Source,
        snapshot: GameState,
    ): Boolean =
        synchronized(lock) {
            isCurrent(source) && !viewing && !changes.value.isClearing &&
                session.read {
                    it.day == snapshot.day && it.timelapse.isEnabled &&
                        it.timelapse.captureMinute == snapshot.timelapse.captureMinute
                }
        }

    private suspend fun loadArchive(source: Source): TimelapseArchive? {
        val archive = withContext(Dispatchers.IO) { store.archive(source.farm) }
        return synchronized(lock) {
            if (!isCurrent(source)) return@synchronized null
            publish(source, archive)
            archive
        }
    }

    private fun publish(
        source: Source,
        archive: TimelapseArchive,
    ) {
        val current =
            session.act {
                if (it.state !== source.identity || it.state.timelapse.historyID != source.farm) return@act false
                if (archive.lastCapturedDay == it.state.day) it.recordTimelapseCapture(it.state.day)
                true
            }
        if (!current) return
        changes.value = changes.value.copy(farm = source.farm, archive = archive, error = null)
    }

    private fun report(
        source: Source,
        message: String,
    ) = synchronized(lock) {
        if (isCurrent(source)) changes.value = changes.value.copy(error = message)
    }

    private class CaptureInvalidated : CancellationException("Timelapse capture invalidated")

    private suspend fun photo(
        source: Source,
        state: GameState,
        now: Instant,
    ): ByteArray? =
        suspendCancellableCoroutine { continuation ->
            synchronized(lock) {
                if (!canSave(source, state)) {
                    continuation.cancel(CaptureInvalidated())
                    return@suspendCancellableCoroutine
                }
                pendingPhoto = continuation
            }
            continuation.invokeOnCancellation {
                synchronized(lock) { if (pendingPhoto === continuation) pendingPhoto = null }
            }
            session.photos.request(PhotoRequest(2048, state, now)) { bytes ->
                synchronized(lock) {
                    if (pendingPhoto === continuation) pendingPhoto = null
                    if (continuation.isActive) continuation.resume(bytes)
                }
            }
        }
}
