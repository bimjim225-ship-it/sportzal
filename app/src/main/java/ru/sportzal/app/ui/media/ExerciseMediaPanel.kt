package ru.sportzal.app.ui.media

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
fun ExerciseMediaPanel(exerciseId: String, equipmentId: String?, setup: String?, instructions: String?,
    saving: Boolean, onSelectTechnique: (String?, (Boolean) -> Unit) -> Unit) {
    val bindings = LocalVisualBindings.current
    val equipment = bindings.equipment(equipmentId)
    val technique = bindings.technique(exerciseId, equipmentId)
    var tab by rememberSaveable(exerciseId, equipmentId) { mutableIntStateOf(0) }
    var choosing by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var selected by remember(technique?.key) { mutableStateOf(technique?.key) }
    var error by remember { mutableStateOf<String?>(null) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(tab == 0, { tab = 0 }, { Text("Настройка") }, Modifier.weight(1f))
                FilterChip(tab == 1, { tab = 1 }, { Text("Техника") }, Modifier.weight(1f))
            }
            if (tab == 0) {
                EquipmentIllustration(equipment, Modifier.fillMaxWidth().height(if (equipment == null) 64.dp else 190.dp))
                Text(setup?.takeIf { it.isNotBlank() } ?: "Настройка не указана в плане", style = MaterialTheme.typography.bodyMedium)
            } else {
                if (technique != null) {
                    Row(Modifier.fillMaxWidth()) {
                        TechniqueFrame(technique.start, "Начало", Modifier.weight(1f))
                        TechniqueFrame(technique.end, "Движение", Modifier.weight(1f))
                    }
                    TextButton({ expanded = true }) { Text("Увеличить схему движения") }
                } else Text("Схема движения пока не выбрана. Инструкции из плана доступны ниже.",
                    style = MaterialTheme.typography.bodyMedium)
                TextButton({ selected = technique?.key; choosing = true; error = null }, enabled = !saving) {
                    Text(if (technique == null) "Выбрать схему движения" else "Изменить схему движения")
                }
            }
            instructions?.takeIf { it.isNotBlank() }?.let {
                HorizontalDivider()
                Text("Из плана ИИ", style = MaterialTheme.typography.labelLarge)
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    if (choosing) AlertDialog(onDismissRequest = { if (!saving) choosing = false }, title = { Text("Схема для этого упражнения") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("Связь действует только для этого движения и выбранного оборудования.")
            VisualPicker(selected) { selected = it }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } }, dismissButton = { TextButton({ choosing = false }, enabled = !saving) { Text("Отмена") } },
        confirmButton = { TextButton({ onSelectTechnique(selected) { success ->
            if (success) choosing = false else error = "Не удалось сохранить схему. Повторите выбор."
        } }, enabled = !saving) { Text("Сохранить") } })
    if (expanded && technique != null) AlertDialog(onDismissRequest = { expanded = false },
        title = { Text(technique.title) }, text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            TechniqueFrame(technique.start, "Начало", Modifier.fillMaxWidth())
            TechniqueFrame(technique.end, "Движение", Modifier.fillMaxWidth())
        } }, confirmButton = { TextButton({ expanded = false }) { Text("Закрыть") } })
}

@Composable private fun TechniqueFrame(resource: Int, label: String, modifier: Modifier) {
    Column(modifier) {
        Image(painterResource(resource), contentDescription = label, contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}
