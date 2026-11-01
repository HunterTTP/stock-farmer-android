package com.hunter.stockfarmer.game

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

internal fun collectingHaptics(
    session: GameSession,
    action: () -> Unit,
): List<HapticRequest> =
    runBlocking {
        val received = mutableListOf<HapticRequest>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) { session.haptics.collect { received += it } }
        try {
            action()
            yield()
            received.toList()
        } finally {
            collector.cancel()
        }
    }
