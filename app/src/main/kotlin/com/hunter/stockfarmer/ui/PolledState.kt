package com.hunter.stockfarmer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

/** A projection of the game read again and again while the screen is visible; [refresh] reads it right now. */
@Stable
class PolledState<T> internal constructor(
    private val state: MutableState<T>,
    private val reread: () -> T,
) : MutableState<T> by state {
    fun refresh() {
        state.value = reread()
    }
}

/** Reads [read] every [intervalMillis] while [lifecycle] is at least started, and stops in the background. */
internal suspend fun <T> pollWhileStarted(
    lifecycle: Lifecycle,
    intervalMillis: Long,
    read: () -> T,
    publish: (T) -> Unit,
) = lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
    while (true) {
        publish(read())
        delay(intervalMillis)
    }
}

/** Read a projection while visible; nothing mutable from GameState escapes the session lock. */
@Composable
internal fun <T> rememberPolledState(
    key: Any?,
    intervalMillis: Long = DEFAULT_POLL_MILLIS,
    read: () -> T,
): PolledState<T> {
    val currentRead = rememberUpdatedState(read)
    val owner = LocalLifecycleOwner.current
    val state = remember(key, owner) { mutableStateOf(read()) }
    LaunchedEffect(state, intervalMillis) {
        pollWhileStarted(owner.lifecycle, intervalMillis, { currentRead.value() }) { state.value = it }
    }
    return remember(state) { PolledState(state) { currentRead.value() } }
}

private const val DEFAULT_POLL_MILLIS = 500L

/** How often an open menu or sheet re-reads the game. */
internal const val FAST_POLL_MILLIS = 250L
