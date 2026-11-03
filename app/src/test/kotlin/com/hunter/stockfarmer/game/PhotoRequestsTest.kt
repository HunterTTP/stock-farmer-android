package com.hunter.stockfarmer.game

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PhotoRequestsTest {
    private val requests = PhotoRequests()
    private val answers = mutableListOf<ByteArray?>()

    @Test
    fun `no farm view answers null at once`() {
        requests.request(16) { answers += it }
        assertEquals(listOf<ByteArray?>(null), answers)
    }

    @Test
    fun `replacing the handler gives up on queued requests only`() {
        val pending = mutableListOf<PendingPhoto>()
        requests.handler = { _, photo -> pending += photo }
        requests.request(16) { answers += it }
        requests.request(16) { answers += it }
        assertTrue(pending[1].begin())

        requests.handler = null

        assertEquals(listOf<ByteArray?>(null), answers)
        assertFalse(pending[0].begin(), "the lost view's runnable must not draw if it ever runs")
        val png = byteArrayOf(7)
        pending[1](png)
        assertEquals(2, answers.size)
        assertArrayEquals(png, answers[1])
    }

    @Test
    fun `an answer is given once`() {
        var pending: PendingPhoto? = null
        requests.handler = { _, photo -> pending = photo }
        requests.request(16) { answers += it }
        pending!!(byteArrayOf(1))
        pending!!(byteArrayOf(2))
        requests.handler = null
        assertEquals(1, answers.size)
        assertEquals(1.toByte(), answers[0]!![0])
    }

    @Test
    fun `a retry made while giving up goes to the new handler and is not given up on`() {
        val toNew = mutableListOf<PendingPhoto>()
        requests.handler = { _, _ -> }
        requests.request(32) { first ->
            assertNull(first)
            requests.request(16) { answers += it }
        }

        requests.handler = { _, photo -> toNew += photo }

        assertEquals(1, toNew.size)
        assertTrue(answers.isEmpty())
        assertTrue(toNew[0].begin())
    }
}
