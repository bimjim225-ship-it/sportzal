package ru.sportzal.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import kotlinx.serialization.decodeFromString
import ru.sportzal.app.data.db.SetResultEntity
import ru.sportzal.app.model.StrictJson

internal val deviationLabels = linkedMapOf(
    "range_shortened" to "Амплитуда сокращена", "technique_changed" to "Техника изменилась",
    "discomfort" to "Дискомфорт", "setup_changed" to "Изменена настройка",
    "equipment_changed" to "Другое оборудование", "exercise_changed" to "Другое упражнение", "other" to "Другое",
)

@Composable
fun SetResultRow(
    result: SetResultEntity,
    saving: Boolean,
    showRir: Boolean,
    onInteraction: (Boolean) -> Unit,
    onEdit: (Double, Int, Int?, List<String>, String?) -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    fun closeAll() { menu = false; edit = false; confirmDelete = false; onInteraction(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("${result.plannedSetNo}. ${result.weightKg.g} кг × ${result.reps}" + (result.rir?.let { " · RIR ${it.rirText()}" } ?: ""))
        IconButton({ menu = true; onInteraction(true) }, enabled = !saving,
            modifier = Modifier.testTag("set-actions-${result.setResultId}")) { Text("⋮") }
        DropdownMenu(menu, onDismissRequest = ::closeAll) {
            DropdownMenuItem({ Text("Изменить") }, { menu = false; edit = true })
            DropdownMenuItem({ Text("Удалить") }, { menu = false; confirmDelete = true })
        }
    }
    if (edit) EditSetDialog(result, showRir, saving, ::closeAll) { weight, reps, rir, deviations, note ->
        onEdit(weight, reps, rir, deviations, note)
    }
    if (confirmDelete) AlertDialog(onDismissRequest = ::closeAll, title = { Text("Удалить этот подход?") },
        text = { Text("Подход снова станет незаполненным.") },
        dismissButton = { TextButton(::closeAll) { Text("Отмена") } },
        confirmButton = { TextButton({ onDelete() }, enabled = !saving, modifier = Modifier.testTag("confirm-delete")) { Text("Удалить") } })
}

@Composable private fun EditSetDialog(
    result: SetResultEntity, showRir: Boolean, saving: Boolean, onDismiss: () -> Unit,
    onSave: (Double, Int, Int?, List<String>, String?) -> Unit,
) {
    var weight by remember { mutableStateOf(result.weightKg.g) }
    var reps by remember { mutableStateOf(result.reps.toString()) }
    var rir by remember { mutableStateOf(result.rir) }
    var deviations by remember { mutableStateOf(runCatching { StrictJson.decodeFromString<List<String>>(result.deviationsJson) }.getOrDefault(emptyList()).toSet()) }
    var note by remember { mutableStateOf(result.note.orEmpty()) }
    val parsedWeight = weight.replace(',', '.').toDoubleOrNull()
    val parsedReps = reps.toIntOrNull()
    val valid = parsedWeight != null && parsedWeight.isFinite() && parsedWeight >= 0 && parsedReps != null && parsedReps >= 0 &&
        (result.loadBasisActual != "bodyweight" || parsedWeight == 0.0)
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Подход ${result.plannedSetNo ?: result.sequenceNo}") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(androidx.compose.ui.unit.dp(8))) {
            OutlinedTextField(weight, { weight = it }, label = { Text("Вес") }, enabled = result.loadBasisActual != "bodyweight")
            OutlinedTextField(reps, { reps = it }, label = { Text("Повторы") }, modifier = Modifier.testTag("edit-reps"))
            if (showRir) Row(horizontalArrangement = Arrangement.spacedBy(androidx.compose.ui.unit.dp(4))) {
                (0..4).forEach { value -> FilterChip(rir == value, { rir = value }, { Text(if (value == 4) "4+" else "$value") }) }
                FilterChip(rir == null, { rir = null }, { Text("Не оценил") })
            }
            Text("Отклонения")
            deviationLabels.forEach { (value, label) -> FilterChip(value in deviations, {
                deviations = if (value in deviations) deviations - value else deviations + value
            }, { Text(label) }, modifier = Modifier.testTag("deviation-$value")) }
            OutlinedTextField(note, { note = it }, label = { Text("Комментарий") })
        } },
        dismissButton = { TextButton(onDismiss) { Text("Отмена") } },
        confirmButton = { TextButton({ onSave(parsedWeight!!, parsedReps!!, rir, deviations.toList(), note) },
            enabled = valid && !saving, modifier = Modifier.testTag("save-edit")) { Text("Сохранить") } })
}

private val Double.g: String get() = if (this % 1.0 == 0.0) toInt().toString() else toString()
