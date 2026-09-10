package ru.sportzal.app.data.files

import java.time.Instant
import java.util.UUID
import kotlinx.serialization.json.*
import ru.sportzal.app.data.repository.SportzalRepository
import ru.sportzal.app.model.StrictJson

data class ExportedSnapshot(val exportId: String, val json: String, val filename: String)
class SnapshotTooLargeException : IllegalStateException("Не удалось подготовить JSON: файл превышает 10 МиБ.")

class SnapshotExporter private constructor(
    private val source: suspend (String?) -> ru.sportzal.app.model.SnapshotSource,
    private val now: () -> Instant = Instant::now,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    constructor(repository: SportzalRepository, now: () -> Instant = Instant::now,
        newId: () -> String = { UUID.randomUUID().toString() }) : this(repository::snapshotSource, now, newId)
    internal constructor(source: suspend (String?) -> ru.sportzal.app.model.SnapshotSource,
        now: () -> Instant, newId: () -> String, @Suppress("UNUSED_PARAMETER") testing: Unit = Unit) :
        this(source, now, newId)
    suspend fun export(focusWorkoutId: String?): ExportedSnapshot {
        val source = source(focusWorkoutId)
        val exportId = newId()
        val root = buildJsonObject {
            put("schema", "sportzal.ai_snapshot"); put("schema_version", 1)
            put("exported_at", now().toString())
            putJsonObject("app") { put("name", "Sportzal"); put("app_version", "0.1.0") }
            source.activeProgramId?.let { id -> putJsonObject("active_program") {
                put("program_id", id); put("program_version", source.activeProgramVersion!!) } }
                ?: put("active_program", JsonNull)
            focusWorkoutId?.let { put("focus_workout_id", it) } ?: put("focus_workout_id", JsonNull)
            putJsonObject("history_scope") {
                put("completed_limit", 24); put("total_stored_workouts", source.totalStoredWorkouts)
                put("included_workouts", source.workouts.size); put("omitted_workouts", source.omittedWorkouts)
            }
            put("equipment", JsonArray(source.equipment.sortedBy { it.equipmentId }.map {
                StrictJson.encodeToJsonElement(it) }))
            put("programs", JsonArray(source.programs.map { StrictJson.parseToJsonElement(it) }))
            putJsonArray("workouts") { source.workouts.forEach { workout -> add(buildJsonObject {
                put("workout_id", workout.workoutId); put("program_id", workout.programId)
                put("program_version", workout.programVersion); put("workout_instance_id", workout.workoutInstanceId)
                put("template_id", workout.templateId); put("started_at", workout.startedAt)
                workout.finishedAt?.let { put("finished_at", it) } ?: put("finished_at", JsonNull)
                put("completion_status", workout.completionStatus)
                putJsonArray("sets") { workout.sets.sortedBy { it.sequenceNo }.forEach { set -> add(buildJsonObject {
                    put("set_result_id",set.setResultId);put("sequence_no",set.sequenceNo);put("exercise_instance_id",set.exerciseInstanceId)
                    set.plannedSetNo?.let { put("planned_set_no",it) } ?: put("planned_set_no",JsonNull)
                    put("set_type",set.setType);put("exercise_id_actual",set.exerciseIdActual);put("title_actual",set.titleActual)
                    set.equipmentIdActual?.let { put("equipment_id_actual",it) } ?: put("equipment_id_actual",JsonNull)
                    set.equipmentNameActual?.let { put("equipment_name_actual",it) } ?: put("equipment_name_actual",JsonNull)
                    set.setupActual?.let { put("setup_actual",it) } ?: put("setup_actual",JsonNull)
                    put("load_basis_actual",set.loadBasisActual);put("side_actual",set.sideActual);put("weight_kg",set.weightKg)
                    put("reps",set.reps);set.rir?.let { put("rir",it) } ?: put("rir",JsonNull);put("completed_at",set.completedAt)
                    if (set.bootId != null && set.elapsedRealtimeMs != null) putJsonObject("clock") {
                        put("boot_id",set.bootId);put("elapsed_realtime_ms",set.elapsedRealtimeMs) }
                    else put("clock", JsonNull)
                    set.editedAt?.let { put("edited_at",it) } ?: put("edited_at",JsonNull)
                    put("deviations",StrictJson.parseToJsonElement(set.deviationsJson));set.note?.let { put("note",it) }
                }) } }
                putJsonArray("skipped_sets") { workout.skippedSets.sortedWith(compareBy({it.exerciseInstanceId},{it.plannedSetNo})).forEach { skip ->
                    add(buildJsonObject { put("exercise_instance_id",skip.exerciseInstanceId);put("planned_set_no",skip.plannedSetNo)
                        put("recorded_at",skip.recordedAt);skip.reason?.let { put("reason",it) } ?: put("reason",JsonNull);skip.note?.let { put("note",it) } }) } }
                workout.notes?.let { put("notes",it) }
                put("equipment_at_start", StrictJson.parseToJsonElement(workout.equipmentAtStartJson))
            }) } }
        }
        val json = StrictJson.encodeToString(JsonObject.serializer(), root)
        if (json.toByteArray(Charsets.UTF_8).size > MAX_BYTES) throw SnapshotTooLargeException()
        return ExportedSnapshot(exportId, json, "sportzal-snapshot-${now().toString().replace(':','-')}.json")
    }
    companion object { const val MAX_BYTES = 10 * 1024 * 1024 }
}
