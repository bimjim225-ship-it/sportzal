package ru.sportzal.app.ui.workout

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import ru.sportzal.app.R
import ru.sportzal.app.ui.components.*
import ru.sportzal.app.ui.theme.*
import java.util.Locale

@Composable fun FinishScreen(summary: FinishSummary, error: String?, preparing: Boolean = false,
    notes: String? = null, onNote: (String, (Boolean) -> Unit) -> Unit = { _, done -> done(true) },
    onShare: () -> Unit, onSave: () -> Unit, onClose: () -> Unit, onAiHelp: (() -> Unit)? = null) {
    var noteValue by rememberSaveable(notes) { mutableStateOf(notes.orEmpty()) }
    var noteSaving by remember { mutableStateOf(false) }
    var closeRequested by remember { mutableStateOf(false) }
    val dirty = noteValue.trim() != notes.orEmpty().trim()
    val busy = preparing || noteSaving
    fun commitThen(action: () -> Unit) {
        if (busy) return
        if (!dirty) action()
        else {
            noteSaving = true
            onNote(noteValue) { success -> noteSaving = false; if (success) action() }
        }
    }
    fun requestClose() { if (!busy) { if (dirty) closeRequested = true else onClose() } }
    BackHandler { requestClose() }
    ScreenWithAction(bottomAction = {
        error?.let { StatusNotice(it, error = true) }
        SportzalButton("Передать отчёт ИИ", { commitThen(onShare) }, busy = busy)
    }) {
      Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(painterResource(R.drawable.sz_ic_check), null, Modifier.size(44.dp), tint = SportzalColors.Success)
        Text("Тренировка завершена", style = MaterialTheme.typography.headlineLarge)
        if (summary.title.isNotBlank()) Text(summary.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(formatDuration(summary.durationSeconds), style = NumericTextStyle)
            Text("Длительность", style = MaterialTheme.typography.bodyMedium)
            HorizontalDivider()
            Text("${summary.completedPlannedWork} из ${summary.plannedWorkCount}", style = NumericTextStyle)
            Text("Рабочих подходов по плану", style = MaterialTheme.typography.bodyMedium)
            if (summary.warmupSetCount > 0) Text("Разминочных: ${summary.warmupSetCount}")
            if (summary.extraSetCount > 0) Text("Дополнительных: ${summary.extraSetCount}")
            if (summary.skippedSetCount > 0) Text("Пропущено: ${summary.skippedSetCount}")
        } }
        if (summary.endedEarly) StatusNotice("Есть невыполненные подходы", "В отчёте сохранены фактические результаты и отмеченные пропуски.")
        if (summary.exercises.isNotEmpty()) Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                summary.exercises.forEach { exercise ->
                    Text(exercise.title, style = MaterialTheme.typography.titleMedium)
                    Text("Рабочих подходов: ${exercise.workSetCount}", style = MaterialTheme.typography.bodyMedium)
                    HorizontalDivider()
                }
            }
        }
        OutlinedTextField(noteValue, { noteValue = it }, Modifier.fillMaxWidth(), enabled = !busy,
            label = { Text("Как прошла тренировка") }, supportingText = { Text("Комментарий необязателен") }, minLines = 3)
        if (dirty) TextButton({ commitThen {} }, enabled = !busy) { Text("Сохранить заметку") }
        Text("Отчёт содержит последние 24 завершённые тренировки и текущее занятие. Открытие меню отправки не подтверждает доставку.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SportzalButton("Сохранить файл", { commitThen(onSave) }, enabled = !busy, secondary = true)
        onAiHelp?.let { help -> TextButton({ commitThen(help) }, enabled = !busy) { Text("Как получить следующий план") } }
        TextButton(::requestClose, enabled = !busy) { Text("На главный экран") }
      }
    }
    if (closeRequested) AlertDialog(onDismissRequest = { closeRequested = false }, title = { Text("Сохранить заметку?") },
        text = { Column {
            Text("Комментарий ещё не сохранён. Записанные подходы уже находятся в истории.")
            TextButton({ closeRequested = false; onClose() }, enabled = !busy) { Text("Закрыть без сохранения") }
        } }, dismissButton = { TextButton({ closeRequested = false }) { Text("Остаться") } },
        confirmButton = { TextButton({ commitThen { closeRequested = false; onClose() } }, enabled = !busy) { Text("Сохранить и закрыть") } })
}

internal fun formatDuration(seconds: Long): String = if (seconds >= 3600) {
    String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
} else String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60)
