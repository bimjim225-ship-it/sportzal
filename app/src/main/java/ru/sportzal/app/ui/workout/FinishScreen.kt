package ru.sportzal.app.ui.workout

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Duration
import java.time.Instant

@Composable fun FinishScreen(state: FinishUiState, onShare: () -> Unit, onSave: () -> Unit, onClose: () -> Unit,
    error: String? = null) {
    val seconds = Duration.between(Instant.parse(state.startedAt), Instant.parse(state.finishedAt)).seconds.coerceAtLeast(0)
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Тренировка завершена", style = MaterialTheme.typography.headlineLarge)
        Text("Длительность: %d:%02d".format(seconds / 60, seconds % 60))
        Text("Рабочих подходов: ${state.workSetCount}")
        state.exercises.forEach { (title, count) -> Text("$title — $count подхода") }
        if (state.hasUnresolved) Text("Остались невыполненные подходы")
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = onShare) { Text("Отправить JSON") }
        Button(onClick = onSave) { Text("Сохранить JSON") }
        TextButton(onClick = onClose) { Text("Закрыть") }
    }
}
