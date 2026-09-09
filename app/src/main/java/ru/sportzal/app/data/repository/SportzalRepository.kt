package ru.sportzal.app.data.repository

import androidx.room.withTransaction
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import ru.sportzal.app.data.db.AppStateEntity
import ru.sportzal.app.data.db.EquipmentEntity
import ru.sportzal.app.data.db.ProgramEntity
import ru.sportzal.app.data.db.ProgramWorkoutIndexEntity
import ru.sportzal.app.data.db.SetResultEntity
import ru.sportzal.app.data.db.SkippedSetEntity
import ru.sportzal.app.data.db.SportzalDatabase
import ru.sportzal.app.data.db.WorkoutEntity
import ru.sportzal.app.model.CancelEmptyResult
import ru.sportzal.app.model.CompletionStatus
import ru.sportzal.app.model.EditSetCommand
import ru.sportzal.app.model.EquipmentDocument
import ru.sportzal.app.model.ImportResult
import ru.sportzal.app.model.ProgramDocument
import ru.sportzal.app.model.SaveSetCommand
import ru.sportzal.app.model.SaveSetResult
import ru.sportzal.app.model.SkipSetCommand
import ru.sportzal.app.model.SnapshotSource
import ru.sportzal.app.model.StrictJson
import ru.sportzal.app.model.TodayData
import ru.sportzal.app.model.WorkoutRuntime
import ru.sportzal.app.model.WorkoutDetails
import ru.sportzal.app.data.db.DraftEntity

interface SportzalRepository {
    suspend fun importProgram(validated: ProgramDocument, canonicalJson: String, canonicalHash: String): ImportResult
    fun observeTodayState(): Flow<TodayData>
    suspend fun startWorkout(programId: String, programVersion: Int, workoutInstanceId: String): String
    suspend fun cancelEmptyWorkout(workoutId: String): CancelEmptyResult
    suspend fun saveSet(command: SaveSetCommand): SaveSetResult
    suspend fun saveDraft(draft: DraftEntity)
    suspend fun workoutDetails(workoutId: String): WorkoutDetails
    suspend fun editSet(command: EditSetCommand)
    suspend fun deleteSet(setResultId: String)
    suspend fun skipSet(command: SkipSetCommand)
    suspend fun restoreSkippedSet(workoutId: String, exerciseInstanceId: String, plannedSetNo: Int)
    suspend fun finishWorkout(workoutId: String): CompletionStatus
    fun observeActiveWorkout(): Flow<WorkoutRuntime?>
    suspend fun snapshotSource(focusWorkoutId: String?): SnapshotSource
}

class RoomSportzalRepository(
    private val db: SportzalDatabase,
    private val now: () -> String = { Instant.now().toString() },
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : SportzalRepository {
    private val dao = db.dao()

    override suspend fun importProgram(
        validated: ProgramDocument,
        canonicalJson: String,
        canonicalHash: String,
    ) = db.withTransaction {
        val old = dao.program(validated.programId, validated.programVersion)
        if (old != null) {
            return@withTransaction if (old.canonicalHash == canonicalHash) ImportResult.NoOp else ImportResult.Conflict
        }
        dao.insertProgram(
            ProgramEntity(
                validated.programId,
                validated.programVersion,
                validated.schemaVersion,
                canonicalJson,
                canonicalHash,
                validated.generatedAt,
                now(),
            ),
        )
        val rawEquipment = StrictJson.parseToJsonElement(canonicalJson).jsonObject
            .getValue("equipment_upserts").jsonArray.map { it.jsonObject }
        validated.equipmentUpserts.zip(rawEquipment).forEach { (incoming, raw) ->
            val existing = dao.equipment(incoming.equipmentId)
            fun <T> patch(key: String, supplied: T?, previous: T?): T? =
                if (raw.containsKey(key)) supplied else previous
            val value = EquipmentEntity(
                equipmentId = incoming.equipmentId,
                name = incoming.name,
                setupHint = incoming.setupHint,
                weightStepKg = patch("weight_step_kg", incoming.weightStepKg, existing?.weightStepKg),
                availableWeightsJson = patch(
                    "available_weights_kg",
                    incoming.availableWeightsKg?.let { StrictJson.encodeToString(it) },
                    existing?.availableWeightsJson,
                ),
                notes = patch("notes", incoming.notes, existing?.notes),
                photoPath = existing?.photoPath,
                updatedAt = now(),
            )
            if (existing == null) dao.insertEquipment(value) else dao.updateEquipment(value)
        }
        dao.upsertIndex(validated.workouts.mapIndexed { index, workout ->
            ProgramWorkoutIndexEntity(
                validated.programId,
                validated.programVersion,
                workout.workoutInstanceId,
                workout.templateId,
                workout.title,
                workout.plannedDate,
                index + 1,
            )
        })
        dao.setAppState(AppStateEntity(activeProgramId = validated.programId, activeProgramVersion = validated.programVersion))
        ImportResult.Imported
    }

    override fun observeTodayState() = dao.observeState().map {
        TodayData(it?.activeProgramId, it?.activeProgramVersion)
    }

    override suspend fun startWorkout(
        programId: String,
        programVersion: Int,
        workoutInstanceId: String,
    ) = db.withTransaction {
        check(dao.activeWorkout() == null) { "An active workout already exists" }
        val program = checkNotNull(dao.program(programId, programVersion))
        val document = StrictJson.decodeFromString<ProgramDocument>(program.canonicalJson)
        val workout = checkNotNull(document.workouts.find { it.workoutInstanceId == workoutInstanceId })
        val id = newId()
        val referenced = workout.blocks.flatMap { it.exercises }.mapNotNull { it.equipmentId }.toSet()
        val equipment = dao.equipment().filter { it.equipmentId in referenced }.map {
            EquipmentDocument(
                it.equipmentId,
                it.name,
                it.setupHint,
                it.weightStepKg,
                it.availableWeightsJson?.let { encoded -> StrictJson.decodeFromString<List<Double>>(encoded) },
                it.notes,
            )
        }
        dao.insertWorkout(
            WorkoutEntity(
                id,
                programId,
                programVersion,
                workoutInstanceId,
                workout.templateId,
                now(),
                null,
                "active",
                StrictJson.encodeToString(workout),
                StrictJson.encodeToString(equipment),
                null,
            ),
        )
        id
    }

    override suspend fun cancelEmptyWorkout(workoutId: String) = db.withTransaction {
        val workout = dao.workout(workoutId)
        if (workout == null) {
            CancelEmptyResult.NotFound
        } else if (workout.completionStatus != "active") {
            CancelEmptyResult.NotActive
        } else if (dao.setCount(workoutId) > 0 || dao.skipCount(workoutId) > 0) {
            CancelEmptyResult.HasFacts
        } else {
            dao.deleteWorkout(workoutId)
            CancelEmptyResult.Cancelled
        }
    }

    override suspend fun saveSet(command: SaveSetCommand) = db.withTransaction {
        val workout = requireActive(command.workoutId)
        dao.set(command.setResultId)?.let {
            return@withTransaction SaveSetResult.Saved(it.setResultId, it.sequenceNo, true)
        }
        val exercise = plannedExercise(workout, command.exerciseInstanceId)
        command.plannedSetNo?.let { setNo ->
            val plannedSet = checkNotNull(exercise.plannedSets.find { it.setNo == setNo }) {
                "Planned slot does not exist in the workout snapshot"
            }
            require(command.setType == plannedSet.setType) {
                "Set type does not match the planned slot"
            }
        }
        if (command.plannedSetNo != null && (
                dao.setInSlot(command.workoutId, command.exerciseInstanceId, command.plannedSetNo) != null ||
                    dao.skipped(command.workoutId, command.exerciseInstanceId, command.plannedSetNo) != null
                )
        ) {
            return@withTransaction SaveSetResult.Conflict("Planned slot already contains a fact")
        }
        val sequence = (dao.maxSequence(command.workoutId) ?: 0) + 1
        dao.insertSet(
            SetResultEntity(
                command.setResultId,
                command.workoutId,
                sequence,
                command.exerciseInstanceId,
                command.plannedSetNo,
                command.setType,
                command.exerciseIdActual,
                command.titleActual,
                command.equipmentIdActual,
                command.equipmentNameActual,
                command.setupActual,
                command.loadBasisActual,
                command.sideActual,
                command.weightKg,
                command.reps,
                command.rir,
                command.completedAt,
                command.bootId,
                command.elapsedRealtimeMs,
                null,
                StrictJson.encodeToString(command.deviations),
                command.note,
            ),
        )
        command.plannedSetNo?.let { dao.deleteDraft(command.workoutId, command.exerciseInstanceId, it) }
        SaveSetResult.Saved(command.setResultId, sequence, false)
    }

    override suspend fun saveDraft(draft: DraftEntity) = db.withTransaction {
        requireActive(draft.workoutId)
        dao.upsertDraft(draft)
    }

    override suspend fun workoutDetails(workoutId: String): WorkoutDetails = db.withTransaction {
        val workout = checkNotNull(dao.workout(workoutId))
        WorkoutDetails(
            WorkoutRuntime(workout.workoutId, workout.planSnapshotJson, workout.equipmentAtStartJson,
                workout.programId, workout.programVersion, workout.workoutInstanceId),
            dao.sets(workoutId), dao.drafts(workoutId), dao.skips(workoutId),
        )
    }

    override suspend fun editSet(command: EditSetCommand) = db.withTransaction {
        val old = checkNotNull(dao.set(command.setResultId))
        requireActive(old.workoutId)
        require(command.weightKg.isFinite() && command.weightKg >= 0) { "Weight must be finite and non-negative" }
        require(command.reps >= 0) { "Reps must be non-negative" }
        require(command.rir == null || command.rir in 0..4) { "RIR must be between 0 and 4" }
        require(command.loadBasisActual != "bodyweight" || command.weightKg == 0.0) { "Bodyweight load must remain zero" }
        val deviations = command.deviations.distinct()
        require(deviations.size == command.deviations.size && deviations.all { it in DEVIATIONS }) {
            "Unknown or duplicate deviation"
        }
        dao.updateSet(
            old.copy(
                weightKg = command.weightKg,
                reps = command.reps,
                rir = command.rir,
                editedAt = command.editedAt,
                deviationsJson = StrictJson.encodeToString(deviations),
                note = command.note?.trim()?.ifEmpty { null },
                equipmentIdActual = command.equipmentIdActual,
                equipmentNameActual = command.equipmentNameActual,
                setupActual = command.setupActual,
                loadBasisActual = command.loadBasisActual,
                sideActual = command.sideActual,
            ),
        )
    }

    override suspend fun deleteSet(setResultId: String) = db.withTransaction {
        val set = dao.set(setResultId) ?: return@withTransaction
        val workout = checkNotNull(dao.workout(set.workoutId))
        dao.deleteSet(setResultId)
        if (workout.completionStatus != "active") {
            dao.updateStatus(workout.workoutId, completionStatus(workout).name.lowercase())
        }
    }

    override suspend fun skipSet(command: SkipSetCommand) = db.withTransaction {
        val workout = requireActive(command.workoutId)
        val exercise = plannedExercise(workout, command.exerciseInstanceId)
        check(exercise.plannedSets.any { it.setNo == command.plannedSetNo }) {
            "Planned slot does not exist in the workout snapshot"
        }
        check(dao.setInSlot(command.workoutId, command.exerciseInstanceId, command.plannedSetNo) == null) {
            "Planned slot already contains a set"
        }
        require(command.reason == null || command.reason in SKIP_REASONS) { "Unknown skip reason" }
        dao.insertSkip(
            SkippedSetEntity(
                command.workoutId,
                command.exerciseInstanceId,
                command.plannedSetNo,
                command.recordedAt,
                command.reason,
                command.note?.trim()?.ifEmpty { null },
            ),
        )
        dao.deleteDraft(command.workoutId, command.exerciseInstanceId, command.plannedSetNo)
    }

    override suspend fun restoreSkippedSet(workoutId: String, exerciseInstanceId: String, plannedSetNo: Int) =
        db.withTransaction {
            requireActive(workoutId)
            checkNotNull(dao.skipped(workoutId, exerciseInstanceId, plannedSetNo)) { "Skipped slot does not exist" }
            dao.deleteSkip(workoutId, exerciseInstanceId, plannedSetNo)
        }

    override suspend fun finishWorkout(workoutId: String): CompletionStatus = db.withTransaction {
        val workout = requireActive(workoutId)
        val status = completionStatus(workout)
        dao.finish(workoutId, status.name.lowercase(), now())
        status
    }

    private suspend fun requireActive(workoutId: String): WorkoutEntity {
        val workout = checkNotNull(dao.workout(workoutId))
        check(workout.completionStatus == "active") { "Workout is not active" }
        return workout
    }

    private fun plannedExercise(workout: WorkoutEntity, exerciseInstanceId: String) =
        StrictJson.decodeFromString<ru.sportzal.app.model.PlannedWorkoutDocument>(workout.planSnapshotJson)
            .blocks.flatMap { it.exercises }
            .find { it.exerciseInstanceId == exerciseInstanceId }
            ?: error("Exercise does not exist in the workout snapshot")

    private suspend fun completionStatus(workout: WorkoutEntity): CompletionStatus {
        val plan = StrictJson.decodeFromString<ru.sportzal.app.model.PlannedWorkoutDocument>(workout.planSnapshotJson)
        val plannedSlots = plan.blocks.flatMap { it.exercises }.flatMap { exercise ->
            exercise.plannedSets.map { exercise.exerciseInstanceId to it.setNo }
        }.toSet()
        val completedSlots = dao.sets(workout.workoutId).mapNotNull { set ->
            set.plannedSetNo?.let { set.exerciseInstanceId to it }
        }.toSet()
        return if (dao.skipCount(workout.workoutId) == 0 && completedSlots.containsAll(plannedSlots)) {
            CompletionStatus.COMPLETED
        } else {
            CompletionStatus.ENDED_EARLY
        }
    }

    override fun observeActiveWorkout() = dao.observeActiveWorkout().map {
        it?.let { workout -> WorkoutRuntime(workout.workoutId, workout.planSnapshotJson, workout.equipmentAtStartJson,
            workout.programId, workout.programVersion, workout.workoutInstanceId) }
    }

    override suspend fun snapshotSource(focusWorkoutId: String?) = SnapshotSource(
        dao.programs().map { it.canonicalJson },
        dao.workouts().filter { focusWorkoutId == null || it.workoutId == focusWorkoutId }.map {
            WorkoutRuntime(it.workoutId, it.planSnapshotJson, it.equipmentAtStartJson,
                it.programId, it.programVersion, it.workoutInstanceId)
        },
    )

    private companion object {
        val DEVIATIONS = setOf("range_shortened", "technique_changed", "discomfort", "setup_changed",
            "equipment_changed", "exercise_changed", "other")
        val SKIP_REASONS = setOf("equipment_busy", "time_limit", "fatigue", "discomfort", "other")
    }
}
