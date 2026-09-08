package ru.sportzal.app.domain

import java.time.LocalDate
import ru.sportzal.app.model.WorkoutRuntime

data class PlannedWorkout(
    val programId: String,
    val programVersion: Int,
    val workoutInstanceId: String,
    val title: String,
    val plannedDate: LocalDate,
    val plannedOrder: Int,
)

data class ConsumedWorkout(val programId: String, val workoutInstanceId: String)

sealed interface TodaySelection {
    data class Resume(val workout: WorkoutRuntime) : TodaySelection
    data class Ready(val workout: PlannedWorkout, val manuallySelected: Boolean = false) : TodaySelection
    data object Exhausted : TodaySelection
    data object NoProgram : TodaySelection
}

class TodaySelector {
    fun select(
        activeWorkout: WorkoutRuntime?,
        activeProgramWorkouts: List<PlannedWorkout>?,
        consumedKeys: Set<ConsumedWorkout>,
        manualWorkoutInstanceId: String? = null,
    ): TodaySelection {
        if (activeWorkout != null) return TodaySelection.Resume(activeWorkout)
        val workouts = activeProgramWorkouts ?: return TodaySelection.NoProgram
        val available = workouts.filter { ConsumedWorkout(it.programId, it.workoutInstanceId) !in consumedKeys }
        if (available.isEmpty()) return TodaySelection.Exhausted
        val manual = manualWorkoutInstanceId?.let { id -> available.find { it.workoutInstanceId == id } }
        return TodaySelection.Ready(
            workout = manual ?: available.minWith(compareBy<PlannedWorkout> { it.plannedDate }.thenBy { it.plannedOrder }),
            manuallySelected = manual != null,
        )
    }
}
