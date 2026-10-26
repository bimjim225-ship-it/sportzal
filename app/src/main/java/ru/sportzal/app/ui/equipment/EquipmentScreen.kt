package ru.sportzal.app.ui.equipment

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import ru.sportzal.app.R
import ru.sportzal.app.data.files.EquipmentPhotoStore
import ru.sportzal.app.model.EquipmentCatalogItem
import ru.sportzal.app.model.SaveEquipmentCommand
import ru.sportzal.app.ui.components.*
import ru.sportzal.app.ui.media.*

@Composable fun EquipmentScreen(state: EquipmentUiState, photoStore: EquipmentPhotoStore,
    onRetry: () -> Unit, onSave: (SaveEquipmentCommand, (Boolean) -> Unit) -> Unit,
    onPhoto: (EquipmentCatalogItem, android.net.Uri) -> Unit, onRemovePhoto: (EquipmentCatalogItem) -> Unit) {
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var editing by rememberSaveable { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var photoTarget by remember { mutableStateOf<EquipmentCatalogItem?>(null) }
    val selected = state.items.firstOrNull { it.equipmentId == selectedId }
    val focus = remember { FocusRequester() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = photoTarget
        if (uri != null && target != null) onPhoto(target, uri)
        photoTarget = null
    }
    BackHandler(selected != null && !editing) { selectedId = null }
    if (selected != null) {
        EquipmentDetail(selected, state, photoStore, { selectedId = null }, { editing = true },
            { photoTarget = selected; picker.launch(arrayOf("image/*")) }, { onRemovePhoto(selected) })
    } else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { AppHeader("Тренажёры", "Оборудование моего зала") }
        item { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().focusRequester(focus),
            label = { Text("Найти тренажёр") }, singleLine = true,
            leadingIcon = { Icon(painterResource(R.drawable.sz_ic_search), null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton({ query = ""; focus.requestFocus() }) {
                Icon(painterResource(R.drawable.sz_ic_close), "Очистить поиск")
            } }) }
        state.error?.let { item { StatusNotice(it, error = true, actionLabel = "Повторить", onAction = onRetry) } }
        if (state.loading) item { LoadingState("Загрузка тренажёров") }
        val filtered = state.items.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
        if (!state.loading && state.error == null && filtered.isEmpty()) item {
            StatusNotice(if (state.items.isEmpty()) "Добавьте оборудование зала" else "Ничего не найдено",
                if (state.items.isEmpty()) "Можно добавить тренажёр вручную или загрузить каталог вместе с планом от ИИ." else "Попробуйте другое название.")
        }
        items(filtered, key = { it.equipmentId }) { item ->
            Card(onClick = { selectedId = item.equipmentId }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    EquipmentIllustration(VisualCatalog.find(item.illustrationKey), Modifier.size(88.dp), thumbnail = true)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(item.name, style = MaterialTheme.typography.titleMedium)
                        Text(item.setupHint ?: "Настройка пока не указана", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(painterResource(R.drawable.sz_ic_chevron_right), null, Modifier.size(20.dp))
                }
            }
        }
        item { SportzalButton("Добавить тренажёр", { adding = true }, secondary = true, enabled = !state.saving) }
    }
    if (adding || (editing && selected != null)) EquipmentEditSheet(if (adding) null else selected, state.saving,
        { adding = false; editing = false }, error = state.error) { command ->
        onSave(command) { success -> if (success) { adding = false; editing = false } }
    }
}

@Composable private fun EquipmentDetail(item: EquipmentCatalogItem, state: EquipmentUiState, photos: EquipmentPhotoStore,
    onBack: () -> Unit, onEdit: () -> Unit, onPhoto: () -> Unit, onRemovePhoto: () -> Unit) {
    var sourceExpanded by rememberSaveable(item.equipmentId) { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { AppHeader("Тренажёр", onBack = onBack) }
        item { Text(item.name, style = MaterialTheme.typography.headlineLarge) }
        item { Card { EquipmentIllustration(VisualCatalog.find(item.illustrationKey), Modifier.fillMaxWidth().height(280.dp)) } }
        item { Text("Типовая схема. Персональная настройка указана ниже.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Моя настройка", style = MaterialTheme.typography.titleMedium)
            Text(item.setupHint ?: "Пока не указана")
            HorizontalDivider()
            Text("Шаг веса", style = MaterialTheme.typography.labelLarge)
            Text(item.weightStepKg?.let { "${formatEquipmentWeight(it)} кг" } ?: "Не указан")
            item.availableWeightsKg?.let { Text("Доступные веса: ${it.joinToString { weight -> formatEquipmentWeight(weight) }} кг") }
            item.notes?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } } }
        state.error?.let { item { StatusNotice(it, error = true) } }
        item { SportzalButton("Изменить настройки", onEdit, enabled = !state.saving) }
        item { TextButton({ sourceExpanded = !sourceExpanded }) { Text("Фото для подготовки схемы") } }
        if (sourceExpanded) item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Фото хранится как исходник. Генерация схем по фото будет добавлена отдельно.", style = MaterialTheme.typography.bodySmall)
            LocalEquipmentPhoto(item.photoPath, photos)
            TextButton(onPhoto, enabled = !state.saving) { Text(if (item.photoPath == null) "Добавить исходное фото" else "Заменить исходное фото") }
            if (item.photoPath != null) TextButton(onRemovePhoto, enabled = !state.saving) { Text("Удалить исходное фото") }
        } }
    }
}

internal fun formatEquipmentWeight(value: Double) = (if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()).replace('.', ',')
