package com.hunter.stockfarmer.notifications

/**
 * Holds back a system prompt until nothing is busy with the player's finger. On Android the permission dialog takes the
 * touch from the window beneath it, so asking in the middle of a planting drag would end the stroke after one tile.
 * Main thread only.
 */
class DeferredPrompt(
    private val isBusy: () -> Boolean,
    private val show: () -> Unit,
) {
    private var isWaiting = false

    /** Shows the prompt now, or as soon as [whenIdle] finds nothing busy. */
    fun request() {
        isWaiting = true
        whenIdle()
    }

    /** Call when what makes it busy may have finished. */
    fun whenIdle() {
        if (!isWaiting || isBusy()) return
        isWaiting = false
        show()
    }
}
