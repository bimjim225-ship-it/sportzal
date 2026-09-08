package ru.sportzal.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.sportzal.app.data.files.ImportPreview
import ru.sportzal.app.domain.ConsumedWorkout
import ru.sportzal.app.domain.PlannedWorkout
import ru.sportzal.app.domain.TodaySelection
import ru.sportzal.app.domain.TodaySelector
import ru.sportzal.app.model.ImportResult
import ru.sportzal.app.ui.theme.SportzalTheme
import ru.sportzal.app.ui.today.TodayScreen

class MainActivity : ComponentActivity() {
    private var pickedUri by mutableStateOf<Uri?>(null)
    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { pickedUri = it }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as SportzalApplication).container
        setContent {
            SportzalTheme {
                val scope = rememberCoroutineScope()
                var selection by remember { mutableStateOf<TodaySelection>(TodaySelection.NoProgram) }
                var choices by remember { mutableStateOf(emptyList<PlannedWorkout>()) }
                var preview by remember { mutableStateOf<ImportPreview?>(null) }
                var message by remember { mutableStateOf<String?>(null) }
                var manual by remember { mutableStateOf<String?>(null) }

                suspend fun refresh() {
                    val state = container.database.dao().observeState().first()
                    val active = container.repository.observeActiveWorkout().first()
                    val programId = state?.activeProgramId
                    val version = state?.activeProgramVersion
                    choices = if (programId != null && version != null) {
                        container.database.dao().plannedWorkouts(programId, version).map {
                            PlannedWorkout(
                                it.programId,
                                it.programVersion,
                                it.workoutInstanceId,
                                it.title,
                                LocalDate.parse(it.plannedDate),
                                it.plannedOrder,
                            )
                        }
                    } else {
                        emptyList()
                    }
                    val consumed = container.database.dao().consumedWorkouts()
                        .map { ConsumedWorkout(it.programId, it.workoutInstanceId) }.toSet()
                    selection = TodaySelector().select(active, if (programId == null) null else choices, consumed, manual)
                }

                LaunchedEffect(Unit) { refresh() }
                LaunchedEffect(pickedUri) {
                    pickedUri?.let { preview = container.programImporter.import(it) }
                }
                TodayScreen(
                    selection = selection,
                    futureChoices = choices.filter { it.plannedDate.isAfter(LocalDate.now()) },
                    preview = preview,
                    message = message,
                    onPickProgram = { picker.launch(arrayOf("application/json", "text/json", "text/plain")) },
                    onConfirmImport = { valid ->
                        scope.launch {
                            message = when (container.programImporter.confirm(valid)) {
                                ImportResult.Imported -> "Программа импортирована"
                                ImportResult.NoOp -> "Эта версия уже импортирована"
                                ImportResult.Conflict -> "Конфликт: версия содержит другие данные"
                            }
                            preview = null
                            refresh()
                        }
                    },
                    onChooseWorkout = { manual = it; scope.launch { refresh() } },
                    onStart = {
                        val ready = selection as? TodaySelection.Ready
                        if (ready != null) scope.launch {
                            runCatching {
                                container.workoutService.start(
                                    ready.workout.programId,
                                    ready.workout.programVersion,
                                    ready.workout.workoutInstanceId,
                                )
                            }.onFailure { message = it.message }
                            refresh()
                        }
                    },
                    onResume = { message = "Тренировка уже активна" },
                )
            }
        }
    }
}
