package ru.sportzal.app.ui.workout

import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import ru.sportzal.app.data.db.DraftEntity
import ru.sportzal.app.data.db.SetResultEntity
import ru.sportzal.app.data.db.SkippedSetEntity
import ru.sportzal.app.data.repository.SportzalRepository
import ru.sportzal.app.domain.ActualContext
import ru.sportzal.app.domain.ClockReading
import ru.sportzal.app.domain.ExerciseRuntimeCard
import ru.sportzal.app.domain.PlannedSlot
import ru.sportzal.app.domain.PrefillResolver
import ru.sportzal.app.domain.PreviousSetFact
import ru.sportzal.app.domain.RotationCoordinator
import ru.sportzal.app.domain.SetDraft
import ru.sportzal.app.model.ClockAnchor
import ru.sportzal.app.model.ExerciseDocument
import ru.sportzal.app.model.EquipmentDocument
import ru.sportzal.app.model.PlannedWorkoutDocument
import ru.sportzal.app.model.SaveSetCommand
import ru.sportzal.app.model.SaveSetResult
import ru.sportzal.app.model.EditSetCommand
import ru.sportzal.app.model.SkipSetCommand
import ru.sportzal.app.model.StrictJson
import ru.sportzal.app.model.elapsedBetween
import ru.sportzal.app.model.CompletionStatus
import ru.sportzal.app.platform.ClockProvider

data class ExerciseUiState(
    val exercise: ExerciseDocument,
    val currentSlot: PlannedSlot?,
    val draft: SetDraft?,
    val saved: List<SetResultEntity>,
    val restReferenceSec: Int? = null,
    val skipped: List<SkippedSetEntity> = emptyList(),
    val equipmentChoices: List<EquipmentDocument> = emptyList(),
    val equipmentPhotoPath: String? = null,
    val exerciseChoices: List<ExerciseDocument> = emptyList(),
)
data class BlockUiState(
    val blockId: String,
    val title: String,
    val mode: String,
    val cards: List<ExerciseUiState>,
    val notes: String? = null,
)
data class WorkoutUiState(
    val workoutId: String = "",
    val title: String = "",
    val blocks: List<BlockUiState> = emptyList(),
    val now: ClockReading = ClockReading(Instant.EPOCH, null, null),
    val saving: Boolean = false,
    val error: String? = null,
    val finishConfirmation: Boolean = false,
    val finishSummary: FinishSummary? = null,
    val notes: String? = null,
    val planNotes: String? = null,
    val reloadRequired: Boolean = false,
)
data class ExerciseFinishSummary(val title: String, val workSetCount: Int)
data class FinishSummary(val durationSeconds: Long, val workSetCount: Int,
    val exercises: List<ExerciseFinishSummary>, val endedEarly: Boolean,
    val plannedWorkCount: Int = workSetCount, val completedPlannedWork: Int = workSetCount,
    val warmupSetCount: Int = 0, val extraSetCount: Int = 0, val skippedSetCount: Int = 0,
    val title: String = "")

enum class InteractionSource {
    FOCUS,
    SET_ACTIONS,
    SKIP_DIALOG,
    CONTEXT_DIALOG,
    EXTRA_SET_DIALOG,
}

private data class InteractionOwner(
    val exerciseInstanceId: String,
    val source: InteractionSource,
)

/** Owns drafts, idempotent save attempts, progression and rotation ordering. */
class WorkoutViewModel(
    private val repository: SportzalRepository,
    private val clock: ClockProvider,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val mutableState = MutableStateFlow(WorkoutUiState())
    val state: StateFlow<WorkoutUiState> = mutableState
    private var plan: PlannedWorkoutDocument? = null
    private var startedAt: Instant = Instant.EPOCH
    private var equipmentAtStart = emptyMap<String, EquipmentDocument>()
    private var equipmentPhotos = emptyMap<String, String?>()
    private var replacementEquipment = emptyList<EquipmentDocument>()
    private val drafts = linkedMapOf<Pair<String, Int>, SetDraft>()
    private val sets = mutableListOf<SetResultEntity>()
    private val skips = mutableListOf<SkippedSetEntity>()
    private val pending = mutableMapOf<Pair<String, Int?>, SaveSetCommand>()
    private val rotations = mutableMapOf<String, RotationCoordinator>()
    private val interactionOwners = mutableMapOf<String, MutableSet<InteractionOwner>>()
    private var committedFinishWorkoutId: String? = null

    suspend fun open(workoutId: String) {
        val details = repository.workoutDetails(workoutId)
        plan = StrictJson.decodeFromString(details.runtime.planSnapshotJson)
        startedAt = details.runtime.startedAt.takeIf { it.isNotBlank() }?.let(Instant::parse) ?: clock.wallNow()
        equipmentAtStart = StrictJson.decodeFromString<List<EquipmentDocument>>(details.runtime.equipmentAtStartJson)
            .associateBy { it.equipmentId }
        // Photos are a current-catalog convenience only; a catalog read failure must not block the workout.
        val catalog = runCatching { repository.equipmentCatalog() }.getOrDefault(emptyList())
        equipmentPhotos = catalog.associate { it.equipmentId to it.photoPath }
        replacementEquipment = (catalog.map { EquipmentDocument(it.equipmentId, it.name, it.setupHint,
            it.weightStepKg, it.availableWeightsKg, it.notes) } + equipmentAtStart.values).distinctBy { it.equipmentId }
        sets.clear(); sets += details.sets
        skips.clear(); skips += details.skippedSets
        drafts.clear()
        val byId = exercises().associateBy { it.exerciseInstanceId }
        details.drafts.forEach { entity ->
            val exercise = byId[entity.exerciseInstanceId] ?: return@forEach
            drafts[entity.exerciseInstanceId to entity.plannedSetNo] = SetDraft(
                entity.weightText.replace(',', '.').toDoubleOrNull(), entity.repsText.toIntOrNull(), entity.rir,
                entity.actualContextJson?.let { StrictJson.decodeFromString<ActualContext>(it) }
                    ?: exercise.resolvedContext(), rirAnswered = entity.rirAnswered,
            )
        }
        mutableState.value = WorkoutUiState(workoutId = workoutId, notes = details.runtime.notes)
        pending.clear()
        committedFinishWorkoutId = null
        rotations.clear()
        interactionOwners.clear()
        plan!!.blocks.filter { it.mode == "rotation" }.forEach { block ->
            rotations[block.blockId] = RotationCoordinator(runtimeCards(block.exercises)).also {
                it.update(runtimeCards(block.exercises), readClock(), committed = true)
            }
        }
        publish()
    }

    suspend fun updateNotes(notes: String): Boolean {
        mutableState.value = mutableState.value.copy(saving = true, error = null)
        return runCatching { repository.updateWorkoutNotes(mutableState.value.workoutId, notes) }.fold({
            val normalized = notes.trim().ifEmpty { null }
            mutableState.value = mutableState.value.copy(saving = false, notes = normalized); true
        }, { fail(it.message ?: "Не удалось сохранить заметку"); false })
    }

    suspend fun updateDraft(exerciseId: String, weight: Double?, reps: Int?, rir: Int?, rirAnswered: Boolean) {
        if (mutableState.value.saving || mutableState.value.reloadRequired) return
        val exercise = exercises().first { it.exerciseInstanceId == exerciseId }
        val slot = currentSlot(exercise) ?: return
        val existing = resolvedDraft(exercise, slot)
        val value = SetDraft(if (existing.context.loadBasis == "bodyweight") 0.0 else weight, reps, rir,
            existing.context, rirAnswered)
        drafts[exerciseId to slot.plannedSetNo] = value
        runCatching { repository.saveDraft(DraftEntity(mutableState.value.workoutId, exerciseId, slot.plannedSetNo,
            value.weightKg?.toString().orEmpty(), value.reps?.toString().orEmpty(), value.rir,
            StrictJson.encodeToString(value.context),
            clock.wallNow().toString(), value.rirAnswered)) }
            .onSuccess { mutableState.value = state.value.copy(error = null) }
            .onFailure { fail("Не удалось сохранить ввод. Повторите изменение перед выходом.") }
        publish()
    }

    /** Failed writes keep the user in the workout at navigation boundaries. */
    suspend fun flushDrafts(): Boolean {
        if (state.value.saving) return false
        if (state.value.reloadRequired && !reloadCommittedState()) return false
        return runCatching {
            drafts.toMap().forEach { (key, value) ->
                val exercise = exercises().firstOrNull { it.exerciseInstanceId == key.first } ?: return@forEach
                if (currentSlot(exercise)?.plannedSetNo != key.second) return@forEach
                repository.saveDraft(DraftEntity(state.value.workoutId, key.first, key.second,
                    value.weightKg?.toString().orEmpty(), value.reps?.toString().orEmpty(), value.rir,
                    StrictJson.encodeToString(value.context), clock.wallNow().toString(), value.rirAnswered))
            }
        }.onFailure { fail("Не удалось сохранить ввод. Повторите попытку или останьтесь в тренировке.") }.isSuccess
    }

    suspend fun saveSet(exerciseId: String, plannedSetNo: Int, weight: Double? = null, reps: Int? = null,
        rir: Int? = null, rirAnswered: Boolean? = null) {
        if (mutableState.value.saving || mutableState.value.reloadRequired) return
        val exercise = exercises().first { it.exerciseInstanceId == exerciseId }
        val slot = currentSlot(exercise)?.takeIf { it.plannedSetNo == plannedSetNo } ?: return
        if (sets.any { it.exerciseInstanceId == exerciseId && it.plannedSetNo == plannedSetNo }) return
        val key = exerciseId to slot.plannedSetNo
        val persistedDraft = resolvedDraft(exercise, slot)
        val draft = if (rirAnswered != null) persistedDraft.copy(
            weightKg = if (persistedDraft.context.loadBasis == "bodyweight") 0.0 else weight,
            reps = reps, rir = rir, rirAnswered = rirAnswered,
        ) else persistedDraft
        val weight = draft.weightKg ?: return fail("Введите вес")
        val reps = draft.reps ?: return fail("Введите повторы")
        if (requiresRir(exercise, slot) && !draft.rirAnswered) return fail("Выберите запас повторов или «Не оценил»")
        val previous = pending[key]
        val reading = if (previous == null) readClock() else null
        val context = draft.context
        val candidate = SaveSetCommand(previous?.setResultId ?: newId(), state.value.workoutId,
            exerciseId, slot.plannedSetNo, slot.setType, context.exerciseId, context.title ?: exercise.title,
            context.equipmentId, context.equipmentName, context.setup, context.loadBasis, context.side,
            weight, reps, draft.rir, previous?.completedAt ?: reading!!.wall.toString(),
            previous?.bootId ?: reading?.bootId, previous?.elapsedRealtimeMs ?: reading?.elapsedRealtimeMs)
        val command = previous?.takeIf { it == candidate } ?: candidate
        pending[key] = command
        commitAttempt(key, command, previous)
    }

    /** Reconcile an unknown acknowledgement before changing its payload; never create a second fact. */
    private suspend fun commitAttempt(key: Pair<String, Int?>, command: SaveSetCommand,
        previous: SaveSetCommand?): Boolean {
        mutableState.value = state.value.copy(saving = true, error = null)
        var committed = false
        return runCatching {
            val existing = if (previous != null)
                repository.workoutDetails(command.workoutId).sets.firstOrNull { it.setResultId == command.setResultId }
                else null
            if (existing != null) {
                check(existing.workoutId == command.workoutId && existing.exerciseInstanceId == command.exerciseInstanceId &&
                    existing.plannedSetNo == command.plannedSetNo) { "ID записи относится к другому подходу" }
                if (!existing.matches(command)) repository.editSet(EditSetCommand(command.setResultId, command.weightKg, command.reps,
                    command.rir, clock.wallNow().toString(), command.deviations, command.note,
                    command.equipmentIdActual, command.equipmentNameActual, command.setupActual,
                    command.loadBasisActual, command.sideActual, command.exerciseIdActual, command.titleActual, command.setType))
            } else {
                val result = repository.saveSet(command)
                check(result is SaveSetResult.Saved) { (result as SaveSetResult.Conflict).message }
            }
            committed = true
            refreshCommittedFacts()
            pending.remove(key)
        }.fold({ true }, { mutationFailed(it, committed); false })
    }

    private fun SetResultEntity.matches(c: SaveSetCommand) = weightKg == c.weightKg && reps == c.reps &&
        rir == c.rir && setType == c.setType && exerciseIdActual == c.exerciseIdActual && titleActual == c.titleActual &&
        equipmentIdActual == c.equipmentIdActual && equipmentNameActual == c.equipmentNameActual &&
        setupActual == c.setupActual && loadBasisActual == c.loadBasisActual && sideActual == c.sideActual &&
        note == c.note && StrictJson.decodeFromString<List<String>>(deviationsJson) == c.deviations

    private suspend fun refreshCommittedFacts() {
        val details = repository.workoutDetails(state.value.workoutId)
        sets.clear(); sets += details.sets
        skips.clear(); skips += details.skippedSets
        replacementEquipment = (replacementEquipment + sets.mapNotNull { fact -> fact.equipmentIdActual?.let { id ->
            fact.equipmentNameActual?.let { EquipmentDocument(id, it, fact.setupActual) }
        } }).distinctBy { it.equipmentId }
        // Keep unrelated in-memory input; remove only drafts for slots closed by committed data.
        drafts.keys.removeAll { key ->
            sets.any { it.exerciseInstanceId == key.first && it.plannedSetNo == key.second } ||
                skips.any { it.exerciseInstanceId == key.first && it.plannedSetNo == key.second }
        }
        updateRotations()
        mutableState.value = state.value.copy(saving = false, error = null, reloadRequired = false)
        publish()
    }

    private fun mutationFailed(error: Throwable, committed: Boolean) {
        mutableState.value = state.value.copy(saving = false, reloadRequired = committed,
            error = if (committed) "Изменение сохранено, но экран не обновился. Повторите загрузку данных."
                else error.message ?: "Не удалось сохранить изменение. Повторите попытку.")
    }

    suspend fun reloadCommittedState(): Boolean {
        if (state.value.saving) return false
        mutableState.value = state.value.copy(saving = true)
        return runCatching {
            refreshCommittedFacts()
            val committedIds = sets.map { it.setResultId }.toSet()
            pending.entries.removeAll { it.value.setResultId in committedIds }
        }.fold({ true }, { mutationFailed(it, true); false })
    }

    suspend fun updateActualContext(exerciseId: String, context: ActualContext): Boolean {
        if (mutableState.value.saving || mutableState.value.reloadRequired) return false
        val exercise = exercises().firstOrNull { it.exerciseInstanceId == exerciseId } ?: return false
        val slot = currentSlot(exercise) ?: return false
        val old = resolvedDraft(exercise, slot)
        val movementChanged = old.context.exerciseId != context.exerciseId
        if (movementChanged && context.title.isNullOrBlank()) return false
        val incompatible = movementChanged || old.context.equipmentId != context.equipmentId || old.context.loadBasis != context.loadBasis
        val value = old.copy(
            weightKg = if (context.loadBasis == "bodyweight") 0.0 else if (incompatible) null else old.weightKg,
            reps = if (movementChanged) null else old.reps,
            rir = if (incompatible) null else old.rir,
            rirAnswered = if (incompatible) false else old.rirAnswered, context = context)
        return runCatching { repository.saveDraft(DraftEntity(state.value.workoutId, exerciseId, slot.plannedSetNo,
            value.weightKg?.toString().orEmpty(), value.reps?.toString().orEmpty(), value.rir,
            StrictJson.encodeToString(value.context), clock.wallNow().toString(), value.rirAnswered)) }.onSuccess { drafts[exerciseId to slot.plannedSetNo] = value; mutableState.value = state.value.copy(error = null); publish() }
            .onFailure { fail(it.message ?: "Не удалось сохранить контекст") }.isSuccess
    }

    suspend fun saveExtraSet(exerciseId: String, setType: String, weight: Double, reps: Int, rir: Int?, note: String?): Boolean {
        if (mutableState.value.saving || mutableState.value.reloadRequired || setType !in setOf("work", "warmup") || !weight.isFinite() || weight < 0 ||
            reps < 0 || (rir != null && rir !in 0..4)) return false
        val exercise = exercises().firstOrNull { it.exerciseInstanceId == exerciseId } ?: return false
        val context = currentSlot(exercise)?.let { resolvedDraft(exercise, it).context }
            ?: latestContext(exercise) ?: exercise.resolvedContext()
        if (context.loadBasis == "bodyweight" && weight != 0.0) return false
        val key = exerciseId to null
        val previous = pending[key]
        val reading = if (previous == null) readClock() else null
        val candidate = SaveSetCommand(previous?.setResultId ?: newId(), state.value.workoutId, exerciseId,
            null, setType, context.exerciseId, context.title ?: exercise.title, context.equipmentId, context.equipmentName,
            context.setup, context.loadBasis, context.side, weight, reps, rir,
            previous?.completedAt ?: reading!!.wall.toString(), previous?.bootId ?: reading?.bootId,
            previous?.elapsedRealtimeMs ?: reading?.elapsedRealtimeMs, note = note?.trim()?.ifEmpty { null })
        val command = previous?.takeIf { it == candidate } ?: candidate
        pending[key] = command
        return commitAttempt(key, command, previous)
    }

    suspend fun cancelExtraAttempt(exerciseId: String): Boolean {
        if (state.value.saving) return false
        val key = exerciseId to null
        val attempt = pending[key] ?: return true
        mutableState.value = state.value.copy(saving = true, error = null)
        return runCatching {
            if (repository.workoutDetails(state.value.workoutId).sets.any { it.setResultId == attempt.setResultId })
                refreshCommittedFacts()
            pending.remove(key)
            mutableState.value = state.value.copy(saving = false)
        }.fold({ true }, { mutationFailed(it, false); false })
    }

    fun tick() { publish() }
    suspend fun requestFinish() {
        if (state.value.saving || state.value.reloadRequired || state.value.finishSummary != null) return
        if (hasUnresolvedSlots()) mutableState.value = state.value.copy(finishConfirmation = true)
        else confirmFinish()
    }
    fun cancelFinish() { mutableState.value = state.value.copy(finishConfirmation = false) }
    fun dismissFinish() {
        committedFinishWorkoutId = null
        mutableState.value = mutableState.value.copy(
            finishSummary = null,
            error = null,
        )
    }
    fun showError(message: String) { fail(message) }
    suspend fun confirmFinish(): Boolean {
        if (state.value.saving || state.value.reloadRequired || state.value.workoutId.isBlank()) return false
        mutableState.value = state.value.copy(saving = true, finishConfirmation = false, error = null)
        val workoutId = state.value.workoutId
        if (committedFinishWorkoutId != workoutId) {
            val committed = runCatching { repository.finishWorkout(workoutId) }
            if (committed.isFailure) {
                fail(committed.exceptionOrNull()?.message ?: "Не удалось завершить тренировку")
                return false
            }
            committedFinishWorkoutId = workoutId
        }
        return restoreFinish(workoutId)
    }

    /** Restores the finish UI exclusively from committed repository state. */
    suspend fun restoreFinish(workoutId: String): Boolean = runCatching {
        val details = repository.workoutDetails(workoutId)
        require(details.runtime.completionStatus in setOf("completed", "ended_early")) { "Workout is not finished" }
        val finished = requireNotNull(details.runtime.finishedAt).let(Instant::parse)
        val restoredPlan = StrictJson.decodeFromString<PlannedWorkoutDocument>(details.runtime.planSnapshotJson)
        val work = details.sets.filter { it.setType == "work" }
        val counts = restoredPlan.blocks.flatMap { it.exercises }.mapNotNull { exercise ->
            val count = work.count { it.exerciseInstanceId == exercise.exerciseInstanceId }
            count.takeIf { it > 0 }?.let { ExerciseFinishSummary(exercise.title, it) }
        }
        plan = restoredPlan
        startedAt = details.runtime.startedAt.takeIf(String::isNotBlank)?.let(Instant::parse) ?: Instant.EPOCH
        committedFinishWorkoutId = workoutId
        mutableState.value = state.value.copy(
            workoutId = workoutId,
            saving = false,
            error = null,
            finishConfirmation = false,
            notes = details.runtime.notes,
            finishSummary = FinishSummary(
                java.time.Duration.between(startedAt, finished).seconds.coerceAtLeast(0),
                work.size,
                counts,
                details.runtime.completionStatus == "ended_early",
                plannedWorkCount = restoredPlan.blocks.flatMap { it.exercises }.sumOf { ex -> ex.plannedSets.count { it.setType == "work" } },
                completedPlannedWork = work.count { it.plannedSetNo != null },
                warmupSetCount = details.sets.count { it.setType == "warmup" },
                extraSetCount = details.sets.count { it.plannedSetNo == null },
                skippedSetCount = details.skippedSets.size,
                title = restoredPlan.title,
            ),
        )
    }.onFailure { fail(it.message ?: "Не удалось загрузить итоги тренировки") }.isSuccess
    private fun hasUnresolvedSlots() = exercises().any { currentSlot(it) != null }
    fun setInteraction(exerciseId: String, source: InteractionSource, interacting: Boolean) {
        val block = plan?.blocks?.firstOrNull { candidate ->
            candidate.mode == "rotation" && candidate.exercises.any { it.exerciseInstanceId == exerciseId }
        } ?: return
        val owners = interactionOwners.getOrPut(block.blockId) { mutableSetOf() }
        val wasInteracting = owners.isNotEmpty()
        val owner = InteractionOwner(exerciseId, source)
        if (interacting) owners += owner else owners -= owner
        val isInteracting = owners.isNotEmpty()
        if (!wasInteracting && isInteracting) rotations[block.blockId]?.beginInteraction()
        if (wasInteracting && !isInteracting) {
            rotations[block.blockId]?.endInteraction()
            publish()
        }
    }
    fun onForegroundReturn() { updateRotations(); publish() }
    fun onCommittedSkipOrDelete() { updateRotations(); publish() }

    suspend fun editSet(setResultId: String, weight: Double, reps: Int, rir: Int?, deviations: List<String>, note: String?, context: ActualContext? = null): Boolean {
        if (mutableState.value.saving || mutableState.value.reloadRequired) return false
        val old = sets.firstOrNull { it.setResultId == setResultId } ?: return false
        val actual = context ?: ActualContext(old.exerciseIdActual, old.equipmentIdActual, old.setupActual,
            old.loadBasisActual, old.sideActual, old.equipmentNameActual, old.titleActual)
        if (!weight.isFinite() || weight < 0 || reps < 0 || (rir != null && rir !in 0..4) ||
            (actual.loadBasis == "bodyweight" && weight != 0.0)) { fail("Проверьте введённые значения"); return false }
        return mutate { repository.editSet(EditSetCommand(setResultId, weight, reps, rir, clock.wallNow().toString(),
            deviations.distinct(), note?.trim()?.ifEmpty { null }, actual.equipmentId, actual.equipmentName,
            actual.setup, actual.loadBasis, actual.side, actual.exerciseId, actual.title ?: old.titleActual)) }
    }

    suspend fun deleteSet(setResultId: String): Boolean {
        if (mutableState.value.saving || mutableState.value.reloadRequired || sets.none { it.setResultId == setResultId }) return false
        return mutate { repository.deleteSet(setResultId) }
    }

    suspend fun skipSet(exerciseId: String, plannedSetNo: Int, reason: String?, note: String?): Boolean {
        if (mutableState.value.saving || mutableState.value.reloadRequired) return false
        val exercise = exercises().firstOrNull { it.exerciseInstanceId == exerciseId } ?: return false
        if (currentSlot(exercise)?.plannedSetNo != plannedSetNo) return false
        return mutate { repository.skipSet(SkipSetCommand(state.value.workoutId, exerciseId, plannedSetNo,
            clock.wallNow().toString(), reason, note?.trim()?.ifEmpty { null })) }
    }

    suspend fun skipRemainingSets(exerciseId: String, reason: String?, note: String?): Boolean {
        if (state.value.saving || state.value.reloadRequired || exercises().none { it.exerciseInstanceId == exerciseId }) return false
        return mutate { repository.skipRemainingSets(state.value.workoutId, exerciseId,
            clock.wallNow().toString(), reason, note) }
    }

    suspend fun restoreSkippedSet(exerciseId: String, plannedSetNo: Int) {
        if (mutableState.value.saving || mutableState.value.reloadRequired || skips.none { it.exerciseInstanceId == exerciseId && it.plannedSetNo == plannedSetNo }) return
        mutate { repository.restoreSkippedSet(state.value.workoutId, exerciseId, plannedSetNo) }
    }

    private suspend fun mutate(action: suspend () -> Unit): Boolean {
        mutableState.value = state.value.copy(saving = true, error = null)
        var committed = false
        return runCatching {
            action()
            committed = true
            refreshCommittedFacts()
        }.fold({ true }, { mutationFailed(it, committed); false })
    }

    private fun updateRotations() = rotations.forEach { (blockId, coordinator) ->
        val block = plan!!.blocks.first { it.blockId == blockId }
        coordinator.update(runtimeCards(block.exercises), readClock(), committed = true)
    }

    private fun publish() {
        val p = plan ?: return
        val now = readClock()
        mutableState.value = mutableState.value.copy(
            title = p.title, now = now, planNotes = p.notes,
            blocks = p.blocks.map { block ->
                val byId = block.exercises.associateBy { it.exerciseInstanceId }
                val order = rotations[block.blockId]?.cards?.map { it.exerciseInstanceId }
                    ?: block.exercises.map { it.exerciseInstanceId }
                BlockUiState(block.blockId, block.title, block.mode, order.mapNotNull { id -> byId[id]?.let { ex ->
                val slot = currentSlot(ex)
                val saved = sets.filter { it.exerciseInstanceId == id }
                val lastPlanned = saved.filter { it.plannedSetNo != null }.maxByOrNull { it.sequenceNo }
                val rest = lastPlanned?.plannedSetNo?.let { no -> ex.plannedSets.firstOrNull { it.setNo == no }?.restTargetSec }
                ExerciseUiState(ex, slot, slot?.let { resolvedDraft(ex, it) }, saved, rest,
                    skips.filter { it.exerciseInstanceId == id }, replacementEquipment,
                    ex.equipmentId?.let(equipmentPhotos::get), exercises())
            } }, block.notes) },
        )
    }

    private fun fail(message: String) { mutableState.value = mutableState.value.copy(saving = false, error = message) }
    private fun readClock() = ClockReading(clock.wallNow(), clock.bootIdOrNull(), clock.elapsedRealtimeMs())
    private fun exercises() = plan?.blocks?.flatMap { it.exercises }.orEmpty()
    private fun ExerciseDocument.resolvedContext(): ActualContext {
        val equipment = equipmentId?.let(equipmentAtStart::get)
        return ActualContext(exerciseId, equipmentId, setupHint ?: equipment?.setupHint, loadBasis, side, equipment?.name)
    }
    private fun ExerciseDocument.slots() = plannedSets.map { PlannedSlot(it.setNo, it.setType, it.targetWeightKg,
        it.repsMin, it.repsMax, it.targetRir, it.restTargetSec, resolvedContext()) }
    private fun currentSlot(exercise: ExerciseDocument) = exercise.slots().firstOrNull { slot ->
        sets.none { it.exerciseInstanceId == exercise.exerciseInstanceId && it.plannedSetNo == slot.plannedSetNo }
            && skips.none { it.exerciseInstanceId == exercise.exerciseInstanceId && it.plannedSetNo == slot.plannedSetNo }
    }
    private fun resolvedDraft(exercise: ExerciseDocument, slot: PlannedSlot): SetDraft {
        val slots = exercise.slots(); val index = slots.indexOf(slot); val previous = slots.getOrNull(index - 1)
        val fact = previous?.let { prior -> sets.lastOrNull { it.exerciseInstanceId == exercise.exerciseInstanceId && it.plannedSetNo == prior.plannedSetNo } }
        val actual = latestContext(exercise) ?: exercise.resolvedContext()
        return PrefillResolver.resolve(slot, previous, fact?.let { PreviousSetFact(priorNo(it), it.weightKg, it.reps, it.rir,
            it.context(exercise)) },
            drafts[exercise.exerciseInstanceId to slot.plannedSetNo], actual)
    }
    private fun latestContext(exercise: ExerciseDocument) = sets.filter { it.exerciseInstanceId == exercise.exerciseInstanceId }
        .maxByOrNull { it.sequenceNo }?.context(exercise)
    private fun SetResultEntity.context(exercise: ExerciseDocument) = ActualContext(exerciseIdActual,
        equipmentIdActual, setupActual, loadBasisActual, sideActual, equipmentNameActual,
        titleActual.takeIf { exerciseIdActual != exercise.exerciseId || it != exercise.title })
    private fun priorNo(set: SetResultEntity) = requireNotNull(set.plannedSetNo)
    private fun runtimeCards(exercises: List<ExerciseDocument>) = exercises.map { exercise ->
        val own = sets.filter { it.exerciseInstanceId == exercise.exerciseInstanceId }
        val latest = own.maxByOrNull { it.sequenceNo }
        ExerciseRuntimeCard(exercise.exerciseInstanceId, exercise.plannedOrder, latest?.let {
            ClockAnchor(Instant.parse(it.completedAt), it.bootId, it.elapsedRealtimeMs)
        }, currentSlot(exercise) == null)
    }

    fun elapsedText(set: SetResultEntity?, now: ClockReading = state.value.now): String {
        if (set == null) return ""
        val elapsed = elapsedBetween(ClockAnchor(Instant.parse(set.completedAt), set.bootId, set.elapsedRealtimeMs), now.anchor())
        val seconds = elapsed.duration.seconds
        val formatted = if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
            else "%d:%02d".format(seconds / 60, seconds % 60)
        return (if (elapsed.approximate) "≈ " else "") + formatted
    }

    private fun requiresRir(exercise: ExerciseDocument, slot: PlannedSlot): Boolean = when (exercise.rirCapture) {
        "all_work_sets" -> slot.setType == "work"
        "last_work_set" -> slot.setType == "work" && slot.plannedSetNo == exercise.plannedSets.lastOrNull { it.setType == "work" }?.setNo
        else -> false
    }
}
