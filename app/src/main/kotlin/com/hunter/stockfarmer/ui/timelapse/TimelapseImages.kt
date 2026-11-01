package com.hunter.stockfarmer.ui.timelapse

import com.hunter.stockfarmer.core.persistence.TimelapseFrame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.UUID

/** Retains the current decoded image and at most its two neighbors. Confined to the viewer's coroutine. */
internal class TimelapseImages<T : Any>(
    private val frames: List<TimelapseFrame>,
    private val load: suspend (TimelapseFrame) -> T,
) {
    private val images = mutableMapOf<UUID, T>()

    suspend fun current(index: Int): T {
        val neighbors = neighbors(index).map { frames[it].id }.toSet()
        images.keys.retainAll(neighbors)
        return image(index)
    }

    suspend fun prefetch(index: Int) {
        for (neighbor in neighbors(index).drop(1)) {
            try {
                image(neighbor)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A missing neighbor must not hide a successfully loaded current screenshot.
            }
        }
    }

    private fun neighbors(index: Int): List<Int> = listOf(index, index + 1, index - 1).filter { it in frames.indices }

    private suspend fun image(index: Int): T {
        val frame = frames[index]
        images[frame.id]?.let { return it }
        val decoded = load(frame)
        currentCoroutineContext().ensureActive()
        images[frame.id] = decoded
        return decoded
    }
}
