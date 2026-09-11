package ru.sportzal.app.data.files

import java.time.Instant
import java.util.UUID
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import ru.sportzal.app.data.repository.SportzalRepository
import ru.sportzal.app.model.*

data class ExportedSnapshot(val exportId: String, val json: String, val filename: String)
class SnapshotTooLargeException(message: String = MESSAGE) : IllegalStateException(message) {
    companion object { const val MESSAGE = "Не удалось подготовить JSON: файл превышает 10 МиБ." }
}

class SnapshotExporter(
    private val repository: SportzalRepository,
    private val now: () -> String = { Instant.now().toString() },
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val appVersion: String = "0.1.0",
) {
    suspend fun export(focusWorkoutId: String? = null): ExportedSnapshot {
        val source = repository.snapshotSource(focusWorkoutId)
        val document = AiSnapshotDocument(
            schema = "sportzal.ai_snapshot", schemaVersion = 1, exportedAt = now(),
            app = AppDocument("Sportzal", appVersion), activeProgram = source.activeProgram,
            focusWorkoutId = focusWorkoutId,
            historyScope = HistoryScopeDocument(24, source.totalStoredWorkouts, source.workouts.size,
                source.omittedWorkouts),
            equipment = source.equipment.sortedBy { it.equipmentId },
            programs = source.programs.sortedWith(compareBy({ it.programId }, { it.programVersion })),
            workouts = source.workouts.map { value ->
                val runtime = value.runtime
                WorkoutResultDocument(runtime.workoutId, runtime.programId, runtime.programVersion,
                    runtime.workoutInstanceId, runtime.templateId, runtime.startedAt, runtime.finishedAt,
                    runtime.completionStatus,
                    value.sets.sortedBy { it.sequenceNo }.map { set -> SetResultDocument(
                        set.setResultId, set.sequenceNo, set.exerciseInstanceId, set.plannedSetNo, set.setType,
                        set.exerciseIdActual, set.titleActual, set.equipmentIdActual, set.equipmentNameActual,
                        set.setupActual, set.loadBasisActual, set.sideActual, set.weightKg, set.reps, set.rir,
                        set.completedAt, if (set.bootId != null && set.elapsedRealtimeMs != null)
                            ClockDocument(set.bootId, set.elapsedRealtimeMs) else null,
                        set.editedAt, StrictJson.decodeFromString(set.deviationsJson), set.note)
                    },
                    value.skippedSets.sortedWith(compareBy({ it.exerciseInstanceId }, { it.plannedSetNo })).map {
                        SkippedSetDocument(it.exerciseInstanceId, it.plannedSetNo, it.recordedAt, it.reason, it.note)
                    }, runtime.notes, StrictJson.decodeFromString(runtime.equipmentAtStartJson))
            },
        )
        val json = StrictJson.encodeToString(document)
        if (json.toByteArray(Charsets.UTF_8).size > MAX_BYTES) throw SnapshotTooLargeException()
        val id = newId()
        val stamp = document.exportedAt.replace(Regex("[^0-9]"), "").take(14)
        return ExportedSnapshot(id, json, "sportzal_ai_snapshot_${stamp}_$id.json")
    }

    companion object { const val MAX_BYTES = 10 * 1024 * 1024 }
}
