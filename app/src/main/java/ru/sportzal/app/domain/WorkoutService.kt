package ru.sportzal.app.domain

import kotlinx.coroutines.flow.first
import ru.sportzal.app.data.repository.SportzalRepository
import ru.sportzal.app.model.CancelEmptyResult

class WorkoutService(private val repository: SportzalRepository) {
    suspend fun start(programId: String, programVersion: Int, workoutInstanceId: String): String {
        repository.observeActiveWorkout().first()?.let { active ->
            if (active.programId == programId && active.workoutInstanceId == workoutInstanceId) return active.workoutId
            error("Другая тренировка уже активна")
        }
        return repository.startWorkout(programId, programVersion, workoutInstanceId)
    }

    suspend fun cancelAccidentalStart(workoutId: String): CancelEmptyResult =
        repository.cancelEmptyWorkout(workoutId)
}
