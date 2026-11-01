package com.hunter.stockfarmer.game

/** Lets the screens ask the farm, which owns the graphics, for a picture of the whole map. */
class PhotoRequests {
    @Volatile
    var handler: ((pixels: Int, done: (ByteArray?) -> Unit) -> Unit)? = null

    /** Asks for a square PNG of the farm; [done] gets its bytes, or null when the farm could not draw it. */
    fun request(
        pixels: Int,
        done: (ByteArray?) -> Unit,
    ) {
        val take = handler
        if (take == null) done(null) else take(pixels, done)
    }
}
