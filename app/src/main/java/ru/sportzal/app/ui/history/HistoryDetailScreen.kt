package ru.sportzal.app.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.serialization.decodeFromString
import ru.sportzal.app.data.db.SetResultEntity
import ru.sportzal.app.domain.ActualContext
import ru.sportzal.app.model.StrictJson
import ru.sportzal.app.ui.components.EditSetDialog
import ru.sportzal.app.ui.components.deviationLabels
import ru.sportzal.app.ui.workout.formatDuration

@Composable
fun HistoryDetailScreen(
    state: HistoryUiState,
    onBack: () -> Unit,
    onNote: (String, (Boolean) -> Unit) -> Unit,
    onEdit: (SetResultEntity, Double, Int, Int?, String?, (Boolean) -> Unit) -> Unit,
    onDelete: (String, (Boolean) -> Unit) -> Unit,
    onShare: () -> Unit,
    onEditActual: ((SetResultEntity, Double, Int, Int?, List<String>, String?, ActualContext, (Boolean) -> Unit) -> Unit)? = null,
) {
    val details = state.details ?: return
    var noteDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SetResultEntity?>(null) }
    var deleting by remember { mutableStateOf<SetResultEntity?>(null) }
    val durationSeconds = remember(details.runtime.startedAt, details.runtime.finishedAt) {
        details.runtime.finishedAt?.let { finished ->
            Duration.between(Instant.parse(details.runtime.startedAt), Instant.parse(finished)).seconds.coerceAtLeast(0)
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            TextButton(onBack) { Text("← История") }
            Text(details.plan.title, style = MaterialTheme.typography.headlineLarge)
        }
        item {
            Text(formatDetailDate(details.runtime.startedAt))
            Text(if (details.runtime.completionStatus == "completed") "Завершена" else "Завершена досрочно")
            durationSeconds?.let { Text("Длительность: ${formatDuration(it)}") }
        }
        state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        item {
            TextButton({ noteDialog = true }) { Text("Заметка") }
            details.runtime.notes?.let { Text(it) }
        }
        details.plan.blocks.forEach { block ->
            item { Text(block.title, style = MaterialTheme.typography.titleLarge) }
            block.exercises.forEach { exercise ->
                item { Text(exercise.title, style = MaterialTheme.typography.titleMedium) }
                exercise.plannedSets.forEach { planned ->
                    item {
                        val fact = details.sets.firstOrNull {
                            it.exerciseInstanceId == exercise.exerciseInstanceId && it.plannedSetNo == planned.setNo
                        }
                        val skip = details.skips.firstOrNull {
                            it.exerciseInstanceId == exercise.exerciseInstanceId && it.plannedSetNo == planned.setNo
                        }
                        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text("Подход ${planned.setNo}")
                            Text(
                                "План: ${planned.targetWeightKg.kg()} кг · ${planned.repsMin}–${planned.repsMax}" +
                                    (planned.targetRir?.let { " · RIR ${it.rirLabel()}" } ?: ""),
                            )
                            when {
                                fact != null -> {
                                    HistoricalFact(fact)
                                    Row {
                                        TextButton({ editing = fact }) { Text("Изменить") }
                                        TextButton({ deleting = fact }) { Text("Удалить") }
                                    }
                                }
                                skip != null -> Text("Факт: Пропущен" + (skip.reason?.let { " · $it" } ?: ""))
                                else -> Text("Факт: Не выполнен")
                            }
                        }
                    }
                }
                val extras = details.sets.filter {
                    it.exerciseInstanceId == exercise.exerciseInstanceId && it.plannedSetNo == null
                }
                if (extras.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Дополнительные", style = MaterialTheme.typography.titleSmall)
                            extras.forEach { fact ->
                                Column {
                                    Text("Доп. подход ${fact.sequenceNo}")
                                    HistoricalFact(fact)
                                    Row {
                                        TextButton({ editing = fact }) { Text("Изменить") }
                                        TextButton({ deleting = fact }) { Text("Удалить") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        item { Button(onShare, enabled = !state.saving) { Text("Отправить JSON") } }
    }

    if (noteDialog) {
        NoteDialog(details.runtime.notes.orEmpty(), state.saving, { noteDialog = false }) { value ->
            onNote(value) { if (it) noteDialog = false }
        }
    }
    editing?.let { fact ->
        val exercise = details.plan.blocks.flatMap { it.exercises }
            .firstOrNull { it.exerciseInstanceId == fact.exerciseInstanceId }
        val showRir = fact.setType == "work" && (exercise?.rirCapture != "none" || fact.rir != null)
        EditSetDialog(fact, showRir, state.saving, { editing = null }) { weight, reps, rir, deviations, note, context ->
            val fullEditor = onEditActual
            if (fullEditor != null) {
                fullEditor(fact, weight, reps, rir, deviations, note, context) { committed ->
                    if (committed) editing = null
                }
            } else {
                onEdit(fact, weight, reps, rir, note) { committed ->
                    if (committed) editing = null
                }
            }
        }
    }
    deleting?.let { fact ->
        AlertDialog(
            onDismissRequest = { if (!state.saving) deleting = null },
            text = { Text("Удалить этот подход?") },
            dismissButton = {
                TextButton({ deleting = null }, enabled = !state.saving) { Text("Отмена") }
            },
            confirmButton = {
                TextButton(
                    { onDelete(fact.setResultId) { if (it) deleting = null } },
                    enabled = !state.saving,
                ) { Text("Удалить") }
            },
        )
    }
}

@Composable
private fun HistoricalFact(fact: SetResultEntity) {
    Text("Факт: ${fact.factText()}")
    val context = fact.actualContextText()
    if (context.isNotBlank()) Text("Факт: $context")
    val deviations = runCatching { StrictJson.decodeFromString<List<String>>(fact.deviationsJson) }
        .getOrDefault(emptyList())
    if (deviations.isNotEmpty()) {
        Text("Отклонения: ${deviations.joinToString { deviationLabels[it] ?: it }}")
    }
    fact.note?.takeIf { it.isNotBlank() }?.let { Text("Комментарий: $it") }
}

private fun Double.kg() = toString().removeSuffix(".0").replace('.', ',')

private fun Int.rirLabel() = if (this == 4) "4+" else toString()

private fun SetResultEntity.factText() = "${weightKg.kg()} кг × $reps" +
    (rir?.let { " · RIR ${it.rirLabel()}" } ?: "") +
    " · " + Instant.parse(completedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))

private fun SetResultEntity.actualContextText(): String = listOfNotNull(
    equipmentNameActual?.takeIf { it.isNotBlank() } ?: equipmentIdActual?.takeIf { it.isNotBlank() },
    setupActual?.takeIf { it.isNotBlank() },
    loadBasisActual.takeIf { it.isNotBlank() },
    sideActual.takeIf { it.isNotBlank() },
).joinToString(" · ")

private fun formatDetailDate(value: String) = Instant.parse(value)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale("ru")))

@Composable
private fun NoteDialog(initial: String, saving: Boolean, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Заметка к тренировке") },
        text = { OutlinedTextField(text, { text = it }, minLines = 3) },
        dismissButton = { TextButton(onDismiss, enabled = !saving) { Text("Отмена") } },
        confirmButton = { TextButton({ onSave(text) }, enabled = !saving) { Text("Сохранить") } },
    )
}
