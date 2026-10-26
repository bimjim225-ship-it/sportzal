package ru.sportzal.app.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import ru.sportzal.app.R
import ru.sportzal.app.model.CompletionStatus
import ru.sportzal.app.ui.components.*

@Composable fun HistoryScreen(state: HistoryUiState, onRetry: () -> Unit, onOpen: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { AppHeader("История", "Ваши занятия") }
        state.error?.let { item { StatusNotice(it, error = true, actionLabel = "Повторить", onAction = onRetry) } }
        if (state.loading) item { LoadingState("Загрузка истории") }
        if (!state.loading && state.error == null && state.workouts.isEmpty()) item {
            StatusNotice("История пока пуста", "После завершения тренировки здесь появятся план, результаты и заметки.")
        }
        items(state.workouts, key = { it.workoutId }) { workout ->
            Card(onClick = { onOpen(workout.workoutId) }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Icon(painterResource(R.drawable.sz_ic_today), null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(formatHistoryDate(workout.startedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(workout.title, style = MaterialTheme.typography.titleMedium)
                        Text("${workout.durationSeconds / 60} мин", style = MaterialTheme.typography.bodyMedium)
                        Text(if (workout.completionStatus == CompletionStatus.ENDED_EARLY) "Досрочно" else "Завершена",
                            style = MaterialTheme.typography.labelLarge)
                    }
                    Icon(painterResource(R.drawable.sz_ic_chevron_right), null, Modifier.size(20.dp))
                }
            }
        }
    }
}

internal fun formatHistoryDate(timestamp: String, now: ZonedDateTime = ZonedDateTime.now()): String {
    val date = Instant.parse(timestamp).atZone(ZoneId.systemDefault())
    val pattern = if (date.year == now.year) "dd MMM" else "dd MMM yyyy"
    return date.format(DateTimeFormatter.ofPattern(pattern, Locale("ru"))).replace(".", "")
}
