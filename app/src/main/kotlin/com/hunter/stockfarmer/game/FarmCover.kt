package com.hunter.stockfarmer.game

/** Stops the farm's frame loop while something opaque hides it, as iOS pauses its scene, and starts it again. */
class FarmCover {
    private var isCovered = false
    private var frameLoop: ((covered: Boolean) -> Unit)? = null

    /** Called from the main thread by whatever covers the farm. */
    @Synchronized
    fun cover(covered: Boolean) {
        isCovered = covered
        frameLoop?.invoke(covered)
    }

    /** Called from the graphics thread by each farm view; a view built under a cover starts paused. */
    @Synchronized
    fun attach(frameLoop: ((covered: Boolean) -> Unit)?) {
        this.frameLoop = frameLoop
        if (isCovered) frameLoop?.invoke(true)
    }
}
