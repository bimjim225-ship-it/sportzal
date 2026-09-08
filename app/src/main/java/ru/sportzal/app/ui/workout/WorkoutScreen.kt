package ru.sportzal.app.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
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
    onDraft: (String, Double?, Int?, Int?) -> Unit,
    onSave: (String) -> Unit,
    onInteraction: (Boolean) -> Unit,
    onTick: () -> Unit,
) {
    LaunchedEffect(Unit) { while (true) { delay(1_000); onTick() } }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(state.title, style = MaterialTheme.typography.headlineLarge) }
        state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        items(state.cards, key = { it.exercise.exerciseInstanceId }) { card ->
            ExerciseCard(card, elapsedFor(card.saved.maxByOrNull { it.sequenceNo }), state.saving,
                { weight, reps, rir -> onDraft(card.exercise.exerciseInstanceId, weight, reps, rir) },
                { onSave(card.exercise.exerciseInstanceId) }, onInteraction)
        }
    }
}
