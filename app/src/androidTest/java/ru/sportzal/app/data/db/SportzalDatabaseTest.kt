package ru.sportzal.app.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.sportzal.app.data.repository.RoomSportzalRepository
import ru.sportzal.app.domain.WorkoutService
import ru.sportzal.app.model.BlockDocument
import ru.sportzal.app.model.CancelEmptyResult
import ru.sportzal.app.model.CompletionStatus
import ru.sportzal.app.model.EquipmentDocument
import ru.sportzal.app.model.EditSetCommand
import ru.sportzal.app.model.ExerciseDocument
import ru.sportzal.app.model.ImportResult
import ru.sportzal.app.model.PlannedSetDocument
import ru.sportzal.app.model.PlannedWorkoutDocument
import ru.sportzal.app.model.ProgramDocument
import ru.sportzal.app.model.SaveSetCommand
import ru.sportzal.app.model.SaveSetResult
import ru.sportzal.app.model.SkipSetCommand
import ru.sportzal.app.model.StrictJson

@RunWith(AndroidJUnit4::class)
class SportzalDatabaseTest {
    private lateinit var database: SportzalDatabase
    private lateinit var repository: RoomSportzalRepository
    private var clock = 0
    private var ids = 0

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            SportzalDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = RoomSportzalRepository(database, { "2026-09-07T00:00:${clock++.toString().padStart(2, '0')}Z" }, { "workout-${ids++}" })
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun sameProgramKeyAndHashIsNoOpButDifferentHashConflictsAndJsonIsImmutable() {
        runBlocking {
            val program = program()
            val json = StrictJson.encodeToString(program)
            assertEquals(ImportResult.Imported, repository.importProgram(program, json, "hash"))
            assertEquals(ImportResult.NoOp, repository.importProgram(program, "different-json", "hash"))
            assertEquals(ImportResult.Conflict, repository.importProgram(program, json, "other-hash"))
            assertEquals(json, database.dao().program("program", 1)?.canonicalJson)
        }
    }

    @Test
    fun programEquipmentIndexAndActivePointerImportTogether() {
        runBlocking {
            import(program())
            assertNotNull(database.dao().program("program", 1))
            assertNotNull(database.dao().equipment("equipment"))
            assertEquals("program", repository.observeTodayState().first().activeProgramId)
        }
    }

    @Test
    fun maximumOneActiveWorkout() {
        runBlocking {
            import(program())
            repository.startWorkout("program", 1, "session")
            assertThrows(IllegalStateException::class.java) {
                runBlocking { repository.startWorkout("program", 1, "session") }
            }
        }
    }

    @Test
    fun recreatedWorkoutServiceResumesTheSameWorkoutId() {
        runBlocking {
            import(program())
            val original = WorkoutService(repository).start("program", 1, "session")
            val afterRecreation = WorkoutService(repository).start("program", 1, "session")
            assertEquals(original, afterRecreation)
            assertEquals(1, database.dao().workouts().count { it.completionStatus == "active" })
        }
    }

    @Test
    fun workoutServiceFinishDelegatesAndEndsActiveLifecycle() {
        runBlocking {
            import(program())
            val service = WorkoutService(repository)
            val id = service.start("program", 1, "session")
            assertEquals(CompletionStatus.ENDED_EARLY, service.finish(id))
            assertNull(repository.observeActiveWorkout().first())
            assertEquals("ended_early", database.dao().workout(id)?.completionStatus)
        }
    }

    @Test
    fun consumedWorkoutInstanceIsUniqueAcrossProgramVersions() {
        runBlocking {
            import(program())
            val first = repository.startWorkout("program", 1, "session")
            repository.finishWorkout(first)
            import(program(version = 2))
            assertThrows(Exception::class.java) {
                runBlocking { repository.startWorkout("program", 2, "session") }
            }
        }
    }

    @Test
    fun emptyCancellationDeletesWorkoutAndDraftsAndAllowsRestart() {
        runBlocking {
            import(program())
            val id = repository.startWorkout("program", 1, "session")
            database.dao().upsertDraft(draft(id))
            assertEquals(CancelEmptyResult.Cancelled, repository.cancelEmptyWorkout(id))
            assertNull(database.dao().workout(id))
            assertEquals(0, database.dao().draftCount(id))
            assertNotEquals(id, repository.startWorkout("program", 1, "session"))
        }
    }

    @Test
    fun finishedEmptyWorkoutCannotBeCancelled() {
        runBlocking {
            import(program())
            val id = repository.startWorkout("program", 1, "session")
            assertEquals(CompletionStatus.ENDED_EARLY, repository.finishWorkout(id))
            val finishedAt = database.dao().workout(id)?.finishedAt

            assertEquals(CancelEmptyResult.NotActive, repository.cancelEmptyWorkout(id))
            assertNotNull(database.dao().workout(id))
            assertEquals(finishedAt, database.dao().workout(id)?.finishedAt)
        }
    }

    @Test
    fun cancellationAfterSetOrSkipIsRejected() {
        runBlocking {
            import(program())
            val setWorkout = repository.startWorkout("program", 1, "session")
            repository.saveSet(setCommand("set", setWorkout, 1))
            assertEquals(CancelEmptyResult.HasFacts, repository.cancelEmptyWorkout(setWorkout))
            repository.finishWorkout(setWorkout)
            import(program(id = "other"))
            val skipWorkout = repository.startWorkout("other", 1, "session")
            repository.skipSet(SkipSetCommand(skipWorkout, "exercise", 1, "now"))
            assertEquals(CancelEmptyResult.HasFacts, repository.cancelEmptyWorkout(skipWorkout))
        }
    }

    @Test
    fun sequenceAndPlannedSlotConstraintsPreventDuplicateFacts() {
        runBlocking {
            import(program())
            val id = repository.startWorkout("program", 1, "session")
            assertTrue(repository.saveSet(setCommand("one", id, 1)) is SaveSetResult.Saved)
            assertTrue(repository.saveSet(setCommand("two", id, 1)) is SaveSetResult.Conflict)
            assertThrows(Exception::class.java) {
                runBlocking { database.dao().insertSet(entity("three", id, 1, null)) }
            }
        }
    }

    @Test
    fun plannedSlotCannotContainBothSetAndSkip() {
        runBlocking {
            import(program())
            val id = repository.startWorkout("program", 1, "session")
            repository.skipSet(SkipSetCommand(id, "exercise", 1, "now"))
            assertTrue(repository.saveSet(setCommand("set", id, 1)) is SaveSetResult.Conflict)
        }
    }

    @Test
    fun factsMustReferenceExercisesAndPlannedSlotsInImmutableSnapshot() {
        runBlocking {
            import(program())
            val id = repository.startWorkout("program", 1, "session")

            assertThrows(IllegalStateException::class.java) {
                runBlocking { repository.saveSet(setCommand("unknown-exercise", id, 1, exerciseId = "unknown")) }
            }
            assertThrows(IllegalStateException::class.java) {
                runBlocking { repository.saveSet(setCommand("unknown-slot", id, 2)) }
            }
            assertThrows(IllegalStateException::class.java) {
                runBlocking { repository.skipSet(SkipSetCommand(id, "exercise", 2, "now")) }
            }
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { repository.saveSet(setCommand("wrong-type", id, 1, setType = "warmup")) }
            }

            assertTrue(repository.saveSet(setCommand("extra", id, null)) is SaveSetResult.Saved)
        }
    }

    @Test
    fun saveRetryIsIdempotentAndSuccessfulSaveRemovesDraft() {
        runBlocking {
            import(program())
            val id = repository.startWorkout("program", 1, "session")
            database.dao().upsertDraft(draft(id))
            val first = repository.saveSet(setCommand("set", id, 1)) as SaveSetResult.Saved
            val retry = repository.saveSet(setCommand("set", id, 1)) as SaveSetResult.Saved
            assertEquals(first.sequenceNo, retry.sequenceNo)
            assertTrue(retry.idempotent)
            assertEquals(0, database.dao().draftCount(id))
        }
    }

    @Test
    fun draftDoesNotCountAsFactOrCompletion() {
        runBlocking {
            import(program())
            val id = repository.startWorkout("program", 1, "session")
            database.dao().upsertDraft(draft(id))
            assertEquals(CompletionStatus.ENDED_EARLY, repository.finishWorkout(id))
        }
    }

    @Test
    fun completionRequiresEveryPlannedSlotAndNoSkipsWhileExtraSetsDoNotFillSlots() {
        runBlocking {
            import(program(sets = 2))
            val zero = repository.startWorkout("program", 1, "session")
            assertEquals(CompletionStatus.ENDED_EARLY, repository.finishWorkout(zero))

            import(program(id = "partial", sets = 3))
            val partial = repository.startWorkout("partial", 1, "session")
            repository.saveSet(setCommand("p1", partial, 1))
            repository.saveSet(setCommand("p2", partial, 2))
            assertEquals(CompletionStatus.ENDED_EARLY, repository.finishWorkout(partial))

            import(program(id = "complete", sets = 2))
            val complete = repository.startWorkout("complete", 1, "session")
            repository.saveSet(setCommand("c1", complete, 1))
            repository.saveSet(setCommand("c2", complete, 2))
            assertEquals(CompletionStatus.COMPLETED, repository.finishWorkout(complete))

            import(program(id = "extra", sets = 2))
            val extra = repository.startWorkout("extra", 1, "session")
            repository.saveSet(setCommand("e1", extra, 1))
            repository.saveSet(setCommand("e2", extra, null))
            assertEquals(CompletionStatus.ENDED_EARLY, repository.finishWorkout(extra))
        }
    }

    @Test
    fun explicitSkipAlwaysEndsEarly() {
        runBlocking {
            import(program())
            val id = repository.startWorkout("program", 1, "session")
            repository.skipSet(SkipSetCommand(id, "exercise", 1, "now"))
            assertEquals(CompletionStatus.ENDED_EARLY, repository.finishWorkout(id))
        }
    }

    @Test
    fun normalLoggingAndRepeatedFinishRequireActiveWorkout() {
        runBlocking {
            import(program())
            val id = repository.startWorkout("program", 1, "session")
            repository.finishWorkout(id)
            val finishedAt = database.dao().workout(id)?.finishedAt
            assertThrows(IllegalStateException::class.java) { runBlocking { repository.finishWorkout(id) } }
            assertThrows(IllegalStateException::class.java) { runBlocking { repository.saveSet(setCommand("late", id, 1)) } }
            assertThrows(IllegalStateException::class.java) { runBlocking { repository.skipSet(SkipSetCommand(id, "exercise", 1, "now")) } }
            assertEquals(finishedAt, database.dao().workout(id)?.finishedAt)
        }
    }

    @Test
    fun deletingSetFromFinishedWorkoutRecalculatesCompletionTransactionally() {
        runBlocking {
            import(program())
            val id = repository.startWorkout("program", 1, "session")
            repository.saveSet(setCommand("set", id, 1))
            assertEquals(CompletionStatus.COMPLETED, repository.finishWorkout(id))
            repository.deleteSet("set")
            assertEquals("ended_early", database.dao().workout(id)?.completionStatus)
        }
    }

    @Test
    fun editDeleteSkipAndRestorePreserveFactualInvariants() = runBlocking {
        import(program(sets = 3))
        val id = repository.startWorkout("program", 1, "session")
        repository.saveSet(setCommand("one", id, 1))
        repository.saveSet(setCommand("two", id, 2))
        val before = database.dao().set("one")!!
        repository.editSet(EditSetCommand("one", 12.5, 7, 3, "edited",
            listOf("technique_changed", "discomfort"), " note "))
        val edited = database.dao().set("one")!!
        assertEquals(before.copy(weightKg = 12.5, reps = 7, rir = 3, editedAt = "edited",
            deviationsJson = "[\"technique_changed\",\"discomfort\"]", note = "note"), edited)

        repository.deleteSet("one")
        assertEquals(listOf(2), database.dao().sets(id).map { it.sequenceNo })
        assertTrue(repository.saveSet(setCommand("replacement", id, 1)) is SaveSetResult.Saved)
        assertEquals(3, database.dao().set("replacement")!!.sequenceNo)

        database.dao().upsertDraft(DraftEntity(id, "exercise", 3, "1", "1", null, null, "now"))
        repository.skipSet(SkipSetCommand(id, "exercise", 3, "recorded", "equipment_busy", "later"))
        assertEquals(0, database.dao().drafts(id).count { it.plannedSetNo == 3 })
        assertTrue(repository.saveSet(setCommand("blocked", id, 3)) is SaveSetResult.Conflict)
        assertThrows(Exception::class.java) {
            runBlocking { repository.skipSet(SkipSetCommand(id, "exercise", 3, "again")) }
        }
        repository.restoreSkippedSet(id, "exercise", 3)
        assertNull(database.dao().skipped(id, "exercise", 3))
        assertTrue(repository.saveSet(setCommand("restored", id, 3)) is SaveSetResult.Saved)
    }

    @Test
    fun programAndEquipmentUpdatesDoNotMutateWorkoutSnapshots() {
        runBlocking {
            import(program())
            val id = repository.startWorkout("program", 1, "session")
            val before = database.dao().workout(id)!!
            import(program(version = 2, equipmentName = "changed"))
            val after = database.dao().workout(id)!!
            assertEquals(before.planSnapshotJson, after.planSnapshotJson)
            assertEquals(before.equipmentAtStartJson, after.equipmentAtStartJson)
        }
    }

    @Test
    fun equipmentPatchPreservesPhotoAndAbsentFieldsButNullClearsAndValueUpdates() {
        runBlocking {
            database.dao().insertEquipment(EquipmentEntity("equipment", "old", null, 2.5, "[1.0]", "old notes", "/photo", "old"))
            val absent = program()
            val absentJson = withoutEquipmentKeys(StrictJson.encodeToString(absent), "weight_step_kg", "available_weights_kg", "notes")
            repository.importProgram(absent, absentJson, "one")
            var equipment = database.dao().equipment("equipment")!!
            assertEquals("/photo", equipment.photoPath)
            assertEquals(2.5, equipment.weightStepKg!!, 0.0)
            assertEquals("old notes", equipment.notes)

            val clear = program(version = 2, weightStep = null, notes = null)
            repository.importProgram(clear, StrictJson.encodeToString(clear), "two")
            equipment = database.dao().equipment("equipment")!!
            assertNull(equipment.weightStepKg)
            assertNull(equipment.notes)
            assertEquals("/photo", equipment.photoPath)

            val update = program(version = 3, weightStep = 5.0, notes = "new")
            repository.importProgram(update, StrictJson.encodeToString(update), "three")
            equipment = database.dao().equipment("equipment")!!
            assertEquals(5.0, equipment.weightStepKg!!, 0.0)
            assertEquals("new", equipment.notes)
        }
    }

    private suspend fun import(value: ProgramDocument) {
        val json = StrictJson.encodeToString(value)
        assertEquals(ImportResult.Imported, repository.importProgram(value, json, "${value.programId}-${value.programVersion}"))
    }

    private fun program(
        id: String = "program",
        version: Int = 1,
        sets: Int = 1,
        equipmentName: String = "machine",
        weightStep: Double? = 2.5,
        notes: String? = "notes",
    ) = ProgramDocument(
        "sportzal.program", 1, id, version, "2026-09-07T00:00:00Z",
        listOf(EquipmentDocument("equipment", equipmentName, null, weightStep, listOf(1.0, 2.0), notes)),
        listOf(PlannedWorkoutDocument(
            "session", "template", "Workout", "2026-09-07",
            listOf(BlockDocument(
                "block", "Block", "straight",
                listOf(ExerciseDocument(
                    "exercise", "exercise-id", "Exercise", "equipment", null,
                    "machine_display", "bilateral", 1, "none",
                    (1..sets).map { PlannedSetDocument(it, "work", 1.0, 1, 1, null, 0) },
                )),
            )),
        )),
    )

    private fun setCommand(
        setId: String,
        workoutId: String,
        planned: Int?,
        exerciseId: String = "exercise",
        setType: String = "work",
    ) = SaveSetCommand(
        setId, workoutId, exerciseId, planned, setType, "exercise-id", "Exercise",
        loadBasisActual = "machine_display", sideActual = "bilateral", weightKg = 1.0,
        reps = 1, completedAt = "now",
    )

    private fun entity(setId: String, workoutId: String, sequence: Int, planned: Int?) = SetResultEntity(
        setId, workoutId, sequence, "exercise", planned, "work", "exercise-id", "Exercise",
        null, null, null, "machine_display", "bilateral", 1.0, 1, null, "now",
        null, null, null, "[]", null,
    )

    private fun draft(workoutId: String) = DraftEntity(workoutId, "exercise", 1, "1", "1", null, null, "now")

    private fun withoutEquipmentKeys(json: String, vararg keys: String): String {
        val root = StrictJson.parseToJsonElement(json).jsonObject.toMutableMap()
        val equipment = root.getValue("equipment_upserts").jsonArray.single().jsonObject.toMutableMap()
        keys.forEach(equipment::remove)
        root["equipment_upserts"] = kotlinx.serialization.json.JsonArray(listOf(JsonObject(equipment)))
        return JsonObject(root).toString()
    }
}
