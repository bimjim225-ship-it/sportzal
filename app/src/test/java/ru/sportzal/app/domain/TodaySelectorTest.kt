package ru.sportzal.app.domain

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test
import ru.sportzal.app.model.WorkoutRuntime

class TodaySelectorTest {
    private val selector = TodaySelector()
    private fun workout(id: String, date: String, version: Int = 2) =
        PlannedWorkout("program", version, id, id, LocalDate.parse(date), id.last().digitToIntOrNull() ?: 1)

    @Test fun activeWorkoutAlwaysWins() {
        val active = WorkoutRuntime("runtime", "{}", "[]")
        assertEquals(TodaySelection.Resume(active), selector.select(active, listOf(workout("w1", "2026-09-01")), emptySet()))
    }

    @Test fun earliestPastUnstartedWorkoutIsSelected() {
        val result = selector.select(null, listOf(workout("w2", "2026-09-02"), workout("w1", "2026-09-01")), emptySet())
        assertEquals("w1", (result as TodaySelection.Ready).workout.workoutInstanceId)
    }

    @Test fun futureWorkoutCanBeSelectedManually() {
        val result = selector.select(null, listOf(workout("w1", "2026-09-08"), workout("w2", "2026-09-10")), emptySet(), "w2")
        assertEquals("w2", (result as TodaySelection.Ready).workout.workoutInstanceId)
        assertTrue(result.manuallySelected)
    }

    @Test fun consumedWorkoutRemainsConsumedAcrossVersions() {
        val result = selector.select(null, listOf(workout("w1", "2026-09-01", 3), workout("w2", "2026-09-02", 3)), setOf(ConsumedWorkout("program", "w1")))
        assertEquals("w2", (result as TodaySelection.Ready).workout.workoutInstanceId)
    }

    @Test fun cancelledEmptyWorkoutIsStartableAgain() {
        assertTrue(selector.select(null, listOf(workout("w1", "2026-09-01")), emptySet()) is TodaySelection.Ready)
    }

    @Test fun exhaustedProgramHasExplicitState() {
        assertEquals(TodaySelection.Exhausted, selector.select(null, listOf(workout("w1", "2026-09-01")), setOf(ConsumedWorkout("program", "w1"))))
    }

    @Test fun absentProgramHasExplicitState() {
        assertEquals(TodaySelection.NoProgram, selector.select(null, null, emptySet()))
    }
}
