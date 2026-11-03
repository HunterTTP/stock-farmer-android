package com.hunter.stockfarmer.farm

import com.badlogic.gdx.utils.GdxRuntimeException
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Turns read-back pictures into PNGs off the graphics thread, one at a time and in order. Each picture is released
 * before [done] hears about it, so an out-of-memory answer leaves room for the caller to try a smaller one.
 */
internal class PhotoEncoder<Picture>(
    private val encode: (Picture) -> ByteArray,
    private val release: (Picture) -> Unit,
    private val executor: Executor = sharedExecutor,
) {
    fun submit(
        picture: Picture,
        done: (ByteArray?) -> Unit,
    ) {
        executor.execute {
            val png =
                try {
                    encode(picture)
                } catch (_: OutOfMemoryError) {
                    null
                } catch (_: GdxRuntimeException) {
                    null
                } finally {
                    release(picture)
                }
            done(png)
        }
    }

    private companion object {
        /** One thread at most, gone when idle: a farm view outlived by its encode needs nothing shut down. */
        val sharedExecutor: Executor by lazy {
            ThreadPoolExecutor(0, 1, 30, TimeUnit.SECONDS, LinkedBlockingQueue()) { task ->
                Thread(task, "farm-photo-encoder").apply { isDaemon = true }
            }
        }
    }
}
