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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable fun FinishScreen(summary: FinishSummary, error: String?, preparing: Boolean = false,
    onShare: () -> Unit, onSave: () -> Unit, onClose: () -> Unit) {
    Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Тренировка завершена", style = MaterialTheme.typography.headlineMedium)
        Text("Длительность: ${formatDuration(summary.durationSeconds)}")
        Text("Рабочих подходов: ${summary.workSetCount}")
        summary.exercises.forEach { Text("${it.title} — ${it.workSetCount} подходов") }
        if (summary.endedEarly) Text("Остались невыполненные подходы")
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = onShare, enabled = !preparing) { Text(if (preparing) "Подготовка JSON…" else "Отправить JSON") }
        Button(onClick = onSave, enabled = !preparing) { Text("Сохранить JSON") }
        Button(onClick = onClose) { Text("Закрыть") }
    }
}

internal fun formatDuration(seconds: Long): String = if (seconds >= 3600) {
    "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
} else "%02d:%02d".format(seconds / 60, seconds % 60)
