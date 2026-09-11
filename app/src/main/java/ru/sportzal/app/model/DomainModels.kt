package ru.sportzal.app.model

data class TodayData(val activeProgramId: String?, val activeProgramVersion: Int?)
data class WorkoutRuntime(
    val workoutId: String,
    val planSnapshotJson: String,
    val equipmentAtStartJson: String,
    val programId: String = "",
    val programVersion: Int = 0,
    val workoutInstanceId: String = "",
    val templateId: String = "",
    val startedAt: String = "",
    val finishedAt: String? = null,
    val completionStatus: String = "active",
    val notes: String? = null,
)
data class WorkoutDetails(
    val runtime: WorkoutRuntime,
    val sets: List<ru.sportzal.app.data.db.SetResultEntity>,
    val drafts: List<ru.sportzal.app.data.db.DraftEntity>,
    val skippedSets: List<ru.sportzal.app.data.db.SkippedSetEntity> = emptyList(),
)
data class SnapshotWorkout(
    val runtime: WorkoutRuntime,
    val sets: List<ru.sportzal.app.data.db.SetResultEntity>,
    val skippedSets: List<ru.sportzal.app.data.db.SkippedSetEntity>,
)
data class SnapshotSource(
    val programs: List<ProgramDocument>,
    val workouts: List<SnapshotWorkout>,
    val equipment: List<EquipmentDocument>,
    val activeProgram: ActiveProgramDocument?,
    val focusWorkoutId: String?,
    val totalStoredWorkouts: Int,
    val omittedWorkouts: Int,
)

enum class CompletionStatus { ACTIVE, COMPLETED, ENDED_EARLY }

sealed interface ImportResult {
    data object Imported : ImportResult
    data object NoOp : ImportResult
    data object Conflict : ImportResult
}

sealed interface CancelEmptyResult {
    data object Cancelled : CancelEmptyResult
    data object HasFacts : CancelEmptyResult
    data object NotActive : CancelEmptyResult
    data object NotFound : CancelEmptyResult
}

data class SaveSetCommand(
    val setResultId: String,
    val workoutId: String,
    val exerciseInstanceId: String,
    val plannedSetNo: Int?,
    val setType: String,
    val exerciseIdActual: String,
    val titleActual: String,
    val equipmentIdActual: String? = null,
    val equipmentNameActual: String? = null,
    val setupActual: String? = null,
    val loadBasisActual: String,
    val sideActual: String,
    val weightKg: Double,
    val reps: Int,
    val rir: Int? = null,
    val completedAt: String,
    val bootId: String? = null,
    val elapsedRealtimeMs: Long? = null,
    val deviations: List<String> = emptyList(),
    val note: String? = null,
)

sealed interface SaveSetResult {
    data class Saved(val setResultId: String, val sequenceNo: Int, val idempotent: Boolean) : SaveSetResult
    data class Conflict(val message: String) : SaveSetResult
}

data class EditSetCommand(
    val setResultId: String,
    val weightKg: Double,
    val reps: Int,
    val rir: Int?,
    val editedAt: String,
    val deviations: List<String>,
    val note: String?,
    val equipmentIdActual: String?,
    val equipmentNameActual: String?,
    val setupActual: String?,
    val loadBasisActual: String,
    val sideActual: String,
)

data class SkipSetCommand(
    val workoutId: String,
    val exerciseInstanceId: String,
    val plannedSetNo: Int,
    val recordedAt: String,
    val reason: String? = null,
    val note: String? = null,
)
