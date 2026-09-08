package ru.sportzal.app.ui.workout

import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.decodeFromString
import ru.sportzal.app.data.db.DraftEntity
import ru.sportzal.app.data.db.SetResultEntity
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
import ru.sportzal.app.model.StrictJson
import ru.sportzal.app.model.elapsedBetween
import ru.sportzal.app.platform.ClockProvider

data class ExerciseUiState(
    val exercise: ExerciseDocument,
    val currentSlot: PlannedSlot?,
    val draft: SetDraft?,
    val saved: List<SetResultEntity>,
    val restReferenceSec: Int? = null,
)
data class BlockUiState(
    val blockId: String,
    val title: String,
    val mode: String,
    val cards: List<ExerciseUiState>,
)
data class WorkoutUiState(
    val workoutId: String = "",
    val title: String = "",
    val blocks: List<BlockUiState> = emptyList(),
    val now: ClockReading = ClockReading(Instant.EPOCH, null, null),
    val saving: Boolean = false,
    val error: String? = null,
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
    private var equipmentAtStart = emptyMap<String, EquipmentDocument>()
    private val drafts = linkedMapOf<Pair<String, Int>, SetDraft>()
    private val sets = mutableListOf<SetResultEntity>()
    private val pending = mutableMapOf<Pair<String, Int>, SaveSetCommand>()
    private val rotations = mutableMapOf<String, RotationCoordinator>()
    private val interactingCards = mutableMapOf<String, MutableSet<String>>()

    suspend fun open(workoutId: String) {
        val details = repository.workoutDetails(workoutId)
        plan = StrictJson.decodeFromString(details.runtime.planSnapshotJson)
        equipmentAtStart = StrictJson.decodeFromString<List<EquipmentDocument>>(details.runtime.equipmentAtStartJson)
            .associateBy { it.equipmentId }
        sets.clear(); sets += details.sets
        drafts.clear()
        val byId = exercises().associateBy { it.exerciseInstanceId }
        details.drafts.forEach { entity ->
            val exercise = byId[entity.exerciseInstanceId] ?: return@forEach
            drafts[entity.exerciseInstanceId to entity.plannedSetNo] = SetDraft(
                entity.weightText.replace(',', '.').toDoubleOrNull(), entity.repsText.toIntOrNull(), entity.rir,
                exercise.resolvedContext(), rirAnswered = entity.rir != null,
            )
        }
        mutableState.value = mutableState.value.copy(workoutId = workoutId)
        rotations.clear()
        interactingCards.clear()
        plan!!.blocks.filter { it.mode == "rotation" }.forEach { block ->
            rotations[block.blockId] = RotationCoordinator(runtimeCards(block.exercises)).also {
                it.update(runtimeCards(block.exercises), readClock(), committed = true)
            }
        }
        publish()
    }

    suspend fun updateDraft(exerciseId: String, weight: Double?, reps: Int?, rir: Int?, rirAnswered: Boolean) {
        if (mutableState.value.saving) return
        val exercise = exercises().first { it.exerciseInstanceId == exerciseId }
        val slot = currentSlot(exercise) ?: return
        val value = SetDraft(if (exercise.loadBasis == "bodyweight") 0.0 else weight, reps, rir,
            exercise.resolvedContext(), rirAnswered)
        drafts[exerciseId to slot.plannedSetNo] = value
        repository.saveDraft(DraftEntity(mutableState.value.workoutId, exerciseId, slot.plannedSetNo,
            value.weightKg?.toString().orEmpty(), value.reps?.toString().orEmpty(), value.rir, null,
            clock.wallNow().toString()))
        publish()
    }

    suspend fun saveSet(exerciseId: String, plannedSetNo: Int) {
        if (mutableState.value.saving) return
        val exercise = exercises().first { it.exerciseInstanceId == exerciseId }
        val slot = currentSlot(exercise)?.takeIf { it.plannedSetNo == plannedSetNo } ?: return
        if (sets.any { it.exerciseInstanceId == exerciseId && it.plannedSetNo == plannedSetNo }) return
        val key = exerciseId to slot.plannedSetNo
        val draft = resolvedDraft(exercise, slot)
        val weight = draft.weightKg ?: return fail("Введите вес")
        val reps = draft.reps ?: return fail("Введите повторы")
        if (requiresRir(exercise, slot) && !draft.rirAnswered) return fail("Выберите RIR или «Не оценил»")
        // Created before entering repository/Room transaction, and retained on every retry.
        val command = pending.getOrPut(key) {
            val reading = readClock()
            val equipment = exercise.equipmentId?.let(equipmentAtStart::get)
            val context = exercise.resolvedContext()
            SaveSetCommand(newId(), mutableState.value.workoutId, exerciseId, slot.plannedSetNo, slot.setType,
                context.exerciseId, exercise.title, context.equipmentId, equipment?.name, context.setup,
                context.loadBasis, context.side, weight, reps, draft.rir, reading.wall.toString(),
                reading.bootId, reading.elapsedRealtimeMs)
        }
        mutableState.value = mutableState.value.copy(saving = true, error = null)
        runCatching { repository.saveSet(command) }.onSuccess { result ->
            when (result) {
                is SaveSetResult.Saved -> {
                    pending.remove(key); drafts.remove(key)
                    // Re-read committed state: the row is not presented before commit.
                    val details = repository.workoutDetails(command.workoutId)
                    sets.clear(); sets += details.sets
                    rotations.forEach { (blockId, coordinator) ->
                        val block = plan!!.blocks.first { it.blockId == blockId }
                        coordinator.update(runtimeCards(block.exercises), readClock(), committed = true)
                    }
                    mutableState.value = mutableState.value.copy(saving = false)
                    publish()
                }
                is SaveSetResult.Conflict -> fail(result.message)
            }
        }.onFailure { fail(it.message ?: "Не удалось записать подход") }
    }

    fun tick() { publish() }
    fun setInteraction(exerciseId: String, interacting: Boolean) {
        val block = plan?.blocks?.firstOrNull { candidate ->
            candidate.mode == "rotation" && candidate.exercises.any { it.exerciseInstanceId == exerciseId }
        } ?: return
        val owners = interactingCards.getOrPut(block.blockId) { mutableSetOf() }
        val wasInteracting = owners.isNotEmpty()
        if (interacting) owners += exerciseId else owners -= exerciseId
        val isInteracting = owners.isNotEmpty()
        if (!wasInteracting && isInteracting) rotations[block.blockId]?.beginInteraction()
        if (wasInteracting && !isInteracting) {
            rotations[block.blockId]?.endInteraction()
            publish()
        }
    }
    fun onForegroundReturn() { updateRotations(); publish() }
    fun onCommittedSkipOrDelete() { updateRotations(); publish() }

    private fun updateRotations() = rotations.forEach { (blockId, coordinator) ->
        val block = plan!!.blocks.first { it.blockId == blockId }
        coordinator.update(runtimeCards(block.exercises), readClock(), committed = true)
    }

    private fun publish() {
        val p = plan ?: return
        val now = readClock()
        mutableState.value = mutableState.value.copy(
            title = p.title, now = now,
            blocks = p.blocks.map { block ->
                val byId = block.exercises.associateBy { it.exerciseInstanceId }
                val order = rotations[block.blockId]?.cards?.map { it.exerciseInstanceId }
                    ?: block.exercises.map { it.exerciseInstanceId }
                BlockUiState(block.blockId, block.title, block.mode, order.mapNotNull { id -> byId[id]?.let { ex ->
                val slot = currentSlot(ex)
                val saved = sets.filter { it.exerciseInstanceId == id }
                val lastPlanned = saved.filter { it.plannedSetNo != null }.maxByOrNull { it.sequenceNo }
                val rest = lastPlanned?.plannedSetNo?.let { no -> ex.plannedSets.firstOrNull { it.setNo == no }?.restTargetSec }
                ExerciseUiState(ex, slot, slot?.let { resolvedDraft(ex, it) }, saved, rest)
            } }) },
        )
    }

    private fun fail(message: String) { mutableState.value = mutableState.value.copy(saving = false, error = message) }
    private fun readClock() = ClockReading(clock.wallNow(), clock.bootIdOrNull(), clock.elapsedRealtimeMs())
    private fun exercises() = plan?.blocks?.flatMap { it.exercises }.orEmpty()
    private fun ExerciseDocument.resolvedContext() = ActualContext(exerciseId, equipmentId,
        setupHint ?: equipmentId?.let(equipmentAtStart::get)?.setupHint, loadBasis, side)
    private fun ExerciseDocument.slots() = plannedSets.map { PlannedSlot(it.setNo, it.setType, it.targetWeightKg,
        it.repsMin, it.repsMax, it.targetRir, it.restTargetSec, resolvedContext()) }
    private fun currentSlot(exercise: ExerciseDocument) = exercise.slots().firstOrNull { slot ->
        sets.none { it.exerciseInstanceId == exercise.exerciseInstanceId && it.plannedSetNo == slot.plannedSetNo }
    }
    private fun resolvedDraft(exercise: ExerciseDocument, slot: PlannedSlot): SetDraft {
        val slots = exercise.slots(); val index = slots.indexOf(slot); val previous = slots.getOrNull(index - 1)
        val fact = previous?.let { prior -> sets.lastOrNull { it.exerciseInstanceId == exercise.exerciseInstanceId && it.plannedSetNo == prior.plannedSetNo } }
        return PrefillResolver.resolve(slot, previous, fact?.let { PreviousSetFact(priorNo(it), it.weightKg, it.reps, it.rir,
            ActualContext(it.exerciseIdActual, it.equipmentIdActual, it.setupActual, it.loadBasisActual, it.sideActual)) },
            drafts[exercise.exerciseInstanceId to slot.plannedSetNo], exercise.resolvedContext())
    }
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
        val seconds = elapsedBetween(ClockAnchor(Instant.parse(set.completedAt), set.bootId, set.elapsedRealtimeMs), now.anchor()).duration.seconds
        return "%d:%02d".format(seconds / 60, seconds % 60)
    }

    private fun requiresRir(exercise: ExerciseDocument, slot: PlannedSlot): Boolean = when (exercise.rirCapture) {
        "all_work_sets" -> slot.setType == "work"
        "last_work_set" -> slot.setType == "work" && slot.plannedSetNo == exercise.plannedSets.lastOrNull { it.setType == "work" }?.setNo
        else -> false
    }
}
