package com.hunter.stockfarmer.game

import com.hunter.stockfarmer.core.model.GameState
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** A supplied state and time keep an archived image consistent with its capture metadata. */
data class PhotoRequest(
    val pixels: Int,
    val state: GameState? = null,
    val at: Instant? = null,
)

/**
 * One request's answer, given exactly once from any thread. A request the graphics thread has not picked up yet can be
 * given up on, because a destroyed farm view drops its queued work without ever running it.
 */
class PendingPhoto internal constructor(
    private val answer: (ByteArray?) -> Unit,
    private val settled: (PendingPhoto) -> Unit,
) : (ByteArray?) -> Unit {
    private val stage = AtomicInteger(QUEUED)

    /** Called on the graphics thread before drawing; false means the request was already given up on. */
    fun begin(): Boolean = stage.compareAndSet(QUEUED, DRAWING)

    override fun invoke(png: ByteArray?) {
        if (stage.getAndSet(ANSWERED) != ANSWERED) {
            settled(this)
            answer(png)
        }
    }

    internal fun giveUpIfQueued() {
        if (stage.compareAndSet(QUEUED, ANSWERED)) {
            settled(this)
            answer(null)
        }
    }

    private companion object {
        const val QUEUED = 0
        const val DRAWING = 1
        const val ANSWERED = 2
    }
}

/** Lets the screens ask the farm, which owns the graphics, for a picture of the whole map. */
class PhotoRequests {
    private val outstanding = ConcurrentHashMap.newKeySet<PendingPhoto>()

    /** Whether the player's own photo is under way, so a second tap does not start another. */
    val isTakingPhoto = AtomicBoolean(false)

    /** Set by each farm view; whatever the previous view had queued and not started is lost with it and answered null. */
    @Volatile
    var handler: ((request: PhotoRequest, pending: PendingPhoto) -> Unit)? = null
        set(value) {
            field = value
            // A snapshot: an answer can ask again at once, and that request belongs to the new handler.
            outstanding.toList().forEach { it.giveUpIfQueued() }
        }

    /** Asks for a square PNG of the farm; [done] gets its bytes, or null when the farm could not draw it, on any thread. */
    fun request(
        pixels: Int,
        done: (ByteArray?) -> Unit,
    ) = request(PhotoRequest(pixels), done)

    fun request(
        request: PhotoRequest,
        done: (ByteArray?) -> Unit,
    ) {
        val pending = PendingPhoto(done, outstanding::remove)
        // Listed before reading the handler, so a handler replaced in between still finds this request to give up on.
        outstanding += pending
        val take = handler
        if (take == null) pending(null) else take(request, pending)
    }
}
