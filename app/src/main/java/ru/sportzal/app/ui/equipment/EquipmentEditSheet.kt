package ru.sportzal.app.ui.equipment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.sportzal.app.model.EquipmentCatalogItem
import ru.sportzal.app.model.SaveEquipmentCommand

@Composable fun EquipmentEditSheet(item: EquipmentCatalogItem?, saving: Boolean, onDismiss: () -> Unit,
    onSave: (SaveEquipmentCommand) -> Unit) {
    var name by remember(item) { mutableStateOf(item?.name.orEmpty()) }
    var setup by remember(item) { mutableStateOf(item?.setupHint.orEmpty()) }
    var notes by remember(item) { mutableStateOf(item?.notes.orEmpty()) }
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, title = { Text(if (item == null) "Добавить тренажёр" else "Изменить тренажёр") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Название *") }, singleLine = true)
            OutlinedTextField(setup, { setup = it }, label = { Text("Настройка тренажёра") })
            OutlinedTextField(notes, { notes = it }, label = { Text("Заметки") }, minLines = 2)
            item?.weightStepKg?.let { Text("Шаг: ${it.toString().replace('.', ',')} кг") }
        } }, dismissButton = { TextButton(onDismiss, enabled = !saving) { Text("Отмена") } }, confirmButton = {
            TextButton({ onSave(SaveEquipmentCommand(item?.equipmentId, name, setup, notes)) }, enabled = name.isNotBlank() && !saving) { Text("Сохранить") }
        })
}
