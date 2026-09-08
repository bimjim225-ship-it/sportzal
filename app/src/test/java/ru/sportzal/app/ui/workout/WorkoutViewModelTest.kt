package ru.sportzal.app.ui.workout

import java.lang.reflect.Proxy
import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import ru.sportzal.app.data.repository.SportzalRepository
import ru.sportzal.app.model.BlockDocument
import ru.sportzal.app.model.ExerciseDocument
import ru.sportzal.app.model.PlannedSetDocument
import ru.sportzal.app.model.PlannedWorkoutDocument
import ru.sportzal.app.model.SaveSetCommand
import ru.sportzal.app.model.SaveSetResult
import ru.sportzal.app.model.StrictJson
import ru.sportzal.app.model.WorkoutDetails
import ru.sportzal.app.model.WorkoutRuntime
import ru.sportzal.app.platform.ClockProvider

class WorkoutViewModelTest {
    @Test fun `retry keeps id timestamp and clock anchor`() = runBlocking {
        val plan = PlannedWorkoutDocument("planned", "template", "Workout", "2026-09-08", listOf(
            BlockDocument("block", "Straight", "straight", listOf(
                ExerciseDocument("instance", "squat", "Squat", null, null, "external", "bilateral", 1, "none",
                    listOf(PlannedSetDocument(1, "work", 100.0, 5, 5, null, 120)))))
        var attempts = 0
        val commands = mutableListOf<SaveSetCommand>()
        val details = WorkoutDetails(WorkoutRuntime("workout", StrictJson.encodeToString(plan), "[]"), emptyList(), emptyList())
        val repository = Proxy.newProxyInstance(SportzalRepository::class.java.classLoader, arrayOf(SportzalRepository::class.java)) { _, method, args ->
            when (method.name) {
                "workoutDetails" -> details
                "saveDraft" -> Unit
                "saveSet" -> {
                    commands += args!![0] as SaveSetCommand
                    if (attempts++ == 0) error("disk full") else SaveSetResult.Saved(commands.first().setResultId, 1, false)
                }
                else -> error("Unexpected ${method.name}")
            }
        } as SportzalRepository
        val clock = object : ClockProvider {
            override fun wallNow() = Instant.parse("2026-09-08T12:00:00Z")
            override fun elapsedRealtimeMs() = 42L
            override fun bootIdOrNull() = "boot"
        }
        val vm = WorkoutViewModel(repository, clock) { "stable-id" }
        vm.open("workout")
        vm.saveSet("instance")
        vm.saveSet("instance")
        assertEquals(2, commands.size)
        assertSame(commands[0], commands[1])
        assertEquals("stable-id", commands[1].setResultId)
        assertEquals("2026-09-08T12:00:00Z", commands[1].completedAt)
        assertEquals(42L, commands[1].elapsedRealtimeMs)
    }
}
