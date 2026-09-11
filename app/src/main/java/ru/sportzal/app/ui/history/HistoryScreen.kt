package ru.sportzal.app.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import ru.sportzal.app.model.CompletionStatus

@Composable fun HistoryScreen(state: HistoryUiState, onRetry: () -> Unit, onOpen: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("История", style = MaterialTheme.typography.headlineLarge) }
        state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = onRetry) { Text("Повторить") } } }
        if (!state.loading && state.error == null && state.workouts.isEmpty()) item { Text("История пока пуста") }
        items(state.workouts, key = { it.workoutId }) { workout ->
            Card(Modifier.fillMaxWidth().clickable { onOpen(workout.workoutId) }) { Column(Modifier.padding(16.dp)) {
                Text("${formatHistoryDate(workout.startedAt)} · ${workout.title}", style = MaterialTheme.typography.titleMedium)
                Text("${workout.durationSeconds / 60} мин" + if (workout.completionStatus == CompletionStatus.ENDED_EARLY) " · досрочно" else "")
            } }
        }
    }
}

internal fun formatHistoryDate(timestamp: String, now: ZonedDateTime = ZonedDateTime.now()): String {
    val date = Instant.parse(timestamp).atZone(ZoneId.systemDefault())
    val pattern = if (date.year == now.year) "dd MMM" else "dd MMM yyyy"
    return date.format(DateTimeFormatter.ofPattern(pattern, Locale("ru"))).replace(".", "")
}
