package ru.sportzal.app.ui.equipment

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.sportzal.app.model.*
import ru.sportzal.app.ui.media.VisualPicker

@Composable fun EquipmentEditSheet(item: EquipmentCatalogItem?, saving: Boolean, onDismiss: () -> Unit,
    error: String? = null, onSave: (SaveEquipmentCommand) -> Unit) {
    var name by rememberSaveable(item?.equipmentId) { mutableStateOf(item?.name.orEmpty()) }
    var setup by rememberSaveable(item?.equipmentId) { mutableStateOf(item?.setupHint.orEmpty()) }
    var notes by rememberSaveable(item?.equipmentId) { mutableStateOf(item?.notes.orEmpty()) }
    var step by rememberSaveable(item?.equipmentId) { mutableStateOf(item?.weightStepKg?.let(::formatEquipmentWeight).orEmpty()) }
    var weights by rememberSaveable(item?.equipmentId) { mutableStateOf(item?.availableWeightsKg?.joinToString("; ") { formatEquipmentWeight(it) }.orEmpty()) }
    var key by rememberSaveable(item?.equipmentId) { mutableStateOf(item?.illustrationKey) }
    var submitted by remember { mutableStateOf(false) }
    val parsedStep = step.replace(',', '.').toDoubleOrNull()
    val stepError = step.isNotBlank() && (parsedStep == null || !parsedStep.isFinite() || parsedStep <= 0)
    val parsedWeights = parseAvailableWeights(weights)
    val weightsError = weights.isNotBlank() && parsedWeights == null
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(if (item == null) "Добавить тренажёр" else "Изменить настройки") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Название") }, singleLine = true, isError = submitted && name.isBlank())
            OutlinedTextField(setup, { setup = it }, label = { Text("Настройка тренажёра") })
            OutlinedTextField(step, { step = it }, label = { Text("Шаг веса, кг") }, singleLine = true,
                isError = submitted && stepError, supportingText = { Text("Необязательно; положительное число") })
            OutlinedTextField(weights, { weights = it }, label = { Text("Доступные веса, кг") },
                isError = submitted && weightsError, supportingText = { Text("Через точку с запятой: 2,5; 5; 12,5") })
            OutlinedTextField(notes, { notes = it }, label = { Text("Заметки") }, minLines = 2)
            VisualPicker(key) { key = it }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        dismissButton = { TextButton(onDismiss, enabled = !saving) { Text("Отмена") } },
        confirmButton = { TextButton({
            submitted = true
            if (name.isNotBlank() && !stepError && !weightsError)
                onSave(SaveEquipmentCommand(item?.equipmentId, name, setup, notes, EquipmentConfiguration(parsedStep, parsedWeights, key)))
        }, enabled = !saving) { Text("Сохранить") } })
}
