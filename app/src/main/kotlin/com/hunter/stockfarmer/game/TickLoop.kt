package com.hunter.stockfarmer.game

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/**
 * Runs [step] once every [intervalMillis] on [dispatcher] between [start] and [stop], never two at once and none
 * started after [stop] returns. A step already under way finishes, so it is never cut in half. [onStart] runs first
 * each time, before any step. Call [start] and [stop] from one thread, the main one.
 */
class TickLoop(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val intervalMillis: Long,
    private val onStart: () -> Unit,
    private val step: () -> Unit,
) {
    private var job: Job? = null

    fun start() {
        val previous = job
        if (previous?.isActive == true) return
        job =
            scope.launch(dispatcher) {
                // The run that was stopped may still be finishing its step.
                previous?.join()
                onStart()
                while (true) {
                    ensureActive()
                    step()
                    delay(intervalMillis)
                }
            }
    }

    fun stop() {
        job?.cancel()
    }
}
