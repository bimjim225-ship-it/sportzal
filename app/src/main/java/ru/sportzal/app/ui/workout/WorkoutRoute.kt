package ru.sportzal.app.ui.workout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import ru.sportzal.app.data.repository.VisualRepository

/** Callback wiring lives with the feature instead of the Android Activity. */
@Composable
fun WorkoutRoute(vm: WorkoutViewModel, state: WorkoutUiState, focused: String?,
    onBack: () -> Unit, onSelectExercise: (String) -> Unit, onCancelEmpty: () -> Unit, visuals: VisualRepository) {
    val scope = rememberCoroutineScope()
    WorkoutScreen(state, vm::elapsedText,
        onDraft = { id, weight, reps, rir, answered -> scope.launch { vm.updateDraft(id, weight, reps, rir, answered) } },
        onSave = { id, no, weight, reps, rir, answered -> scope.launch { vm.saveSet(id, no, weight, reps, rir, answered) } },
        onInteraction = vm::setInteraction, onTick = vm::tick,
        onEdit = { id, weight, reps, rir, deviations, note, done -> scope.launch { done(vm.editSet(id, weight, reps, rir, deviations, note)) } },
        onDelete = { id, done -> scope.launch { done(vm.deleteSet(id)) } },
        onSkip = { id, no, reason, note, done -> scope.launch { done(vm.skipSet(id, no, reason, note)) } },
        onRestore = { id, no -> scope.launch { vm.restoreSkippedSet(id, no) } },
        onContext = { id, context, done -> scope.launch { done(vm.updateActualContext(id, context)) } },
        onExtra = { id, type, weight, reps, rir, note, done -> scope.launch { done(vm.saveExtraSet(id, type, weight, reps, rir, note)) } },
        onEditActual = { id, weight, reps, rir, deviations, note, context, done -> scope.launch { done(vm.editSet(id, weight, reps, rir, deviations, note, context)) } },
        onReload = { scope.launch { vm.reloadCommittedState() } },
        onSkipRemaining = { id, reason, note, done -> scope.launch { done(vm.skipRemainingSets(id, reason, note)) } },
        onCancelExtra = { id, done -> scope.launch { done(vm.cancelExtraAttempt(id)) } },
        onFinish = { scope.launch { vm.requestFinish() } },
        onConfirmFinish = { scope.launch { vm.confirmFinish() } }, onCancelFinish = vm::cancelFinish,
        onNote = { value, done -> scope.launch { done(vm.updateNotes(value)) } },
        overview = focused == null, focusedExerciseId = focused, onBack = onBack,
        onSelectExercise = onSelectExercise, onCancelEmpty = onCancelEmpty,
        onSelectTechnique = { exercise, equipment, key, done -> scope.launch {
            done(runCatching { visuals.saveTechnique(exercise, equipment, key) }.isSuccess)
        } },
    )
}
