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
import ru.sportzal.app.platform.FileIntentResult

data class TodayUiState(
    val selection: TodaySelection = TodaySelection.NoProgram,
    val manualChoices: List<PlannedWorkout> = emptyList(),
    val preview: ImportPreview? = null,
    val message: String? = null,
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

    suspend fun refresh() {
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
        mutableState.value = mutableState.value.copy(
            selection = selection,
            manualChoices = available.filterNot { it.workoutInstanceId == primaryId },
        )
    }

    suspend fun preview(uri: Uri) {
        mutableState.value = mutableState.value.copy(preview = importer.import(uri))
    }

    fun showFileResult(result: FileIntentResult) {
        mutableState.value = when (result) {
            is FileIntentResult.Program -> mutableState.value.copy(preview = result.preview, message = null)
            is FileIntentResult.Message -> mutableState.value.copy(preview = null, message = result.text)
        }
    }

    suspend fun confirmImport(valid: ImportPreview.Valid) {
        val result = importer.confirm(valid)
        mutableState.value = mutableState.value.copy(
            preview = null,
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

    suspend fun start() {
        val ready = mutableState.value.selection as? TodaySelection.Ready ?: return
        runCatching {
            workoutService.start(ready.workout.programId, ready.workout.programVersion, ready.workout.workoutInstanceId)
        }.onFailure { mutableState.value = mutableState.value.copy(message = it.message) }
        refresh()
    }

    fun resume() {
        mutableState.value = mutableState.value.copy(message = "Тренировка уже активна")
    }
}
