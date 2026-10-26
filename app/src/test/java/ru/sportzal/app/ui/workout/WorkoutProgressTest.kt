package ru.sportzal.app.ui.workout

import org.junit.Assert.*
import org.junit.Test
import ru.sportzal.app.data.db.*
import ru.sportzal.app.model.*

class WorkoutProgressTest {
    private val exercise = ExerciseDocument("e", "row", "Тяга", null, null, "total_external", "bilateral", 1, "none",
        listOf(PlannedSetDocument(1, "warmup", 10.0, 10, 10, null, 30),
            PlannedSetDocument(2, "work", 20.0, 8, 12, null, 90),
            PlannedSetDocument(3, "work", 20.0, 8, 12, null, 90)))

    @Test fun extraAndWarmupNeverInflateCompletedWorkOrClosePlannedSlots() {
        val card = ExerciseUiState(exercise, null, null, listOf(fact(1, "warmup"), fact(2, "work"), fact(null, "work")))
        val p = card.progress()
        assertEquals(1, p.completedWork)
        assertEquals(2, p.plannedWork)
        assertEquals(1, p.completedWarmup)
        assertEquals(1, p.extra)
        assertEquals(2, p.completedPlanned)
        assertEquals(1, p.remaining)
    }

    @Test fun skippedSlotsResolveTheQueueWithoutClaimingCompletion() {
        val card = ExerciseUiState(exercise, null, null, listOf(fact(1, "warmup"), fact(2, "work")),
            skipped = listOf(SkippedSetEntity("w", "e", 3, "now", "time_limit", null)))
        val p = card.progress()
        assertEquals(0, p.remaining)
        assertEquals(1, p.skipped)
        assertEquals(1, p.completedWork)
        assertFalse(p.fullyPerformed)
    }

    private fun fact(no: Int?, type: String) = SetResultEntity("s-$no", "w", no ?: 4, "e", no, type, "row", "Тяга",
        null, null, null, "total_external", "bilateral", 20.0, 10, null, "2026-10-26T10:00:00Z", null, null, null, "[]", null)
}
