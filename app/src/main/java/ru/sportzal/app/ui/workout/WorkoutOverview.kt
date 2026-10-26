package ru.sportzal.app.ui.workout

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import ru.sportzal.app.R
import ru.sportzal.app.ui.components.*
import ru.sportzal.app.ui.media.*
import ru.sportzal.app.ui.text.setTypeLabel

@Composable
fun WorkoutOverview(state: WorkoutUiState, onBack: () -> Unit, onExercise: (String) -> Unit,
    onFinish: () -> Unit, onCancelEmpty: () -> Unit) {
    val cards = state.blocks.flatMap { it.cards }
    val progress = cards.map { it.progress() }
    val completed = progress.sumOf { it.completedWork }
    val planned = progress.sumOf { it.plannedWork }
    val bindings = LocalVisualBindings.current
    LazyColumn(Modifier.fillMaxSize().testTag("workout-overview"), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { AppHeader(state.title, "Активная тренировка", onBack) }
        item { Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("$completed из $planned рабочих подходов", style = MaterialTheme.typography.titleLarge)
            LinearProgressIndicator(progress = { if (planned > 0) completed.toFloat() / planned else 0f }, Modifier.fillMaxWidth())
            Text("Выберите упражнение. Можно менять порядок, если тренажёр занят.", style = MaterialTheme.typography.bodyMedium)
        } } }
        state.planNotes?.let { item { StatusNotice("Из плана ИИ", it) } }
        state.error?.let { item { StatusNotice(it, error = true) } }
        state.blocks.forEach { block ->
            item(key = "block-${block.blockId}") { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(block.title, style = MaterialTheme.typography.titleMedium)
                Text(if (block.mode == "rotation") "Чередование упражнений" else "Подходы по порядку", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                block.notes?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            } }
            block.cards.forEach { card -> item(key = card.exercise.exerciseInstanceId) {
                val p = card.progress()
                val context = card.actualContext()
                Card(onClick = { onExercise(card.exercise.exerciseInstanceId) }, Modifier.fillMaxWidth().testTag("open-${card.exercise.exerciseInstanceId}")) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        EquipmentIllustration(bindings.equipment(context.equipmentId), Modifier.size(76.dp), thumbnail = true)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(card.exercise.title, style = MaterialTheme.typography.titleMedium)
                            Text("Рабочие: ${p.completedWork}/${p.plannedWork}" + if (p.skipped > 0) " · Пропуски: ${p.skipped}" else "",
                                style = MaterialTheme.typography.bodySmall)
                            val slot = card.currentSlot
                            if (slot != null) Text("${setTypeLabel(slot.setType)} · ${slot.targetWeightKg.numberText()} кг · ${slot.repsMin}–${slot.repsMax}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            else Text(if (p.fullyPerformed) "Выполнено" else "Есть пропущенные подходы", style = MaterialTheme.typography.labelLarge)
                        }
                        Icon(painterResource(if (p.fullyPerformed) R.drawable.sz_ic_check else R.drawable.sz_ic_chevron_right), null, Modifier.size(22.dp))
                    }
                }
            } }
        }
        item { SportzalButton("Завершить тренировку", onFinish, enabled = !state.saving) }
        if (cards.all { it.saved.isEmpty() && it.skipped.isEmpty() }) item {
            TextButton(onCancelEmpty, enabled = !state.saving) { Text("Отменить начало тренировки") }
        }
    }
}

internal fun Double.numberText() = (if (this % 1.0 == 0.0) toLong().toString() else toString()).replace('.', ',')
