package com.hunter.stockfarmer.ui.timelapse

import com.hunter.stockfarmer.core.model.GameState
import com.hunter.stockfarmer.core.persistence.LocalSaveFileSystem
import com.hunter.stockfarmer.core.persistence.SaveFileSystem
import com.hunter.stockfarmer.core.persistence.TimelapseStore
import com.hunter.stockfarmer.core.rules.GameEngine
import com.hunter.stockfarmer.game.GameSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

class TimelapseRecorderTest {
    @TempDir lateinit var directory: Path
    private val farm = UUID(0, 1)
    private val session = GameSession(GameEngine(GameState().apply { timelapse = timelapse.copy(historyID = farm) }))
    private val png = byteArrayOf(1, 2, 3)
    private var nextID = 10L

    private fun store() = TimelapseStore(directory) { UUID(0, nextID++) }

    private fun at(minute: Long) = Instant.ofEpochSecond(minute)

    @Test
    fun `a forward crossing captures a 2048 pixel snapshot with matching metadata only once per day`() =
        runBlocking {
            val store = store()
            val recorder = TimelapseRecorder(session, store)
            var requests = 0
            session.photos.handler = { request, done ->
                requests++
                assertEquals(2048, request.pixels)
                assertEquals(at(540), request.at)
                assertEquals(1, request.state?.day)
                session.act { it.state.money = 123.0 }
                assertEquals(0.0, request.state?.money)
                done(png)
            }
            recorder.tick(at(539))
            recorder.tick(at(540))
            recorder.tick(at(400))
            recorder.tick(at(600))
            val archive = store.archive(farm)
            assertEquals(1, requests)
            assertEquals(1, archive.frames.size)
            assertEquals(540, archive.frames.single().minute)
            assertEquals(at(540), archive.frames.single().capturedAt)
            assertArrayEquals(png, store.image(archive.frames.single(), farm))
            assertEquals(1, session.read { it.timelapse.lastCapturedDay })
        }

    @Test
    fun `opening late resuming and leaving the viewer do not catch up missed captures`() =
        runBlocking {
            val store = store()
            val recorder = TimelapseRecorder(session, store)
            session.photos.handler = { _, done -> done(png) }
            recorder.tick(at(700))
            recorder.tick(at(539), active = false)
            recorder.tick(at(600))
            recorder.tick(at(539))
            recorder.setViewing(true)
            recorder.tick(at(540))
            recorder.setViewing(false)
            recorder.tick(at(600))
            assertTrue(store.archive(farm).frames.isEmpty())
            session.act { it.state.day = 2 }
            recorder.tick(at(539))
            recorder.tick(at(540))
            assertEquals(
                2,
                store
                    .archive(farm)
                    .frames
                    .single()
                    .day,
            )
        }

    @Test
    fun `settings changes rebaseline and disabled recording never captures`() =
        runBlocking {
            val recorder = TimelapseRecorder(session, store())
            session.photos.handler = { _, done -> done(png) }
            recorder.tick(at(539))
            recorder.setEnabled(false)
            recorder.tick(at(540))
            recorder.setEnabled(true)
            recorder.tick(at(600))
            recorder.setMinute(700)
            recorder.tick(at(699))
            recorder.tick(at(700))
            assertEquals(
                700,
                recorder.state.value.archive
                    ?.frames
                    ?.single()
                    ?.minute,
            )
        }

    @Test
    fun `refresh restores a saved capture marker and clear preserves it and settings`() =
        runBlocking {
            val store = store()
            val archive = store.archive(farm)
            store.append(png, farm, archive.generation, 1, 540, at(540))
            val recorder = TimelapseRecorder(session, store)
            recorder.refresh()
            assertEquals(1, session.read { it.timelapse.lastCapturedDay })
            val settings = session.read { it.timelapse }
            recorder.clear()
            assertEquals(settings, session.read { it.timelapse })
            assertTrue(store.archive(farm).frames.isEmpty())
            assertEquals(1, store.archive(farm).lastCapturedDay)
        }

    @Test
    fun `a missing renderer reports failure without marking the day captured and refresh can retry`() =
        runBlocking {
            val recorder = TimelapseRecorder(session, store())
            recorder.tick(at(539))
            recorder.tick(at(540))
            assertNotNull(recorder.state.value.error)
            assertNull(session.read { it.timelapse.lastCapturedDay })
            recorder.refresh()
            assertNull(recorder.state.value.error)
            assertTrue(
                recorder.state.value.archive!!
                    .frames
                    .isEmpty(),
            )
        }

    @Test
    fun `cancelling a suspended capture releases the recorder and ignores a late photo callback`() =
        runBlocking {
            withTimeout(5000) {
                val store = store()
                val recorder = TimelapseRecorder(session, store)
                val pending = CompletableDeferred<(ByteArray?) -> Unit>()
                session.photos.handler = { _, done -> pending.complete(done) }
                recorder.tick(at(539))
                val capture = launch { recorder.tick(at(540)) }
                val done = pending.await()
                capture.cancelAndJoin()
                done(png)
                recorder.refresh()
                assertTrue(store.archive(farm).frames.isEmpty())
                assertNull(recorder.state.value.error)
            }
        }

    @Test
    fun `clear invalidates a photo already in flight and prevents it repopulating history`() =
        runBlocking {
            withTimeout(5000) {
                val store = store()
                val recorder = TimelapseRecorder(session, store)
                val pending = CompletableDeferred<(ByteArray?) -> Unit>()
                session.photos.handler = { _, done -> pending.complete(done) }
                recorder.tick(at(539))
                val capture = launch { recorder.tick(at(540)) }
                val done = pending.await()
                val clear = launch(start = CoroutineStart.UNDISPATCHED) { recorder.clear() }
                capture.join()
                clear.join()
                done(png)
                assertTrue(store.archive(farm).frames.isEmpty())
                assertNull(session.read { it.timelapse.lastCapturedDay })
            }
        }

    @Test
    fun `a farm replacement during rendering cannot save or mark a stale frame`() =
        runBlocking {
            val store = store()
            val recorder = TimelapseRecorder(session, store)
            session.photos.handler = { _, done ->
                session.act { it.state = it.state.copy() }
                done(png)
            }
            recorder.tick(at(539))
            recorder.tick(at(540))
            assertTrue(store.archive(farm).frames.isEmpty())
            assertNull(session.read { it.timelapse.lastCapturedDay })
        }

    @Test
    fun `reset and overlapping ticks cannot save an inactive capture`() =
        runBlocking {
            withTimeout(5000) {
                val store = store()
                val recorder = TimelapseRecorder(session, store)
                val pending = CompletableDeferred<(ByteArray?) -> Unit>()
                var requests = 0
                session.photos.handler = { _, done ->
                    requests++
                    pending.complete(done)
                }
                recorder.tick(at(539))
                val capture = launch { recorder.tick(at(540)) }
                val done = pending.await()
                recorder.tick(at(541))
                recorder.reset()
                done(png)
                capture.join()
                assertEquals(1, requests)
                assertTrue(store.archive(farm).frames.isEmpty())
            }
        }

    @Test
    fun `eight hours of active play across sessions produces twenty daily captures`() =
        runBlocking {
            val store = store()
            val recorder = TimelapseRecorder(session, store)
            session.photos.handler = { _, done -> done(png) }
            repeat(8 * 60 * 60) { second ->
                session.act { it.state.day = second / 1440 + 1 }
                if (second % 3600 == 0) recorder.reset()
                recorder.tick(at(second.toLong()))
            }
            assertEquals((1..20).toList(), store.archive(farm).frames.map { it.day })
        }

    @Test
    fun `midnight capture uses the new game day and a changed farm rebaselines the clock`() =
        runBlocking {
            val store = store()
            val recorder = TimelapseRecorder(session, store)
            session.photos.handler = { _, done -> done(png) }
            recorder.setMinute(0)
            recorder.tick(at(1439))
            session.act { it.state.day = 2 }
            recorder.tick(at(1441))
            assertEquals(
                2,
                store
                    .archive(farm)
                    .frames
                    .single()
                    .day,
            )
            assertEquals(
                1,
                store
                    .archive(farm)
                    .frames
                    .single()
                    .minute,
            )
            val otherFarm = UUID(0, 2)
            session.act {
                it.state.timelapse = it.state.timelapse.copy(historyID = otherFarm, lastCapturedDay = null)
            }
            recorder.tick(at(1500))
            assertTrue(store.archive(otherFarm).frames.isEmpty())
            assertEquals(otherFarm, recorder.state.value.farm)
        }

    @Test
    fun `a failed archive write leaves no capture marker and the next day can still record`() =
        runBlocking {
            var fails = false
            val files =
                object : SaveFileSystem by LocalSaveFileSystem() {
                    override fun write(
                        data: ByteArray,
                        to: Path,
                    ) {
                        if (fails && to.fileName.toString() == "index.json") throw IOException("Disk full")
                        LocalSaveFileSystem().write(data, to)
                    }
                }
            val store = TimelapseStore(directory, files) { UUID(0, nextID++) }
            val recorder = TimelapseRecorder(session, store)
            session.photos.handler = { _, done -> done(png) }
            recorder.tick(at(539))
            fails = true
            recorder.tick(at(540))
            assertNotNull(recorder.state.value.error)
            assertNull(session.read { it.timelapse.lastCapturedDay })
            assertTrue(store.archive(farm).frames.isEmpty())
            fails = false
            session.act { it.state.day = 2 }
            recorder.tick(at(539))
            recorder.tick(at(540))
            assertEquals(
                2,
                store
                    .archive(farm)
                    .frames
                    .single()
                    .day,
            )
            assertNull(recorder.state.value.error)
        }

    @Test
    fun `a failed clear reports an error releases its busy state and can be retried`() =
        runBlocking {
            var fails = true
            val files =
                object : SaveFileSystem by LocalSaveFileSystem() {
                    override fun remove(url: Path) {
                        if (fails) throw IOException("Read only")
                        LocalSaveFileSystem().remove(url)
                    }
                }
            val store = TimelapseStore(directory, files) { UUID(0, nextID++) }
            val recorder = TimelapseRecorder(session, store)
            recorder.refresh()
            recorder.clear()
            assertNotNull(recorder.state.value.error)
            assertTrue(!recorder.state.value.isClearing)
            fails = false
            recorder.clear()
            assertNull(recorder.state.value.error)
            assertTrue(!recorder.state.value.isClearing)
            assertTrue(store.archive(farm).frames.isEmpty())
        }
}
