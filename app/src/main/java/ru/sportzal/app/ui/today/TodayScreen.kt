package ru.sportzal.app.ui.today

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import ru.sportzal.app.R
import ru.sportzal.app.data.files.ImportPreview
import ru.sportzal.app.domain.*
import ru.sportzal.app.model.*
import ru.sportzal.app.ui.components.*
import ru.sportzal.app.ui.media.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TodayScreen(selection: TodaySelection, manualChoices: List<PlannedWorkout>, preview: ImportPreview?,
    message: String?, onPickProgram: () -> Unit, onConfirmImport: (ImportPreview.Valid) -> Unit,
    onChooseWorkout: (String) -> Unit, onStart: () -> Unit, onResume: () -> Unit,
    onShare: () -> Unit = {}, preparing: Boolean = false,
    plannedWorkout: PlannedWorkoutDocument? = null, equipment: List<EquipmentDocument> = emptyList(),
    loading: Boolean = false, busy: Boolean = false, onSaveFile: (() -> Unit)? = null,
    error: String? = null, onRetry: () -> Unit = {}, onEquipment: (() -> Unit)? = null) {
    if (preview != null) {
        ProgramImportScreen(preview, false, busy, message, {}, onPickProgram, onConfirmImport)
        return
    }
    val bindings = LocalVisualBindings.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Спортзал", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { AppHeader("Сегодня", formatPlanDate(LocalDate.now().toString())) }
        if (loading) item { LoadingState("Загрузка плана") }
        else if (error == null) item {
            when (selection) {
                TodaySelection.NoProgram -> Card { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Ваш план — здесь", style = MaterialTheme.typography.headlineSmall)
                    Text("Добавьте оборудование зала, передайте данные ИИ и загрузите полученный план.")
                    SportzalButton("Загрузить план от ИИ", onPickProgram)
                    onEquipment?.let { TextButton(it) { Text("Добавить тренажёры") } }
                } }
                TodaySelection.Exhausted -> StatusNotice("План выполнен", "Передайте результаты ИИ, чтобы получить следующий план.",
                    actionLabel = "Загрузить план от ИИ", onAction = onPickProgram)
                is TodaySelection.Ready, is TodaySelection.Resume -> Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(plannedWorkout?.title ?: (selection as? TodaySelection.Ready)?.workout?.title ?: "Активная тренировка",
                        style = MaterialTheme.typography.headlineSmall)
                    if (selection is TodaySelection.Resume) Text("В процессе · можно продолжить", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary)
                    else plannedWorkout?.plannedDate?.let { Text("По плану: ${formatPlanDate(it)}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    plannedWorkout?.let { plan ->
                        val exercises = plan.blocks.flatMap { it.exercises }
                        Text("Упражнений: ${exercises.size} · Рабочих подходов: ${exercises.sumOf { ex -> ex.plannedSets.count { it.setType == "work" } }}",
                            style = MaterialTheme.typography.bodyMedium)
                        exercises.take(3).forEach { exercise ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                EquipmentIllustration(bindings.equipment(exercise.equipmentId), Modifier.size(64.dp), thumbnail = true)
                                Column(Modifier.weight(1f)) {
                                    Text(exercise.title, style = MaterialTheme.typography.titleSmall)
                                    plannedSetLines(exercise).firstOrNull()?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                }
                            }
                        }
                        if (exercises.size > 3) Text("Ещё упражнений: ${exercises.size - 3}", style = MaterialTheme.typography.bodySmall)
                    }
                    SportzalButton(if (selection is TodaySelection.Resume) "Продолжить тренировку" else "Открыть тренировку",
                        if (selection is TodaySelection.Resume) onResume else onStart, enabled = !busy)
                } }
            }
        }
        error?.let { item { StatusNotice(it, error = true, actionLabel = "Повторить", onAction = onRetry) } }
        message?.let { item { StatusNotice(it) } }
        if (manualChoices.isNotEmpty() && selection !is TodaySelection.Resume) {
            item { Text("Другие тренировки", style = MaterialTheme.typography.titleMedium) }
            items(manualChoices, key = { it.workoutInstanceId }) { plan ->
                Card(onClick = { onChooseWorkout(plan.workoutInstanceId) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(plan.title, style = MaterialTheme.typography.titleMedium); Text(formatPlanDate(plan.plannedDate.toString()), style = MaterialTheme.typography.bodySmall) }
                        Icon(painterResource(R.drawable.sz_ic_chevron_right), null, Modifier.size(20.dp))
                    }
                }
            }
        }
        item { Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Обмен с ИИ", style = MaterialTheme.typography.titleMedium)
            Text("Каталог, план и последние 24 завершённые тренировки. Отправьте файл в свой чат с ИИ.", style = MaterialTheme.typography.bodyMedium)
            SportzalButton("Передать отчёт ИИ", onShare, busy = preparing, secondary = true)
            onSaveFile?.let { TextButton(it, enabled = !preparing) { Text("Сохранить файл") } }
            if (selection !is TodaySelection.NoProgram) TextButton(onPickProgram, enabled = !busy) { Text("Загрузить план от ИИ") }
        } } }
    }
}

internal fun formatPlanDate(value: String): String = runCatching {
    LocalDate.parse(value).format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ru")))
}.getOrDefault(value)
