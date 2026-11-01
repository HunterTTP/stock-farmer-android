package com.hunter.stockfarmer.ui.screens

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class PerformanceMonitorTest {
    private var now = 0.0
    private val monitor = PerformanceMonitor(clock = { now }, wallClock = { Instant.EPOCH })

    @Test
    fun `disabled instrumentation does nothing and live window waits for two frames`() {
        assertNull(monitor.begin())
        monitor.frame()
        monitor.setVisible(true)
        assertEquals(0.0, monitor.begin())
        monitor.frame()
        monitor.sample()
        assertNull(monitor.snapshot.liveFPS)
        now = 0.02
        monitor.frame()
        monitor.end(PerformanceWork.scene, 0.018)
        monitor.sample(PerformanceResources(64.0, 42, "Warm"))
        assertEquals(50.0, monitor.snapshot.liveFPS!!, 0.001)
        assertEquals(20.0, monitor.snapshot.liveFrameMS!!, 0.001)
        assertEquals(2.0, monitor.snapshot.liveUpdateMS!!, 0.001)
        assertEquals(64.0, monitor.snapshot.memoryMB)
        assertEquals(42, monitor.snapshot.nodeCount)
        now = 2.0
        monitor.sample()
        assertNull(monitor.snapshot.liveFPS)
        monitor.setVisible(false)
        assertNull(monitor.begin())
    }

    @Test
    fun `recording includes menus excludes inactive time and resets frame clock on resume`() {
        monitor.start("Test farm")
        monitor.frame()
        now = 0.02
        monitor.frame()
        now = 2.0
        monitor.setActive(false)
        now = 102.0
        monitor.frame()
        monitor.setActive(true)
        monitor.frame()
        now = 102.02
        monitor.frame()
        now = 105.0
        monitor.stop()
        val report = monitor.snapshot.report!!
        assertEquals(5.0, report.activeSeconds, 0.0001)
        assertEquals(
            2,
            report.frames.durations.count
                .toInt(),
        )
        assertEquals(0, report.frames.hitches.toInt())
        assertFalse(monitor.snapshot.isRecording)
        assertNull(monitor.begin())
        monitor.start("Replacement")
        assertEquals(report, monitor.snapshot.report)
        now = 106.0
        monitor.stop()
        assertEquals("Replacement", monitor.snapshot.report!!.environment)
        assertEquals(
            0,
            monitor.snapshot.report!!
                .frames.durations.count
                .toInt(),
        )
    }

    @Test
    fun `stopping while paused preserves elapsed foreground time even without a periodic sample`() {
        monitor.start("Paused farm")
        now = 3.0
        monitor.setActive(false)
        now = 100.0
        monitor.stop()
        assertEquals(3.0, monitor.snapshot.report!!.activeSeconds)
        assertEquals(3.0, monitor.snapshot.elapsed)
    }

    @Test
    fun `report ranks systems excludes containers from insight and keeps eight longest gaps`() {
        monitor.start("Test farm")
        monitor.frame()
        repeat(14) { index ->
            val start = now
            now += (index + 1) * 0.01
            monitor.end(PerformanceWork.crops, start)
            monitor.end(PerformanceWork.scene, start - 0.01)
            monitor.frame()
        }
        monitor.sample(PerformanceResources(80.0, 50, "Warm"))
        monitor.sample(PerformanceResources(60.0, 25, "Nominal"))
        monitor.stop()
        val report = monitor.snapshot.report!!
        assertEquals(PerformanceWork.scene, report.rankedWork.first().first)
        assertTrue(report.insight.startsWith("Crop visuals & growth"))
        assertEquals(8, report.hitches.size)
        assertTrue(report.hitches.zipWithNext().all { (a, b) -> a.milliseconds >= b.milliseconds })
        assertTrue(report.hitches.all { it.nearbyWork.startsWith("Crop visuals & growth:") })
        assertEquals(80.0, report.peakMemoryMB)
        assertEquals(50, report.peakNodes)
        assertTrue(report.thermalStates.containsAll(setOf("Warm", "Nominal")))
        assertTrue(report.text.contains("Test farm"))
        assertTrue(report.text.contains("System | Calls | Total ms | Average ms | p95 ms | Worst ms"))
        assertTrue(report.text.contains(PerformanceReport.measurementNotes))
    }

    @Test
    fun `empty recordings explain missing instrumentation and measurement preserves throwing actions`() {
        monitor.start("No frames")
        monitor.stop()
        assertTrue(
            monitor.snapshot.report!!
                .insight
                .startsWith("No farm frames captured"),
        )
        monitor.start("Frames only")
        monitor.frame()
        now = 0.02
        monitor.frame()
        runCatching {
            monitor.measure(PerformanceWork.tap) {
                now = 0.03
                error("Action failure")
            }
        }
        monitor.stop()
        assertTrue(
            monitor.snapshot.report!!
                .insight
                .startsWith("No system timings captured yet"),
        )
        assertEquals(
            1,
            monitor.snapshot.report!!
                .work
                .getValue(PerformanceWork.tap)
                .count
                .toInt(),
        )
        assertEquals("1.2", PerformanceReport.number(1.2))
        assertEquals("1.25 ms", PerformanceReport.ms(1.25))
    }
}
