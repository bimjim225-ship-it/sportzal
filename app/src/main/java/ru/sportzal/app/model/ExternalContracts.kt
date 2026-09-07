package ru.sportzal.app.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val StrictJson = Json { ignoreUnknownKeys = false; explicitNulls = true; encodeDefaults = true }

@Serializable data class ProgramDocument(
 val schema: String, @SerialName("schema_version") val schemaVersion: Int,
 @SerialName("program_id") val programId: String, @SerialName("program_version") val programVersion: Int,
 @SerialName("generated_at") val generatedAt: String,
 @SerialName("equipment_upserts") val equipmentUpserts: List<EquipmentDocument>,
 val workouts: List<PlannedWorkoutDocument>, @SerialName("athlete_context") val athleteContext: String? = null,
 @SerialName("coach_notes") val coachNotes: String? = null)
@Serializable data class EquipmentDocument(@SerialName("equipment_id") val equipmentId:String, val name:String,
 @SerialName("setup_hint") val setupHint:String?, @SerialName("weight_step_kg") val weightStepKg:Double?=null,
 @SerialName("available_weights_kg") val availableWeightsKg:List<Double>?=null, val notes:String?=null)
@Serializable data class PlannedWorkoutDocument(@SerialName("workout_instance_id") val workoutInstanceId:String,
 @SerialName("template_id") val templateId:String, val title:String, @SerialName("planned_date") val plannedDate:String,
 val blocks:List<BlockDocument>, val notes:String?=null)
@Serializable data class BlockDocument(@SerialName("block_id") val blockId:String, val title:String, val mode:String,
 val exercises:List<ExerciseDocument>, val notes:String?=null)
@Serializable data class ExerciseDocument(@SerialName("exercise_instance_id") val exerciseInstanceId:String,
 @SerialName("exercise_id") val exerciseId:String, val title:String, @SerialName("equipment_id") val equipmentId:String?,
 @SerialName("setup_hint") val setupHint:String?, @SerialName("load_basis") val loadBasis:String, val side:String,
 @SerialName("planned_order") val plannedOrder:Int, @SerialName("rir_capture") val rirCapture:String,
 @SerialName("planned_sets") val plannedSets:List<PlannedSetDocument>, val notes:String?=null)
@Serializable data class PlannedSetDocument(@SerialName("set_no") val setNo:Int, @SerialName("set_type") val setType:String,
 @SerialName("target_weight_kg") val targetWeightKg:Double, @SerialName("reps_min") val repsMin:Int,
 @SerialName("reps_max") val repsMax:Int, @SerialName("target_rir") val targetRir:Int?,
 @SerialName("rest_target_sec") val restTargetSec:Int)

@Serializable data class AiSnapshotDocument(val schema:String, @SerialName("schema_version") val schemaVersion:Int,
 @SerialName("exported_at") val exportedAt:String, val app:AppDocument, @SerialName("active_program") val activeProgram:ActiveProgramDocument?,
 @SerialName("focus_workout_id") val focusWorkoutId:String?, @SerialName("history_scope") val historyScope:HistoryScopeDocument,
 val equipment:List<EquipmentDocument>, val programs:List<ProgramDocument>, val workouts:List<WorkoutResultDocument>)
@Serializable data class AppDocument(val name:String, @SerialName("app_version") val appVersion:String)
@Serializable data class ActiveProgramDocument(@SerialName("program_id") val programId:String,@SerialName("program_version") val programVersion:Int)
@Serializable data class HistoryScopeDocument(@SerialName("completed_limit") val completedLimit:Int,@SerialName("total_stored_workouts") val totalStoredWorkouts:Int,@SerialName("included_workouts") val includedWorkouts:Int,@SerialName("omitted_workouts") val omittedWorkouts:Int)
@Serializable data class ClockDocument(@SerialName("boot_id") val bootId:String,@SerialName("elapsed_realtime_ms") val elapsedRealtimeMs:Long)
@Serializable data class SetResultDocument(@SerialName("set_result_id") val setResultId:String,@SerialName("sequence_no") val sequenceNo:Int,@SerialName("exercise_instance_id") val exerciseInstanceId:String,@SerialName("planned_set_no") val plannedSetNo:Int?,@SerialName("set_type") val setType:String,@SerialName("exercise_id_actual") val exerciseIdActual:String,@SerialName("title_actual") val titleActual:String,@SerialName("equipment_id_actual") val equipmentIdActual:String?,@SerialName("equipment_name_actual") val equipmentNameActual:String?,@SerialName("setup_actual") val setupActual:String?,@SerialName("load_basis_actual") val loadBasisActual:String,@SerialName("side_actual") val sideActual:String,@SerialName("weight_kg") val weightKg:Double,val reps:Int,val rir:Int?,@SerialName("completed_at") val completedAt:String,val clock:ClockDocument?,@SerialName("edited_at") val editedAt:String?,val deviations:List<String>,val note:String?=null)
@Serializable data class SkippedSetDocument(@SerialName("exercise_instance_id") val exerciseInstanceId:String,@SerialName("planned_set_no") val plannedSetNo:Int,@SerialName("recorded_at") val recordedAt:String,val reason:String?,val note:String?=null)
@Serializable data class WorkoutResultDocument(@SerialName("workout_id") val workoutId:String,@SerialName("program_id") val programId:String,@SerialName("program_version") val programVersion:Int,@SerialName("workout_instance_id") val workoutInstanceId:String,@SerialName("template_id") val templateId:String,@SerialName("started_at") val startedAt:String,@SerialName("finished_at") val finishedAt:String?,@SerialName("completion_status") val completionStatus:String,val sets:List<SetResultDocument>,@SerialName("skipped_sets") val skippedSets:List<SkippedSetDocument>,val notes:String?=null,@SerialName("equipment_at_start") val equipmentAtStart:List<EquipmentDocument>)
