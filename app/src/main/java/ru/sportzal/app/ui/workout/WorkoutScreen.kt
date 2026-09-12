package ru.sportzal.app.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import ru.sportzal.app.ui.components.ExerciseCard
import ru.sportzal.app.domain.ActualContext
import ru.sportzal.app.data.files.EquipmentPhotoStore

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
) {
    var noteDialog by remember { mutableStateOf(false) }
    if (state.finishConfirmation) AlertDialog(onDismissRequest = onCancelFinish,
        text = { Text("Завершить с невыполненными подходами?") },
        confirmButton = { TextButton(onClick = onConfirmFinish) { Text("Завершить") } },
        dismissButton = { TextButton(onClick = onCancelFinish) { Text("Отмена") } })
    LaunchedEffect(Unit) { while (true) { delay(1_000); onTick() } }
    LazyColumn(Modifier.fillMaxSize().imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(state.title, style = MaterialTheme.typography.headlineLarge) }
        item { TextButton({ noteDialog = true }) { Text("Заметка к тренировке") }; state.notes?.let { Text(it) } }
        state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        state.blocks.forEach { block ->
            item(key = "block-${block.blockId}") { Text(block.title, style = MaterialTheme.typography.titleMedium) }
            items(block.cards, key = { it.exercise.exerciseInstanceId }) { card ->
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
                    onEditActual, photoStore)
            }
        }
        item { Button(onClick = onFinish, enabled = !state.saving) { Text("Завершить тренировку") } }
    }
    if (noteDialog) WorkoutNoteDialog(state.notes.orEmpty(), state.saving, { noteDialog = false }) { value ->
        onNote(value) { if (it) noteDialog = false }
    }
}

@Composable private fun WorkoutNoteDialog(initial: String, saving: Boolean, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, title = { Text("Заметка к тренировке") },
        text = { androidx.compose.material3.OutlinedTextField(value, { value = it }, minLines = 3) },
        dismissButton = { TextButton(onDismiss, enabled = !saving) { Text("Отмена") } },
        confirmButton = { TextButton({ onSave(value) }, enabled = !saving) { Text("Сохранить") } })
}
