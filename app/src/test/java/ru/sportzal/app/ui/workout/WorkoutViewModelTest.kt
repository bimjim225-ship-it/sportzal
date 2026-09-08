package ru.sportzal.app.ui.workout

import java.lang.reflect.Proxy
import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import ru.sportzal.app.data.repository.SportzalRepository
import ru.sportzal.app.data.db.SetResultEntity
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
    @Test fun `resume sorts independently inside rotation blocks and preserves block order`() = runBlocking {
        fun exercise(id: String, order: Int) = ExerciseDocument(id, id, id, null, null, "external", "bilateral", order,
            "none", listOf(
                PlannedSetDocument(1, "work", 10.0, 1, 1, null, 60),
                PlannedSetDocument(2, "work", 10.0, 1, 1, null, 60),
            ))
        val aStarted = exercise("a-started", 1)
        val aNever = exercise("a-never", 2)
        val straight = exercise("straight", 1)
        val cStarted = exercise("c-started", 1)
        val cNever = exercise("c-never", 2)
        val plan = PlannedWorkoutDocument("planned", "template", "Workout", "2026-09-08", listOf(
            BlockDocument("a", "A", "rotation", listOf(aStarted, aNever)),
            BlockDocument("b", "B", "straight", listOf(straight)),
            BlockDocument("c", "C", "rotation", listOf(cStarted, cNever)),
        ))
        val details = WorkoutDetails(WorkoutRuntime("workout", StrictJson.encodeToString(plan), "[]"),
            listOf(saved("a-started", 1), saved("c-started", 2)), emptyList())
        val repository = repository(details)
        val vm = WorkoutViewModel(repository, fixedClock())

        vm.open("workout")

        assertEquals(listOf("a", "b", "c"), vm.state.value.blocks.map { it.blockId })
        assertEquals(listOf("a-never", "a-started"), vm.state.value.blocks[0].cards.map { it.exercise.exerciseInstanceId })
        assertEquals(listOf("straight"), vm.state.value.blocks[1].cards.map { it.exercise.exerciseInstanceId })
        assertEquals(listOf("c-never", "c-started"), vm.state.value.blocks[2].cards.map { it.exercise.exerciseInstanceId })
        vm.onCommittedSkipOrDelete()
        assertEquals(listOf("a", "b", "c"), vm.state.value.blocks.map { it.blockId })
    }

    @Test fun `retry keeps id timestamp and clock anchor`() = runBlocking {
        val plan = PlannedWorkoutDocument("planned", "template", "Workout", "2026-09-08", listOf(
            BlockDocument("block", "Straight", "straight", listOf(
                ExerciseDocument("instance", "squat", "Squat", null, null, "external", "bilateral", 1, "none",
                    listOf(PlannedSetDocument(1, "work", 100.0, 5, 5, null, 120)))))))
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

    private fun fixedClock() = object : ClockProvider {
        override fun wallNow() = Instant.parse("2026-09-08T12:00:00Z")
        override fun elapsedRealtimeMs() = 42L
        override fun bootIdOrNull() = "boot"
    }

    private fun repository(details: WorkoutDetails) = Proxy.newProxyInstance(
        SportzalRepository::class.java.classLoader, arrayOf(SportzalRepository::class.java),
    ) { _, method, _ -> when (method.name) {
        "workoutDetails" -> details
        else -> error("Unexpected ${method.name}")
    } } as SportzalRepository

    private fun saved(exerciseId: String, sequence: Int) = SetResultEntity(
        "set-$exerciseId", "workout", sequence, exerciseId, 1, "work", exerciseId, exerciseId,
        null, null, null, "external", "bilateral", 10.0, 1, null,
        "2026-09-08T11:59:00Z", "boot", 0L, null, "[]", null,
    )
}
