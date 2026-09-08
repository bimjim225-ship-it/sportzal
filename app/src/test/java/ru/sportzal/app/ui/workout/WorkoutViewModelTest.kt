package ru.sportzal.app.ui.workout

import java.lang.reflect.Proxy
import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.yield
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resume
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    @Test fun `required RIR is unanswered until an explicit choice and maps choices to facts`() = runBlocking {
        suspend fun commandFor(rir: Int?): SaveSetCommand {
            val commands = mutableListOf<SaveSetCommand>()
            val details = details(exercise(rirCapture = "all_work_sets"))
            val vm = WorkoutViewModel(repository({ details }) { command ->
                commands += command
                SaveSetResult.Saved(command.setResultId, 1, false)
            }, fixedClock())
            vm.open("workout")
            assertFalse(vm.state.value.blocks.single().cards.single().draft!!.rirAnswered)
            vm.saveSet("instance", 1)
            assertTrue(commands.isEmpty())
            vm.updateDraft("instance", 100.0, 5, rir, rirAnswered = true)
            vm.saveSet("instance", 1)
            return commands.single()
        }

        assertEquals(2, commandFor(2).rir)
        assertEquals(4, commandFor(4).rir)
        assertNull(commandFor(null).rir)
    }

    @Test fun `resolved equipment-at-start setup drives slots prefill display and factual save`() = runBlocking {
        val exercise = exercise(setup = null, sets = listOf(
            PlannedSetDocument(1, "work", 100.0, 5, 5, null, 120),
            PlannedSetDocument(2, "work", 100.0, 5, 5, null, 120),
        ))
        val equipment = listOf(ru.sportzal.app.model.EquipmentDocument("rack", "Rack", "Сиденье 5"))
        var current = details(exercise, equipment)
        val commands = mutableListOf<SaveSetCommand>()
        val vm = WorkoutViewModel(repository({ current }) { command ->
            commands += command
            current = current.copy(sets = listOf(entity(command)))
            SaveSetResult.Saved(command.setResultId, 1, false)
        }, fixedClock())
        vm.open("workout")
        vm.updateDraft("instance", 97.5, 7, null, false)
        vm.saveSet("instance", 1)

        val card = vm.state.value.blocks.single().cards.single()
        assertEquals("Сиденье 5", card.currentSlot!!.plannedContext.setup)
        assertEquals("Сиденье 5", card.draft!!.context.setup)
        assertEquals(97.5, card.draft.weightKg!!, 0.0)
        assertEquals(7, card.draft.reps)
        assertEquals("rack", commands.single().equipmentIdActual)
        assertEquals("Rack", commands.single().equipmentNameActual)
        assertEquals("Сиденье 5", commands.single().setupActual)
    }

    @Test fun `save in flight survives ticks rejects double callback and stale slot callback`() = runBlocking {
        val exercise = exercise(sets = listOf(
            PlannedSetDocument(1, "work", 100.0, 5, 5, null, 120),
            PlannedSetDocument(2, "work", 100.0, 5, 5, null, 60),
        ))
        var current = details(exercise)
        val commands = mutableListOf<SaveSetCommand>()
        lateinit var continuation: Continuation<SaveSetResult>
        val repository = Proxy.newProxyInstance(SportzalRepository::class.java.classLoader,
            arrayOf(SportzalRepository::class.java)) { _, method, args -> when (method.name) {
                "workoutDetails" -> current
                "saveDraft" -> Unit
                "saveSet" -> {
                    commands += args!![0] as SaveSetCommand
                    @Suppress("UNCHECKED_CAST")
                    continuation = args.last() as Continuation<SaveSetResult>
                    COROUTINE_SUSPENDED
                }
                else -> error("Unexpected ${method.name}")
            } } as SportzalRepository
        val vm = WorkoutViewModel(repository, fixedClock())
        vm.open("workout")
        val first = async(Dispatchers.Default) { vm.saveSet("instance", 1) }
        while (commands.isEmpty()) yield()
        vm.tick()
        assertTrue(vm.state.value.saving)
        val second = async(Dispatchers.Default) { vm.saveSet("instance", 1) }
        yield()
        assertEquals(1, commands.size)
        current = current.copy(sets = listOf(entity(commands.single())))
        continuation.resume(SaveSetResult.Saved(commands.single().setResultId, 1, false))
        first.await(); second.await()
        assertEquals(2, vm.state.value.blocks.single().cards.single().currentSlot!!.plannedSetNo)
        assertEquals(120, vm.state.value.blocks.single().cards.single().restReferenceSec)
        vm.saveSet("instance", 1)
        assertEquals(1, commands.size)
    }

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
        vm.onForegroundReturn()
        assertEquals(listOf("a-never", "a-started"), vm.state.value.blocks[0].cards.map { it.exercise.exerciseInstanceId })
    }

    @Test fun `exercise setup overrides equipment-at-start setup in saved fact`() = runBlocking {
        val equipment = listOf(ru.sportzal.app.model.EquipmentDocument("rack", "Rack", "Сиденье 5"))
        val details = details(exercise(setup = "Сиденье 3"), equipment)
        var command: SaveSetCommand? = null
        val vm = WorkoutViewModel(repository({ details }) {
            command = it; SaveSetResult.Saved(it.setResultId, 1, false)
        }, fixedClock())
        vm.open("workout")
        vm.saveSet("instance", 1)
        assertEquals("Сиденье 3", command!!.setupActual)
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
        vm.saveSet("instance", 1)
        vm.saveSet("instance", 1)
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

    private fun exercise(
        setup: String? = null,
        rirCapture: String = "none",
        sets: List<PlannedSetDocument> = listOf(PlannedSetDocument(1, "work", 100.0, 5, 5, null, 120)),
    ) = ExerciseDocument("instance", "squat", "Squat", "rack", setup, "external", "bilateral", 1,
        rirCapture, sets)

    private fun details(exercise: ExerciseDocument, equipment: List<ru.sportzal.app.model.EquipmentDocument> = emptyList()) =
        WorkoutDetails(WorkoutRuntime("workout", StrictJson.encodeToString(PlannedWorkoutDocument("planned", "template", "Workout",
            "2026-09-08", listOf(BlockDocument("block", "Straight", "straight", listOf(exercise))))),
            StrictJson.encodeToString(equipment)), emptyList(), emptyList())

    private fun repository(details: () -> WorkoutDetails, save: suspend (SaveSetCommand) -> SaveSetResult) =
        Proxy.newProxyInstance(SportzalRepository::class.java.classLoader, arrayOf(SportzalRepository::class.java)) { _, method, args ->
            when (method.name) {
                "workoutDetails" -> details()
                "saveDraft" -> Unit
                "saveSet" -> runBlocking { save(args!![0] as SaveSetCommand) }
                else -> error("Unexpected ${method.name}")
            }
        } as SportzalRepository

    private fun entity(command: SaveSetCommand) = SetResultEntity(command.setResultId, command.workoutId, 1,
        command.exerciseInstanceId, command.plannedSetNo, command.setType, command.exerciseIdActual,
        command.titleActual, command.equipmentIdActual, command.equipmentNameActual, command.setupActual,
        command.loadBasisActual, command.sideActual, command.weightKg, command.reps, command.rir, command.completedAt,
        command.bootId, command.elapsedRealtimeMs, null, "[]", null)

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
