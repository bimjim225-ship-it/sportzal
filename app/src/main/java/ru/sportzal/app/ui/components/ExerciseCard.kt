package ru.sportzal.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import ru.sportzal.app.ui.workout.ExerciseUiState

@Composable
fun ExerciseCard(
    state: ExerciseUiState,
    elapsed: String,
    saving: Boolean,
    onDraft: (Double?, Int?, Int?) -> Unit,
    onSave: () -> Unit,
    onInteraction: (Boolean) -> Unit,
) {
    val slot = state.currentSlot
    val draft = state.draft
    var weightText by remember(state.exercise.exerciseInstanceId, slot?.plannedSetNo, draft?.weightKg) {
        mutableStateOf(draft?.weightKg?.display().orEmpty())
    }
    var repsText by remember(state.exercise.exerciseInstanceId, slot?.plannedSetNo, draft?.reps) {
        mutableStateOf(draft?.reps?.toString().orEmpty())
    }
    Card(Modifier.fillMaxWidth().testTag("exercise-${state.exercise.exerciseInstanceId}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(state.exercise.title, style = MaterialTheme.typography.headlineSmall)
            Text(listOfNotNull(state.exercise.equipmentId, state.exercise.setupHint, state.exercise.loadBasis, state.exercise.side).joinToString(" · "))
            Text("Подходы: ${state.saved.size}/${state.exercise.plannedSets.size}")
            state.saved.forEach { SetResultRow(it) }
            if (slot != null && draft != null) {
                Text("Подход ${slot.plannedSetNo} · ${slot.setType}")
                Text("Цель: ${slot.targetWeightKg.display()} кг · ${slot.repsMin}–${slot.repsMax}" + (slot.targetRir?.let { " · RIR $it" } ?: ""))
                Text("Ориентир до следующего подхода: ${slot.restTargetSec / 60}:${(slot.restTargetSec % 60).toString().padStart(2, '0')}")
                Text("С записи: $elapsed", modifier = Modifier.testTag("elapsed-${state.exercise.exerciseInstanceId}"))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumericField(weightText, "Вес", NumericKind.WEIGHT, {
                        weightText = it; onDraft(parseNumeric(it, NumericKind.WEIGHT)?.toDouble(), parseNumeric(repsText, NumericKind.REPS)?.toInt(), draft.rir)
                    }, Modifier.onFocusChanged { onInteraction(it.hasFocus) }.testTag("weight-${state.exercise.exerciseInstanceId}"), enabled = state.exercise.loadBasis != "bodyweight")
                    NumericField(repsText, "Повторы", NumericKind.REPS, {
                        repsText = it; onDraft(parseNumeric(weightText, NumericKind.WEIGHT)?.toDouble(), parseNumeric(it, NumericKind.REPS)?.toInt(), draft.rir)
                    }, Modifier.onFocusChanged { onInteraction(it.hasFocus) }.testTag("reps-${state.exercise.exerciseInstanceId}"))
                }
                if (requiresRir(state)) RirSelector(draft.rir) {
                    onDraft(parseNumeric(weightText, NumericKind.WEIGHT)?.toDouble(), parseNumeric(repsText, NumericKind.REPS)?.toInt(), it)
                }
                Button(onClick = onSave, enabled = !saving, modifier = Modifier.testTag("save-${state.exercise.exerciseInstanceId}")) {
                    Text("Записать подход")
                }
            } else Text("Все подходы записаны")
        }
    }
}

private fun requiresRir(state: ExerciseUiState): Boolean = when (state.exercise.rirCapture) {
    "all_work_sets" -> state.currentSlot?.setType == "work"
    "last_work_set" -> state.currentSlot?.setType == "work" && state.currentSlot.plannedSetNo == state.exercise.plannedSets.lastOrNull { it.setType == "work" }?.setNo
    else -> false
}
private fun Double.display() = if (this % 1.0 == 0.0) toInt().toString() else toString()
