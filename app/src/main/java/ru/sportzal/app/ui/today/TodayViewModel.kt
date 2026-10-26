package ru.sportzal.app.ui.today

import android.net.Uri
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import ru.sportzal.app.data.db.SportzalDatabase
import ru.sportzal.app.data.files.ImportPreview
import ru.sportzal.app.data.files.ProgramImporter
import ru.sportzal.app.data.repository.SportzalRepository
import ru.sportzal.app.domain.ConsumedWorkout
import ru.sportzal.app.domain.PlannedWorkout
import ru.sportzal.app.domain.TodaySelection
import ru.sportzal.app.domain.TodaySelector
import ru.sportzal.app.domain.WorkoutService
import ru.sportzal.app.model.ImportResult
import ru.sportzal.app.model.PlannedWorkoutDocument
import ru.sportzal.app.model.EquipmentDocument
import ru.sportzal.app.model.ProgramDocument
import ru.sportzal.app.model.StrictJson
import kotlinx.serialization.decodeFromString
import ru.sportzal.app.platform.FileIntentResult

data class TodayUiState(
    val selection: TodaySelection = TodaySelection.NoProgram,
    val manualChoices: List<PlannedWorkout> = emptyList(),
    val preview: ImportPreview? = null,
    val message: String? = null,
    val plannedWorkout: PlannedWorkoutDocument? = null,
    val equipment: List<EquipmentDocument> = emptyList(),
    val loading: Boolean = true,
    val busy: Boolean = false,
    val readingFile: Boolean = false,
    val error: String? = null,
)

internal fun availableWorkouts(
    planned: List<PlannedWorkout>,
    consumed: Set<ConsumedWorkout>,
): List<PlannedWorkout> = planned.filter {
    ConsumedWorkout(it.programId, it.workoutInstanceId) !in consumed
}

/** Owns the Today feature state while the activity remains only an Android host. */
class TodayViewModel(
    private val database: SportzalDatabase,
    private val repository: SportzalRepository,
    private val importer: ProgramImporter,
    private val workoutService: WorkoutService,
    private val selector: TodaySelector = TodaySelector(),
) {
    private val mutableState = MutableStateFlow(TodayUiState())
    val state: StateFlow<TodayUiState> = mutableState
    private var manualWorkoutInstanceId: String? = null
    private var refreshGeneration = 0
    private var fileGeneration = 0

    suspend fun refresh() {
      val generation = ++refreshGeneration
      try {
        val appState = database.dao().observeState().first()
        val active = repository.observeActiveWorkout().first()
        val programId = appState?.activeProgramId
        val version = appState?.activeProgramVersion
        val planned = if (programId != null && version != null) {
            database.dao().plannedWorkouts(programId, version).map {
                PlannedWorkout(it.programId, it.programVersion, it.workoutInstanceId, it.title,
                    LocalDate.parse(it.plannedDate), it.plannedOrder)
            }
        } else emptyList()
        val consumed = database.dao().consumedWorkouts()
            .map { ConsumedWorkout(it.programId, it.workoutInstanceId) }.toSet()
        val available = availableWorkouts(planned, consumed)
        val selection = selector.select(active, if (programId == null) null else planned, consumed, manualWorkoutInstanceId)
        val primaryId = (selection as? TodaySelection.Ready)?.workout?.workoutInstanceId
        val program = if (programId != null && version != null) database.dao().program(programId, version)
            ?.canonicalJson?.let { StrictJson.decodeFromString<ProgramDocument>(it) } else null
        val plan = if (selection is TodaySelection.Resume)
            StrictJson.decodeFromString<PlannedWorkoutDocument>(selection.workout.planSnapshotJson)
            else program?.workouts?.firstOrNull { it.workoutInstanceId == primaryId }
        val equipment = if (selection is TodaySelection.Resume)
            StrictJson.decodeFromString<List<EquipmentDocument>>(selection.workout.equipmentAtStartJson)
            else repository.equipmentCatalog().map { EquipmentDocument(it.equipmentId, it.name, it.setupHint, it.weightStepKg, it.availableWeightsKg, it.notes) }
        if (generation != refreshGeneration) return
        mutableState.value = mutableState.value.copy(
            selection = selection,
            manualChoices = available.filterNot { it.workoutInstanceId == primaryId },
            plannedWorkout = plan, equipment = equipment, loading = false, error = null,
        )
      } catch (_: Exception) {
          if (generation == refreshGeneration) mutableState.value = mutableState.value.copy(loading = false, error = "Не удалось загрузить план. Повторите попытку.")
      }
    }

    suspend fun preview(uri: Uri) {
        if (state.value.readingFile || state.value.busy) return
        val generation = ++fileGeneration
        mutableState.value = mutableState.value.copy(readingFile = true, preview = null, message = null)
        try {
            val preview = importer.import(uri)
            if (generation == fileGeneration) mutableState.value = mutableState.value.copy(preview = preview)
        } finally { if (generation == fileGeneration) mutableState.value = mutableState.value.copy(readingFile = false) }
    }

    fun dismissPreview() {
        if (!state.value.busy) {
            fileGeneration++
            mutableState.value = mutableState.value.copy(preview = null, message = null, readingFile = false)
        }
    }

    fun showFileResult(result: FileIntentResult) {
        fileGeneration++
        mutableState.value = when (result) {
            is FileIntentResult.Program -> mutableState.value.copy(preview = result.preview, message = null, readingFile = false)
            is FileIntentResult.Message -> mutableState.value.copy(preview = null, message = result.text, readingFile = false)
        }
    }

    suspend fun confirmImport(valid: ImportPreview.Valid) {
        if (state.value.busy) return
        mutableState.value = mutableState.value.copy(busy = true, message = null)
        val result = runCatching { importer.confirm(valid) }.getOrElse {
            mutableState.value = mutableState.value.copy(busy = false, message = "Не удалось принять план. Данные не изменены; повторите попытку.")
            return
        }
        mutableState.value = mutableState.value.copy(
            preview = if (result == ImportResult.Conflict) valid else null,
            busy = false,
            message = when (result) {
                ImportResult.Imported -> "Программа импортирована"
                ImportResult.NoOp -> "Эта версия уже импортирована"
                ImportResult.Conflict -> "Конфликт: версия содержит другие данные"
            },
        )
        manualWorkoutInstanceId = null
        refresh()
    }

    suspend fun chooseWorkout(workoutInstanceId: String) {
        manualWorkoutInstanceId = workoutInstanceId
        refresh()
    }

    suspend fun start(): String? {
        if (state.value.busy) return null
        val ready = mutableState.value.selection as? TodaySelection.Ready ?: return null
        mutableState.value = mutableState.value.copy(busy = true, message = null)
        val id = runCatching {
            workoutService.start(ready.workout.programId, ready.workout.programVersion, ready.workout.workoutInstanceId)
        }.onFailure { mutableState.value = mutableState.value.copy(message = "Не удалось начать тренировку. Повторите попытку.") }.getOrNull()
        mutableState.value = mutableState.value.copy(busy = false)
        refresh()
        return id
    }

    fun resume() {
        mutableState.value = mutableState.value.copy(message = "Тренировка уже активна")
    }
}
