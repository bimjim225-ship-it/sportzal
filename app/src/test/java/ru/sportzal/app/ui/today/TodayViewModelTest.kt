package ru.sportzal.app.ui.today

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.sportzal.app.domain.ConsumedWorkout
import ru.sportzal.app.domain.PlannedWorkout

class TodayViewModelTest {
    private fun workout(id: String, date: String) =
        PlannedWorkout("program", 1, id, id, LocalDate.parse(date), 1)

    @Test fun consumedFutureWorkoutIsNotAvailableButSecondOverdueWorkoutIs() {
        val available = availableWorkouts(
            listOf(
                workout("first-overdue", "2026-09-01"),
                workout("second-overdue", "2026-09-02"),
                workout("consumed-future", "2026-10-01"),
            ),
            setOf(ConsumedWorkout("program", "consumed-future")),
        )

        assertEquals(listOf("first-overdue", "second-overdue"), available.map { it.workoutInstanceId })
    }
}
