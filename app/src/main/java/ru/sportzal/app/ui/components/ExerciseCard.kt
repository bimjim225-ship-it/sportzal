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
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import ru.sportzal.app.ui.workout.ExerciseUiState

@Composable
fun ExerciseCard(
    state: ExerciseUiState,
    elapsed: String,
    saving: Boolean,
    onDraft: (Double?, Int?, Int?, Boolean) -> Unit,
    onSave: () -> Unit,
    onInteraction: (Boolean) -> Unit,
    onEdit: (String, Double, Int, Int?, List<String>, String?, (Boolean) -> Unit) -> Unit,
    onDelete: (String, (Boolean) -> Unit) -> Unit,
    onSkip: (Int, String?, String?, (Boolean) -> Unit) -> Unit,
    onRestore: (Int) -> Unit,
) {
    val slot = state.currentSlot
    val draft = state.draft
    var weightText by remember(state.exercise.exerciseInstanceId, slot?.plannedSetNo, draft?.weightKg) {
        mutableStateOf(draft?.weightKg?.display().orEmpty())
    }
    var repsText by remember(state.exercise.exerciseInstanceId, slot?.plannedSetNo, draft?.reps) {
        mutableStateOf(draft?.reps?.toString().orEmpty())
    }
    var skipDialog by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().onFocusChanged { onInteraction(it.hasFocus) }.focusGroup()
        .testTag("exercise-${state.exercise.exerciseInstanceId}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(state.exercise.title, style = MaterialTheme.typography.headlineSmall)
            Text(listOfNotNull(state.exercise.equipmentId, slot?.plannedContext?.setup, state.exercise.loadBasis, state.exercise.side).joinToString(" · "))
            Text("Подходы: ${state.saved.size}/${state.exercise.plannedSets.size}")
            state.saved.forEach { result -> SetResultRow(result, saving, requiresRir(state, result.plannedSetNo), onInteraction,
                { weight, reps, rir, deviations, note, completed ->
                    onEdit(result.setResultId, weight, reps, rir, deviations, note, completed)
                },
                { completed -> onDelete(result.setResultId, completed) }) }
            state.skipped.sortedBy { it.plannedSetNo }.forEach { skipped ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${skipped.plannedSetNo}. Пропущено" + (skipped.reason?.let { " · ${skipReasonLabels[it] ?: it}" } ?: ""))
                    TextButton({ onRestore(skipped.plannedSetNo) }, enabled = !saving,
                        modifier = Modifier.testTag("restore-${state.exercise.exerciseInstanceId}-${skipped.plannedSetNo}")) { Text("Вернуть подход") }
                }
            }
            if (slot != null && draft != null) {
                Text("Подход ${slot.plannedSetNo} · ${slot.setType}")
                Text("Цель: ${slot.targetWeightKg.display()} кг · ${slot.repsMin}–${slot.repsMax}" + (slot.targetRir?.let { " · RIR ${it.rirText()}" } ?: ""))
                val rest = state.restReferenceSec ?: slot.restTargetSec
                Text("Ориентир до следующего подхода: ${rest / 60}:${(rest % 60).toString().padStart(2, '0')}")
                Text(if (state.saved.isEmpty()) "Ещё не записывали" else "С записи: $elapsed",
                    modifier = Modifier.testTag("elapsed-${state.exercise.exerciseInstanceId}"))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumericField(weightText, "Вес", NumericKind.WEIGHT, {
                        weightText = it; onDraft(parseNumeric(it, NumericKind.WEIGHT)?.toDouble(), parseNumeric(repsText, NumericKind.REPS)?.toInt(), draft.rir, draft.rirAnswered)
                    }, Modifier.testTag("weight-${state.exercise.exerciseInstanceId}"), enabled = !saving && state.exercise.loadBasis != "bodyweight")
                    NumericField(repsText, "Повторы", NumericKind.REPS, {
                        repsText = it; onDraft(parseNumeric(weightText, NumericKind.WEIGHT)?.toDouble(), parseNumeric(it, NumericKind.REPS)?.toInt(), draft.rir, draft.rirAnswered)
                    }, Modifier.testTag("reps-${state.exercise.exerciseInstanceId}"), enabled = !saving)
                }
                if (requiresRir(state)) RirSelector(draft.rir, draft.rirAnswered, enabled = !saving) {
                    onDraft(parseNumeric(weightText, NumericKind.WEIGHT)?.toDouble(), parseNumeric(repsText, NumericKind.REPS)?.toInt(), it, true)
                }
                Button(onClick = onSave, enabled = !saving, modifier = Modifier.testTag("save-${state.exercise.exerciseInstanceId}")) {
                    Text("Записать подход")
                }
                TextButton({ skipDialog = true; onInteraction(true) }, enabled = !saving,
                    modifier = Modifier.testTag("skip-${state.exercise.exerciseInstanceId}")) { Text("Пропустить подход") }
            } else Text("Все подходы записаны")
        }
    }
    if (skipDialog && slot != null) SkipSetDialog(saving, {
        skipDialog = false; onInteraction(false)
    }) { reason, note ->
        onSkip(slot.plannedSetNo, reason, note) { committed ->
            if (committed) {
                skipDialog = false
                onInteraction(false)
            }
        }
    }
}

private fun requiresRir(state: ExerciseUiState, setNo: Int? = state.currentSlot?.plannedSetNo): Boolean = when (state.exercise.rirCapture) {
    "all_work_sets" -> state.exercise.plannedSets.firstOrNull { it.setNo == setNo }?.setType == "work"
    "last_work_set" -> setNo == state.exercise.plannedSets.lastOrNull { it.setType == "work" }?.setNo
    else -> false
}
private val skipReasonLabels = linkedMapOf<String?, String>(null to "Без причины", "equipment_busy" to "Оборудование занято",
    "time_limit" to "Не хватает времени", "fatigue" to "Усталость", "discomfort" to "Дискомфорт", "other" to "Другое")

@Composable private fun SkipSetDialog(saving: Boolean, onDismiss: () -> Unit, onSkip: (String?, String?) -> Unit) {
    var reason by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, title = { Text("Пропустить подход") }, text = {
        Column { skipReasonLabels.forEach { (value, label) -> FilterChip(reason == value, { reason = value }, { Text(label) },
            modifier = Modifier.testTag("skip-reason-${value ?: "none"}")) }
            OutlinedTextField(note, { note = it }, label = { Text("Комментарий") }) }
    }, dismissButton = { TextButton(onDismiss, enabled = !saving) { Text("Отмена") } }, confirmButton = {
        TextButton({ onSkip(reason, note) }, enabled = !saving, modifier = Modifier.testTag("confirm-skip")) { Text("Пропустить") }
    })
}
private fun Double.display() = if (this % 1.0 == 0.0) toInt().toString() else toString()
internal fun Int.rirText() = if (this == 4) "4+" else toString()
