package com.hunter.stockfarmer.game

import com.hunter.stockfarmer.core.model.GameState
import java.time.Instant

/** A supplied state and time keep an archived image consistent with its capture metadata. */
data class PhotoRequest(
    val pixels: Int,
    val state: GameState? = null,
    val at: Instant? = null,
)

/** Lets the screens ask the farm, which owns the graphics, for a picture of the whole map. */
class PhotoRequests {
    @Volatile
    var handler: ((request: PhotoRequest, done: (ByteArray?) -> Unit) -> Unit)? = null

    /** Asks for a square PNG of the farm; [done] gets its bytes, or null when the farm could not draw it. */
    fun request(
        pixels: Int,
        done: (ByteArray?) -> Unit,
    ) = request(PhotoRequest(pixels), done)

    fun request(
        request: PhotoRequest,
        done: (ByteArray?) -> Unit,
    ) {
        val take = handler
        if (take == null) done(null) else take(request, done)
    }
}
