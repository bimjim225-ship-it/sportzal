package ru.sportzal.app.ui.history

import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.decodeFromString
import ru.sportzal.app.data.db.SetResultEntity
import ru.sportzal.app.data.repository.SportzalRepository
import ru.sportzal.app.domain.ActualContext
import ru.sportzal.app.model.*

data class HistoryUiState(
    val workouts: List<HistoryWorkout> = emptyList(),
    val details: HistoryWorkoutDetails? = null,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
    val equipmentChoices: List<EquipmentDocument> = emptyList(),
)

class HistoryViewModel(private val repository: SportzalRepository) {
    private val mutableState = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = mutableState

    suspend fun load() = runCatching { repository.history() }.onSuccess {
        mutableState.value = mutableState.value.copy(workouts = it, loading = false, error = null)
    }.onFailure {
        mutableState.value = mutableState.value.copy(loading = false, error = "Не удалось загрузить историю")
    }

    suspend fun open(id: String) = reloadDetails(id)
    fun close() { mutableState.value = mutableState.value.copy(details = null) }
    fun showError(message: String) { mutableState.value = mutableState.value.copy(error = message) }

    suspend fun saveNote(text: String): Boolean = commit {
        repository.updateWorkoutNotes(requireNotNull(state.value.details).runtime.workoutId, text)
    }

    suspend fun deleteSet(id: String): Boolean = commit { repository.deleteSet(id) }

    suspend fun editSet(
        old: SetResultEntity,
        weight: Double,
        reps: Int,
        rir: Int?,
        note: String?,
    ): Boolean = editSet(
        old,
        weight,
        reps,
        rir,
        StrictJson.decodeFromString(old.deviationsJson),
        note,
        ActualContext(
            old.exerciseIdActual,
            old.equipmentIdActual,
            old.setupActual,
            old.loadBasisActual,
            old.sideActual,
            old.equipmentNameActual,
        ),
    )

    suspend fun editSet(
        old: SetResultEntity,
        weight: Double,
        reps: Int,
        rir: Int?,
        deviations: List<String>,
        note: String?,
        context: ActualContext,
    ): Boolean = commit {
        repository.editSet(
            EditSetCommand(
                setResultId = old.setResultId,
                weightKg = weight,
                reps = reps,
                rir = rir,
                editedAt = Instant.now().toString(),
                deviations = deviations.distinct(),
                note = note?.trim()?.ifEmpty { null },
                equipmentIdActual = context.equipmentId,
                equipmentNameActual = context.equipmentName,
                setupActual = context.setup,
                loadBasisActual = context.loadBasis,
                sideActual = context.side,
                exerciseIdActual = context.exerciseId,
                titleActual = context.title ?: old.titleActual,
            ),
        )
    }

    private suspend fun catalog(details: HistoryWorkoutDetails): List<EquipmentDocument> = runCatching {
        repository.equipmentCatalog().map { EquipmentDocument(it.equipmentId, it.name, it.setupHint,
            it.weightStepKg, it.availableWeightsKg, it.notes) }
    }.getOrElse { if (it is kotlinx.coroutines.CancellationException) throw it else details.equipmentAtStart }

    private suspend fun commit(block: suspend () -> Unit): Boolean {
        if (state.value.saving) return false
        val id = state.value.details?.runtime?.workoutId ?: return false
        mutableState.value = state.value.copy(saving = true, error = null)
        var committed = false
        return runCatching {
            block()
            committed = true
            val details = repository.historyDetails(id)
            val workouts = repository.history()
            val choices = catalog(details)
            mutableState.value = state.value.copy(details = details, workouts = workouts,
                equipmentChoices = choices, saving = false, error = null)
        }.fold({ true }, {
            if (it is kotlinx.coroutines.CancellationException) throw it
            mutableState.value = state.value.copy(saving = false,
                error = if (committed) "Изменение сохранено. Не удалось обновить историю; откройте запись заново."
                    else it.message ?: "Не удалось сохранить")
            false
        })
    }

    private suspend fun reloadDetails(id: String) = runCatching {
        val details = repository.historyDetails(id)
        details to catalog(details)
    }.onSuccess { (details, choices) ->
        mutableState.value = state.value.copy(details = details, equipmentChoices = choices,
            loading = false, saving = false, error = null)
    }.onFailure {
        if (it is kotlinx.coroutines.CancellationException) throw it
        mutableState.value = state.value.copy(loading = false, saving = false, error = "Не удалось загрузить историю")
    }
}
