package ru.sportzal.app.ui.today

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import ru.sportzal.app.ui.components.SportzalButton

@Composable
fun FirstPlanCard(equipmentCount: Int, busy: Boolean, onEquipment: (() -> Unit)?,
    onAiHelp: (() -> Unit)?, onImport: () -> Unit) {
    val hasEquipment = equipmentCount > 0
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Ваш план — здесь", style = MaterialTheme.typography.headlineSmall)
            FirstPlanStep("1", "Оборудование зала",
                if (hasEquipment) "Добавлено: $equipmentCount" else "Добавьте тренажёры, которыми пользуетесь", !hasEquipment, hasEquipment)
            FirstPlanStep("2", "Запрос для ИИ", "Передайте каталог и готовый запрос в свой чат", hasEquipment)
            FirstPlanStep("3", "План тренировки", "Загрузите полученный от ИИ файл", false)
            val action = if (hasEquipment) onAiHelp else onEquipment
            action?.let {
                SportzalButton(if (hasEquipment) "Получить план с ИИ" else "Добавить тренажёры", it,
                    Modifier.testTag("first-plan-action"), enabled = !busy)
            }
            TextButton(onImport, enabled = !busy) { Text("Загрузить план от ИИ") }
            if (!hasEquipment) onAiHelp?.let { TextButton(it, enabled = !busy) { Text("Как получить план от ИИ") } }
            else onEquipment?.let { TextButton(it, enabled = !busy) { Text("Мои тренажёры") } }
        }
    }
}

@Composable
private fun FirstPlanStep(number: String, title: String, body: String, current: Boolean, complete: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Surface(shape = MaterialTheme.shapes.small,
            color = if (current) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
            Box(Modifier.defaultMinSize(minWidth = 32.dp, minHeight = 32.dp).padding(horizontal = 8.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
                Text(if (complete) "✓" else number, style = MaterialTheme.typography.titleSmall)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
