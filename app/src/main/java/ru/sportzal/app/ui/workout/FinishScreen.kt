package ru.sportzal.app.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable fun FinishScreen(summary: FinishSummary, error: String?, preparing: Boolean = false,
    notes: String? = null, onNote: (String, (Boolean) -> Unit) -> Unit = { _, done -> done(true) },
    onShare: () -> Unit, onSave: () -> Unit, onClose: () -> Unit) {
    var noteDialog by remember { mutableStateOf(false) }
    var noteValue by remember(notes) { mutableStateOf(notes.orEmpty()) }
    Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Тренировка завершена", style = MaterialTheme.typography.headlineMedium)
        Text("Длительность: ${formatDuration(summary.durationSeconds)}")
        Text("Рабочих подходов: ${summary.workSetCount}")
        summary.exercises.forEach { Text("${it.title} — ${it.workSetCount} подходов") }
        if (summary.endedEarly) Text("Остались невыполненные подходы")
        notes?.let { Text(it) }
        androidx.compose.material3.TextButton({ noteDialog = true }) { Text("Заметка") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = onShare, enabled = !preparing) { Text(if (preparing) "Подготовка JSON…" else "Отправить JSON") }
        Button(onClick = onSave, enabled = !preparing) { Text("Сохранить JSON") }
        Button(onClick = onClose) { Text("Закрыть") }
    }
    if (noteDialog) androidx.compose.material3.AlertDialog(onDismissRequest = { noteDialog = false }, title = { Text("Заметка к тренировке") }, text = {
        androidx.compose.material3.OutlinedTextField(noteValue, { noteValue = it }, minLines = 3)
    }, dismissButton = { androidx.compose.material3.TextButton({ noteDialog = false }) { Text("Отмена") } }, confirmButton = {
        androidx.compose.material3.TextButton({ onNote(noteValue) { if (it) noteDialog = false } }) { Text("Сохранить") }
    })
}

internal fun formatDuration(seconds: Long): String = if (seconds >= 3600) {
    "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
} else "%02d:%02d".format(seconds / 60, seconds % 60)
