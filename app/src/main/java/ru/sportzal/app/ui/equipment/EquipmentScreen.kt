package ru.sportzal.app.ui.equipment

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.sportzal.app.data.files.EquipmentPhotoStore
import ru.sportzal.app.model.EquipmentCatalogItem
import ru.sportzal.app.ui.components.LocalEquipmentPhoto

@Composable fun EquipmentScreen(state: EquipmentUiState, photoStore: EquipmentPhotoStore,
    onRetry: () -> Unit, onSave: (ru.sportzal.app.model.SaveEquipmentCommand, (Boolean) -> Unit) -> Unit,
    onPhoto: (EquipmentCatalogItem, android.net.Uri) -> Unit, onRemovePhoto: (EquipmentCatalogItem) -> Unit) {
    var editing by remember { mutableStateOf<EquipmentCatalogItem?>(null) }
    var adding by remember { mutableStateOf(false) }
    var photoTarget by remember { mutableStateOf<EquipmentCatalogItem?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = photoTarget; if (uri != null && target != null) onPhoto(target, uri); photoTarget = null
    }
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Тренажёры", style = MaterialTheme.typography.headlineLarge) }
        state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = onRetry) { Text("Повторить") } } }
        items(state.items, key = { it.equipmentId }) { item ->
            Card(onClick = { editing = item }, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LocalEquipmentPhoto(item.photoPath, photoStore)
                Text(item.name, style = MaterialTheme.typography.titleLarge)
                item.setupHint?.let { Text(it) }; item.notes?.let { Text(it) }
                item.weightStepKg?.let { Text("Шаг: ${it.toString().replace('.', ',')} кг") }
                item.availableWeightsKg?.let { Text("Веса: ${it.joinToString { value -> value.toString().replace('.', ',') }} кг") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton({ photoTarget = item; picker.launch(arrayOf("image/*")) }) { Text(if (item.photoPath == null) "Добавить фото" else "Заменить фото") }
                    if (item.photoPath != null) TextButton({ onRemovePhoto(item) }) { Text("Удалить фото") }
                }
            } }
        }
        item { Button({ adding = true }, enabled = !state.saving) { Text("Добавить тренажёр") } }
    }
    if (adding || editing != null) EquipmentEditSheet(editing, state.saving, { adding = false; editing = null }) { command ->
        onSave(command) { success -> if (success) { adding = false; editing = null } }
    }
}
