package ru.sportzal.app.domain

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.sportzal.app.model.ClockAnchor

class RotationInteractionTest {
    @Test fun `focused edited card stays put until release and draft identity is independent of position`() {
        val firstNow = ClockReading(Instant.parse("2026-09-08T12:00:00Z"), "b", 10_000)
        val initial = listOf(ExerciseRuntimeCard("a", 1), ExerciseRuntimeCard("b", 2))
        val coordinator = RotationCoordinator(initial)
        val drafts = mutableMapOf(("a" to 1) to SetDraft(50.0, 9, null, ActualContext("a", null, null, "external", "bilateral")))
        coordinator.beginInteraction()
        val afterCommit = listOf(
            ExerciseRuntimeCard("a", 1, ClockAnchor(firstNow.wall, "b", 10_000)),
            ExerciseRuntimeCard("b", 2),
        )
        coordinator.update(afterCommit, firstNow.copy(elapsedRealtimeMs = 11_000), committed = true)
        assertEquals(listOf("a", "b"), coordinator.cards.map { it.exerciseInstanceId })
        coordinator.endInteraction()
        assertEquals(listOf("b", "a"), coordinator.cards.map { it.exerciseInstanceId })
        assertEquals(9, drafts.getValue("a" to 1).reps)
    }
}
