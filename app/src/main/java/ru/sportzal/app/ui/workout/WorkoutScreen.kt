package ru.sportzal.app.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import ru.sportzal.app.ui.components.ExerciseCard

@Composable
fun WorkoutScreen(
    state: WorkoutUiState,
    elapsedFor: (ru.sportzal.app.data.db.SetResultEntity?) -> String,
    onDraft: (String, Double?, Int?, Int?, Boolean) -> Unit,
    onSave: (String, Int) -> Unit,
    onInteraction: (String, InteractionSource, Boolean) -> Unit,
    onTick: () -> Unit,
    onEdit: (String, Double, Int, Int?, List<String>, String?, (Boolean) -> Unit) -> Unit = { _, _, _, _, _, _, _ -> },
    onDelete: (String, (Boolean) -> Unit) -> Unit = { _, _ -> },
    onSkip: (String, Int, String?, String?, (Boolean) -> Unit) -> Unit = { _, _, _, _, _ -> },
    onRestore: (String, Int) -> Unit = { _, _ -> },
) {
    LaunchedEffect(Unit) { while (true) { delay(1_000); onTick() } }
    LazyColumn(Modifier.fillMaxSize().imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(state.title, style = MaterialTheme.typography.headlineLarge) }
        state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        state.blocks.forEach { block ->
            item(key = "block-${block.blockId}") { Text(block.title, style = MaterialTheme.typography.titleMedium) }
            items(block.cards, key = { it.exercise.exerciseInstanceId }) { card ->
                ExerciseCard(card, elapsedFor(card.saved.maxByOrNull { it.sequenceNo }), state.saving,
                    { weight, reps, rir, answered -> onDraft(card.exercise.exerciseInstanceId, weight, reps, rir, answered) },
                    { card.currentSlot?.let { onSave(card.exercise.exerciseInstanceId, it.plannedSetNo) } },
                    { source, interacting -> onInteraction(card.exercise.exerciseInstanceId, source, interacting) },
                    onEdit, onDelete,
                    { setNo, reason, note, completed -> onSkip(card.exercise.exerciseInstanceId, setNo, reason, note, completed) },
                    { setNo -> onRestore(card.exercise.exerciseInstanceId, setNo) })
            }
        }
    }
}
