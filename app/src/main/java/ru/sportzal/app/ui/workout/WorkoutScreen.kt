package ru.sportzal.app.ui.workout

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import ru.sportzal.app.ui.components.ExerciseCard
import ru.sportzal.app.domain.ActualContext
import ru.sportzal.app.data.files.EquipmentPhotoStore
import ru.sportzal.app.ui.components.AppHeader
import ru.sportzal.app.ui.media.ExerciseMediaPanel

@Composable
fun WorkoutScreen(
    state: WorkoutUiState,
    elapsedFor: (ru.sportzal.app.data.db.SetResultEntity?) -> String,
    onDraft: (String, Double?, Int?, Int?, Boolean) -> Unit,
    onSave: (String, Int, Double?, Int?, Int?, Boolean) -> Unit,
    onInteraction: (String, InteractionSource, Boolean) -> Unit,
    onTick: () -> Unit,
    onEdit: (String, Double, Int, Int?, List<String>, String?, (Boolean) -> Unit) -> Unit = { _, _, _, _, _, _, _ -> },
    onDelete: (String, (Boolean) -> Unit) -> Unit = { _, _ -> },
    onSkip: (String, Int, String?, String?, (Boolean) -> Unit) -> Unit = { _, _, _, _, _ -> },
    onRestore: (String, Int) -> Unit = { _, _ -> },
    onContext: (String, ActualContext, (Boolean) -> Unit) -> Unit = { _, _, _ -> },
    onExtra: (String, String, Double, Int, Int?, String?, (Boolean) -> Unit) -> Unit = { _, _, _, _, _, _, _ -> },
    onEditActual: ((String, Double, Int, Int?, List<String>, String?, ActualContext, (Boolean) -> Unit) -> Unit)? = null,
    onFinish: () -> Unit = {},
    onConfirmFinish: () -> Unit = {},
    onCancelFinish: () -> Unit = {},
    onNote: (String, (Boolean) -> Unit) -> Unit = { _, done -> done(true) },
    photoStore: EquipmentPhotoStore? = null,
    overview: Boolean = false,
    focusedExerciseId: String? = null,
    onBack: () -> Unit = {},
    onSelectExercise: (String) -> Unit = {},
    onCancelEmpty: () -> Unit = {},
    onSelectTechnique: (String, String?, String?, (Boolean) -> Unit) -> Unit = { _, _, _, done -> done(false) },
    onReload: () -> Unit = {},
    onSkipRemaining: (String, String?, String?, (Boolean) -> Unit) -> Unit = { _, _, _, _ -> },
    onCancelExtra: (String, (Boolean) -> Unit) -> Unit = { _, done -> done(true) },
) {
    var noteDialog by rememberSaveable { mutableStateOf(false) }
    var quickGuide by rememberSaveable(focusedExerciseId) { mutableStateOf(false) }
    val selected = state.blocks.flatMap { it.cards }.firstOrNull { it.exercise.exerciseInstanceId == focusedExerciseId }
    if (state.finishConfirmation) AlertDialog(onDismissRequest = onCancelFinish,
        text = { Text("Завершить с невыполненными подходами?") },
        confirmButton = { TextButton(onClick = onConfirmFinish) { Text("Завершить") } },
        dismissButton = { TextButton(onClick = onCancelFinish) { Text("Отмена") } })
    LaunchedEffect(Unit) { while (true) { delay(1_000); onTick() } }
    if (overview) WorkoutOverview(state, onBack, onSelectExercise, onFinish, onCancelEmpty, elapsedFor)
    else BoxWithConstraints(Modifier.fillMaxSize()) {
      val compactNavigation = maxHeight < 500.dp || LocalDensity.current.fontScale > 1.3f
      Column(Modifier.fillMaxSize()) {
      if (focusedExerciseId != null) {
          AppHeader(if (compactNavigation) "Подход" else selected?.actualContext()?.title ?: selected?.exercise?.title ?: "Упражнение", onBack = onBack, compact = true,
              action = { TextButton({ quickGuide = true }, enabled = selected != null) { Text("Схема") } })
      }
      LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 8.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            if (focusedExerciseId == null) Text(state.title, style = MaterialTheme.typography.headlineLarge)
            else if (compactNavigation) Text(selected?.actualContext()?.title ?: selected?.exercise?.title ?: "Упражнение",
                style = MaterialTheme.typography.titleLarge)
        }
        if (focusedExerciseId == null) item { TextButton({ noteDialog = true }) { Text("Заметка к тренировке") }; state.notes?.let { Text(it) } }
        if (focusedExerciseId == null) state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        state.blocks.forEach { block ->
            val cards = block.cards.filter { focusedExerciseId == null || it.exercise.exerciseInstanceId == focusedExerciseId }
            if (cards.isNotEmpty() && focusedExerciseId == null) item(key = "block-${block.blockId}") {
                Text(block.title, style = MaterialTheme.typography.titleMedium)
                block.notes?.let { Text(it) }
            }
            items(cards, key = { it.exercise.exerciseInstanceId }) { card ->
                ExerciseCard(card, elapsedFor(card.saved.maxByOrNull { it.sequenceNo }), state.saving,
                    { weight, reps, rir, answered -> onDraft(card.exercise.exerciseInstanceId, weight, reps, rir, answered) },
                    { weight, reps, rir, answered -> card.currentSlot?.let {
                        onSave(card.exercise.exerciseInstanceId, it.plannedSetNo, weight, reps, rir, answered)
                    } },
                    { source, interacting -> onInteraction(card.exercise.exerciseInstanceId, source, interacting) },
                    onEdit, onDelete,
                    { setNo, reason, note, completed -> onSkip(card.exercise.exerciseInstanceId, setNo, reason, note, completed) },
                    { setNo -> onRestore(card.exercise.exerciseInstanceId, setNo) },
                    { context, completed -> onContext(card.exercise.exerciseInstanceId, context, completed) },
                    { type, weight, reps, rir, note, completed -> onExtra(card.exercise.exerciseInstanceId, type, weight, reps, rir, note, completed) },
                    onEditActual, photoStore, focused = focusedExerciseId != null,
                    error = state.error.takeIf { focusedExerciseId != null }, reloadRequired = state.reloadRequired,
                    onReload = onReload,
                    onSkipRemaining = { reason, note, done -> onSkipRemaining(card.exercise.exerciseInstanceId, reason, note, done) },
                    onCancelExtra = { done -> onCancelExtra(card.exercise.exerciseInstanceId, done) })
            }
            if (focusedExerciseId != null) cards.forEach { card -> item(key = "media-${card.exercise.exerciseInstanceId}") {
                var expanded by rememberSaveable(card.exercise.exerciseInstanceId) { mutableStateOf(false) }
                TextButton({ expanded = !expanded }, modifier = Modifier.testTag("toggle-technique")) { Text("Настройка и техника") }
                if (expanded) {
                    val context = card.actualContext()
                    ExerciseMediaPanel(context.exerciseId, context.equipmentId, context.setup,
                        card.exercise.notes.takeIf { context.exerciseId == card.exercise.exerciseId }, state.saving || state.reloadRequired) { key, done ->
                        onSelectTechnique(context.exerciseId, context.equipmentId, key, done)
                    }
                    block.notes?.let { Text(it) }
                }
            } }
        }
        item {
            if (focusedExerciseId != null) ru.sportzal.app.ui.components.SportzalButton("К упражнениям", onBack, secondary = true, enabled = !state.saving)
            else Button(onClick = onFinish, enabled = !state.saving) { Text("Завершить тренировку") }
        }
        if (focusedExerciseId != null) item { TextButton({ noteDialog = true }) { Text("Заметка к тренировке") } }
      }
      }
    }
    if (noteDialog) WorkoutNoteDialog(state.notes.orEmpty(), state.saving, { noteDialog = false }) { value ->
        onNote(value) { if (it) noteDialog = false }
    }
    if (quickGuide && selected != null) {
        val context = selected.actualContext()
        AlertDialog(onDismissRequest = { quickGuide = false },
            title = { Text("Настройка и техника") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                ExerciseMediaPanel(context.exerciseId, context.equipmentId, context.setup,
                    selected.exercise.notes.takeIf { context.exerciseId == selected.exercise.exerciseId },
                    state.saving || state.reloadRequired) { key, done ->
                    onSelectTechnique(context.exerciseId, context.equipmentId, key, done)
                }
            } }, confirmButton = { TextButton({ quickGuide = false }) { Text("Закрыть") } })
    }
}

@Composable private fun WorkoutNoteDialog(initial: String, saving: Boolean, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, title = { Text("Заметка к тренировке") },
        text = { androidx.compose.material3.OutlinedTextField(value, { value = it }, minLines = 3) },
        dismissButton = { TextButton(onDismiss, enabled = !saving) { Text("Отмена") } },
        confirmButton = { TextButton({ onSave(value) }, enabled = !saving) { Text("Сохранить") } })
}
