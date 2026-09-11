package ru.sportzal.app.ui.workout

import java.lang.reflect.Proxy
import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.sportzal.app.data.repository.SportzalRepository
import ru.sportzal.app.model.BlockDocument
import ru.sportzal.app.model.ExerciseDocument
import ru.sportzal.app.model.PlannedSetDocument
import ru.sportzal.app.model.PlannedWorkoutDocument
import ru.sportzal.app.model.StrictJson
import ru.sportzal.app.model.WorkoutDetails
import ru.sportzal.app.model.WorkoutRuntime
import ru.sportzal.app.platform.ClockProvider

class WorkoutNotesRegressionTest {
    @Test
    fun restoreFinishRestoresPersistedWorkoutNote() = runBlocking {
        val plan = PlannedWorkoutDocument(
            "planned",
            "template",
            "Workout",
            "2026-09-08",
            listOf(
                BlockDocument(
                    "block",
                    "Straight",
                    "straight",
                    listOf(
                        ExerciseDocument(
                            "instance",
                            "squat",
                            "Squat",
                            null,
                            null,
                            "bodyweight",
                            "bilateral",
                            1,
                            "none",
                            listOf(PlannedSetDocument(1, "work", 0.0, 5, 5, null, 60)),
                        ),
                    ),
                ),
            ),
        )
        val details = WorkoutDetails(
            WorkoutRuntime(
                workoutId = "workout",
                planSnapshotJson = StrictJson.encodeToString(plan),
                equipmentAtStartJson = "[]",
                startedAt = "2026-09-08T11:30:00Z",
                finishedAt = "2026-09-08T12:00:00Z",
                completionStatus = "completed",
                notes = "Сохранённая заметка",
            ),
            emptyList(),
            emptyList(),
        )
        val repository = Proxy.newProxyInstance(
            SportzalRepository::class.java.classLoader,
            arrayOf(SportzalRepository::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "workoutDetails" -> details
                else -> error("Unexpected ${method.name}")
            }
        } as SportzalRepository

        val vm = WorkoutViewModel(repository, fixedClock())

        assertTrue(vm.restoreFinish("workout"))
        assertEquals("Сохранённая заметка", vm.state.value.notes)
    }

    private fun fixedClock() = object : ClockProvider {
        override fun wallNow() = Instant.parse("2026-09-08T12:00:00Z")
        override fun elapsedRealtimeMs() = 42L
        override fun bootIdOrNull() = "boot"
    }
}
