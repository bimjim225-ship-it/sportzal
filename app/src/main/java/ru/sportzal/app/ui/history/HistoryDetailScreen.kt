package ru.sportzal.app.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import ru.sportzal.app.data.db.SetResultEntity
import ru.sportzal.app.model.CompletionStatus

@Composable fun HistoryDetailScreen(state: HistoryUiState, onBack: () -> Unit, onNote: (String, (Boolean) -> Unit) -> Unit,
    onEdit: (SetResultEntity, Double, Int, Int?, String?, (Boolean) -> Unit) -> Unit,
    onDelete: (String, (Boolean) -> Unit) -> Unit, onShare: () -> Unit) {
    val details = state.details ?: return
    var noteDialog by remember { mutableStateOf(false) }; var editing by remember { mutableStateOf<SetResultEntity?>(null) }
    var deleting by remember { mutableStateOf<SetResultEntity?>(null) }
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { TextButton(onBack) { Text("← История") }; Text(details.plan.title, style = MaterialTheme.typography.headlineLarge) }
        item { Text(formatDetailDate(details.runtime.startedAt)); Text(if (details.runtime.completionStatus == "completed") "Завершена" else "Завершена досрочно") }
        state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        item { TextButton({ noteDialog = true }) { Text("Заметка") }; details.runtime.notes?.let { Text(it) } }
        details.plan.blocks.forEach { block ->
            item { Text(block.title, style = MaterialTheme.typography.titleLarge) }
            block.exercises.forEach { exercise ->
                item { Text(exercise.title, style = MaterialTheme.typography.titleMedium) }
                exercise.plannedSets.forEach { planned ->
                    item {
                        val fact = details.sets.firstOrNull { it.exerciseInstanceId == exercise.exerciseInstanceId && it.plannedSetNo == planned.setNo }
                        val skip = details.skips.firstOrNull { it.exerciseInstanceId == exercise.exerciseInstanceId && it.plannedSetNo == planned.setNo }
                        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text("Подход ${planned.setNo}")
                            Text("План: ${planned.targetWeightKg.kg()} кг · ${planned.repsMin}–${planned.repsMax}" + (planned.targetRir?.let { " · RIR $it" } ?: ""))
                            when { fact != null -> { Text("Факт: ${fact.factText()}"); Row { TextButton({ editing = fact }) { Text("Изменить") }; TextButton({ deleting = fact }) { Text("Удалить") } } }
                                skip != null -> Text("Факт: Пропущен" + (skip.reason?.let { " · $it" } ?: ""))
                                else -> Text("Факт: Не выполнен") }
                        }
                    }
                }
                val extras = details.sets.filter { it.exerciseInstanceId == exercise.exerciseInstanceId && it.plannedSetNo == null }
                if (extras.isNotEmpty()) item { Column { Text("Дополнительные", style = MaterialTheme.typography.titleSmall); extras.forEach { fact ->
                    Row { Text("Доп. · ${fact.factText()}", Modifier.weight(1f)); TextButton({ editing = fact }) { Text("Изменить") }; TextButton({ deleting = fact }) { Text("Удалить") } }
                } } }
            }
        }
        item { Button(onShare, enabled = !state.saving) { Text("Отправить JSON") } }
    }
    if (noteDialog) NoteDialog(details.runtime.notes.orEmpty(), state.saving, { noteDialog = false }) { value -> onNote(value) { if (it) noteDialog = false } }
    editing?.let { fact -> EditFactDialog(fact, state.saving, { editing = null }) { weight, reps, rir, note -> onEdit(fact, weight, reps, rir, note) { if (it) editing = null } } }
    deleting?.let { fact -> AlertDialog(onDismissRequest = { if (!state.saving) deleting = null }, text = { Text("Удалить этот подход?") },
        dismissButton = { TextButton({ deleting = null }, enabled = !state.saving) { Text("Отмена") } }, confirmButton = {
            TextButton({ onDelete(fact.setResultId) { if (it) deleting = null } }, enabled = !state.saving) { Text("Удалить") }
        }) }
}

private fun Double.kg() = toString().removeSuffix(".0").replace('.', ',')
private fun SetResultEntity.factText() = "${weightKg.kg()} кг × $reps" + (rir?.let { " · RIR $it" } ?: "") +
    " · " + Instant.parse(completedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
private fun formatDetailDate(value: String) = Instant.parse(value).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale("ru")))

@Composable private fun NoteDialog(initial: String, saving: Boolean, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember(initial) { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, title = { Text("Заметка к тренировке") },
        text = { OutlinedTextField(text, { text = it }, minLines = 3) }, dismissButton = { TextButton(onDismiss) { Text("Отмена") } },
        confirmButton = { TextButton({ onSave(text) }, enabled = !saving) { Text("Сохранить") } })
}
@Composable private fun EditFactDialog(fact: SetResultEntity, saving: Boolean, onDismiss: () -> Unit, onSave: (Double, Int, Int?, String?) -> Unit) {
    var weight by remember { mutableStateOf(fact.weightKg.toString()) }; var reps by remember { mutableStateOf(fact.reps.toString()) }
    var rir by remember { mutableStateOf(fact.rir?.toString().orEmpty()) }; var note by remember { mutableStateOf(fact.note.orEmpty()) }
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, title = { Text("Изменить подход") }, text = { Column {
        OutlinedTextField(weight, { weight = it }, label = { Text("Вес") }); OutlinedTextField(reps, { reps = it }, label = { Text("Повторы") })
        OutlinedTextField(rir, { rir = it }, label = { Text("RIR") }); OutlinedTextField(note, { note = it }, label = { Text("Комментарий") })
    } }, dismissButton = { TextButton(onDismiss) { Text("Отмена") } }, confirmButton = { TextButton({ onSave(weight.replace(',', '.').toDouble(), reps.toInt(), rir.toIntOrNull(), note) },
        enabled = !saving && weight.replace(',', '.').toDoubleOrNull()?.let { it >= 0 } == true && reps.toIntOrNull()?.let { it >= 0 } == true &&
            (rir.isBlank() || rir.toIntOrNull()?.let { it in 0..4 } == true)) { Text("Сохранить") } })
}
