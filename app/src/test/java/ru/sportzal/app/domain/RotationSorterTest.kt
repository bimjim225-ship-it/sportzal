package ru.sportzal.app.domain

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.sportzal.app.model.ClockAnchor

class RotationSorterTest {
    private val now = ClockReading(Instant.parse("2026-09-08T12:10:00Z"), "boot", 600_000)
    private fun card(id: String, order: Int, elapsed: Long? = null, done: Boolean = false) = ExerciseRuntimeCard(
        id, order, elapsed?.let { ClockAnchor(now.wall.minusSeconds(it), "boot", now.elapsedRealtimeMs!! - it * 1000) }, done)

    @Test fun `never started precede started and preserve planned order`() {
        val sorted = sortRotation(listOf(card("started", 1, 20), card("second", 2), card("first", 1)), now)
        assertEquals(listOf("first", "second", "started"), sorted.map { it.exerciseInstanceId })
    }
    @Test fun `longest elapsed started exercise first and ties use order`() {
        val sorted = sortRotation(listOf(card("short", 1, 20), card("tie2", 2, 80), card("tie1", 1, 80)), now)
        assertEquals(listOf("tie1", "tie2", "short"), sorted.map { it.exerciseInstanceId })
    }
    @Test fun `completed are below active and manual card identity remains available`() {
        val sorted = sortRotation(listOf(card("done", 1, 100, true), card("active", 2, 5)), now)
        assertEquals(listOf("active", "done"), sorted.map { it.exerciseInstanceId })
        assertEquals("done", sorted.first { it.exerciseInstanceId == "done" }.exerciseInstanceId)
    }
}
