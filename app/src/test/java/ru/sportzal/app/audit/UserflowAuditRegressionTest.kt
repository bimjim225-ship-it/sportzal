package ru.sportzal.app.audit

import java.lang.reflect.Proxy
import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test
import ru.sportzal.app.data.db.SetResultEntity
import ru.sportzal.app.data.files.*
import ru.sportzal.app.data.repository.SportzalRepository
import ru.sportzal.app.model.*
import ru.sportzal.app.platform.ClockProvider
import ru.sportzal.app.ui.workout.WorkoutViewModel

/** Permanent regressions reproduced during the full userflow audit. */
class UserflowAuditRegressionTest {
    @Test fun nextPlanCanReferenceExistingCatalogWithoutRepeatingUpserts() = runBlocking {
        val source = javaClass.classLoader!!.getResource("program.json")!!.readText()
        val program = StrictJson.decodeFromString<ProgramDocument>(source)
        val equipmentIds = program.equipmentUpserts.map { it.equipmentId }.toSet()
        val next = StrictJson.encodeToString(program.copy(programVersion = program.programVersion + 1, equipmentUpserts = emptyList()))
        val validator = ProgramValidator()
        assertTrue("Fixture is semantically valid with existing catalog", validator.validate(next, equipmentIds) is ValidationResult.Success)
        val importer = ProgramImporter(null, validator, repo { method, _ -> if (method == "equipmentCatalog") program.equipmentUpserts.map { EquipmentCatalogItem(it.equipmentId, it.name, it.setupHint, it.weightStepKg, it.availableWeightsKg, it.notes, null) } else if (method == "programImportStatus") ProgramImportStatus.NEW else error("Unexpected $method") })
        val preview = importer.preview(next)
        assertTrue("Existing catalog must be recognized; actual=$preview", preview is ImportPreview.Valid)
    }

    @Test fun editedValuesAfterFailedSaveAreTheValuesActuallyCommitted() = runBlocking {
        var current = initialDetails()
        val commands = mutableListOf<SaveSetCommand>()
        val repository = repo { method, args -> when (method) {
            "workoutDetails" -> current
            "equipmentCatalog" -> emptyList<EquipmentCatalogItem>()
            "saveDraft" -> Unit
            "saveSet" -> {
                val command = args!![0] as SaveSetCommand
                commands += command
                if (commands.size == 1) error("simulated write failure before commit")
                current = current.copy(sets = listOf(entity(command)))
                SaveSetResult.Saved(command.setResultId, 1, false)
            }
            else -> error("Unexpected $method")
        } }
        val vm = WorkoutViewModel(repository, clock()) { "stable-set-id" }
        vm.open("workout")
        vm.saveSet("instance", 1, 100.0, 5, null, true)
        assertNotNull(vm.state.value.error)
        vm.updateDraft("instance", 105.0, 7, 2, true)
        vm.saveSet("instance", 1, 105.0, 7, 2, true)
        assertEquals("Saved weight must match corrected visible input", 105.0, current.sets.single().weightKg, 0.0)
        assertEquals(7, current.sets.single().reps)
        assertEquals(2, current.sets.single().rir)
    }

    @Test fun committedWriteFollowedByReadFailureMustRemainRecoverableInUi() = runBlocking {
        val current = initialDetails()
        var committed = false
        val repository = repo { method, _ -> when (method) {
            "workoutDetails" -> if (committed) error("simulated post-commit read failure") else current
            "equipmentCatalog" -> emptyList<EquipmentCatalogItem>()
            "saveSet" -> { committed = true; SaveSetResult.Saved("stable-set-id", 1, false) }
            else -> error("Unexpected $method")
        } }
        val vm = WorkoutViewModel(repository, clock()) { "stable-set-id" }
        vm.open("workout")
        val failure = runCatching { vm.saveSet("instance", 1, 100.0, 5, null, true) }.exceptionOrNull()
        assertTrue(committed)
        assertNull("A read failure after commit must be handled by the UI state; actual=$failure", failure)
        assertFalse(vm.state.value.saving)
        assertNotNull(vm.state.value.error)
    }

    @Test fun editedRetryReconcilesAnAlreadyCommittedAttemptWithoutDuplicatingIt() = runBlocking {
        var current = initialDetails()
        var writes = 0
        var edits = 0
        val repository = repo { method, args -> when (method) {
            "workoutDetails" -> current
            "equipmentCatalog" -> emptyList<EquipmentCatalogItem>()
            "saveDraft" -> Unit
            "saveSet" -> {
                val c = args!![0] as SaveSetCommand
                writes++
                current = current.copy(sets = listOf(entity(c)))
                error("unknown transaction acknowledgement")
            }
            "editSet" -> {
                val c = args!![0] as EditSetCommand
                edits++
                current = current.copy(sets = listOf(current.sets.single().copy(weightKg=c.weightKg, reps=c.reps, rir=c.rir, editedAt=c.editedAt)))
                Unit
            }
            else -> error("Unexpected $method")
        } }
        val vm = WorkoutViewModel(repository, clock()) { "stable-set-id" }
        vm.open("workout")
        vm.saveSet("instance", 1, 100.0, 5, null, true)
        vm.updateDraft("instance", 105.0, 7, 2, true)
        vm.saveSet("instance", 1, 105.0, 7, 2, true)
        assertEquals(1, writes)
        assertEquals(1, edits)
        assertEquals(1, current.sets.size)
        assertEquals(105.0, current.sets.single().weightKg, 0.0)
        assertEquals("2026-10-26T10:00:00Z", current.sets.single().completedAt)
        assertEquals(1, current.sets.single().sequenceNo)
    }

    @Test fun postCommitReloadRestoresTheFactAndUnlocksEditing() = runBlocking {
        var current = initialDetails()
        var failRead = false
        val repository = repo { method, args -> when (method) {
            "workoutDetails" -> if (failRead) error("temporary read failure") else current
            "equipmentCatalog" -> emptyList<EquipmentCatalogItem>()
            "saveSet" -> {
                val c = args!![0] as SaveSetCommand
                current = current.copy(sets = listOf(entity(c)))
                failRead = true
                SaveSetResult.Saved(c.setResultId, 1, false)
            }
            else -> error("Unexpected $method")
        } }
        val vm = WorkoutViewModel(repository, clock()) { "stable-set-id" }
        vm.open("workout")
        vm.saveSet("instance", 1, 100.0, 5, null, true)
        assertTrue(vm.state.value.reloadRequired)
        assertFalse(vm.state.value.saving)
        failRead = false
        assertTrue(vm.reloadCommittedState())
        assertFalse(vm.state.value.reloadRequired)
        assertNull(vm.state.value.error)
        assertEquals(1, vm.state.value.blocks.single().cards.single().saved.size)
    }

    private fun repo(handler: (String, Array<out Any?>?) -> Any?) = Proxy.newProxyInstance(
        SportzalRepository::class.java.classLoader, arrayOf(SportzalRepository::class.java)
    ) { _, method, args -> handler(method.name, args) } as SportzalRepository

    private fun clock() = object : ClockProvider {
        override fun wallNow() = Instant.parse("2026-10-26T10:00:00Z")
        override fun elapsedRealtimeMs() = 42L
        override fun bootIdOrNull() = "audit-boot"
    }

    private fun initialDetails(): WorkoutDetails {
        val exercise = ExerciseDocument("instance", "squat", "Squat", null, null, "total_external", "bilateral", 1, "none",
            listOf(PlannedSetDocument(1, "work", 100.0, 5, 8, null, 90)))
        val plan = PlannedWorkoutDocument("planned", "template", "Workout", "2026-10-26", listOf(BlockDocument("block", "Block", "straight", listOf(exercise))))
        return WorkoutDetails(WorkoutRuntime("workout", StrictJson.encodeToString(plan), "[]"), emptyList(), emptyList())
    }

    private fun entity(c: SaveSetCommand) = SetResultEntity(c.setResultId, c.workoutId, 1,
        c.exerciseInstanceId, c.plannedSetNo, c.setType, c.exerciseIdActual, c.titleActual,
        c.equipmentIdActual, c.equipmentNameActual, c.setupActual, c.loadBasisActual, c.sideActual,
        c.weightKg, c.reps, c.rir, c.completedAt, c.bootId, c.elapsedRealtimeMs, null, "[]", c.note)
}
