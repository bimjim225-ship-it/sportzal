package ru.sportzal.app.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.sportzal.app.data.files.ImportPreview
import ru.sportzal.app.domain.PlannedWorkout
import ru.sportzal.app.domain.TodaySelection

@Composable
fun TodayScreen(
    selection: TodaySelection,
    manualChoices: List<PlannedWorkout>,
    preview: ImportPreview?,
    message: String?,
    onPickProgram: () -> Unit,
    onConfirmImport: (ImportPreview.Valid) -> Unit,
    onChooseWorkout: (String) -> Unit,
    onStart: () -> Unit,
    onResume: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Сегодня", style = MaterialTheme.typography.headlineLarge) }
        item {
            when (selection) {
                TodaySelection.NoProgram -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Нет активной программы")
                    Button(onClick = onPickProgram) { Text("Импортировать программу") }
                }
                TodaySelection.Exhausted -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("План выполнен")
                    OutlinedButton(onClick = onPickProgram) { Text("Импортировать программу") }
                }
                is TodaySelection.Resume -> Button(onClick = onResume) { Text("Продолжить тренировку") }
                is TodaySelection.Ready -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(selection.workout.title, style = MaterialTheme.typography.titleLarge)
                    Text("Запланировано: ${selection.workout.plannedDate}")
                    Button(onClick = onStart) { Text("Начать") }
                }
            }
        }
        if (selection is TodaySelection.Ready || selection is TodaySelection.Resume) {
            item { OutlinedButton(onClick = onPickProgram) { Text("Импортировать программу") } }
        }
        if (manualChoices.isNotEmpty() && selection !is TodaySelection.Resume) {
            item { Text("Выбрать другую тренировку", style = MaterialTheme.typography.titleMedium) }
            items(manualChoices, key = { it.workoutInstanceId }) { workout ->
                OutlinedButton(onClick = { onChooseWorkout(workout.workoutInstanceId) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(workout.title); Text(workout.plannedDate.toString())
                    }
                }
            }
        }
        when (preview) {
            is ImportPreview.Valid -> item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Предпросмотр: ${preview.program.workouts.size} тренировок")
                    Text("Программа ${preview.program.programId}, версия ${preview.program.programVersion}")
                    Button(onClick = { onConfirmImport(preview) }) { Text("Импортировать") }
                }
            }
            is ImportPreview.Error -> item { Text(preview.message, color = MaterialTheme.colorScheme.error) }
            null -> Unit
        }
        message?.let { item { Text(it) } }
    }
}
