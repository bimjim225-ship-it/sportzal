package ru.sportzal.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "app_state")
data class AppStateEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "active_program_id") val activeProgramId: String?,
    @ColumnInfo(name = "active_program_version") val activeProgramVersion: Int?,
)

@Entity(tableName = "equipment")
data class EquipmentEntity(
    @PrimaryKey @ColumnInfo(name = "equipment_id") val equipmentId: String,
    val name: String,
    @ColumnInfo(name = "setup_hint") val setupHint: String?,
    @ColumnInfo(name = "weight_step_kg") val weightStepKg: Double?,
    @ColumnInfo(name = "available_weights_json") val availableWeightsJson: String?,
    val notes: String?,
    @ColumnInfo(name = "photo_path") val photoPath: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
)

@Entity(tableName = "programs", primaryKeys = ["program_id", "program_version"])
data class ProgramEntity(
    @ColumnInfo(name = "program_id") val programId: String,
    @ColumnInfo(name = "program_version") val programVersion: Int,
    @ColumnInfo(name = "schema_version") val schemaVersion: Int,
    @ColumnInfo(name = "canonical_json") val canonicalJson: String,
    @ColumnInfo(name = "canonical_hash") val canonicalHash: String,
    @ColumnInfo(name = "generated_at") val generatedAt: String,
    @ColumnInfo(name = "imported_at") val importedAt: String,
)

@Entity(
    tableName = "program_workout_index",
    primaryKeys = ["program_id", "program_version", "workout_instance_id"],
    foreignKeys = [ForeignKey(
        entity = ProgramEntity::class,
        parentColumns = ["program_id", "program_version"],
        childColumns = ["program_id", "program_version"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("program_id", "program_version")],
)
data class ProgramWorkoutIndexEntity(
    @ColumnInfo(name = "program_id") val programId: String,
    @ColumnInfo(name = "program_version") val programVersion: Int,
    @ColumnInfo(name = "workout_instance_id") val workoutInstanceId: String,
    @ColumnInfo(name = "template_id") val templateId: String,
    val title: String,
    @ColumnInfo(name = "planned_date") val plannedDate: String,
    @ColumnInfo(name = "planned_order") val plannedOrder: Int,
)

@Entity(
    tableName = "workouts",
    indices = [
        Index("completion_status"),
        Index(value = ["program_id", "workout_instance_id"], unique = true),
        Index("program_id", "program_version"),
    ],
    foreignKeys = [ForeignKey(
        entity = ProgramEntity::class,
        parentColumns = ["program_id", "program_version"],
        childColumns = ["program_id", "program_version"],
    )],
)
data class WorkoutEntity(
    @PrimaryKey @ColumnInfo(name = "workout_id") val workoutId: String,
    @ColumnInfo(name = "program_id") val programId: String,
    @ColumnInfo(name = "program_version") val programVersion: Int,
    @ColumnInfo(name = "workout_instance_id") val workoutInstanceId: String,
    @ColumnInfo(name = "template_id") val templateId: String,
    @ColumnInfo(name = "started_at") val startedAt: String,
    @ColumnInfo(name = "finished_at") val finishedAt: String?,
    @ColumnInfo(name = "completion_status") val completionStatus: String,
    @ColumnInfo(name = "plan_snapshot_json") val planSnapshotJson: String,
    @ColumnInfo(name = "equipment_at_start_json") val equipmentAtStartJson: String,
    val notes: String?,
)

@Entity(
    tableName = "set_results",
    foreignKeys = [ForeignKey(entity = WorkoutEntity::class, parentColumns = ["workout_id"], childColumns = ["workout_id"], onDelete = ForeignKey.CASCADE)],
    indices = [
        Index("workout_id"),
        Index(value = ["workout_id", "sequence_no"], unique = true),
        Index(value = ["workout_id", "exercise_instance_id", "planned_set_no"], unique = true),
    ],
)
data class SetResultEntity(
    @PrimaryKey @ColumnInfo(name = "set_result_id") val setResultId: String,
    @ColumnInfo(name = "workout_id") val workoutId: String,
    @ColumnInfo(name = "sequence_no") val sequenceNo: Int,
    @ColumnInfo(name = "exercise_instance_id") val exerciseInstanceId: String,
    @ColumnInfo(name = "planned_set_no") val plannedSetNo: Int?,
    @ColumnInfo(name = "set_type") val setType: String,
    @ColumnInfo(name = "exercise_id_actual") val exerciseIdActual: String,
    @ColumnInfo(name = "title_actual") val titleActual: String,
    @ColumnInfo(name = "equipment_id_actual") val equipmentIdActual: String?,
    @ColumnInfo(name = "equipment_name_actual") val equipmentNameActual: String?,
    @ColumnInfo(name = "setup_actual") val setupActual: String?,
    @ColumnInfo(name = "load_basis_actual") val loadBasisActual: String,
    @ColumnInfo(name = "side_actual") val sideActual: String,
    @ColumnInfo(name = "weight_kg") val weightKg: Double,
    val reps: Int,
    val rir: Int?,
    @ColumnInfo(name = "completed_at") val completedAt: String,
    @ColumnInfo(name = "boot_id") val bootId: String?,
    @ColumnInfo(name = "elapsed_realtime_ms") val elapsedRealtimeMs: Long?,
    @ColumnInfo(name = "edited_at") val editedAt: String?,
    @ColumnInfo(name = "deviations_json") val deviationsJson: String,
    val note: String?,
)

@Entity(
    tableName = "skipped_sets",
    primaryKeys = ["workout_id", "exercise_instance_id", "planned_set_no"],
    foreignKeys = [ForeignKey(entity = WorkoutEntity::class, parentColumns = ["workout_id"], childColumns = ["workout_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("workout_id")],
)
data class SkippedSetEntity(
    @ColumnInfo(name = "workout_id") val workoutId: String,
    @ColumnInfo(name = "exercise_instance_id") val exerciseInstanceId: String,
    @ColumnInfo(name = "planned_set_no") val plannedSetNo: Int,
    @ColumnInfo(name = "recorded_at") val recordedAt: String,
    val reason: String?,
    val note: String?,
)

@Entity(
    tableName = "drafts",
    primaryKeys = ["workout_id", "exercise_instance_id", "planned_set_no"],
    foreignKeys = [ForeignKey(entity = WorkoutEntity::class, parentColumns = ["workout_id"], childColumns = ["workout_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("workout_id")],
)
data class DraftEntity(
    @ColumnInfo(name = "workout_id") val workoutId: String,
    @ColumnInfo(name = "exercise_instance_id") val exerciseInstanceId: String,
    @ColumnInfo(name = "planned_set_no") val plannedSetNo: Int,
    @ColumnInfo(name = "weight_text") val weightText: String,
    @ColumnInfo(name = "reps_text") val repsText: String,
    val rir: Int?,
    @ColumnInfo(name = "actual_context_json") val actualContextJson: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
)
