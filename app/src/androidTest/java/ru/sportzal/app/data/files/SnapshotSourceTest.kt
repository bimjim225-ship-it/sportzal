package ru.sportzal.app.data.files

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.sportzal.app.data.db.AppStateEntity
import ru.sportzal.app.data.db.EquipmentEntity
import ru.sportzal.app.data.db.ProgramEntity
import ru.sportzal.app.data.db.SportzalDatabase
import ru.sportzal.app.data.db.WorkoutEntity
import ru.sportzal.app.data.repository.RoomSportzalRepository
import ru.sportzal.app.model.BlockDocument
import ru.sportzal.app.model.EditSetCommand
import ru.sportzal.app.model.EquipmentDocument
import ru.sportzal.app.model.ExerciseDocument
import ru.sportzal.app.model.PlannedSetDocument
import ru.sportzal.app.model.PlannedWorkoutDocument
import ru.sportzal.app.model.ProgramDocument
import ru.sportzal.app.model.SaveSetCommand
import ru.sportzal.app.model.SkipSetCommand
import ru.sportzal.app.model.StrictJson

@RunWith(AndroidJUnit4::class)
class SnapshotSourceTest {
    private lateinit var database: SportzalDatabase
    private lateinit var repository: RoomSportzalRepository

    @Before fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), SportzalDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = RoomSportzalRepository(database, { "2026-09-11T12:00:00Z" }, { "generated" })
    }

    @After fun closeDatabase() = database.close()

    @Test fun latest24FinishedActiveAndFocusHaveTruthfulDeterministicScope() = runBlocking {
        insertProgram("history", 1)
        repeat(26) { index ->
            insertWorkout("finished-$index", "history", 1, "instance-$index",
                "2026-09-${(index + 1).toString().padStart(2, '0')}T10:00:00Z", "completed")
        }
        val historicalPlan = plan("active-plan", "Historical active plan")
        val historicalEquipment = listOf(EquipmentDocument("rack", "Historical rack", null))
        insertWorkout("active", "history", 1, "active-instance", "2026-09-30T10:00:00Z", "active",
            historicalPlan, historicalEquipment)

        val inside = repository.snapshotSource("finished-25")
        assertEquals((25 downTo 2).map { "finished-$it" } + "active", inside.workouts.map { it.runtime.workoutId })
        assertEquals(27, inside.totalStoredWorkouts)
        assertEquals(25, inside.workouts.size)
        assertEquals(2, inside.omittedWorkouts)
        assertEquals(1, inside.workouts.count { it.runtime.workoutId == "active" })
        val active = inside.workouts.single { it.runtime.workoutId == "active" }.runtime
        assertNull(active.finishedAt)
        assertEquals(StrictJson.encodeToString(historicalPlan), active.planSnapshotJson)
        assertEquals(StrictJson.encodeToString(historicalEquipment), active.equipmentAtStartJson)

        val outside = repository.snapshotSource("finished-0")
        assertEquals((25 downTo 2).map { "finished-$it" } + listOf("active", "finished-0"),
            outside.workouts.map { it.runtime.workoutId })
        assertEquals(26, outside.workouts.size)
        assertEquals(1, outside.omittedWorkouts)
        val exported = SnapshotExporter(repository, { "2026-09-11T12:00:00Z" }, { "id" })
            .export("finished-0")
        assertTrue(exported.json.contains("\"completed_limit\":24"))
        assertTrue(exported.json.contains("\"focus_workout_id\":\"finished-0\""))
    }

    @Test fun activeAndReferencedImmutableProgramVersionsExportWithoutFakeWorkouts() = runBlocking {
        insertProgram("program", 1, "Version one")
        insertProgram("program", 2, "Version two")
        database.dao().setAppState(AppStateEntity(activeProgramId = "program", activeProgramVersion = 2))

        var source = repository.snapshotSource(null)
        assertEquals("program" to 2, source.activeProgram!!.let { it.programId to it.programVersion })
        assertEquals(listOf(2), source.programs.map { it.programVersion })
        assertTrue(source.workouts.isEmpty())

        insertWorkout("old", "program", 1, "old-instance", "2026-09-01T10:00:00Z", "completed",
            plan("old-instance", "Historical plan"))
        source = repository.snapshotSource(null)
        assertEquals(listOf(1, 2), source.programs.map { it.programVersion })
        assertEquals("Historical plan", StrictJson.decodeFromString<PlannedWorkoutDocument>(
            source.workouts.single().runtime.planSnapshotJson).title)
    }

    @Test fun historicalEquipmentAndCorrectedFactsComeFromCommittedSnapshotState() = runBlocking {
        insertProgram("facts", 1)
        database.dao().insertEquipment(EquipmentEntity("rack", "Current rack", null, null, null, null, null, "now"))
        val historical = listOf(EquipmentDocument("rack", "Historical rack", "Seat 1"))
        insertWorkout("facts-workout", "facts", 1, "facts-instance", "2026-09-10T10:00:00Z", "active",
            equipment = historical)
        repository.saveSet(command("edited", 1))
        repository.saveSet(command("deleted", null))
        repository.editSet(EditSetCommand("edited", 12.5, 7, 2, "edited-at", emptyList(), null,
            null, null, null, "external", "bilateral"))
        repository.deleteSet("deleted")
        repository.saveSet(command("extra", null))
        repository.skipSet(SkipSetCommand("facts-workout", "exercise", 2, "skipped"))
        repository.restoreSkippedSet("facts-workout", "exercise", 2)

        val workout = repository.snapshotSource(null).workouts.single()
        assertEquals("Historical rack", StrictJson.decodeFromString<List<EquipmentDocument>>(
            workout.runtime.equipmentAtStartJson).single().name)
        assertEquals(listOf("edited", "extra"), workout.sets.map { it.setResultId })
        assertEquals("original", workout.sets.first().completedAt)
        assertEquals("edited-at", workout.sets.first().editedAt)
        assertNull(workout.sets.last().plannedSetNo)
        assertTrue(workout.skippedSets.isEmpty())
    }

    private suspend fun insertProgram(id: String, version: Int, title: String = "Plan") {
        val document = ProgramDocument("sportzal.program", 1, id, version, "2026-09-01T00:00:00Z",
            emptyList(), listOf(plan("template-instance", title)))
        database.dao().insertProgram(ProgramEntity(id, version, 1, StrictJson.encodeToString(document),
            "$id-$version", document.generatedAt, "now"))
    }

    private suspend fun insertWorkout(
        id: String, programId: String, version: Int, instanceId: String, startedAt: String, status: String,
        plan: PlannedWorkoutDocument = plan(instanceId, "Plan"),
        equipment: List<EquipmentDocument> = emptyList(),
    ) = database.dao().insertWorkout(WorkoutEntity(id, programId, version, instanceId, "template", startedAt,
        if (status == "active") null else startedAt, status, StrictJson.encodeToString(plan),
        StrictJson.encodeToString(equipment), null))

    private fun plan(instanceId: String, title: String) = PlannedWorkoutDocument(instanceId, "template", title,
        "2026-09-10", listOf(BlockDocument("block", "Block", "straight", listOf(
            ExerciseDocument("exercise", "squat", "Squat", "rack", null, "external", "bilateral", 1,
                "none", listOf(
                    PlannedSetDocument(1, "work", 10.0, 5, 5, null, 60),
                    PlannedSetDocument(2, "work", 10.0, 5, 5, null, 60),
                )),
        ))))

    private fun command(id: String, planned: Int?) = SaveSetCommand(id, "facts-workout", "exercise", planned,
        "work", "squat", "Squat", loadBasisActual = "external", sideActual = "bilateral", weightKg = 10.0,
        reps = 5, completedAt = "original")
}
