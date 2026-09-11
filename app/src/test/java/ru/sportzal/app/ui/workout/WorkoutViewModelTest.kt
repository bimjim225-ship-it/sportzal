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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.sportzal.app.data.repository.SportzalRepository
import ru.sportzal.app.data.db.SetResultEntity
import ru.sportzal.app.data.db.SkippedSetEntity
import ru.sportzal.app.data.db.DraftEntity
import ru.sportzal.app.domain.ActualContext
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
    @Test fun finishingFullyCompletedWorkoutProducesCompleted() = runBlocking {
        val planned = exercise(sets = listOf(PlannedSetDocument(1, "work", 100.0, 5, 5, null, 60)))
        var current = details(planned).copy(sets = listOf(saved("instance", 1)))
        var finished = false
        val repository = Proxy.newProxyInstance(SportzalRepository::class.java.classLoader,
            arrayOf(SportzalRepository::class.java)) { _, method, _ -> when (method.name) {
                "workoutDetails" -> current
                "finishWorkout" -> { finished = true
                    current = current.copy(runtime = current.runtime.copy(finishedAt = "2026-09-08T12:00:00Z",
                        completionStatus = "completed"))
                    ru.sportzal.app.model.CompletionStatus.COMPLETED }
                else -> error("Unexpected ${method.name}")
            } } as SportzalRepository
        val vm = WorkoutViewModel(repository, fixedClock())
        vm.open("workout"); vm.requestFinish()
        assertTrue(finished)
        assertFalse(vm.state.value.finishSummary!!.endedEarly)
        assertEquals(1, vm.state.value.finishSummary!!.workSetCount)
    }

    @Test fun finishWithUnresolvedSlotsRequiresConfirmationAndCancelLeavesWorkoutActive() = runBlocking {
        val vm = WorkoutViewModel(repository(details(exercise())), fixedClock())
        vm.open("workout"); vm.requestFinish()
        assertTrue(vm.state.value.finishConfirmation)
        vm.cancelFinish()
        assertFalse(vm.state.value.finishConfirmation)
        assertNull(vm.state.value.finishSummary)
    }

    @Test fun requestConfirmationThenConfirmFinishesEndedEarlyWithoutSyntheticSkips() = runBlocking {
        var current = details(exercise())
        var finishCalls = 0
        val repository = Proxy.newProxyInstance(SportzalRepository::class.java.classLoader,
            arrayOf(SportzalRepository::class.java)) { _, method, _ -> when (method.name) {
                "workoutDetails" -> current
                "finishWorkout" -> {
                    finishCalls++
                    current = current.copy(runtime = current.runtime.copy(
                        finishedAt = "2026-09-08T12:30:00Z", completionStatus = "ended_early"))
                    ru.sportzal.app.model.CompletionStatus.ENDED_EARLY
                }
                else -> error("Unexpected ${method.name}")
            } } as SportzalRepository
        val vm = WorkoutViewModel(repository, fixedClock())
        vm.open("workout")
        vm.requestFinish()
        assertTrue(vm.state.value.finishConfirmation)
        assertTrue(vm.confirmFinish())
        assertTrue(vm.state.value.finishSummary!!.endedEarly)
        assertTrue(current.skippedSets.isEmpty())
        assertEquals(1, finishCalls)
    }

    @Test fun `draft actual context is restored and equipment changes clear only incompatible weight`() = runBlocking {
        val context = ActualContext("squat", "machine-b", "Сиденье 5", "external", "bilateral", "Machine B")
        val draft = DraftEntity("workout", "instance", 1, "97.5", "7", null,
            StrictJson.encodeToString(context), "2026-09-08T12:00:00Z")
        var persisted = draft
        val repository = Proxy.newProxyInstance(SportzalRepository::class.java.classLoader,
            arrayOf(SportzalRepository::class.java)) { _, method, args -> when (method.name) {
                "workoutDetails" -> details(exercise()).copy(drafts = listOf(persisted))
                "saveDraft" -> persisted = args!![0] as DraftEntity
                else -> error("Unexpected ${method.name}")
            } } as SportzalRepository
        val vm = WorkoutViewModel(repository, fixedClock())
        vm.open("workout")
        assertEquals(context, vm.state.value.blocks.single().cards.single().draft!!.context)

        val setupOnly = context.copy(setup = "Сиденье 6")
        assertTrue(vm.updateActualContext("instance", setupOnly))
        assertEquals(97.5, vm.state.value.blocks.single().cards.single().draft!!.weightKg!!, 0.0)
        val changed = setupOnly.copy(equipmentId = "machine-c", equipmentName = "Machine C")
        assertTrue(vm.updateActualContext("instance", changed))
        assertNull(vm.state.value.blocks.single().cards.single().draft!!.weightKg)

        vm.open("workout")
        assertEquals(changed, vm.state.value.blocks.single().cards.single().draft!!.context)
    }

    @Test fun `extra set uses null planned slot next sequence and does not consume planned slot`() = runBlocking {
        var current = details(exercise())
        var command: SaveSetCommand? = null
        val repository = repository({ current }) { value ->
            command = value
            current = current.copy(sets = listOf(entity(value).copy(sequenceNo = 4)))
            SaveSetResult.Saved(value.setResultId, 4, false)
        }
        val vm = WorkoutViewModel(repository, fixedClock()) { "extra-id" }
        vm.open("workout")
        assertTrue(vm.saveExtraSet("instance", "work", 110.0, 4, null, "bonus"))
        assertNull(command!!.plannedSetNo)
        assertEquals("extra-id", command!!.setResultId)
        val card = vm.state.value.blocks.single().cards.single()
        assertEquals(1, card.currentSlot!!.plannedSetNo)
        assertEquals(4, card.saved.single().sequenceNo)
    }

    @Test fun `saved substitution becomes the next planned slot actual context`() = runBlocking {
        val exercise = exercise(sets = listOf(
            PlannedSetDocument(1, "work", 100.0, 5, 5, null, 60),
            PlannedSetDocument(2, "work", 100.0, 5, 5, null, 60)))
        var current = details(exercise, listOf(
            ru.sportzal.app.model.EquipmentDocument("rack", "Rack", null),
            ru.sportzal.app.model.EquipmentDocument("machine-b", "Machine B", "Сиденье 5")))
        val commands = mutableListOf<SaveSetCommand>()
        val vm = WorkoutViewModel(repository({ current }) { command ->
            commands += command
            current = current.copy(sets = current.sets + entity(command).copy(sequenceNo = current.sets.size + 1))
            SaveSetResult.Saved(command.setResultId, current.sets.size, false)
        }, fixedClock())
        vm.open("workout")
        vm.updateActualContext("instance", ActualContext("squat", "machine-b", "Сиденье 5", "external",
            "bilateral", "Machine B"))
        assertNull(vm.state.value.blocks.single().cards.single().draft!!.weightKg)
        vm.updateDraft("instance", 97.5, 7, null, false)
        vm.saveSet("instance", 1)
        assertEquals("machine-b", commands.single().equipmentIdActual)
        assertEquals("Machine B", commands.single().equipmentNameActual)
        assertEquals("Сиденье 5", commands.single().setupActual)
        val next = vm.state.value.blocks.single().cards.single()
        assertEquals(2, next.currentSlot!!.plannedSetNo)
        assertEquals("machine-b", next.draft!!.context.equipmentId)
        assertEquals("Сиденье 5", next.draft.context.setup)
    }

    @Test fun `overlapping focus and overlay owners defer committed rotation until final release`() = runBlocking {
        fun rotationExercise(id: String, order: Int) = ExerciseDocument(id, id, id.uppercase(), null, null,
            "external", "bilateral", order, "none",
            listOf(PlannedSetDocument(1, "work", 10.0, 1, 1, null, 60)))
        val plan = PlannedWorkoutDocument("planned", "template", "Workout", "2026-09-08", listOf(
            BlockDocument("rotation", "Rotation", "rotation", listOf(rotationExercise("a", 1), rotationExercise("b", 2)))))
        var current = WorkoutDetails(WorkoutRuntime("workout", StrictJson.encodeToString(plan), "[]"), emptyList(), emptyList())
        val repository = Proxy.newProxyInstance(SportzalRepository::class.java.classLoader,
            arrayOf(SportzalRepository::class.java)) { _, method, args -> when (method.name) {
                "workoutDetails" -> current
                "skipSet" -> {
                    val command = args!![0] as ru.sportzal.app.model.SkipSetCommand
                    current = current.copy(skippedSets = listOf(SkippedSetEntity(command.workoutId,
                        command.exerciseInstanceId, command.plannedSetNo, command.recordedAt, command.reason, command.note)))
                }
                else -> error("Unexpected ${method.name}")
            } } as SportzalRepository
        val vm = WorkoutViewModel(repository, fixedClock())
        vm.open("workout")

        vm.setInteraction("a", InteractionSource.FOCUS, true)
        vm.setInteraction("a", InteractionSource.SET_ACTIONS, true)
        vm.setInteraction("a", InteractionSource.FOCUS, false)
        assertEquals(listOf("a", "b"), vm.state.value.blocks.single().cards.map { it.exercise.exerciseInstanceId })

        assertTrue(vm.skipSet("a", 1, null, null))
        assertEquals(listOf("a", "b"), vm.state.value.blocks.single().cards.map { it.exercise.exerciseInstanceId })

        vm.setInteraction("a", InteractionSource.SET_ACTIONS, false)
        assertEquals(listOf("b", "a"), vm.state.value.blocks.single().cards.map { it.exercise.exerciseInstanceId })
    }

    @Test fun `delete skip and restore reload committed facts and unresolved slot`() = runBlocking {
        val exercise = exercise(sets = (1..3).map { PlannedSetDocument(it, "work", 100.0, 5, 5, null, 60) })
        val base = details(exercise)
        var current = base.copy(sets = listOf(saved("instance", 1)))
        val repository = Proxy.newProxyInstance(SportzalRepository::class.java.classLoader,
            arrayOf(SportzalRepository::class.java)) { _, method, args -> when (method.name) {
                "workoutDetails" -> current
                "deleteSet" -> current = current.copy(sets = emptyList())
                "skipSet" -> {
                    val command = args!![0] as ru.sportzal.app.model.SkipSetCommand
                    current = current.copy(skippedSets = listOf(SkippedSetEntity(command.workoutId,
                        command.exerciseInstanceId, command.plannedSetNo, command.recordedAt, command.reason, command.note)))
                }
                "restoreSkippedSet" -> current = current.copy(skippedSets = emptyList())
                else -> error("Unexpected ${method.name}")
            } } as SportzalRepository
        val vm = WorkoutViewModel(repository, fixedClock())
        vm.open("workout")
        assertEquals(2, vm.state.value.blocks.single().cards.single().currentSlot!!.plannedSetNo)
        vm.deleteSet("set-instance")
        assertEquals(1, vm.state.value.blocks.single().cards.single().currentSlot!!.plannedSetNo)
        assertEquals("", vm.elapsedText(null))
        vm.skipSet("instance", 1, "equipment_busy", null)
        assertEquals(2, vm.state.value.blocks.single().cards.single().currentSlot!!.plannedSetNo)
        vm.restoreSkippedSet("instance", 1)
        assertEquals(1, vm.state.value.blocks.single().cards.single().currentSlot!!.plannedSetNo)
    }

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
        vm.setInteraction("straight", InteractionSource.FOCUS, true)
        vm.setInteraction("straight", InteractionSource.FOCUS, false)
        assertTrue(vm.state.value.blocks.flatMap { it.cards }.all { it.skipped.isEmpty() })
        assertEquals(2, vm.state.value.blocks[1].cards.single().exercise.plannedSets.size)
        assertEquals(1, vm.state.value.blocks[1].cards.single().exercise.plannedOrder)
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

    @Test fun finishDurationUsesPersistedFinishedAtAndOmitsExercisesWithoutFacts() = runBlocking {
        val performed = exercise(sets = emptyList())
        val untouched = ExerciseDocument("untouched", "row", "Row", null, null, "total_external",
            "bilateral", 2, "none", listOf(PlannedSetDocument(1, "work", 20.0, 8, 8, null, 60)))
        val plan = PlannedWorkoutDocument("planned", "template", "Workout", "2026-09-08", listOf(
            BlockDocument("block", "Block", "straight", listOf(performed, untouched))))
        var current = WorkoutDetails(WorkoutRuntime("workout", StrictJson.encodeToString(plan), "[]",
            startedAt = "2026-09-08T12:00:00Z"), listOf(saved("instance", 1)), emptyList())
        val repository = Proxy.newProxyInstance(SportzalRepository::class.java.classLoader,
            arrayOf(SportzalRepository::class.java)) { _, method, _ -> when (method.name) {
                "workoutDetails" -> current
                "finishWorkout" -> {
                    current = current.copy(runtime = current.runtime.copy(
                        finishedAt = "2026-09-08T12:30:00Z", completionStatus = "ended_early"))
                    ru.sportzal.app.model.CompletionStatus.ENDED_EARLY
                }
                else -> error("Unexpected ${method.name}")
            } } as SportzalRepository
        val lateClock = object : ClockProvider {
            override fun wallNow() = Instant.parse("2026-09-08T12:45:00Z")
            override fun elapsedRealtimeMs() = 42L
            override fun bootIdOrNull() = null
        }
        val vm = WorkoutViewModel(repository, lateClock)
        vm.open("workout"); assertTrue(vm.confirmFinish())
        val summary = vm.state.value.finishSummary!!
        assertEquals(1800, summary.durationSeconds)
        assertEquals(listOf("Squat"), summary.exercises.map { it.title })
        assertTrue(summary.endedEarly)
    }

    @Test fun failedFinishKeepsWorkoutActiveAndShowsError() = runBlocking {
        val repository = Proxy.newProxyInstance(SportzalRepository::class.java.classLoader,
            arrayOf(SportzalRepository::class.java)) { _, method, _ -> when (method.name) {
                "workoutDetails" -> details(exercise())
                "finishWorkout" -> error("disk full")
                else -> error("Unexpected ${method.name}")
            } } as SportzalRepository
        val vm = WorkoutViewModel(repository, fixedClock())
        vm.open("workout")
        assertFalse(vm.confirmFinish())
        assertNull(vm.state.value.finishSummary)
        assertFalse(vm.state.value.saving)
        assertEquals("disk full", vm.state.value.error)
    }

    @Test fun finishCommitIsNotRepeatedWhenFirstSummaryReadFails() = runBlocking {
        var current = details(exercise()).copy(sets = listOf(saved("instance", 1)))
        var finishCalls = 0
        var failNextRead = false
        val repository = Proxy.newProxyInstance(SportzalRepository::class.java.classLoader,
            arrayOf(SportzalRepository::class.java)) { _, method, _ -> when (method.name) {
                "workoutDetails" -> {
                    if (failNextRead) { failNextRead = false; error("temporary read failure") }
                    current
                }
                "finishWorkout" -> {
                    finishCalls++
                    current = current.copy(runtime = current.runtime.copy(
                        finishedAt = "2026-09-08T12:30:00Z", completionStatus = "completed"))
                    failNextRead = true
                    ru.sportzal.app.model.CompletionStatus.COMPLETED
                }
                else -> error("Unexpected ${method.name}")
            } } as SportzalRepository
        val vm = WorkoutViewModel(repository, fixedClock())
        vm.open("workout")

        assertFalse(vm.confirmFinish())
        assertEquals("temporary read failure", vm.state.value.error)
        assertTrue(vm.confirmFinish())
        assertEquals(1, finishCalls)
        assertEquals(1800, vm.state.value.finishSummary!!.durationSeconds)
    }

    @Test fun restoreFinishReadsPersistedSummaryWithoutFinishingAgain() = runBlocking {
        val persisted = details(exercise()).copy(
            runtime = details(exercise()).runtime.copy(startedAt = "2026-09-08T12:00:00Z",
                finishedAt = "2026-09-08T12:30:00Z", completionStatus = "completed"),
            sets = listOf(saved("instance", 1)),
        )
        var finishCalls = 0
        val repository = Proxy.newProxyInstance(SportzalRepository::class.java.classLoader,
            arrayOf(SportzalRepository::class.java)) { _, method, _ -> when (method.name) {
                "workoutDetails" -> persisted
                "finishWorkout" -> { finishCalls++; error("must not be called") }
                else -> error("Unexpected ${method.name}")
            } } as SportzalRepository
        val vm = WorkoutViewModel(repository, fixedClock())
        assertTrue(vm.restoreFinish("workout"))
        assertEquals(1800, vm.state.value.finishSummary!!.durationSeconds)
        assertEquals(0, finishCalls)
    }

    @Test fun dismissFinishClearsOnlyFinishUiState() = runBlocking {
        var current = details(exercise()).copy(sets = listOf(saved("instance", 1)))
        var finishCalls = 0
        val repository = Proxy.newProxyInstance(SportzalRepository::class.java.classLoader,
            arrayOf(SportzalRepository::class.java)) { _, method, _ -> when (method.name) {
                "workoutDetails" -> current
                "finishWorkout" -> {
                    finishCalls++
                    current = current.copy(runtime = current.runtime.copy(
                        finishedAt = "2026-09-08T12:30:00Z", completionStatus = "completed"))
                    ru.sportzal.app.model.CompletionStatus.COMPLETED
                }
                else -> error("Unexpected ${method.name}")
            } } as SportzalRepository
        val vm = WorkoutViewModel(repository, fixedClock())
        vm.open("workout")
        assertTrue(vm.confirmFinish())
        assertNotNull(vm.state.value.finishSummary)
        val persisted = current
        vm.showError("visible error")

        vm.dismissFinish()

        assertNull(vm.state.value.finishSummary)
        assertNull(vm.state.value.error)
        assertFalse(vm.state.value.finishConfirmation)
        assertEquals(persisted, current)
        assertEquals(1, finishCalls)
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
