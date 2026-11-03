package com.hunter.stockfarmer.farm

import com.badlogic.gdx.utils.GdxRuntimeException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

class PhotoEncoderTest {
    @Test
    fun `pictures encode one at a time in the order asked, off the calling thread`() {
        val log = mutableListOf<String>()
        val threads = mutableSetOf<Thread>()
        val gate = CountDownLatch(1)
        val finished = CountDownLatch(3)
        val encoder =
            PhotoEncoder<Int>(
                encode = { picture ->
                    gate.await(5, TimeUnit.SECONDS)
                    synchronized(log) { log += "encode $picture" }
                    threads += Thread.currentThread()
                    byteArrayOf(picture.toByte())
                },
                release = { synchronized(log) { log += "release $it" } },
            )
        for (picture in 1..3) {
            encoder.submit(picture) { png ->
                synchronized(log) { log += "done ${png!![0]}" }
                finished.countDown()
            }
        }
        gate.countDown()
        assertTrue(finished.await(5, TimeUnit.SECONDS))
        assertEquals((1..3).flatMap { listOf("encode $it", "release $it", "done $it") }, log)
        assertEquals(1, threads.size)
        assertNotEquals(Thread.currentThread(), threads.single())
    }

    @Test
    fun `running out of memory answers null after releasing the picture`() {
        assertFailureAnswersNull(OutOfMemoryError())
    }

    @Test
    fun `a libGDX failure answers null after releasing the picture`() {
        assertFailureAnswersNull(GdxRuntimeException("no room"))
    }

    private fun assertFailureAnswersNull(failure: Throwable) {
        val log = mutableListOf<String>()
        val encoder =
            PhotoEncoder<Int>(
                encode = { throw failure },
                release = { log += "release" },
                executor = Executor { it.run() },
            )
        var answered = false
        encoder.submit(1) { png ->
            assertNull(png)
            log += "done"
            answered = true
        }
        assertTrue(answered)
        assertEquals(listOf("release", "done"), log)
    }

    @Test
    fun `a slow first answer holds back the next picture`() {
        val order = ArrayBlockingQueue<Int>(2)
        val encoder = PhotoEncoder<Int>(encode = { byteArrayOf(it.toByte()) }, release = {})
        encoder.submit(1) {
            Thread.sleep(50)
            order.add(1)
        }
        encoder.submit(2) { order.add(2) }
        assertEquals(1, order.poll(5, TimeUnit.SECONDS))
        assertEquals(2, order.poll(5, TimeUnit.SECONDS))
    }
}
